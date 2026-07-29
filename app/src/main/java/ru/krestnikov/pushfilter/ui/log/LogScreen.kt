package ru.krestnikov.pushfilter.ui.log

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.krestnikov.pushfilter.R
import ru.krestnikov.pushfilter.appContainer
import ru.krestnikov.pushfilter.data.LogEntry
import ru.krestnikov.pushfilter.data.LogFilter
import ru.krestnikov.pushfilter.data.LogQuery
import ru.krestnikov.pushfilter.data.escapeRegexLiteral
import ru.krestnikov.pushfilter.ui.EditorTarget
import ru.krestnikov.pushfilter.ui.common.AppIcon
import ru.krestnikov.pushfilter.ui.common.DetailRow
import ru.krestnikov.pushfilter.ui.common.EmptyState
import ru.krestnikov.pushfilter.ui.common.Label
import ru.krestnikov.pushfilter.ui.common.absoluteTime
import ru.krestnikov.pushfilter.ui.common.labelRes
import ru.krestnikov.pushfilter.ui.common.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    onCreateRule: (EditorTarget) -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val scope = rememberCoroutineScope()

    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var search by rememberSaveable { mutableStateOf("") }
    var packageFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var appMenuOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<LogEntry?>(null) }

    val query = LogQuery(filter = filter, packageName = packageFilter, search = search)
    val entries by remember(query) { container.log.observe(query) }
        .collectAsStateWithLifecycle(emptyList())
    val packages by remember { container.log.observePackages() }
        .collectAsStateWithLifecycle(emptyList())

    val anyAppLabel = stringResource(R.string.log_app_any)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(stringResource(R.string.log_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { search = "" }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogFilter.entries.forEach { entry ->
                FilterChip(
                    selected = filter == entry,
                    onClick = { filter = entry },
                    label = { Text(stringResource(entry.labelRes())) },
                )
            }
            Box {
                FilterChip(
                    selected = packageFilter != null,
                    onClick = { appMenuOpen = true },
                    label = {
                        Text(
                            text = packageFilter?.let { container.apps.label(it) } ?: anyAppLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    },
                )
                DropdownMenu(expanded = appMenuOpen, onDismissRequest = { appMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(anyAppLabel) },
                        onClick = {
                            packageFilter = null
                            appMenuOpen = false
                        },
                    )
                    packages.forEach { pkg ->
                        DropdownMenuItem(
                            text = { Text(container.apps.label(pkg)) },
                            leadingIcon = { AppIcon(pkg, size = 24.dp) },
                            onClick = {
                                packageFilter = pkg
                                appMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.log_empty_title),
                body = stringResource(R.string.log_empty_body),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    LogCard(entry = entry, onClick = { detail = entry })
                }
            }
        }
    }

    val current = detail
    if (current != null) {
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            LogDetail(
                entry = current,
                onCopy = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText("notification", "${current.title}\n${current.text}"),
                    )
                    Toast.makeText(context, R.string.log_copied, Toast.LENGTH_SHORT).show()
                },
                onCreateRule = {
                    detail = null
                    onCreateRule(
                        EditorTarget(
                            packageName = current.packageName,
                            pattern = escapeRegexLiteral(current.ruleSeed()),
                            sampleTitle = current.title,
                            sampleText = current.text,
                        ),
                    )
                },
                onIgnoreApp = {
                    detail = null
                    container.ignored.add(current.packageName)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(
                                R.string.log_ignored_done,
                                current.appLabel,
                            ),
                            actionLabel = context.getString(R.string.log_ignored_clear),
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            withContext(Dispatchers.IO) {
                                container.log.clearPackage(current.packageName)
                            }
                        }
                    }
                },
            )
        }
    }
}

/** The most distinctive line of the notification, used to pre-fill a new rule. */
private fun LogEntry.ruleSeed(): String =
    title.ifBlank { text }
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(60)
        .orEmpty()

@Composable
private fun LogCard(entry: LogEntry, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(entry.packageName, size = 24.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${entry.appLabel} · ${relativeTime(entry.postedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                BlockedBadge(entry.blocked)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.title.ifBlank { stringResource(R.string.log_no_title) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.text.isNotBlank()) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BlockedBadge(blocked: Boolean) {
    Label(
        text = stringResource(
            if (blocked) R.string.log_badge_blocked else R.string.log_badge_passed,
        ),
        container = if (blocked) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        content = if (blocked) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    )
}

@Composable
private fun LogDetail(
    entry: LogEntry,
    onCopy: () -> Unit,
    onCreateRule: () -> Unit,
    onIgnoreApp: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(entry.packageName, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = entry.appLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            BlockedBadge(entry.blocked)
        }
        Spacer(Modifier.height(16.dp))

        SelectionContainer {
            Column {
                Text(
                    text = entry.title.ifBlank { stringResource(R.string.log_no_title) },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (entry.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        DetailRow(
            label = stringResource(R.string.log_detail_reason),
            value = stringResource(entry.reason.labelRes()),
        )
        entry.ruleName?.takeIf { it.isNotBlank() }?.let {
            DetailRow(label = stringResource(R.string.log_detail_rule), value = it)
        }
        entry.rulePattern?.takeIf { it.isNotBlank() }?.let {
            DetailRow(label = stringResource(R.string.log_detail_pattern), value = it)
        }
        DetailRow(
            label = stringResource(R.string.log_detail_time),
            value = absoluteTime(context, entry.postedAt),
        )
        DetailRow(
            label = stringResource(R.string.log_detail_package),
            value = entry.packageName,
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onCreateRule, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.log_create_rule))
            }
            OutlinedButton(onClick = onCopy) {
                Text(stringResource(R.string.log_copy))
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onIgnoreApp, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.log_ignore_app))
        }
    }
}

private fun LogFilter.labelRes(): Int = when (this) {
    LogFilter.ALL -> R.string.log_filter_all
    LogFilter.BLOCKED -> R.string.log_filter_blocked
    LogFilter.ALLOWED -> R.string.log_filter_passed
}
