package com.macareen.stitchbook2.domain.cards

import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Milestone
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.YarnAllocation
import com.macareen.stitchbook2.domain.model.beforeAfterPair
import com.macareen.stitchbook2.domain.model.parseLocalDateOrNull
import com.macareen.stitchbook2.domain.model.roundQuantity
import com.macareen.stitchbook2.domain.statistics.StatisticsRange
import com.macareen.stitchbook2.domain.statistics.computeStatistics
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Exportable visual cards (PRODUCT_SPEC.md 6.10). This file decides *what*
 * a card says; rendering (and all localized wording) lives in the UI
 * layer. Nothing appears unless its [CardField] is switched on, and the
 * private fields ([CardField.NOTES]) are never on by default -- so a card
 * can't leak a private note unless the user deliberately picks it.
 */
enum class CardTemplate(val isProjectCard: Boolean) {
    PROGRESS(true),
    COMPLETED(true),
    MILESTONE(true),
    BEFORE_AFTER(true),
    YARN(true),
    WEEKLY(false),
    ANNUAL(false);

    val availableFields: List<CardField>
        get() = when (this) {
            PROGRESS -> listOf(CardField.PHOTO, CardField.CRAFT_AND_TYPE, CardField.DATES, CardField.TIME, CardField.ROWS, CardField.MILESTONE, CardField.DESCRIPTION, CardField.NOTES)
            COMPLETED -> listOf(CardField.PHOTO, CardField.CRAFT_AND_TYPE, CardField.DATES, CardField.TIME, CardField.ROWS, CardField.YARN, CardField.DESCRIPTION, CardField.NOTES)
            MILESTONE -> listOf(CardField.PHOTO, CardField.CRAFT_AND_TYPE, CardField.DATES, CardField.TIME)
            BEFORE_AFTER -> listOf(CardField.CRAFT_AND_TYPE, CardField.DATES, CardField.TIME)
            YARN -> listOf(CardField.PHOTO, CardField.CRAFT_AND_TYPE, CardField.YARN)
            WEEKLY, ANNUAL -> listOf(CardField.TIME, CardField.ROWS, CardField.YARN)
        }

    /** Sensible starting selection: never includes a private field. */
    val defaultFields: Set<CardField>
        get() = availableFields.filterNot { it.isPrivate || it == CardField.DESCRIPTION || it == CardField.DATES }.toSet()
}

enum class CardField(val isPrivate: Boolean = false) {
    PHOTO,
    CRAFT_AND_TYPE,
    DATES,
    TIME,
    ROWS,
    MILESTONE,
    YARN,
    DESCRIPTION,
    NOTES(isPrivate = true)
}

enum class CardFactKind {
    CRAFT,
    TYPE,
    STATUS,
    STARTED,
    FINISHED,
    DURATION_DAYS,
    TIME_SPENT,
    SESSIONS,
    ROWS,
    MILESTONE,
    MILESTONE_DATE,
    YARN_USED,
    YARN_ESTIMATED_YARDS,
    STREAK,
    PROJECTS_COMPLETED,
    DESCRIPTION,
    NOTES
}

/** Typed values so the renderer can localize and the domain stays string-free. */
sealed interface CardValue {
    data class Text(val text: String) : CardValue
    data class CraftValue(val craft: Craft) : CardValue
    data class TypeValue(val type: ProjectType, val customLabel: String?) : CardValue
    data class StatusValue(val status: ProjectStatus) : CardValue
    data class DateValue(val isoDate: String) : CardValue
    data class DurationValue(val millis: Long) : CardValue
    data class CountValue(val count: Int) : CardValue
    data class DaysValue(val days: Long) : CardValue
    data class AmountValue(val amount: Double, val unit: String, val label: String? = null) : CardValue
}

data class CardFact(val kind: CardFactKind, val value: CardValue)

data class CardContent(
    val template: CardTemplate,
    /** Project name for project cards; null for summaries (the renderer titles them). */
    val title: String?,
    val facts: List<CardFact>,
    /** 0, 1, or (before/after) 2 photo URIs, in display order. */
    val photoUris: List<String>,
    /** The window a summary covers, for its subtitle. */
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null
)

data class CardSource(
    val project: Project?,
    val projects: List<Project>,
    val photos: List<Photo>,
    val milestones: List<Milestone>,
    val sessions: List<CraftingSession>,
    val allocations: List<YarnAllocation>,
    val stashItems: List<StashItem>,
    val today: LocalDate,
    val now: Long
)

/** The photo a template starts with: the latest milestone's photo, else the newest photo. */
fun defaultPhotoId(template: CardTemplate, source: CardSource): String? {
    if (template == CardTemplate.MILESTONE) {
        latestReachedMilestone(source.milestones)?.let { milestone ->
            source.photos.firstOrNull { it.milestoneId == milestone.id }?.let { return it.id }
        }
    }
    return source.photos.maxByOrNull { it.createdAt }?.id
}

