// @file BlockHistory.kt
// @author: ForUltraplayer
// @brief Display block outcomes and inspect locally retained notification content.
package ru.krestnikov.pushfilter.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import ru.krestnikov.pushfilter.appContainer
import ru.krestnikov.pushfilter.data.BlockStatus
import ru.krestnikov.pushfilter.data.LogEntry
import ru.krestnikov.pushfilter.data.LogQuery
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private data class HistorySnapshot(
    val entries: List<LogEntry> = emptyList(),
    val confirmed: Int = 0,
    val loading: Boolean = true,
    val error: Boolean = false,
)

@Composable
fun BlockHistoryCard() {
    val context = LocalContext.current
    val repository = remember(context) { context.appContainer().log }
    val historyFlow = remember(repository) {
        combine(repository.observe(LogQuery()), repository.observeStats()) { entries, stats ->
            HistorySnapshot(entries.filter { it.blockStatus != null }, stats.blocked, loading = false)
        }.catch { emit(HistorySnapshot(loading = false, error = true)) }
    }
    val state by historyFlow.collectAsStateWithLifecycle(initialValue = HistorySnapshot())
    val writeFailure by repository.hasWriteFailure.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var clearConfirmation by rememberSaveable { mutableStateOf(false) }
    var selectedEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, repository) {
        repository.refreshBlockHistory()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) repository.refreshBlockHistory()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Card {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("차단 기록", style = MaterialTheme.typography.titleMedium)
            when {
                state.loading -> Text("기록을 불러오는 중…")
                state.error -> Text("기록을 불러오지 못했어요. 앱을 다시 열어 주세요.")
                else -> {
                    Text("삭제 확인 ${state.confirmed}건", style = MaterialTheme.typography.headlineSmall)
                    Text("보관 중인 최근 7일 기록 기준", style = MaterialTheme.typography.bodySmall)
                    if (state.entries.isEmpty()) Text("아직 차단 기록이 없어요.")
                    state.entries.firstOrNull()?.let { latest ->
                        Text("최근: ${latest.appLabel} · ${statusLabel(latest.blockStatus)}")
                        Text(formatTime(latest.postedAt), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (writeFailure) Text("기록 저장에 문제가 있어 일부 내역이 없을 수 있어요.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { expanded = true }) { Text("기록 보기") }
        }
    }
    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Scaffold { insets ->
                Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("차단 기록", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { expanded = false }) { Text("닫기") }
                    }
                    Text("차단 대상으로 잡힌 알림의 제목과 본문을 이 기기에 저장해요. 기록을 누르면 내용을 볼 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                    Text("최근 7일, 최대 5,000건 보관 · 최근 500건 표시", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "삭제 확인은 시스템의 삭제 통지를 받은 기록이에요. 확인되지 않은 요청은 성공 횟수에 포함하지 않아요.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    if (writeFailure || state.error) Text("기록을 모두 확인하지 못할 수 있어요.", color = MaterialTheme.colorScheme.error)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("삭제 확인 ${state.confirmed}건", modifier = Modifier.weight(1f))
                        TextButton(onClick = { clearConfirmation = true }, enabled = !state.loading && !state.error) { Text("전체 삭제") }
                    }
                    if (state.loading) CircularProgressIndicator()
                    else if (!state.error && state.entries.isEmpty()) {
                        Text("아직 차단 기록이 없어요. 알림 접근 허용과 필터 ON 상태를 확인해 주세요.")
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                        items(state.entries, key = { it.id }) { entry ->
                            OutlinedCard(onClick = { selectedEntryId = entry.id }) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(entry.appLabel, style = MaterialTheme.typography.titleMedium)
                                    if (entry.title.isNotEmpty()) {
                                        Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("내용 보기", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(formatTime(entry.postedAt), style = MaterialTheme.typography.bodySmall)
                                    Text("키워드: ${entry.ruleName.orEmpty()}")
                                    Text(statusLabel(entry.blockStatus), color = if (entry.blockStatus == BlockStatus.CONFIRMED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val selectedEntry = state.entries.firstOrNull { it.id == selectedEntryId }
    if (selectedEntry != null) {
        AlertDialog(
            onDismissRequest = { selectedEntryId = null },
            title = { Text("알림 내용") },
            text = {
                SelectionContainer {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(selectedEntry.appLabel, style = MaterialTheme.typography.titleMedium)
                        Text(formatTime(selectedEntry.postedAt))
                        Text("키워드: ${selectedEntry.ruleName.orEmpty()}")
                        Text(statusLabel(selectedEntry.blockStatus))
                        HorizontalDivider()
                        if (selectedEntry.title.isEmpty() && selectedEntry.text.isEmpty()) {
                            Text("저장된 내용이 없어요. 이전 테스트 버전에서 생성한 기록에는 제목과 본문이 없을 수 있어요.")
                        } else {
                            Text("제목", style = MaterialTheme.typography.labelLarge)
                            Text(selectedEntry.title.ifEmpty { "제목 없음" })
                            Text("본문", style = MaterialTheme.typography.labelLarge)
                            Text(selectedEntry.text.ifEmpty { "본문 없음" })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedEntryId = null }) { Text("닫기") } },
        )
    }
    if (clearConfirmation) {
        AlertDialog(
            onDismissRequest = { clearConfirmation = false },
            title = { Text("차단 기록을 모두 삭제할까요?") },
            text = { Text("표시된 삭제 확인 횟수도 초기화돼요. 키워드와 제외 앱 설정은 유지돼요.") },
            confirmButton = {
                TextButton(onClick = { repository.clearBlockHistory(); clearConfirmation = false }) { Text("전체 삭제") }
            },
            dismissButton = { TextButton(onClick = { clearConfirmation = false }) { Text("취소") } },
        )
    }
}

private fun statusLabel(status: BlockStatus?): String = when (status) {
    BlockStatus.REQUESTED -> "삭제 요청 · 확인 중"
    BlockStatus.CONFIRMED -> "삭제 확인"
    BlockStatus.UNCONFIRMED -> "삭제 요청 · 확인 안 됨"
    BlockStatus.FAILED -> "삭제 요청 실패"
    null -> "확인 정보 없음"
}

private fun formatTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.KOREA).format(Date(timestamp))
