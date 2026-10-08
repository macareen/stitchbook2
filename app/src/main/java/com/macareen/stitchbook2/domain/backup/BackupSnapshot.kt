package com.macareen.stitchbook2.domain.backup

import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.CounterNote
import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.model.JournalEntry
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.Milestone
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectPatternLink
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.ToolTemplate
import com.macareen.stitchbook2.domain.model.YarnAllocation

/** A project-to-tool membership (the junction has no domain model of its own elsewhere). */
data class ToolAssignment(val projectId: String, val toolItemId: String)

/** Every portable record type, in dependency (foreign-key) order. */
enum class BackupRecordType {
    PROJECTS,
    LIBRARY_ITEMS,
    STASH_ITEMS,
    TOOL_SETS,
    TOOL_ITEMS,
    TOOL_TEMPLATES,
    TOOL_ASSIGNMENTS,
    COUNTERS,
    COUNTER_NOTES,
    YARN_ALLOCATIONS,
    PATTERN_LINKS,
    MILESTONES,
    PHOTOS,
    JOURNAL_ENTRIES,
    SESSIONS,
    GUIDES,
    GUIDE_DRAFTS,
    GUIDE_REVISIONS,
    EXECUTIONS,
    ACTIVE_EXECUTIONS,
    PROJECT_GUIDES
}

/** The guide-graph types, which a file carries all together or not at all. */
val GUIDE_GRAPH_TYPES: Set<BackupRecordType> = setOf(
    BackupRecordType.GUIDES,
    BackupRecordType.GUIDE_DRAFTS,
    BackupRecordType.GUIDE_REVISIONS,
    BackupRecordType.EXECUTIONS,
    BackupRecordType.ACTIVE_EXECUTIONS,
    BackupRecordType.PROJECT_GUIDES
)

/**
 * The portable library (ROADMAP.md Phase 10). Each list is null when a
 * file doesn't contain that record type at all -- older backups predate
 * most types -- which is different from an empty list ("this library has
 * none"). Restores never touch types a file doesn't mention.
 *
 * The guide graph (guides, drafts, revisions, executions, active pointers,
 * project-guide links) is carried as a whole from format 3 on; format 1 and
 * 2 files leave all six null.
 */
