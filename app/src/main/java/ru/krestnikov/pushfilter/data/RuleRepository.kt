package ru.krestnikov.pushfilter.data

import android.content.ContentValues
import android.database.Cursor
import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_ID
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_RULE_CASE_SENSITIVE
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_RULE_CREATED_AT
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_RULE_ENABLED
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_RULE_MODE
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_RULE_NAME
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_RULE_PACKAGE
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_RULE_PATTERN
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_RULE_TARGET
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.TABLE_RULES

/**
 * Rules are kept in memory as well as in SQLite: [NotificationFilterService] must decide
 * whether to drop a notification on the main thread, within milliseconds of it being posted.
 */
class RuleRepository(private val db: PushFilterDb) {

    private val loadLock = Any()
    private var loaded = false
    private val state = MutableStateFlow<List<FilterRule>>(emptyList())

    // Same instance on every access: Compose keys flow collection on identity, so handing out
    // a fresh asStateFlow() wrapper would restart the collector on every recomposition.
    private val readOnlyState = state.asStateFlow()

    val rules: StateFlow<List<FilterRule>>
        get() {
            ensureLoaded()
            return readOnlyState
        }

    fun snapshot(): List<FilterRule> {
        ensureLoaded()
        return state.value
    }

    fun byId(id: Long): FilterRule? = snapshot().firstOrNull { it.id == id }

    /** Inserts when [rule] has no id, updates otherwise. Returns the row id. */
    fun save(rule: FilterRule): Long {
        val values = rule.toContentValues()
        val id = if (rule.id == 0L) {
            db.writableDatabase.insert(TABLE_RULES, null, values)
        } else {
            db.writableDatabase.update(TABLE_RULES, values, "$COL_ID = ?", arrayOf(rule.id.toString()))
            rule.id
        }
        reload()
        return id
    }

    fun delete(id: Long) {
        db.writableDatabase.delete(TABLE_RULES, "$COL_ID = ?", arrayOf(id.toString()))
        reload()
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        val values = ContentValues().apply { put(COL_RULE_ENABLED, if (enabled) 1 else 0) }
        db.writableDatabase.update(TABLE_RULES, values, "$COL_ID = ?", arrayOf(id.toString()))
        reload()
    }

    /** Bulk insert used by rule import. Returns how many rules were stored. */
    fun insertAll(rules: List<FilterRule>, replaceExisting: Boolean): Int {
        db.writableDatabase.transaction {
            if (replaceExisting) delete(TABLE_RULES, null, null)
            for (rule in rules) {
                insert(TABLE_RULES, null, rule.copy(id = 0L).toContentValues())
            }
        }
        reload()
        return rules.size
    }

    private fun ensureLoaded() {
        synchronized(loadLock) {
            if (loaded) return
            state.value = readAll()
            loaded = true
        }
    }

    private fun reload() {
        synchronized(loadLock) {
            state.value = readAll()
            loaded = true
        }
    }

    private fun readAll(): List<FilterRule> =
        db.readableDatabase
            .query(TABLE_RULES, null, null, null, null, null, "$COL_RULE_CREATED_AT ASC")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toFilterRule())
                }
            }

    private fun FilterRule.toContentValues() = ContentValues().apply {
        put(COL_RULE_PACKAGE, packageName)
        put(COL_RULE_NAME, name)
        put(COL_RULE_MODE, mode.name)
        put(COL_RULE_PATTERN, pattern)
        put(COL_RULE_TARGET, target.name)
        put(COL_RULE_CASE_SENSITIVE, if (caseSensitive) 1 else 0)
        put(COL_RULE_ENABLED, if (enabled) 1 else 0)
        put(COL_RULE_CREATED_AT, if (createdAt == 0L) System.currentTimeMillis() else createdAt)
    }

    private fun Cursor.toFilterRule() = FilterRule(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        packageName = getString(getColumnIndexOrThrow(COL_RULE_PACKAGE)),
        name = getString(getColumnIndexOrThrow(COL_RULE_NAME)),
        mode = enumOrDefault(getString(getColumnIndexOrThrow(COL_RULE_MODE)), RuleMode.BLOCK),
        pattern = getString(getColumnIndexOrThrow(COL_RULE_PATTERN)),
        target = enumOrDefault(getString(getColumnIndexOrThrow(COL_RULE_TARGET)), MatchTarget.BOTH),
        caseSensitive = getInt(getColumnIndexOrThrow(COL_RULE_CASE_SENSITIVE)) != 0,
        enabled = getInt(getColumnIndexOrThrow(COL_RULE_ENABLED)) != 0,
        createdAt = getLong(getColumnIndexOrThrow(COL_RULE_CREATED_AT)),
    )
}

internal inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