fun buildCardContent(
    template: CardTemplate,
    fields: Set<CardField>,
    selectedPhotoId: String?,
    source: CardSource
): CardContent {
    val enabled = fields intersect template.availableFields.toSet()
    if (!template.isProjectCard) return buildSummary(template, enabled, source)

    val project = requireNotNull(source.project) { "Project cards need a project" }
    val facts = mutableListOf<CardFact>()
    val projectSessions = source.sessions.filter { it.projectId == project.id }
    val workedMillis = projectSessions.sumOf { it.workedMillis(source.now) }

    if (CardField.CRAFT_AND_TYPE in enabled) {
        facts += CardFact(CardFactKind.CRAFT, CardValue.CraftValue(project.craft))
        facts += CardFact(CardFactKind.TYPE, CardValue.TypeValue(project.projectType, project.customTypeLabel))
    }
    if (template == CardTemplate.MILESTONE) {
        latestReachedMilestone(source.milestones)?.let { milestone ->
            facts += CardFact(CardFactKind.MILESTONE, CardValue.Text(milestone.title))
            if (CardField.DATES in enabled) {
                milestone.reachedDate?.let { facts += CardFact(CardFactKind.MILESTONE_DATE, CardValue.DateValue(it)) }
            }
        }
    }
    if (CardField.DATES in enabled) {
        project.startDate?.let { facts += CardFact(CardFactKind.STARTED, CardValue.DateValue(it)) }
        if (template == CardTemplate.COMPLETED || project.status == ProjectStatus.COMPLETED) {
            project.completedDate?.let { facts += CardFact(CardFactKind.FINISHED, CardValue.DateValue(it)) }
            val start = parseLocalDateOrNull(project.startDate)
            val end = parseLocalDateOrNull(project.completedDate)
            if (start != null && end != null && !end.isBefore(start)) {
                facts += CardFact(CardFactKind.DURATION_DAYS, CardValue.DaysValue(ChronoUnit.DAYS.between(start, end)))
            }
        }
    }
    if (CardField.TIME in enabled && workedMillis > 0) {
        facts += CardFact(CardFactKind.TIME_SPENT, CardValue.DurationValue(workedMillis))
        facts += CardFact(CardFactKind.SESSIONS, CardValue.CountValue(projectSessions.size))
    }
    if (CardField.ROWS in enabled) {
        val rows = projectSessions.sumOf { it.rowsCompleted ?: 0 }
        if (rows > 0) facts += CardFact(CardFactKind.ROWS, CardValue.CountValue(rows))
    }
    if (CardField.MILESTONE in enabled && template != CardTemplate.MILESTONE) {
        latestReachedMilestone(source.milestones)?.let {
            facts += CardFact(CardFactKind.MILESTONE, CardValue.Text(it.title))
        }
    }
    if (CardField.YARN in enabled) facts += yarnFacts(project.id, source)
    if (CardField.DESCRIPTION in enabled) {
        project.description?.let { facts += CardFact(CardFactKind.DESCRIPTION, CardValue.Text(it)) }
    }
    if (CardField.NOTES in enabled) {
        project.notes?.let { facts += CardFact(CardFactKind.NOTES, CardValue.Text(it)) }
    }

    val photoUris = when {
        template == CardTemplate.BEFORE_AFTER ->
            source.photos.beforeAfterPair()?.let { listOf(it.first.uri, it.second.uri) }.orEmpty()
        CardField.PHOTO in enabled ->
            listOfNotNull(source.photos.firstOrNull { it.id == selectedPhotoId }?.uri)
        else -> emptyList()
    }
    return CardContent(template, project.name, facts, photoUris)
}

private fun buildSummary(template: CardTemplate, enabled: Set<CardField>, source: CardSource): CardContent {
    val range = if (template == CardTemplate.WEEKLY) StatisticsRange.LAST_7_DAYS else StatisticsRange.THIS_YEAR
    val stats = computeStatistics(
        source.sessions, source.projects, source.allocations, source.stashItems, range, source.today, source.now
    )
    val facts = mutableListOf<CardFact>()
    if (CardField.TIME in enabled) {
        facts += CardFact(CardFactKind.TIME_SPENT, CardValue.DurationValue(stats.totalWorkedMillis))
        facts += CardFact(CardFactKind.SESSIONS, CardValue.CountValue(stats.sessionCount))
        facts += CardFact(
            CardFactKind.STREAK,
            CardValue.DaysValue((if (template == CardTemplate.WEEKLY) stats.currentStreakDays else stats.longestStreakDays).toLong())
        )
    }
    if (template == CardTemplate.ANNUAL) {
        facts += CardFact(CardFactKind.PROJECTS_COMPLETED, CardValue.CountValue(stats.projectsCompleted))
    }
    if (CardField.ROWS in enabled && stats.rowsCompleted > 0) {
        facts += CardFact(CardFactKind.ROWS, CardValue.CountValue(stats.rowsCompleted))
    }
    if (CardField.YARN in enabled) {
        stats.estimatedYardsUsed?.let {
            facts += CardFact(CardFactKind.YARN_ESTIMATED_YARDS, CardValue.AmountValue(it, "yd"))
        }
    }
    return CardContent(template, null, facts, emptyList(), stats.window.start, stats.window.end)
}

private fun yarnFacts(projectId: String, source: CardSource): List<CardFact> {
    val stashById = source.stashItems.associateBy { it.id }
    val used = source.allocations.filter { it.projectId == projectId && it.quantityUsed > 0.0 }
    val facts = used.mapNotNull { allocation ->
        val item = stashById[allocation.stashItemId] ?: return@mapNotNull null
        CardFact(
            CardFactKind.YARN_USED,
            CardValue.AmountValue(roundQuantity(allocation.quantityUsed), item.unitLabel, label = item.name)
        )
    }
    val yards = used.mapNotNull { allocation ->
        stashById[allocation.stashItemId]?.yardagePerUnit?.let { it * allocation.quantityUsed }
    }
    return if (yards.isEmpty()) {
        facts
    } else {
        facts + CardFact(CardFactKind.YARN_ESTIMATED_YARDS, CardValue.AmountValue(Math.round(yards.sum() * 10) / 10.0, "yd"))
    }
}

private fun latestReachedMilestone(milestones: List<Milestone>): Milestone? =
    milestones.filter { it.reachedDate != null }.maxWithOrNull(compareBy<Milestone> { it.reachedDate }.thenBy { it.position })
        ?: milestones.maxByOrNull { it.position }
