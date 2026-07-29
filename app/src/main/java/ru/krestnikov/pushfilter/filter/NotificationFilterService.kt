package ru.krestnikov.pushfilter.filter

import android.app.Notification
import android.content.ComponentName
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.krestnikov.pushfilter.AppContainer
import ru.krestnikov.pushfilter.PushFilterApplication
import ru.krestnikov.pushfilter.data.LogEntry

class NotificationFilterService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentlyLogged = HashMap<String, Long>()

    private lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = (application as PushFilterApplication).container
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        // Warms the in-memory rule cache so the first notification is not delayed by disk I/O.
        container.rules.snapshot()
        scope.launch { runCatching { container.log.cleanup() } }
    }

    override fun onListenerDisconnected() {
        // The system unbinds listeners on package updates; ask to be bound again.
        runCatching { requestRebind(ComponentName(this, NotificationFilterService::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (sbn.packageName == packageName) return
        if (container.ignored.contains(sbn.packageName)) return

        val settings = container.settings.current()
        if (!settings.filteringEnabled) return
        if (settings.ignoreOngoing && notification.hasFlag(Notification.FLAG_ONGOING_EVENT)) return
        if (settings.ignoreGroupSummary && notification.hasFlag(Notification.FLAG_GROUP_SUMMARY)) return

        val content = NotificationContentExtractor.extract(notification)
        if (content.isEmpty) return

        val verdict = FilterEngine.evaluate(sbn.packageName, content, container.rules.snapshot())

        if (verdict.blocked) {
            runCatching { cancelNotification(sbn.key) }
                .onFailure { Log.w(TAG, "Could not dismiss ${sbn.key}", it) }
        } else if (!settings.logAllowed) {
            return
        }

        if (!shouldLog(sbn.key, content, verdict.blocked)) return

        val packageName = sbn.packageName
        val postedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        scope.launch {
            runCatching {
                container.log.insert(
                    LogEntry(
                        packageName = packageName,
                        appLabel = container.apps.label(packageName),
                        title = content.title,
                        text = content.text,
                        postedAt = postedAt,
                        blocked = verdict.blocked,
                        ruleId = verdict.rule?.id,
                        ruleName = verdict.rule?.name,
                        rulePattern = verdict.rule?.pattern,
                        reason = verdict.reason,
                    ),
                )
            }.onFailure { Log.w(TAG, "Could not write log entry", it) }
        }
    }

    /**
     * Apps re-post the same notification on every minor update (progress, read receipts).
     * Without this the log fills up with identical rows.
     */
    private fun shouldLog(key: String, content: NotificationContent, blocked: Boolean): Boolean {
        val id = "$key|$blocked|${content.title}|${content.text}"
        val now = SystemClock.elapsedRealtime()
        synchronized(recentlyLogged) {
            if (recentlyLogged.size > MAX_DEDUPE_ENTRIES) {
                recentlyLogged.entries.removeAll { now - it.value > DEDUPE_WINDOW_MS }
            }
            val previous = recentlyLogged[id]
            if (previous != null && now - previous < DEDUPE_WINDOW_MS) return false
            recentlyLogged[id] = now
            return true
        }
    }

    private fun Notification.hasFlag(flag: Int) = (flags and flag) != 0

    private companion object {
        const val TAG = "PushFilter"
        const val DEDUPE_WINDOW_MS = 5_000L
        const val MAX_DEDUPE_ENTRIES = 300
    }
}