data class BackupSnapshot(
    val formatVersion: Int,
    val projects: List<Project>? = null,
    val libraryItems: List<LibraryItem>? = null,
    val stashItems: List<StashItem>? = null,
    val toolSets: List<ToolSet>? = null,
    val toolItems: List<ToolItem>? = null,
    val toolTemplates: List<ToolTemplate>? = null,
    val toolAssignments: List<ToolAssignment>? = null,
    val counters: List<Counter>? = null,
    val counterNotes: List<CounterNote>? = null,
    val yarnAllocations: List<YarnAllocation>? = null,
    val patternLinks: List<ProjectPatternLink>? = null,
    val milestones: List<Milestone>? = null,
    val photos: List<Photo>? = null,
    val journalEntries: List<JournalEntry>? = null,
    val sessions: List<CraftingSession>? = null,
    val guides: List<GuideRecord>? = null,
    val guideDrafts: List<GuideDraftRecord>? = null,
    val guideRevisions: List<GuideRevisionRecord>? = null,
    val executions: List<ExecutionRecord>? = null,
    val activeExecutions: List<ActiveExecutionRecord>? = null,
    val projectGuides: List<ProjectGuideLink>? = null
) {
    /** The guide graph this snapshot carries, or null when it carries no guides. */
    fun guideGraph(): GuideBackupGraph? {
        if (guides == null) return null
        return GuideBackupGraph(
            guides = guides,
            drafts = guideDrafts.orEmpty(),
            revisions = guideRevisions.orEmpty(),
            executions = executions.orEmpty(),
            activeExecutions = activeExecutions.orEmpty(),
            projectGuides = projectGuides.orEmpty()
        )
    }

    /** A copy carrying [graph] as its guide types; null leaves them all absent. */
    fun withGuideGraph(graph: GuideBackupGraph?): BackupSnapshot = copy(
        guides = graph?.guides,
        guideDrafts = graph?.drafts,
        guideRevisions = graph?.revisions,
        executions = graph?.executions,
        activeExecutions = graph?.activeExecutions,
        projectGuides = graph?.projectGuides
    )

    /** Record count per type present in this snapshot. */
    fun counts(): Map<BackupRecordType, Int> = buildMap {
        BackupRecordType.entries.forEach { type -> keysOf(type)?.let { put(type, it.size) } }
    }

    /** The identity keys of one type's records, or null if the type is absent. */
    fun keysOf(type: BackupRecordType): List<String>? = when (type) {
        BackupRecordType.PROJECTS -> projects?.map { it.id }
        BackupRecordType.LIBRARY_ITEMS -> libraryItems?.map { it.id }
        BackupRecordType.STASH_ITEMS -> stashItems?.map { it.id }
        BackupRecordType.TOOL_SETS -> toolSets?.map { it.id }
        BackupRecordType.TOOL_ITEMS -> toolItems?.map { it.id }
        BackupRecordType.TOOL_TEMPLATES -> toolTemplates?.map { it.id }
        BackupRecordType.TOOL_ASSIGNMENTS -> toolAssignments?.map { "${it.projectId}/${it.toolItemId}" }
        BackupRecordType.COUNTERS -> counters?.map { it.id }
        BackupRecordType.COUNTER_NOTES -> counterNotes?.map { it.id }
        BackupRecordType.YARN_ALLOCATIONS -> yarnAllocations?.map { it.id }
        BackupRecordType.PATTERN_LINKS -> patternLinks?.map { "${it.projectId}/${it.libraryItemId}" }
        BackupRecordType.MILESTONES -> milestones?.map { it.id }
        BackupRecordType.PHOTOS -> photos?.map { it.id }
        BackupRecordType.JOURNAL_ENTRIES -> journalEntries?.map { it.id }
        BackupRecordType.SESSIONS -> sessions?.map { it.id }
        BackupRecordType.GUIDES -> guides?.map { it.id }
        BackupRecordType.GUIDE_DRAFTS -> guideDrafts?.map { it.id }
        BackupRecordType.GUIDE_REVISIONS -> guideRevisions?.map { it.id }
        BackupRecordType.EXECUTIONS -> executions?.map { it.id }
        BackupRecordType.ACTIVE_EXECUTIONS -> activeExecutions?.map { activeExecutionKey(it.guideId, it.projectKey) }
        BackupRecordType.PROJECT_GUIDES -> projectGuides?.map { "${it.projectId}/${it.guideId}" }
    }

    /** Records of one type keyed by identity, for equality comparison during merge. */
    fun recordsOf(type: BackupRecordType): Map<String, Any>? {
        val keys = keysOf(type) ?: return null
        val records: List<Any> = when (type) {
            BackupRecordType.PROJECTS -> projects!!
            BackupRecordType.LIBRARY_ITEMS -> libraryItems!!
            BackupRecordType.STASH_ITEMS -> stashItems!!
            BackupRecordType.TOOL_SETS -> toolSets!!
            BackupRecordType.TOOL_ITEMS -> toolItems!!
            BackupRecordType.TOOL_TEMPLATES -> toolTemplates!!
            BackupRecordType.TOOL_ASSIGNMENTS -> toolAssignments!!
            BackupRecordType.COUNTERS -> counters!!
            BackupRecordType.COUNTER_NOTES -> counterNotes!!
            BackupRecordType.YARN_ALLOCATIONS -> yarnAllocations!!
            BackupRecordType.PATTERN_LINKS -> patternLinks!!
            BackupRecordType.MILESTONES -> milestones!!
            BackupRecordType.PHOTOS -> photos!!
            BackupRecordType.JOURNAL_ENTRIES -> journalEntries!!
            BackupRecordType.SESSIONS -> sessions!!
            BackupRecordType.GUIDES -> guides!!
            BackupRecordType.GUIDE_DRAFTS -> guideDrafts!!
            BackupRecordType.GUIDE_REVISIONS -> guideRevisions!!
            BackupRecordType.EXECUTIONS -> executions!!
            BackupRecordType.ACTIVE_EXECUTIONS -> activeExecutions!!
            BackupRecordType.PROJECT_GUIDES -> projectGuides!!
        }
        return keys.zip(records).toMap()
    }
}

enum class BackupIssueKind {
    NEWER_FORMAT,
    DUPLICATE_ID,
    MISSING_REFERENCE,
    INVALID_VALUE
}

/** One actionable validation problem: which record, of which type, and what's wrong. */
data class BackupIssue(
    val kind: BackupIssueKind,
    val type: BackupRecordType?,
    val recordKey: String?,
    val detail: String
)

enum class RestoreMode {
    /** Make the library match the file for every type the file contains. */
    REPLACE,

    /** Add only records that don't exist yet; conflicts are reported and left untouched. */
    MERGE
}

/** Per-type outcome of comparing a file with the current library. */
data class TypeComparison(
    val incoming: Int,
    val new: Int,
    val identical: Int,
    val conflicting: Int,
    /** Present locally but absent from the file -- REPLACE would remove these. */
    val onlyLocal: Int
)

data class BackupConflict(val type: BackupRecordType, val recordKey: String)

/** The identity of an active-progress pointer; the "" key (a guide on its own) reads as "(no project)". */
fun activeExecutionKey(guideId: String, projectKey: String): String =
    "$guideId/${projectKey.ifEmpty { "(no project)" }}"

/**
 * Something a restore changes or leaves out on purpose instead of failing:
 * a link cleared because its target is missing, or a record not written
 * because its guide stays as it is on this device. Shown to the user so
 * nothing is dropped silently.
 */
data class BackupNotice(
    val type: BackupRecordType,
    val recordKey: String,
    val detail: String
)

sealed interface BackupPreview {
    /** The file couldn't be read as a Stitchbook backup at all. */
    data object Unreadable : BackupPreview

    data class Invalid(val issues: List<BackupIssue>) : BackupPreview

    data class Ready(
        val formatVersion: Int,
        val comparison: Map<BackupRecordType, TypeComparison>,
        val conflicts: List<BackupConflict>,
        /** What a MERGE of this file would change or leave out instead of failing. */
        val mergeNotices: List<BackupNotice> = emptyList()
    ) : BackupPreview
}
