package ru.krestnikov.pushfilter.ui

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import ru.krestnikov.pushfilter.R
import ru.krestnikov.pushfilter.appContainer
import ru.krestnikov.pushfilter.ui.log.LogScreen
import ru.krestnikov.pushfilter.ui.rules.RuleEditorScreen
import ru.krestnikov.pushfilter.ui.rules.RulesScreen
import ru.krestnikov.pushfilter.ui.settings.SettingsScreen

/**
 * What the rule editor should open with. A zero [ruleId] means a brand new rule; the
 * remaining fields are pre-filled when the editor is opened from a log entry.
 */
@Parcelize
data class EditorTarget(
    val ruleId: Long = 0L,
    val packageName: String? = null,
    val pattern: String? = null,
    val sampleTitle: String? = null,
    val sampleText: String? = null,
) : Parcelable

private enum class Tab(@param:StringRes val titleRes: Int) {
    RULES(R.string.nav_rules),
    LOG(R.string.nav_log),
    SETTINGS(R.string.nav_settings),
}

private val Tab.icon: ImageVector
    get() = when (this) {
        Tab.RULES -> Icons.AutoMirrored.Filled.List
        Tab.LOG -> Icons.Filled.Notifications
        Tab.SETTINGS -> Icons.Filled.Settings
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushFilterApp() {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(Tab.RULES) }
    var editorTarget by rememberSaveable { mutableStateOf<EditorTarget?>(null) }
    var confirmClearLog by rememberSaveable { mutableStateOf(false) }

    val target = editorTarget
    if (target != null) {
        BackHandler { editorTarget = null }
        RuleEditorScreen(target = target, onClose = { editorTarget = null })
        return
    }

    Scaffold(
        // The activity draws edge to edge, so the system no longer resizes the window for the
        // keyboard: without this the bottom bar and the fab stay hidden underneath it.
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tab.titleRes)) },
                actions = {
                    if (tab == Tab.LOG) {
                        IconButton(onClick = { confirmClearLog = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.log_clear))
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.titleRes)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.RULES) {
                ExtendedFloatingActionButton(
                    onClick = { editorTarget = EditorTarget() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.rules_add)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (tab) {
            Tab.RULES -> RulesScreen(
                contentPadding = padding,
                onEditRule = { editorTarget = it },
            )

            Tab.LOG -> LogScreen(
                contentPadding = padding,
                snackbarHostState = snackbarHostState,
                onCreateRule = { editorTarget = it },
            )

            Tab.SETTINGS -> SettingsScreen(
                contentPadding = padding,
                snackbarHostState = snackbarHostState,
            )
        }
    }

    if (confirmClearLog) {
        AlertDialog(
            onDismissRequest = { confirmClearLog = false },
            title = { Text(stringResource(R.string.log_clear_title)) },
            text = { Text(stringResource(R.string.log_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearLog = false
                        scope.launch(Dispatchers.IO) { container.log.clear() }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearLog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
