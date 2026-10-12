// @file BlockAttemptTrackerTest.kt
// @author: ForUltraplayer
// @brief Verify conservative request/removal correlation and duplicate handling.
package ru.krestnikov.pushfilter.filter

import org.junit.Assert.*
import org.junit.Test

class BlockAttemptTrackerTest {
    @Test fun unrequestedRemovalsNeverConfirmABlock() {
        assertNull(BlockAttemptTracker().removed("notification", 100))
    }
    @Test fun matchingRemovalIsConsumedOnlyOnce() {
        val tracker = BlockAttemptTracker()
        val attempt = tracker.begin("key", 100)
        assertEquals(attempt, tracker.removed("key", 100))
        assertNull(tracker.removed("key", 100))
    }
    @Test fun repeatedPostsDoNotDoubleCountPendingAttempt() {
        val tracker = BlockAttemptTracker()
        assertNotNull(tracker.begin("key", 100))
        assertNull(tracker.begin("key", 100))
    }
    @Test fun repostedNotificationCannotConfirmDifferentPostTime() {
        val tracker = BlockAttemptTracker()
        val attempt = tracker.begin("key", 100)
        assertNull(tracker.removed("key", 200))
        assertEquals(attempt, tracker.removed("key", 100))
    }
    @Test fun timeoutPreventsALateCallbackFromClaimingSuccess() {
        val tracker = BlockAttemptTracker()
        val attempt = tracker.begin("key", 100)!!
        assertEquals(attempt, tracker.finish(attempt.token))
        assertNull(tracker.removed("key", 100))
    }
    @Test fun staleTimeoutCannotRemoveNewAttemptWithSameKey() {
        val tracker = BlockAttemptTracker()
        val first = tracker.begin("key", 100)!!
        tracker.removed("key", 100)
        val second = tracker.begin("key", 200)
        assertNull(tracker.finish(first.token))
        assertEquals(second, tracker.removed("key", 200))
    }
    @Test fun disconnectDrainsAllUnconfirmedRequestsOnce() {
        val tracker = BlockAttemptTracker()
        tracker.begin("a", 1)
        tracker.begin("b", 2)
        assertEquals(2, tracker.drain().size)
        assertTrue(tracker.drain().isEmpty())
        assertNull(tracker.removed("a", 1))
    }
}
