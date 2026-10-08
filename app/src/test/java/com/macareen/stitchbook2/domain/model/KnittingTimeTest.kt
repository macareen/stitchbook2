package com.macareen.stitchbook2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnittingTimeTest {

    private val minute = 60_000L

    @Test
    fun `total is every session's worked time and the step counts from when it began`() {
        val yesterday = session(start = 0, end = 60 * minute)
        val today = session(start = 1_000 * minute, end = null)

        val time = KnittingTime.of(listOf(yesterday, today), stepStartedAt = 1_010 * minute, now = 1_030 * minute)

        assertEquals(60 * minute + 30 * minute, time.totalMillis)
        assertEquals(20 * minute, time.stepMillis)
        assertFalse(time.stepIsEstimated)
    }

    @Test
    fun `a paused session stops counting at the pause`() {
        val paused = session(start = 0, end = null, pausedAt = 10 * minute)

        val time = KnittingTime.of(listOf(paused), stepStartedAt = 5 * minute, now = 90 * minute)

        assertEquals(10 * minute, time.totalMillis)
        assertEquals(5 * minute, time.stepMillis)
    }

    @Test
    fun `pauses inside a session the step began part-way through are shared out and flagged`() {
        // 40 minutes long with 20 minutes paused somewhere; the step covers the second half.
        val withBreak = session(start = 0, end = 40 * minute, pausedTotal = 20 * minute)

        val time = KnittingTime.of(listOf(withBreak), stepStartedAt = 20 * minute, now = 60 * minute)

        assertEquals(20 * minute, time.totalMillis)
        assertEquals(10 * minute, time.stepMillis)
        assertTrue(time.stepIsEstimated)
    }

    private fun session(start: Long, end: Long?, pausedAt: Long? = null, pausedTotal: Long = 0) = CraftingSession(
        id = "s$start",
        projectId = "project",
        startedAt = start,
        endedAt = end,
        pausedAt = pausedAt,
        pausedTotalMillis = pausedTotal,
        zoneId = "UTC",
        rowsCompleted = null,
        stitchesPerRow = null,
        notes = null,
        createdAt = start,
        updatedAt = start
    )
}
