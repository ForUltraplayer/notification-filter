package ru.krestnikov.pushfilter

import android.content.Context
import ru.krestnikov.pushfilter.apps.AppInfoProvider
import ru.krestnikov.pushfilter.data.IgnoreRepository
import ru.krestnikov.pushfilter.data.LogRepository
import ru.krestnikov.pushfilter.data.PushFilterDb
import ru.krestnikov.pushfilter.data.RuleRepository
import ru.krestnikov.pushfilter.data.SettingsRepository

/**
 * The notification listener runs in the same process as the UI, so a single set of
 * repositories keeps both in sync without any cross-process plumbing.
 */
class AppContainer(context: Context) {
    private val db = PushFilterDb(context)

    val settings = SettingsRepository(context)
    val ignored = IgnoreRepository(context)
    val rules = RuleRepository(db)
    val log = LogRepository(db) { settings.current().retentionDays }
    val apps = AppInfoProvider(context)
}

fun Context.appContainer(): AppContainer =
    (applicationContext as PushFilterApplication).container
