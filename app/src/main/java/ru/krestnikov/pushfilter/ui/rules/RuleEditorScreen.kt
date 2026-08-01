package ru.krestnikov.pushfilter.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.krestnikov.pushfilter.R
import ru.krestnikov.pushfilter.appContainer
import ru.krestnikov.pushfilter.data.ALL_APPS
import ru.krestnikov.pushfilter.data.FilterRule
import ru.krestnikov.pushfilter.data.MatchTarget
import ru.krestnikov.pushfilter.data.RuleMode
import ru.krestnikov.pushfilter.filter.FilterEngine
import ru.krestnikov.pushfilter.filter.NotificationContent
import ru.krestnikov.pushfilter.filter.RegexCache
import ru.krestnikov.pushfilter.ui.EditorTarget
import ru.krestnikov.pushfilter.ui.apps.AppPickerDialog
import ru.krestnikov.pushfilter.ui.common.AppIcon
import ru.krestnikov.pushfilter.ui.common.Label
import ru.krestnikov.pushfilter.ui.common.SectionHeader
import ru.krestnikov.pushfilter.ui.common.SwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(target: EditorTarget, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val scope = rememberCoroutineScope()

    val existing = remember(target.ruleId) {
        if (target.ruleId != 0L) container.rules.byId(target.ruleId) else null
    }

    var packageName by rememberSaveable {
        mutableStateOf(existing?.packageName ?: target.packageName ?: ALL_APPS)
    }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var mode by rememberSaveable { mutableStateOf(existing?.mode ?: RuleMode.BLOCK) }
    var pattern by rememberSaveable { mutableStateOf(existing?.pattern ?: target.pattern.orEmpty()) }
    var matchTarget by rememberSaveable { mutableStateOf(existing?.target ?: MatchTarget.BOTH) }
    var caseSensitive by rememberSaveable { mutableStateOf(existing?.caseSensitive ?: false) }
    var enabled by rememberSaveable { mutableStateOf(existing?.enabled ?: true) }
    var sampleTitle by rememberSaveable { mutableStateOf(target.sampleTitle.orEmpty()) }
    var sampleText by rememberSaveable { mutableStateOf(target.sampleText.orEmpty()) }

    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showEmptyPatternError by rememberSaveable { mutableStateOf(false) }
    // Somebody writing their first rule needs the examples; somebody fixing an old one does not.
    var showExamples by rememberSaveable { mutableStateOf(existing == null) }

    val syntaxError = remember(pattern) {
        if (pattern.isEmpty()) null else RegexCache.errorOf(pattern)
    }
    val emptyPatternMessage = stringResource(R.string.editor_pattern_empty)
    val errorMessage = when {
        syntaxError != null -> syntaxError
        showEmptyPatternError && pattern.isEmpty() -> emptyPatternMessage
        else -> null
    }

    val onSave: () -> Unit = {
        if (pattern.isEmpty()) {
            showEmptyPatternError = true
        } else if (syntaxError == null) {
            val rule = FilterRule(
                id = target.ruleId,
                packageName = packageName,
                name = name.trim(),
                mode = mode,
                pattern = pattern,
                target = matchTarget,
                caseSensitive = caseSensitive,
                enabled = enabled,
                createdAt = existing?.createdAt ?: 0L,
            )
            scope.launch(Dispatchers.IO) { container.rules.save(rule) }
            onClose()
        }
    }

    Scaffold(
        // The activity draws edge to edge, so the keyboard overlaps the window instead of
        // resizing it. Without this the fields at the bottom cannot be scrolled into view.
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (existing == null) R.string.editor_title_new else R.string.editor_title_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                        )
                    }
                },
                // Saving lives in the bar rather than in a fab: every field here opens the
                // keyboard, and a bottom button would spend most of its life behind it.
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                        }
                    }
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader(stringResource(R.string.editor_app_label))
            AppSelector(packageName = packageName, onClick = { showPicker = true })

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.editor_name_label)) },
                placeholder = { Text(stringResource(R.string.editor_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.editor_mode_label))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RuleMode.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = mode == entry,
                        onClick = { mode = entry },
                        shape = SegmentedButtonDefaults.itemShape(index, RuleMode.entries.size),
                        label = {
                            Text(
                                text = stringResource(
                                    if (entry == RuleMode.BLOCK) {
                                        R.string.rule_mode_block
                                    } else {
                                        R.string.rule_mode_allow
                                    },
                                ),
                                maxLines = 2,
                            )
                        },
                    )
                }
            }
            Text(
                text = stringResource(
                    if (mode == RuleMode.BLOCK) {
                        R.string.editor_mode_block_hint
                    } else {
                        R.string.editor_mode_allow_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )

            Spacer(Modifier.height(12.dp))
            SectionHeader(stringResource(R.string.editor_target_label))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                MatchTarget.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = matchTarget == entry,
                        onClick = { matchTarget = entry },
                        shape = SegmentedButtonDefaults.itemShape(index, MatchTarget.entries.size),
                        label = { Text(stringResource(entry.labelRes()), maxLines = 2) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pattern,
                onValueChange = {
                    pattern = it
                    showEmptyPatternError = false
                },
                label = { Text(stringResource(R.string.editor_pattern_label)) },
                placeholder = { Text(stringResource(R.string.editor_pattern_placeholder)) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                isError = errorMessage != null,
                supportingText = {
                    Text(errorMessage ?: stringResource(R.string.editor_pattern_help))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            PatternExamples(expanded = showExamples, onToggle = { showExamples = !showExamples })

            SwitchRow(
                title = stringResource(R.string.editor_case_sensitive),
                checked = caseSensitive,
                onCheckedChange = { caseSensitive = it },
            )
            SwitchRow(
                title = stringResource(R.string.editor_enabled),
                checked = enabled,
                onCheckedChange = { enabled = it },
            )

            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.editor_tester_label))
            Tester(
                sampleTitle = sampleTitle,
                sampleText = sampleText,
                onSampleTitleChange = { sampleTitle = it },
                onSampleTextChange = { sampleText = it },
                rule = FilterRule(
                    packageName = packageName,
                    mode = mode,
                    pattern = pattern,
                    target = matchTarget,
                    caseSensitive = caseSensitive,
                ),
                patternValid = pattern.isNotEmpty() && syntaxError == null,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPicker) {
        AppPickerDialog(
            selected = setOf(packageName),
            onPick = {
                packageName = it
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_rule_title)) },
            text = { Text(stringResource(R.string.delete_rule_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch(Dispatchers.IO) { container.rules.delete(existing.id) }
                        onClose()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun AppSelector(packageName: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val allAppsLabel = stringResource(R.string.all_apps)
    val label = remember(packageName, allAppsLabel) {
        if (packageName == ALL_APPS) allAppsLabel else context.appContainer().apps.label(packageName)
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(packageName, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (packageName == ALL_APPS) {
                        stringResource(R.string.picker_all_apps_hint)
                    } else {
                        packageName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.editor_app_choose),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Pattern and the plain words explaining it, in the order they are shown. */
private val PATTERN_EXAMPLES = listOf(
    R.string.editor_example_word to R.string.editor_example_word_hint,
    R.string.editor_example_any to R.string.editor_example_any_hint,
    R.string.editor_example_start to R.string.editor_example_start_hint,
    R.string.editor_example_digits to R.string.editor_example_digits_hint,
)

@Composable
private fun PatternExamples(expanded: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = onToggle) {
            Text(stringResource(R.string.editor_examples_title))
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
            )
        }
        if (!expanded) return@Column

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp)) {
                PATTERN_EXAMPLES.forEachIndexed { index, (pattern, hint) ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(pattern),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.editor_examples_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Tester(
    sampleTitle: String,
    sampleText: String,
    onSampleTitleChange: (String) -> Unit,
    onSampleTextChange: (String) -> Unit,
    rule: FilterRule,
    patternValid: Boolean,
) {
    val sample = NotificationContent(sampleTitle.trim(), sampleText.trim())
    val matched = patternValid && FilterEngine.matches(rule, sample)
    val wouldBlock = if (rule.mode == RuleMode.BLOCK) matched else !matched

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.editor_tester_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = sampleTitle,
                onValueChange = onSampleTitleChange,
                label = { Text(stringResource(R.string.editor_tester_title_field)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = sampleText,
                onValueChange = onSampleTextChange,
                label = { Text(stringResource(R.string.editor_tester_text_field)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!sample.isEmpty && patternValid) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(
                        text = stringResource(
                            if (matched) R.string.tester_matched else R.string.tester_not_matched,
                        ),
                        container = if (matched) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        content = if (matched) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(
                            if (wouldBlock) R.string.tester_would_block else R.string.tester_would_pass,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (wouldBlock) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}
