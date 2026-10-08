package com.macareen.stitchbook2.domain.statistics

import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CraftingStatisticsTest {

    private val today = LocalDate.of(2026, 3, 10)
    private val minute = 60_000L

    private fun session(id: String, date: LocalDate, minutes: Long, rows: Int? = null, stitches: Int? = null, projectId: String? = "p") =
        date.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli().let { start ->
            CraftingSession(id, projectId, start, start + minutes * minute, null, 0, "UTC", rows, stitches, null, 0, 0)
        }

    private val project = Project(
        id = "p", name = "Shawl", craft = Craft.CROCHET, projectType = ProjectType.SHAWL,
        status = ProjectStatus.COMPLETED, notes = null, createdAt = 0, updatedAt = 0,
        startDate = "2026-03-01", completedDate = "2026-03-09"
    )

    private fun compute(sessions: List<CraftingSession>, range: StatisticsRange = StatisticsRange.LAST_7_DAYS) =
        computeStatistics(sessions, listOf(project), emptyList(), emptyList(), range, today, now = 0)

    @Test
    fun windowTotalsAverageAndCraftAttribution() {
        val stats = compute(
            listOf(
                session("a", today, 30),
                session("b", today.minusDays(6), 60),
                session("old", today.minusDays(7), 500)
            )
        )
        assertEquals(90 * minute, stats.totalWorkedMillis)
        assertEquals(2, stats.sessionCount)
        assertEquals(45 * minute, stats.averageSessionMillis)
        assertEquals(mapOf<Craft?, Long>(Craft.CROCHET to 90 * minute), stats.workedMillisByCraft)
    }

    @Test
    fun rowsAreRecordedAndStitchesOnlyEstimatedWhenGiven() {
        val stats = compute(listOf(session("a", today, 30, rows = 10, stitches = 20), session("b", today, 10, rows = 5)))
        assertEquals(15, stats.rowsCompleted)
        assertEquals(40 * minute / 15, stats.averageMillisPerRow)
        assertEquals(200, stats.estimatedStitches)
        assertNull(compute(listOf(session("c", today, 5, rows = 3))).estimatedStitches)
    }

    @Test
    fun streaksAllowTodayToBeStillOpen() {
        val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(5), today.minusDays(6), today.minusDays(7))
        assertEquals(2 to 3, streaks(days, today))
        assertEquals(0 to 3, streaks(days, today.plusDays(2)))
    }

    @Test
    fun projectDatesFeedStartedCompletedAndDuration() {
        val stats = compute(emptyList(), StatisticsRange.LAST_30_DAYS)
        assertEquals(1, stats.projectsStarted)
        assertEquals(1, stats.projectsCompleted)
        assertEquals(8.0, stats.averageProjectDurationDays!!, 0.0)
    }

    @Test
    fun correctionsRecomputeDeterministically() {
        val original = listOf(session("a", today, 30))
        val corrected = listOf(session("a", today, 45))
        assertEquals(compute(original), compute(original))
        assertEquals(45 * minute, compute(corrected).totalWorkedMillis)
    }
}
