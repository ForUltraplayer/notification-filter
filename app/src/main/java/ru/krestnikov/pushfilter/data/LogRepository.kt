package ru.krestnikov.pushfilter.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
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

    private val writeFailure = MutableStateFlow(false)
    val hasWriteFailure = writeFailure.asStateFlow()
    private val writes = Channel<() -> Unit>(Channel.UNLIMITED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                db.writableDatabase.execSQL(
                    "UPDATE $TABLE_LOG SET block_status = ? WHERE block_status = ?",
                    arrayOf(BlockStatus.UNCONFIRMED.name, BlockStatus.REQUESTED.name),
                )
                cleanup()
            }.onFailure { reportWriteFailure(it) }
            for (write in writes) {
                runCatching { write() }.onFailure { reportWriteFailure(it) }
            }
        }
    }

    private fun reportWriteFailure(error: Throwable) {
        writeFailure.value = true
        Log.w("PushFilter", "Could not save block history", error)
    }

    fun recordBlockAttempt(token: String, packageName: String, keyword: String, requestedAt: Long, title: String, text: String, appLabel: () -> String) {
        writes.trySend {
            insert(
                LogEntry(
                    packageName = packageName, appLabel = appLabel(),
                    title = title, text = text, postedAt = requestedAt, blocked = false,
                    ruleName = keyword, reason = VerdictReason.BLOCKED_BY_RULE,
                    attemptId = token, blockStatus = BlockStatus.REQUESTED,
                ),
            )
            cleanup()
        }
    }

    fun finishBlockAttempt(token: String, status: BlockStatus) {
        require(status != BlockStatus.REQUESTED)
        writes.trySend {
            val values = ContentValues().apply {
                put("block_status", status.name)
                put(COL_LOG_BLOCKED, if (status == BlockStatus.CONFIRMED) 1 else 0)
            }
            db.writableDatabase.update(
                TABLE_LOG, values, "attempt_id = ? AND block_status = ?",
                arrayOf(token, BlockStatus.REQUESTED.name),
            )
            revision.update { it + 1 }
        }
    }

    fun clearBlockHistory() {
        writes.trySend {
            clear()
            writeFailure.value = false
        }
    }

    fun refreshBlockHistory() {
        writes.trySend { cleanup() }
    }

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
            put("attempt_id", entry.attemptId)
            put("block_status", entry.blockStatus?.name)
        }
        db.writableDatabase.insertOrThrow(TABLE_LOG, null, values)
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
            attemptId = getString(getColumnIndexOrThrow("attempt_id")),
            blockStatus = getString(getColumnIndexOrThrow("block_status"))?.let { value -> BlockStatus.entries.firstOrNull { it.name == value } },
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
