// @file SimpleFilterApp.kt
// @author: ForUltraplayer
// @brief Simple Korean keyword filtering and app exclusions UI.
package ru.krestnikov.pushfilter.ui

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.krestnikov.pushfilter.appContainer
import ru.krestnikov.pushfilter.apps.InstalledApp
import ru.krestnikov.pushfilter.data.FilterRule
import ru.krestnikov.pushfilter.data.KeywordError
import ru.krestnikov.pushfilter.data.KeywordRules
import ru.krestnikov.pushfilter.filter.NotificationFilterService
import ru.krestnikov.pushfilter.ui.common.AppIcon

@Composable
fun SimpleFilterApp() {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val rules by container.rules.rules.collectAsStateWithLifecycle()
    val ignored by container.ignored.ignored.collectAsStateWithLifecycle()
    val connected by NotificationFilterService.connected.collectAsStateWithLifecycle()
    val access = rememberNotificationAccessGranted()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var exclusions by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var license by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(access, settings.filteringEnabled) {
        if (access && settings.filteringEnabled && !connected) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(context, NotificationFilterService::class.java),
                )
            }
        }
    }

    if (exclusions) {
        ExcludedAppsScreen(onBack = { exclusions = false })
    } else {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { insets ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(insets),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text("알림 필터", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("필요 없는 알림만 가볍게 정리해요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        when {
                                            !settings.filteringEnabled -> "필터 꺼짐"
                                            !access -> "알림 접근 허용 필요"
                                            !connected -> "알림 연결 확인 중"
                                            rules.isEmpty() -> "키워드를 추가해 주세요"
                                            else -> "필터 켜짐"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(if (settings.filteringEnabled) "ON" else "OFF")
                                }
                                Switch(
                                    checked = settings.filteringEnabled,
                                    modifier = Modifier.semantics { contentDescription = "알림 필터 ON/OFF" },
                                    onCheckedChange = { enabled ->
                                        container.settings.update { it.copy(filteringEnabled = enabled) }
                                    },
                                )
                            }
                            Text("새 알림의 제목이나 내용에 키워드가 있으면 지워요.")
                            if (!access) {
                                Button(onClick = { NotificationAccess.openSettings(context) }) {
                                    Text("알림 접근 허용하기")
                                }
                            } else if (settings.filteringEnabled && !connected) {
                                Text("계속 연결되지 않으면 알림 접근 설정에서 권한을 껐다 켜 주세요.")
                                TextButton(onClick = { NotificationAccess.openSettings(context) }) { Text("설정 열기") }
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("차단 키워드", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text("${rules.size}개", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { editingId = 0L }, modifier = Modifier.fillMaxWidth()) {
                        Text("+ 키워드 추가")
                    }
                }
                if (rules.isEmpty()) {
                    item {
                        Text("예: (광고), 무료체험\n입력한 문구가 포함된 알림을 차단해요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(rules, key = { it.id }) { rule ->
                    Card {
                        Row(
                            Modifier.fillMaxWidth().clickable { editingId = rule.id }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(rule.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { deletingId = rule.id }) { Text("삭제") }
                        }
                    }
                }
                item { BlockHistoryCard() }
                item {
                    OutlinedCard(onClick = { exclusions = true }) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("제외할 앱", style = MaterialTheme.typography.titleMedium)
                            Text(if (ignored.isEmpty()) "차단하지 않을 앱 선택" else "${ignored.size}개 앱은 차단하지 않아요")
                        }
                    }
                }
                item {
                    Text(
                        "차단 기록과 알림 내용은 이 기기에 7일간 보관해요.\n진행 중인 알림과 그룹 대표 알림은 유지해요.\n소리·진동·팝업은 알림이 지워지기 전에 발생할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { license = true }) { Text("오픈소스 안내 · 0.1.0") }
                }
            }
        }
    }
    editingId?.let { id ->
        val original = rules.firstOrNull { it.id == id }
        KeywordDialog(
            original = original,
            rules = rules,
            onDismiss = { editingId = null },
            onSave = { input ->
                withContext(Dispatchers.IO) {
                    val error = KeywordRules.validate(input, container.rules.snapshot(), id)
                    if (error != null) errorText(error) else {
                        check(container.rules.save(KeywordRules.create(input, original)) > 0)
                        null
                    }
                }
            },
        )
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("키워드를 삭제할까요?") },
            text = { Text(rules.firstOrNull { it.id == id }?.name.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    deletingId = null
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { container.rules.delete(id) } }
                        if (result.isFailure) snackbar.showSnackbar("삭제하지 못했어요. 다시 시도해 주세요.")
                    }
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("취소") } },
        )
    }
    if (license) {
        val text by produceState("불러오는 중…") {
            value = withContext(Dispatchers.IO) {
                runCatching { context.assets.open("licenses/push-filter.txt").bufferedReader().use { it.readText() } }
                    .getOrDefault("라이선스를 불러오지 못했어요.")
            }
        }
        AlertDialog(
            onDismissRequest = { license = false },
            title = { Text("오픈소스 안내") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Push Filter 기반\nhttps://github.com/Rai220/push-filter\n수정: ForUltraplayer\n")
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { license = false }) { Text("닫기") } },
        )
    }
}

