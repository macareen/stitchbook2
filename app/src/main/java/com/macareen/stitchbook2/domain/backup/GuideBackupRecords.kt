package com.macareen.stitchbook2.domain.backup

import com.macareen.stitchbook2.domain.execution.ExecutionStatus
import com.macareen.stitchbook2.domain.guide.DraftNodeType

/*
 * Portable rows of the guide graph: guides, their editable drafts, their
 * immutable published revisions, the progress (executions) pinned to those
 * revisions, the active-progress pointers, and project-to-guide links.
 *
 * These mirror the Room tables column for column rather than the guide
 * domain model, so a restore reproduces exactly what was stored (including
 * optimistic-concurrency versions and revision numbers) without re-running
 * publication or the execution engine. Node trees and address frames are
 * nested in their owner because they have no identity outside it.
 */

data class GuideRecord(
    val id: String,
    /** The project that owns this guide; null for a pattern guide or one opened on its own. */
    val projectId: String?,
    /** The pattern this guide was written for; null when it isn't tied to a pattern. */
    val libraryItemId: String?,
    val sizeLabel: String?,
    val name: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/** One node of a draft or revision tree. The tree it belongs to is the enclosing record. */
data class GuideNodeRecord(
    val nodeId: String,
    val parentNodeId: String?,
    val childOrder: Int,
    val type: String,
    val title: String?,
    val instructionText: String?,
    val rangeUnitLabel: String?,
    val rangeStartInclusive: Int?,
    val rangeEndInclusive: Int?,
    val repeatCount: Int?,
    val repeatLabel: String?
)

data class GuideDraftRecord(
    val id: String,
    val guideId: String,
    val baseRevisionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val nodes: List<GuideNodeRecord>
)

data class GuideRevisionRecord(
    val id: String,
    val guideId: String,
    val revisionNumber: Int,
    val createdAt: Long,
    val nodes: List<GuideNodeRecord>
)

/** One ordered ancestry frame of an execution address. */
data class AddressFrameRecord(
    val frameOrder: Int,
    val containerNodeId: String,
    val frameType: String,
    val frameValue: Int
)

data class CompletedOccurrenceRecord(
    val addressSignature: String,
    val instructionNodeId: String,
    val frames: List<AddressFrameRecord>
)

data class ExecutionRecord(
    val id: String,
    val guideId: String,
    val definitionRevisionId: String,
    /** The project this progress belongs to; null when the guide was opened on its own. */
    val projectId: String?,
    val status: String,
    val currentInstructionNodeId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val version: Long,
    val currentFrames: List<AddressFrameRecord>,
    val completedOccurrences: List<CompletedOccurrenceRecord>
)

/** Which execution is resumed for a guide in one project ("" for the guide on its own). */
data class ActiveExecutionRecord(
    val guideId: String,
    val projectKey: String,
    val executionId: String
)

/** A project using a pattern guide it doesn't own. */
data class ProjectGuideLink(val projectId: String, val guideId: String)

/** The whole guide graph, as loaded from or written to storage in one transaction. */
data class GuideBackupGraph(
    val guides: List<GuideRecord> = emptyList(),
    val drafts: List<GuideDraftRecord> = emptyList(),
    val revisions: List<GuideRevisionRecord> = emptyList(),
    val executions: List<ExecutionRecord> = emptyList(),
    val activeExecutions: List<ActiveExecutionRecord> = emptyList(),
    val projectGuides: List<ProjectGuideLink> = emptyList()
)

/**
 * Reads and writes the guide graph as rows. Each write runs in a single
 * transaction because the graph's foreign keys (drafts and executions on
 * revisions, active pointers on executions) only hold as a whole.
 */
interface GuideBackupStore {
    suspend fun load(): GuideBackupGraph

    /**
     * Makes the stored graph match [graph]: deletes guides it lacks (their
     * drafts, revisions, and progress cascade), updates kept guide rows in
     * place, replaces each kept guide's drafts, revisions, and progress with
     * the file's, and makes the project-guide links match.
     */
    suspend fun replace(graph: GuideBackupGraph)

    /** Inserts [graph]'s rows, which the caller has already limited to guides that don't exist yet. */
    suspend fun add(graph: GuideBackupGraph)
}

/** Node type names the guide model accepts. */
internal val GUIDE_NODE_TYPES: Set<String> = DraftNodeType.entries.map { it.name }.toSet()

/** Execution status names the execution model accepts. */
internal val EXECUTION_STATUSES: Set<String> = ExecutionStatus.entries.map { it.name }.toSet()

/** Address frame kinds; mirrors the frame-type constants in `ExecutionEntityMapping.kt`. */
internal val ADDRESS_FRAME_TYPES = setOf("RANGE_VALUE", "REPEAT_ITERATION")

/*
 * Nested lists are kept in a canonical order so the same stored graph always
 * compares equal, whichever order a database query or a file listed them in.
 */

fun List<GuideNodeRecord>.canonicalNodes(): List<GuideNodeRecord> =
    sortedWith(compareBy<GuideNodeRecord>({ it.parentNodeId.orEmpty() }, { it.childOrder }, { it.nodeId }))

fun List<AddressFrameRecord>.canonicalFrames(): List<AddressFrameRecord> = sortedBy { it.frameOrder }

fun GuideDraftRecord.canonical(): GuideDraftRecord = copy(nodes = nodes.canonicalNodes())

fun GuideRevisionRecord.canonical(): GuideRevisionRecord = copy(nodes = nodes.canonicalNodes())

fun ExecutionRecord.canonical(): ExecutionRecord = copy(
    currentFrames = currentFrames.canonicalFrames(),
    completedOccurrences = completedOccurrences
        .map { it.copy(frames = it.frames.canonicalFrames()) }
        .sortedBy { it.addressSignature }
)
