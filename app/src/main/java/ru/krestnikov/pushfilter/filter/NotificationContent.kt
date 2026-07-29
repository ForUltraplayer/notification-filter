package ru.krestnikov.pushfilter.filter

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import ru.krestnikov.pushfilter.data.MatchTarget

data class NotificationContent(val title: String, val text: String) {

    val isEmpty: Boolean get() = title.isEmpty() && text.isEmpty()

    fun forTarget(target: MatchTarget): String = when (target) {
        MatchTarget.TITLE -> title
        MatchTarget.TEXT -> text
        MatchTarget.BOTH -> when {
            title.isEmpty() -> text
            text.isEmpty() -> title
            else -> "$title\n$text"
        }
    }
}

/**
 * Pulls every piece of user-visible text out of a notification. Apps are inconsistent about
 * which extra they use, and promotional content in particular often lives in the big-text or
 * messaging extras rather than in [Notification.EXTRA_TEXT].
 */
object NotificationContentExtractor {

    private val TEXT_KEYS = arrayOf(
        Notification.EXTRA_TEXT,
        Notification.EXTRA_BIG_TEXT,
        Notification.EXTRA_SUB_TEXT,
        Notification.EXTRA_INFO_TEXT,
        Notification.EXTRA_SUMMARY_TEXT,
    )

    fun extract(notification: Notification): NotificationContent {
        val extras = notification.extras ?: return NotificationContent("", "")

        val title = listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_CONVERSATION_TITLE,
        ).firstNotNullOfOrNull { extras.text(it) }.orEmpty()

        // LinkedHashSet: apps frequently duplicate the same string across extras.
        val parts = LinkedHashSet<String>()
        for (key in TEXT_KEYS) extras.text(key)?.let { parts += it }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
            line?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it }
        }
        parts += messages(extras, Notification.EXTRA_MESSAGES)
        parts += messages(extras, Notification.EXTRA_HISTORIC_MESSAGES)
        notification.tickerText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it }

        return NotificationContent(title = title, text = parts.joinToString("\n"))
    }

    /** MessagingStyle stores each message as a [Bundle] with "text" and "sender" entries. */
    private fun messages(extras: Bundle, key: String): List<String> {
        val array = parcelableArray(extras, key) ?: return emptyList()
        return array.filterIsInstance<Bundle>().mapNotNull { message ->
            val text = message.text("text") ?: return@mapNotNull null
            val sender = message.text("sender")
            if (sender == null) text else "$sender: $text"
        }
    }

    @Suppress("DEPRECATION")
    private fun parcelableArray(extras: Bundle, key: String): Array<out Parcelable>? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(key, Bundle::class.java)
        } else {
            extras.getParcelableArray(key)
        }
    }.getOrNull()

    private fun Bundle.text(key: String): String? =
        runCatching { getCharSequence(key) }.getOrNull()?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
