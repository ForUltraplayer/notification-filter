package ru.krestnikov.pushfilter.data

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_ID
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_LOG_APP_LABEL
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_LOG_BLOCKED
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_LOG_PACKAGE
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_LOG_POSTED_AT
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_LOG_REASON
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_LOG_RULE_ID
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_LOG_RULE_NAME
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_LOG_RULE_PATTERN
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_LOG_TEXT
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.COL_LOG_TITLE
import ru.krestnikov.pushfilter.data.PushFilterDb.Companion.TABLE_LOG
import java.util.concurrent.atomic.AtomicInteger

class LogRepository(
    private val db: PushFilterDb,
    private val retentionDays: () -> Int,
) {

    /** Bumped after every write so the query flows below re-run. */
    private val revision = MutableStateFlow(0L)
    private val insertsSinceCleanup = AtomicInteger(0)

    fun observe(query: LogQuery): Flow<List<LogEntry>> =
        revision.map { read(query) }.flowOn(Dispatchers.IO)

    fun observePackages(): Flow<List<String>> =
        revision.map { readPackages() }.flowOn(Dispatchers.IO)

    fun observeStats(): Flow<LogStats> =
        revision.map { readStats() }.flowOn(Dispatchers.IO)

    fun insert(entry: LogEntry) {
        val values = ContentValues().apply {
            put(COL_LOG_PACKAGE, entry.packageName)
            put(COL_LOG_APP_LABEL, entry.appLabel)
            put(COL_LOG_TITLE, entry.title)
            put(COL_LOG_TEXT, entry.text)
            put(COL_LOG_POSTED_AT, entry.postedAt)
            put(COL_LOG_BLOCKED, if (entry.blocked) 1 else 0)
            put(COL_LOG_RULE_ID, entry.ruleId)
            put(COL_LOG_RULE_NAME, entry.ruleName)
            put(COL_LOG_RULE_PATTERN, entry.rulePattern)
            put(COL_LOG_REASON, entry.reason.name)
        }
        db.writableDatabase.insert(TABLE_LOG, null, values)
        if (insertsSinceCleanup.incrementAndGet() >= CLEANUP_EVERY_N_INSERTS) {
            insertsSinceCleanup.set(0)
            cleanup()
        }
        revision.update { it + 1 }
    }

    fun clear() {
        db.writableDatabase.delete(TABLE_LOG, null, null)
        revision.update { it + 1 }
    }

    fun clearPackage(packageName: String) {
        db.writableDatabase.delete(TABLE_LOG, "$COL_LOG_PACKAGE = ?", arrayOf(packageName))
        revision.update { it + 1 }
    }

    /** Drops entries past the retention window and caps the table at [MAX_ENTRIES]. */
    fun cleanup() {
        val database = db.writableDatabase
        val days = retentionDays()
        if (days > 0) {
            val cutoff = System.currentTimeMillis() - days * MILLIS_PER_DAY
            database.delete(TABLE_LOG, "$COL_LOG_POSTED_AT < ?", arrayOf(cutoff.toString()))
        }
        database.execSQL(
            """
            DELETE FROM $TABLE_LOG WHERE $COL_ID NOT IN (
                SELECT $COL_ID FROM $TABLE_LOG ORDER BY $COL_LOG_POSTED_AT DESC LIMIT $MAX_ENTRIES
            )
            """.trimIndent(),
        )
        revision.update { it + 1 }
    }

    private fun read(query: LogQuery): List<LogEntry> {
        val where = StringBuilder()
        val args = mutableListOf<String>()

        when (query.filter) {
            LogFilter.ALL -> Unit
            LogFilter.BLOCKED -> where.append("$COL_LOG_BLOCKED = 1")
            LogFilter.ALLOWED -> where.append("$COL_LOG_BLOCKED = 0")
        }
        query.packageName?.let {
            if (where.isNotEmpty()) where.append(" AND ")
            where.append("$COL_LOG_PACKAGE = ?")
            args += it
        }
        query.search.trim().takeIf { it.isNotEmpty() }?.let {
            if (where.isNotEmpty()) where.append(" AND ")
            where.append("($COL_LOG_TITLE LIKE ? OR $COL_LOG_TEXT LIKE ? OR $COL_LOG_APP_LABEL LIKE ?)")
            val like = "%$it%"
            args += listOf(like, like, like)
        }

        return db.readableDatabase.query(
            TABLE_LOG,
            null,
            where.takeIf { it.isNotEmpty() }?.toString(),
            args.takeIf { it.isNotEmpty() }?.toTypedArray(),
            null,
            null,
            "$COL_LOG_POSTED_AT DESC",
            PAGE_SIZE.toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toLogEntry())
            }
        }
    }

    private fun readPackages(): List<String> =
        db.readableDatabase.rawQuery(
            "SELECT $COL_LOG_PACKAGE FROM $TABLE_LOG GROUP BY $COL_LOG_PACKAGE " +
                "ORDER BY MAX($COL_LOG_POSTED_AT) DESC",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun readStats(): LogStats =
        db.readableDatabase.rawQuery(
            "SELECT $COL_LOG_BLOCKED, COUNT(*) FROM $TABLE_LOG GROUP BY $COL_LOG_BLOCKED",
            null,
        ).use { cursor ->
            var blocked = 0
            var allowed = 0
            while (cursor.moveToNext()) {
                if (cursor.getInt(0) != 0) blocked = cursor.getInt(1) else allowed = cursor.getInt(1)
            }
            LogStats(blocked = blocked, allowed = allowed)
        }

    private fun Cursor.toLogEntry(): LogEntry {
        val ruleIdIndex = getColumnIndexOrThrow(COL_LOG_RULE_ID)
        return LogEntry(
            id = getLong(getColumnIndexOrThrow(COL_ID)),
            packageName = getString(getColumnIndexOrThrow(COL_LOG_PACKAGE)),
            appLabel = getString(getColumnIndexOrThrow(COL_LOG_APP_LABEL)),
            title = getString(getColumnIndexOrThrow(COL_LOG_TITLE)),
            text = getString(getColumnIndexOrThrow(COL_LOG_TEXT)),
            postedAt = getLong(getColumnIndexOrThrow(COL_LOG_POSTED_AT)),
            blocked = getInt(getColumnIndexOrThrow(COL_LOG_BLOCKED)) != 0,
            ruleId = if (isNull(ruleIdIndex)) null else getLong(ruleIdIndex),
            ruleName = getString(getColumnIndexOrThrow(COL_LOG_RULE_NAME)),
            rulePattern = getString(getColumnIndexOrThrow(COL_LOG_RULE_PATTERN)),
            reason = enumOrDefault(getString(getColumnIndexOrThrow(COL_LOG_REASON)), VerdictReason.NO_RULES),
        )
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_ENTRIES = 5000
        const val CLEANUP_EVERY_N_INSERTS = 200
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
