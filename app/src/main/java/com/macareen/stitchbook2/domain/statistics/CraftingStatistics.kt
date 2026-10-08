package com.macareen.stitchbook2.domain.statistics

import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.YarnAllocation
import com.macareen.stitchbook2.domain.model.parseLocalDateOrNull
import com.macareen.stitchbook2.domain.model.roundQuantity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Where a number comes from (PRODUCT_SPEC.md 6.4: statistics must
 * "distinguish recorded measurements from estimates or derived values").
 */
enum class StatisticProvenance {
    /** Entered or timed directly. */
    RECORDED,

    /** Calculated exactly from recorded values (sums, averages, streaks). */
    DERIVED,

    /** Depends on an assumption such as stitches per row or yards per unit. */
    ESTIMATED
}

enum class StatisticsRange {
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_YEAR,
    ALL_TIME;

    /** Inclusive local-date window ending [today]; a null start means unbounded. */
    fun window(today: LocalDate): StatisticsWindow = when (this) {
        LAST_7_DAYS -> StatisticsWindow(today.minusDays(6), today)
        LAST_30_DAYS -> StatisticsWindow(today.minusDays(29), today)
        THIS_YEAR -> StatisticsWindow(today.withDayOfYear(1), today)
        ALL_TIME -> StatisticsWindow(null, today)
    }
}

data class StatisticsWindow(val start: LocalDate?, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean =
        (start == null || !date.isBefore(start)) && !date.isAfter(end)
}

data class WeekTotal(val weekStart: LocalDate, val workedMillis: Long)

data class CraftingStatistics(
    val window: StatisticsWindow,
    // Time (derived from recorded session timestamps)
    val totalWorkedMillis: Long,
    val sessionCount: Int,
    val averageSessionMillis: Long?,
    val workedMillisByProject: Map<String?, Long>,
    val workedMillisByCraft: Map<Craft?, Long>,
    val workedMillisByMonth: Map<YearMonth, Long>,
    /** The 8 ISO weeks (Monday start) ending with the window's end, oldest first. */
    val weeklyTrend: List<WeekTotal>,
    // Rows (recorded) and what's derived/estimated from them
    val rowsCompleted: Int,
    val averageMillisPerRow: Long?,
    val estimatedStitches: Int?,
    // Streaks are always computed over all sessions, relative to today.
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    // Projects (recorded dates)
    val projectsStarted: Int,
    val projectsCompleted: Int,
    val averageProjectDurationDays: Double?,
    // Yarn: consumption records carry no per-use date, so these are all-time.
    val yarnUsedByUnit: Map<String, Double>,
    val estimatedYardsUsed: Double?
)

/**
 * Pure and deterministic: identical inputs always give identical output, so
 * any correction to a session or project simply recomputes everything
 * (ROADMAP.md Phase 8: "corrections cause deterministic recomputation").
 */
