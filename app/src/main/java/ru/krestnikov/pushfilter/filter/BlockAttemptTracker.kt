// @file BlockAttemptTracker.kt
// @author: ForUltraplayer
// @brief Correlate cancellation requests with removal callbacks without notification content.
package ru.krestnikov.pushfilter.filter

import java.util.UUID

data class BlockAttempt(val token: String, val key: String, val postTime: Long)

class BlockAttemptTracker {
    private val pending = mutableMapOf<String, BlockAttempt>()

    fun begin(key: String, postTime: Long): BlockAttempt? {
        if (pending.containsKey(key)) return null
        return BlockAttempt(UUID.randomUUID().toString(), key, postTime)
            .also { pending[key] = it }
    }

    fun removed(key: String, postTime: Long): BlockAttempt? {
        val attempt = pending[key] ?: return null
        if (attempt.postTime != postTime) return null
        return pending.remove(key)
    }

    fun finish(token: String): BlockAttempt? {
        val attempt = pending.values.firstOrNull { it.token == token } ?: return null
        return pending.remove(attempt.key)
    }

    fun drain(): List<BlockAttempt> = pending.values.toList().also { pending.clear() }

    companion object {
        const val CONFIRMATION_TIMEOUT_MS = 10_000L
    }
}
