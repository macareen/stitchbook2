package com.macareen.stitchbook2.domain.model

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ManualCraftingTimeTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-13T12:00:00Z").toEpochMilli()
    private fun create(input: ManualTimeInput, inZone: ZoneId = zone, at: Long = now) = ManualCraftingTime.create(input, "p", "m", inZone, at)
    @Test fun createsFinishedManualRecordWithoutAnyTimerState() {
        val session = (create(ManualTimeInput("2026-10-13", "11:30", "10", " Note ")) as ManualTimeResult.Ready).session
        assertEquals(CraftingSessionSource.MANUAL, session.source); assertFalse(session.isActive)
        assertEquals(600_000L, session.workedMillis(now)); assertEquals("Note", session.notes)
        assertEquals(now, session.createdAt); assertEquals("UTC", session.zoneId)
        assertEquals(Instant.parse("2026-10-13T11:20:00Z").toEpochMilli(), session.startedAt)
    }
    @Test fun midnightEntriesKeepTheExistingStartDayAccountingRule() {
        val session = (create(ManualTimeInput("2026-10-13", "0:15", "30")) as ManualTimeResult.Ready).session
        assertEquals("2026-10-12", session.localStartDate().toString())
    }
    @Test fun invalidDatesTimesDurationsAndFutureTimesCannotCreateRecords() {
        val base = ManualTimeInput("2026-10-13", "11:30", "10")
        listOf(base.copy(finishedDate = "2026-02-30") to ManualTimeError.INVALID_DATE,
            base.copy(finishedTime = "24:00") to ManualTimeError.INVALID_TIME,
            base.copy(finishedTime = "11:75") to ManualTimeError.INVALID_TIME,
            base.copy(minutes = "0") to ManualTimeError.INVALID_DURATION,
            base.copy(minutes = "1.5") to ManualTimeError.INVALID_DURATION,
            base.copy(minutes = "1441") to ManualTimeError.INVALID_DURATION,
            base.copy(minutes = "99999999999999999999") to ManualTimeError.INVALID_DURATION,
            base.copy(finishedTime = "12:01") to ManualTimeError.FUTURE_TIME).forEach { (input, error) ->
                assertEquals(error, (create(input) as ManualTimeResult.Invalid).error)
            }
    }
    @Test fun daylightSavingGapIsRejectedAndRepeatedHourRequiresExplicitChoice() {
        val ny = ZoneId.of("America/New_York")
        val laterNow = Instant.parse("2026-12-01T12:00:00Z").toEpochMilli()
        assertEquals(ManualTimeError.CLOCK_GAP, (create(ManualTimeInput("2026-03-08", "2:30", "10"), ny, laterNow) as ManualTimeResult.Invalid).error)
        val input = ManualTimeInput("2026-11-01", "1:30", "10")
        val result = create(input, ny, laterNow) as ManualTimeResult.Invalid
        assertEquals(ManualTimeError.CHOOSE_OFFSET, result.error)
        assertEquals(listOf("-04:00", "-05:00"), result.offsets)
        val early = (create(input.copy(offset = "-04:00"), ny, laterNow) as ManualTimeResult.Ready).session
        val late = (create(input.copy(offset = "-05:00"), ny, laterNow) as ManualTimeResult.Ready).session
        assertEquals(3_600_000L, late.endedAt!! - early.endedAt!!)
        assertEquals(early.workedMillis(laterNow), late.workedMillis(laterNow))
    }
    @Test fun overlapWarningIncludesOtherScopesButNotPausedTailsOrTouchingBoundaries() {
        val candidate = (create(ManualTimeInput("2026-10-13", "11:30", "10")) as ManualTimeResult.Ready).session
        val recorded = SessionTimer.start("r", "other-project", candidate.startedAt - 60_000, "UTC")
        assertTrue(ManualCraftingTime.possiblyOverlaps(candidate, listOf(recorded), now))
        val paused = SessionTimer.pause(recorded, candidate.startedAt)
        assertFalse(ManualCraftingTime.possiblyOverlaps(candidate, listOf(paused), now))
        val zero = recorded.copy(pausedTotalMillis = now - recorded.startedAt)
        assertFalse(ManualCraftingTime.possiblyOverlaps(candidate, listOf(zero), now))
    }
}
