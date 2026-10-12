package ru.krestnikov.pushfilter.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PushFilterDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    init {
        // Notifications are written from the listener thread while the UI reads,
        // so WAL keeps readers from blocking on inserts.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_RULES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_RULE_PACKAGE TEXT NOT NULL,
                $COL_RULE_NAME TEXT NOT NULL,
                $COL_RULE_MODE TEXT NOT NULL,
                $COL_RULE_PATTERN TEXT NOT NULL,
                $COL_RULE_TARGET TEXT NOT NULL,
                $COL_RULE_CASE_SENSITIVE INTEGER NOT NULL,
                $COL_RULE_ENABLED INTEGER NOT NULL,
                $COL_RULE_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_rules_package ON $TABLE_RULES ($COL_RULE_PACKAGE)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_LOG (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_LOG_PACKAGE TEXT NOT NULL,
                $COL_LOG_APP_LABEL TEXT NOT NULL,
                $COL_LOG_TITLE TEXT NOT NULL,
                $COL_LOG_TEXT TEXT NOT NULL,
                $COL_LOG_POSTED_AT INTEGER NOT NULL,
                $COL_LOG_BLOCKED INTEGER NOT NULL,
                $COL_LOG_RULE_ID INTEGER,
                $COL_LOG_RULE_NAME TEXT,
                $COL_LOG_RULE_PATTERN TEXT,
                $COL_LOG_REASON TEXT NOT NULL,
                attempt_id TEXT,
                block_status TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_log_posted_at ON $TABLE_LOG ($COL_LOG_POSTED_AT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_LOG ADD COLUMN attempt_id TEXT")
            db.execSQL("ALTER TABLE $TABLE_LOG ADD COLUMN block_status TEXT")
        }
    }

    companion object {
        private const val DB_NAME = "push_filter.db"
        private const val DB_VERSION = 2

        const val COL_ID = "id"

        const val TABLE_RULES = "rules"
        const val COL_RULE_PACKAGE = "package_name"
        const val COL_RULE_NAME = "name"
        const val COL_RULE_MODE = "mode"
        const val COL_RULE_PATTERN = "pattern"
        const val COL_RULE_TARGET = "target"
        const val COL_RULE_CASE_SENSITIVE = "case_sensitive"
        const val COL_RULE_ENABLED = "enabled"
        const val COL_RULE_CREATED_AT = "created_at"

        const val TABLE_LOG = "log"
        const val COL_LOG_PACKAGE = "package_name"
        const val COL_LOG_APP_LABEL = "app_label"
        const val COL_LOG_TITLE = "title"
        const val COL_LOG_TEXT = "text"
        const val COL_LOG_POSTED_AT = "posted_at"
        const val COL_LOG_BLOCKED = "blocked"
        const val COL_LOG_RULE_ID = "rule_id"
        const val COL_LOG_RULE_NAME = "rule_name"
        const val COL_LOG_RULE_PATTERN = "rule_pattern"
        const val COL_LOG_REASON = "reason"
    }
}
