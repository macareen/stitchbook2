package com.macareen.stitchbook2.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CraftingSessionTest {

    private val minute = 60_000L

    @Test
    fun pausesAreExcludedAndAPausedSessionStopsAccruing() {
        var session = SessionTimer.start("s", "p", now = 0, zoneId = "UTC")
        session = SessionTimer.pause(session, 10 * minute)
        assertTrue(session.isPaused)
        assertEquals(10 * minute, session.workedMillis(now = 50 * minute))
        session = SessionTimer.resume(session, 30 * minute)
        assertEquals(15 * minute, session.workedMillis(now = 35 * minute))
        session = SessionTimer.stop(session, 40 * minute)
        assertFalse(session.isActive)
        assertEquals(20 * minute, session.workedMillis(now = 999 * minute))
    }

    @Test
    fun stoppingWhilePausedEndsAtThePause() {
        val paused = SessionTimer.pause(SessionTimer.start("s", null, 0, "UTC"), 5 * minute)
        val stopped = SessionTimer.stop(paused, 60 * minute)
        assertEquals(5 * minute, stopped.endedAt)
        assertEquals(5 * minute, stopped.workedMillis(0))
    }

    @Test
    fun correctionReplacesStartAndWorkedTime() {
        val finished = SessionTimer.stop(SessionTimer.pause(SessionTimer.start("s", null, 0, "UTC"), minute), 2 * minute)
        val corrected = SessionTimer.correct(finished, startedAt = 100 * minute, workedMillis = 45 * minute, now = 0)
        assertEquals(45 * minute, corrected.workedMillis(0))
        assertEquals(0L, corrected.pausedTotalMillis)
    }

    @Test
    fun dayBoundaryUsesTheRecordedZoneOfTheStart() {
        // 2026-03-01T23:30 in New York is 2026-03-02T04:30Z.
        val startedAt = java.time.ZonedDateTime.of(2026, 3, 1, 23, 30, 0, 0, java.time.ZoneId.of("America/New_York"))
            .toInstant().toEpochMilli()
        val session = SessionTimer.start("s", null, startedAt, "America/New_York")
        assertEquals(LocalDate.of(2026, 3, 1), session.localStartDate())
        assertEquals(LocalDate.of(2026, 3, 2), session.copy(zoneId = "UTC").localStartDate())
    }
}
