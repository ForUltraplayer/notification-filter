// @file NotificationFilterService.kt
// @author: ForUltraplayer
// @brief Filter notifications and retain matched notification content for local inspection.
package ru.krestnikov.pushfilter.filter

import android.app.Notification
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.krestnikov.pushfilter.AppContainer
import ru.krestnikov.pushfilter.PushFilterApplication
import ru.krestnikov.pushfilter.data.BlockStatus

class NotificationFilterService : NotificationListenerService() {
    private lateinit var container: AppContainer
    private val attempts = BlockAttemptTracker()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        container = (application as PushFilterApplication).container
    }

    override fun onListenerConnected() {
        container.rules.snapshot()
        connection.value = true
    }

    private fun finishPending() {
        handler.removeCallbacksAndMessages(null)
        attempts.drain().forEach { container.log.finishBlockAttempt(it.token, BlockStatus.UNCONFIRMED) }
    }

    override fun onListenerDisconnected() {
        connection.value = false
        finishPending()
        runCatching { requestRebind(ComponentName(this, NotificationFilterService::class.java)) }
    }

    override fun onDestroy() {
        connection.value = false
        finishPending()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (sbn.packageName == packageName) return
        val settings = container.settings.current()
        if (!settings.filteringEnabled || container.ignored.contains(sbn.packageName)) return
        if (settings.ignoreOngoing && notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (settings.ignoreGroupSummary && notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val content = NotificationContentExtractor.extract(notification)
        if (content.isEmpty) return
        val verdict = FilterEngine.evaluate(sbn.packageName, content, container.rules.snapshot())
        if (!verdict.blocked) return

        val attempt = attempts.begin(sbn.key, sbn.postTime) ?: return
        val sourcePackage = sbn.packageName
        container.log.recordBlockAttempt(
            attempt.token, sourcePackage, verdict.rule?.name.orEmpty(), System.currentTimeMillis(), content.title, content.text,
        ) { container.apps.label(sourcePackage) }

        runCatching { cancelNotification(sbn.key) }.onFailure {
            attempts.finish(attempt.token)
            container.log.finishBlockAttempt(attempt.token, BlockStatus.FAILED)
            Log.w(TAG, "Could not dismiss notification", it)
            return
        }
        handler.postDelayed({
            if (attempts.finish(attempt.token) != null) {
                container.log.finishBlockAttempt(attempt.token, BlockStatus.UNCONFIRMED)
            }
        }, BlockAttemptTracker.CONFIRMATION_TIMEOUT_MS)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        if (sbn == null) return
        val attempt = attempts.removed(sbn.key, sbn.postTime) ?: return
        val status = if (reason == REASON_LISTENER_CANCEL) BlockStatus.CONFIRMED else BlockStatus.UNCONFIRMED
        container.log.finishBlockAttempt(attempt.token, status)
    }

    companion object {
        private const val TAG = "PushFilter"
        private val connection = MutableStateFlow(false)
        val connected = connection.asStateFlow()
    }
}
