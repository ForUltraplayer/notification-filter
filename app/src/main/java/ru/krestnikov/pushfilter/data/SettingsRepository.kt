package ru.krestnikov.pushfilter.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val filteringEnabled: Boolean = true,
    val logAllowed: Boolean = true,
    val ignoreOngoing: Boolean = true,
    val ignoreGroupSummary: Boolean = true,
    /** 0 means "keep forever". */
    val retentionDays: Int = 7,
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("push_filter_settings", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())

    // Held as a field on purpose: SharedPreferences keeps only a weak reference to listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        state.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    val settings: StateFlow<Settings> = state.asStateFlow()

    fun current(): Settings = state.value

    fun update(transform: (Settings) -> Settings) {
        val next = transform(state.value)
        prefs.edit {
            putBoolean(KEY_FILTERING_ENABLED, next.filteringEnabled)
            putBoolean(KEY_LOG_ALLOWED, next.logAllowed)
            putBoolean(KEY_IGNORE_ONGOING, next.ignoreOngoing)
            putBoolean(KEY_IGNORE_GROUP_SUMMARY, next.ignoreGroupSummary)
            putInt(KEY_RETENTION_DAYS, next.retentionDays)
        }
        state.value = next
    }

    private fun read(): Settings {
        val defaults = Settings()
        return Settings(
            filteringEnabled = prefs.getBoolean(KEY_FILTERING_ENABLED, defaults.filteringEnabled),
            logAllowed = prefs.getBoolean(KEY_LOG_ALLOWED, defaults.logAllowed),
            ignoreOngoing = prefs.getBoolean(KEY_IGNORE_ONGOING, defaults.ignoreOngoing),
            ignoreGroupSummary = prefs.getBoolean(KEY_IGNORE_GROUP_SUMMARY, defaults.ignoreGroupSummary),
            retentionDays = prefs.getInt(KEY_RETENTION_DAYS, defaults.retentionDays),
        )
    }

    private companion object {
        const val KEY_FILTERING_ENABLED = "filtering_enabled"
        const val KEY_LOG_ALLOWED = "log_allowed"
        const val KEY_IGNORE_ONGOING = "ignore_ongoing"
        const val KEY_IGNORE_GROUP_SUMMARY = "ignore_group_summary"
        const val KEY_RETENTION_DAYS = "retention_days"
    }
}
