package com.macareen.stitchbook2.domain.model

import org.junit.Assert.*
import org.junit.Test

class SessionTimeReviewTest {
    private val start = 100_000L
    private val session = SessionTimer.start("s", "p", start, "UTC")
    @Test fun asksOnlyAfterTheConfiguredUnpausedInterval() {
        assertFalse(SessionTimeReview.needsReview(session, start + 30 * 60_000, 30))
        assertTrue(SessionTimeReview.needsReview(session, start + 30 * 60_000 + 1, 30))
        assertFalse(SessionTimeReview.needsReview(session, start + 30 * 60_000 + 1, 60))
        assertFalse(SessionTimeReview.needsReview(session, start - 1, 30))
        assertFalse(SessionTimeReview.needsReview(session, start + 999_999, 0))
    }
    @Test fun pauseStopAndResumeAreRespected() {
        val paused = SessionTimer.pause(session, start + 60_000)
        assertFalse(SessionTimeReview.needsReview(paused, start + 900 * 60_000, 30))
        assertFalse(SessionTimeReview.needsReview(SessionTimer.stop(session, start + 60_000), start + 900 * 60_000, 30))
        val resumed = SessionTimer.resume(paused, start + 900 * 60_000)
        assertFalse(SessionTimeReview.needsReview(resumed, start + 910 * 60_000, 30))
    }
    @Test fun acknowledgingAllTimePersistsAReviewCheckpointWithoutDeletingTime() {
        val now = start + 900 * 60_000
        val acknowledged = SessionTimeReview.keepAll(session, now)
        assertEquals(900 * 60_000L, acknowledged.workedMillis(now))
        assertTrue(acknowledged.isRunning)
        assertFalse(SessionTimeReview.needsReview(acknowledged.copy(), now + 60_000, 30))
        assertTrue(SessionTimeReview.needsReview(acknowledged, now + 31 * 60_000, 30))
    }
    @Test fun overnight900MinutesCanBeCorrectedTo10WithoutLosingTheOriginalIntervalOrNotes() {
        val now = start + 900 * 60_000
        val original = session.copy(notes = "My note", rowsCompleted = 12, stitchesPerRow = 60)
        val result = SessionTimeReview.correctAndPause(original, 10, now)!!
        assertEquals(10 * 60_000L, result.workedMillis(now))
        assertEquals(10 * 60_000L, result.workedMillis(now + 999_999))
        assertEquals(original.startedAt, result.startedAt)
        assertEquals(now, result.pausedAt)
        assertNull(result.endedAt)
        assertEquals(original.rowsCompleted, result.rowsCompleted)
        assertEquals(original.stitchesPerRow, result.stitchesPerRow)
        assertTrue(result.notes!!.startsWith("My note\n\n"))
        assertTrue(result.notes!!.contains("kept 10 min of 900 min"))
        val resumed = SessionTimer.resume(result, now + 30 * 60_000)
        assertEquals(12 * 60_000L, resumed.workedMillis(now + 32 * 60_000))
    }
    @Test fun correctionPreservesPreviouslyExcludedPauses() {
        val original = session.copy(pausedTotalMillis = 20 * 60_000)
        val result = SessionTimeReview.correctAndPause(original, 10, start + 100 * 60_000)!!
        assertEquals(90 * 60_000L, result.pausedTotalMillis)
        assertEquals(10 * 60_000L, result.workedMillis(start + 100 * 60_000))
    }
    @Test fun invalidCorrectionsNeverChangeTheSession() {
        val now = start + 10 * 60_000
        assertNull(SessionTimeReview.correctAndPause(session, -1, now))
        assertNull(SessionTimeReview.correctAndPause(session, 11, now))
        assertNull(SessionTimeReview.correctAndPause(session, Long.MAX_VALUE, now))
        assertNull(SessionTimeReview.correctAndPause(session, 0, start - 1))
        assertNull(SessionTimeReview.correctAndPause(SessionTimer.pause(session, now), 0, now))
        assertEquals(0L, SessionTimeReview.correctAndPause(session, 0, now)!!.workedMillis(now))
    }
}