fun computeStatistics(
    sessions: List<CraftingSession>,
    projects: List<Project>,
    allocations: List<YarnAllocation>,
    stashItems: List<StashItem>,
    range: StatisticsRange,
    today: LocalDate,
    now: Long
): CraftingStatistics {
    val window = range.window(today)
    val inWindow = sessions.filter { it.localStartDate() in window }
    val worked = inWindow.associateWith { it.workedMillis(now) }
    val total = worked.values.sum()
    val projectsById = projects.associateBy { it.id }

    val withRows = inWindow.filter { (it.rowsCompleted ?: 0) > 0 }
    val rows = withRows.sumOf { it.rowsCompleted ?: 0 }
    val rowTime = withRows.sumOf { worked.getValue(it) }
    val stitchSessions = withRows.filter { (it.stitchesPerRow ?: 0) > 0 }

    val weekEnd = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val windowEndWeek = window.end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val trendEnd = if (windowEndWeek.isAfter(weekEnd)) weekEnd else windowEndWeek
    val allWorkedByWeek = sessions.groupBy { it.localStartDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .mapValues { (_, list) -> list.sumOf { it.workedMillis(now) } }

    val started = projects.mapNotNull { parseLocalDateOrNull(it.startDate) }.filter { it in window }
    val completedProjects = projects.filter { parseLocalDateOrNull(it.completedDate)?.let { date -> date in window } == true }
    val durations = completedProjects.mapNotNull { project ->
        val start = parseLocalDateOrNull(project.startDate) ?: return@mapNotNull null
        val end = parseLocalDateOrNull(project.completedDate) ?: return@mapNotNull null
        ChronoUnit.DAYS.between(start, end).takeIf { it >= 0 }
    }

    val stashById = stashItems.associateBy { it.id }
    val usedAllocations = allocations.filter { it.quantityUsed > 0.0 }
    val yarnByUnit = usedAllocations
        .groupBy { stashById[it.stashItemId]?.unitLabel ?: "units" }
        .mapValues { (_, list) -> roundQuantity(list.sumOf { it.quantityUsed }) }
    val yardEstimates = usedAllocations.mapNotNull { allocation ->
        stashById[allocation.stashItemId]?.yardagePerUnit?.let { it * allocation.quantityUsed }
    }

    val streaks = streaks(sessions.map { it.localStartDate() }.toSet(), today)

    return CraftingStatistics(
        window = window,
        totalWorkedMillis = total,
        sessionCount = inWindow.size,
        averageSessionMillis = if (inWindow.isEmpty()) null else total / inWindow.size,
        workedMillisByProject = inWindow.groupBy { it.projectId }.mapValues { (_, list) -> list.sumOf { worked.getValue(it) } },
        workedMillisByCraft = inWindow.groupBy { session -> session.projectId?.let { projectsById[it]?.craft } }
            .mapValues { (_, list) -> list.sumOf { worked.getValue(it) } },
        workedMillisByMonth = inWindow.groupBy { YearMonth.from(it.localStartDate()) }
            .mapValues { (_, list) -> list.sumOf { worked.getValue(it) } }
            .toSortedMap(),
        weeklyTrend = (7 downTo 0).map { weeksAgo ->
            val weekStart = trendEnd.minusWeeks(weeksAgo.toLong())
            WeekTotal(weekStart, allWorkedByWeek[weekStart] ?: 0L)
        },
        rowsCompleted = rows,
        averageMillisPerRow = if (rows == 0) null else rowTime / rows,
        estimatedStitches = if (stitchSessions.isEmpty()) {
            null
        } else {
            stitchSessions.sumOf { (it.rowsCompleted ?: 0) * (it.stitchesPerRow ?: 0) }
        },
        currentStreakDays = streaks.first,
        longestStreakDays = streaks.second,
        projectsStarted = started.size,
        projectsCompleted = completedProjects.size,
        averageProjectDurationDays = if (durations.isEmpty()) null else durations.average(),
        yarnUsedByUnit = yarnByUnit,
        estimatedYardsUsed = if (yardEstimates.isEmpty()) null else Math.round(yardEstimates.sum() * 10.0) / 10.0
    )
}

/**
 * (current, longest) runs of consecutive crafting days. The current streak
 * still counts if today has no session yet but yesterday did -- the day
 * isn't over.
 */
internal fun streaks(days: Set<LocalDate>, today: LocalDate): Pair<Int, Int> {
    if (days.isEmpty()) return 0 to 0
    var longest = 0
    days.forEach { day ->
        if (day.minusDays(1) !in days) {
            var length = 1
            while (day.plusDays(length.toLong()) in days) length++
            if (length > longest) longest = length
        }
    }
    val anchor = when {
        today in days -> today
        today.minusDays(1) in days -> today.minusDays(1)
        else -> return 0 to longest
    }
    var current = 1
    while (anchor.minusDays(current.toLong()) in days) current++
    return current to longest
}
