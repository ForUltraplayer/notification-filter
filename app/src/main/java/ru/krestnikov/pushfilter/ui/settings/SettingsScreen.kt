package ru.krestnikov.pushfilter.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.krestnikov.pushfilter.R
import ru.krestnikov.pushfilter.appContainer
import ru.krestnikov.pushfilter.data.BackupData
import ru.krestnikov.pushfilter.data.LogStats
import ru.krestnikov.pushfilter.data.RulesBackup
import ru.krestnikov.pushfilter.ui.NotificationAccess
import ru.krestnikov.pushfilter.ui.common.NoticeCard
import ru.krestnikov.pushfilter.ui.common.SectionHeader
import ru.krestnikov.pushfilter.ui.common.StatColumn
import ru.krestnikov.pushfilter.ui.common.SwitchRow
import ru.krestnikov.pushfilter.ui.rememberNotificationAccessGranted

private val RETENTION_OPTIONS = listOf(1, 7, 30, 0)

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val scope = rememberCoroutineScope()

    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val rules by container.rules.rules.collectAsStateWithLifecycle()
    val stats by remember { container.log.observeStats() }
        .collectAsStateWithLifecycle(LogStats(0, 0))
    val ignored by container.ignored.ignored.collectAsStateWithLifecycle()
    val accessGranted = rememberNotificationAccessGranted()

    var pendingImport by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showIgnored by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val exported = withContext(Dispatchers.IO) {
                val data = BackupData(
                    rules = container.rules.snapshot(),
                    ignoredApps = container.ignored.ignored.value,
                    settings = container.settings.current(),
                )
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(RulesBackup.encode(data).toByteArray())
                }
                data.rules.size
            }
            snackbarHostState.showSnackbar(
                context.getString(R.string.settings_export_done, exported),
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImport = uri }

    val runImport: (Uri, Boolean) -> Unit = { uri, replace ->
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                        ?: return@runCatching null
                    val data = RulesBackup.decode(json)
                    container.ignored.replaceAll(
                        if (replace) {
                            data.ignoredApps
                        } else {
                            container.ignored.ignored.value + data.ignoredApps
                        },
                    )
                    // A switch cannot be merged with another switch, so the settings travel with
                    // the replacing import only: adding rules leaves this phone as it was.
                    if (replace) {
                        data.settings?.let { restored ->
                            container.settings.update { restored }
                            container.log.cleanup()
                        }
                    }
                    container.rules.insertAll(data.rules, replace)
                }.getOrNull()
            }
            snackbarHostState.showSnackbar(
                if (imported == null) {
                    context.getString(R.string.settings_backup_error)
                } else {
                    context.getString(R.string.settings_import_done, imported)
                },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionHeader(stringResource(R.string.settings_section_service))
        if (!accessGranted) {
            NoticeCard(
                title = stringResource(R.string.access_required_title),
                body = stringResource(R.string.access_required_body),
                actionLabel = stringResource(R.string.access_grant),
                onAction = { NotificationAccess.openSettings(context) },
            )
            Spacer(Modifier.height(8.dp))
        }
        Card(Modifier.fillMaxWidth()) {
            SwitchRow(
                title = stringResource(R.string.settings_filtering),
                subtitle = stringResource(R.string.settings_filtering_hint),
                checked = settings.filteringEnabled,
                onCheckedChange = { value ->
                    container.settings.update { it.copy(filteringEnabled = value) }
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader(stringResource(R.string.settings_section_capture))
        Card(Modifier.fillMaxWidth()) {
            SwitchRow(
                title = stringResource(R.string.settings_ignore_ongoing),
                subtitle = stringResource(R.string.settings_ignore_ongoing_hint),
                checked = settings.ignoreOngoing,
                onCheckedChange = { value ->
                    container.settings.update { it.copy(ignoreOngoing = value) }
                },
            )
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.settings_ignore_group),
                subtitle = stringResource(R.string.settings_ignore_group_hint),
                checked = settings.ignoreGroupSummary,
                onCheckedChange = { value ->
                    container.settings.update { it.copy(ignoreGroupSummary = value) }
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader(stringResource(R.string.settings_section_log))
        Card(Modifier.fillMaxWidth()) {
            SwitchRow(
                title = stringResource(R.string.settings_log_passed),
                subtitle = stringResource(R.string.settings_log_passed_hint),
                checked = settings.logAllowed,
                onCheckedChange = { value ->
                    container.settings.update { it.copy(logAllowed = value) }
                },
            )
            HorizontalDivider()
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_retention),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RETENTION_OPTIONS.forEach { days ->
                        FilterChip(
                            selected = settings.retentionDays == days,
                            onClick = {
                                container.settings.update { it.copy(retentionDays = days) }
                                scope.launch(Dispatchers.IO) { container.log.cleanup() }
                            },
                            label = {
                                Text(
                                    if (days == 0) {
                                        stringResource(R.string.settings_retention_forever)
                                    } else {
                                        pluralStringResource(R.plurals.retention_days, days, days)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader(stringResource(R.string.settings_section_ignored))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_ignored_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (ignored.isEmpty()) {
                        stringResource(R.string.settings_ignored_none)
                    } else {
                        pluralStringResource(R.plurals.ignored_count, ignored.size, ignored.size)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { showIgnored = true }) {
                    Text(stringResource(R.string.settings_ignored_manage))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader(stringResource(R.string.settings_section_backup))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_export_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("push-filter-rules.json") },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.settings_export)) }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.settings_import)) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader(stringResource(R.string.settings_section_stats))
        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatColumn(
                    value = stats.blocked.toString(),
                    label = stringResource(R.string.settings_stat_blocked),
                )
                StatColumn(
                    value = stats.allowed.toString(),
                    label = stringResource(R.string.settings_stat_passed),
                )
                StatColumn(
                    value = rules.size.toString(),
                    label = stringResource(R.string.settings_stat_rules),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader(stringResource(R.string.settings_section_about))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_about_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_version, appVersion(context)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showIgnored) {
        IgnoredAppsDialog(onDismiss = { showIgnored = false })
    }

    val importUri = pendingImport
    if (importUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.settings_import_title)) },
            text = { Text(stringResource(R.string.settings_import_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImport = null
                        runImport(importUri, true)
                    },
                ) { Text(stringResource(R.string.settings_import_replace)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingImport = null
                        runImport(importUri, false)
                    },
                ) { Text(stringResource(R.string.settings_import_merge)) }
            },
        )
    }
}

@Composable
private fun appVersion(context: android.content.Context): String = remember(context) {
    runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}
