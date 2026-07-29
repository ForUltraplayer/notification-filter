package ru.krestnikov.pushfilter.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.krestnikov.pushfilter.R
import ru.krestnikov.pushfilter.appContainer
import ru.krestnikov.pushfilter.data.ALL_APPS
import ru.krestnikov.pushfilter.data.FilterRule
import ru.krestnikov.pushfilter.data.MatchTarget
import ru.krestnikov.pushfilter.ui.EditorTarget
import ru.krestnikov.pushfilter.ui.NotificationAccess
import ru.krestnikov.pushfilter.ui.common.AppIcon
import ru.krestnikov.pushfilter.ui.common.EmptyState
import ru.krestnikov.pushfilter.ui.common.Label
import ru.krestnikov.pushfilter.ui.common.ModeBadge
import ru.krestnikov.pushfilter.ui.common.NoticeCard
import ru.krestnikov.pushfilter.ui.common.cardPadding
import ru.krestnikov.pushfilter.ui.rememberNotificationAccessGranted

private data class RuleGroup(
    val packageName: String,
    val label: String,
    val rules: List<FilterRule>,
    val ignoredApp: Boolean,
)

@Composable
fun RulesScreen(
    contentPadding: PaddingValues,
    onEditRule: (EditorTarget) -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val scope = rememberCoroutineScope()

    val rules by container.rules.rules.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val ignored by container.ignored.ignored.collectAsStateWithLifecycle()
    val accessGranted = rememberNotificationAccessGranted()
    var pendingDelete by remember { mutableStateOf<FilterRule?>(null) }

    val groups = remember(rules, ignored) {
        rules.groupBy { it.packageName }
            .map { (packageName, grouped) ->
                RuleGroup(
                    packageName = packageName,
                    label = if (packageName == ALL_APPS) "" else container.apps.label(packageName),
                    rules = grouped,
                    ignoredApp = packageName in ignored,
                )
            }
            .sortedWith(compareBy({ it.packageName != ALL_APPS }, { it.label.lowercase() }))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = cardPadding(contentPadding, extraBottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!accessGranted) {
            item(key = "access") {
                NoticeCard(
                    title = stringResource(R.string.access_required_title),
                    body = stringResource(R.string.access_required_body),
                    actionLabel = stringResource(R.string.access_grant),
                    onAction = { NotificationAccess.openSettings(context) },
                )
            }
        } else if (!settings.filteringEnabled) {
            item(key = "paused") {
                NoticeCard(
                    title = stringResource(R.string.paused_title),
                    body = stringResource(R.string.paused_body),
                    actionLabel = stringResource(R.string.paused_resume),
                    emphasised = false,
                    onAction = {
                        container.settings.update { it.copy(filteringEnabled = true) }
                    },
                )
            }
        }

        if (rules.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.rules_empty_title),
                    body = stringResource(R.string.rules_empty_body),
                )
            }
        }

        groups.forEach { group ->
            item(key = "header-${group.packageName}") {
                GroupHeader(group)
            }
            items(group.rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onClick = { onEditRule(EditorTarget(ruleId = rule.id)) },
                    onToggle = { enabled ->
                        scope.launch(Dispatchers.IO) {
                            container.rules.setEnabled(rule.id, enabled)
                        }
                    },
                    onDelete = { pendingDelete = rule },
                )
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_rule_title)) },
            text = { Text(stringResource(R.string.delete_rule_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch(Dispatchers.IO) { container.rules.delete(toDelete.id) }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun GroupHeader(group: RuleGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(group.packageName, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = group.label.ifEmpty { stringResource(R.string.all_apps) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.rules_count,
                    group.rules.size,
                    group.rules.size,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Without this the rules below look broken: the app is skipped before they are read.
        if (group.ignoredApp) {
            Spacer(Modifier.width(8.dp))
            Label(
                text = stringResource(R.string.rule_group_ignored),
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: FilterRule,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.name.ifBlank { stringResource(R.string.rule_untitled) },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    ModeBadge(rule.mode)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rule.pattern,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(4.dp))
                val targetLabel = stringResource(rule.target.labelRes())
                val caseLabel = stringResource(R.string.editor_case_sensitive)
                val disabledLabel = stringResource(R.string.rule_disabled)
                Text(
                    text = buildList {
                        add(targetLabel)
                        if (rule.caseSensitive) add(caseLabel)
                        if (!rule.enabled) add(disabledLabel)
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun MatchTarget.labelRes(): Int = when (this) {
    MatchTarget.TITLE -> R.string.rule_target_title
    MatchTarget.TEXT -> R.string.rule_target_text
    MatchTarget.BOTH -> R.string.rule_target_both
}