private fun errorText(error: KeywordError): String = when (error) {
    KeywordError.EMPTY -> "키워드를 입력해 주세요."
    KeywordError.TOO_LONG -> "키워드는 120자 이내로 입력해 주세요."
    KeywordError.DUPLICATE -> "이미 등록한 키워드예요."
}

@Composable
private fun KeywordDialog(
    original: FilterRule?,
    rules: List<FilterRule>,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> String?,
) {
    var input by rememberSaveable(original?.id) { mutableStateOf(original?.name.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save() {
        if (saving) return
        val validation = KeywordRules.validate(input, rules, original?.id ?: 0L)
        if (validation != null) { error = errorText(validation); return }
        saving = true
        scope.launch {
            error = runCatching { onSave(input) }.getOrElse { "저장하지 못했어요. 다시 시도해 주세요." }
            saving = false
            if (error == null) onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (original == null) "키워드 추가" else "키워드 수정") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null },
                label = { Text("차단할 문구") },
                placeholder = { Text("(광고)") },
                supportingText = { Text(error ?: "앞뒤 공백을 제외하고 그대로 검색해요.") },
                isError = error != null,
                singleLine = true,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
        },
        confirmButton = { TextButton(onClick = { save() }, enabled = !saving) { Text(if (saving) "저장 중…" else "저장") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("취소") } },
    )
}

@Composable
private fun ExcludedAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val ignored by container.ignored.ignored.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    LaunchedEffect(attempt) {
        failed = false
        apps = null
        runCatching { container.apps.installedApps().filter { it.packageName != context.packageName } }
            .onSuccess { apps = it }.onFailure { failed = true }
    }
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("제외할 앱", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("완료") }
            }
            Text("선택한 앱의 알림은 차단하지 않아요.\n선택하면 바로 저장돼요.")
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("앱 검색") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
            val loaded = apps
            when {
                failed -> {
                    Text("앱 목록을 불러오지 못했어요.")
                    TextButton(onClick = { attempt++ }) { Text("다시 시도") }
                }
                loaded == null -> CircularProgressIndicator()
                else -> {
                    val filtered = remember(loaded, query) {
                        loaded.filter { it.label.contains(query.trim(), true) || it.packageName.contains(query.trim(), true) }
                    }
                    if (filtered.isEmpty()) Text("검색 결과가 없어요.")
                    LazyColumn(Modifier.weight(1f)) {
                        items(filtered, key = { it.packageName }) { app ->
                            val checked = app.packageName in ignored
                            Row(
                                Modifier.fillMaxWidth().toggleable(
                                    value = checked, role = Role.Checkbox,
                                    onValueChange = { if (it) container.ignored.add(app.packageName) else container.ignored.remove(app.packageName) },
                                ).padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.packageName)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Checkbox(checked = checked, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
