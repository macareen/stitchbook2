package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.data.database.ActiveExecutionEntity
import com.macareen.stitchbook2.data.database.DefinitionRevisionEntity
import com.macareen.stitchbook2.data.database.DraftNodeEntity
import com.macareen.stitchbook2.data.database.ExecutionCompletedOccurrenceEntity
import com.macareen.stitchbook2.data.database.ExecutionCompletedOccurrenceFrameEntity
import com.macareen.stitchbook2.data.database.ExecutionCurrentAddressFrameEntity
import com.macareen.stitchbook2.data.database.ExecutionEntity
import com.macareen.stitchbook2.data.database.GuideBackupDao
import com.macareen.stitchbook2.data.database.GuideDraftEntity
import com.macareen.stitchbook2.data.database.GuideEntity
import com.macareen.stitchbook2.data.database.GuideGraphRows
import com.macareen.stitchbook2.data.database.ProjectGuideLinkEntity
import com.macareen.stitchbook2.data.database.RevisionNodeEntity
import com.macareen.stitchbook2.domain.backup.ActiveExecutionRecord
import com.macareen.stitchbook2.domain.backup.AddressFrameRecord
import com.macareen.stitchbook2.domain.backup.CompletedOccurrenceRecord
import com.macareen.stitchbook2.domain.backup.ExecutionRecord
import com.macareen.stitchbook2.domain.backup.GuideBackupGraph
import com.macareen.stitchbook2.domain.backup.GuideBackupStore
import com.macareen.stitchbook2.domain.backup.GuideDraftRecord
import com.macareen.stitchbook2.domain.backup.GuideNodeRecord
import com.macareen.stitchbook2.domain.backup.GuideRecord
import com.macareen.stitchbook2.domain.backup.GuideRevisionRecord
import com.macareen.stitchbook2.domain.backup.ProjectGuideLink
import com.macareen.stitchbook2.domain.backup.canonical

/** The Room-backed guide graph for backup: one transaction per load or write. */
class RoomGuideBackupStore(private val dao: GuideBackupDao) : GuideBackupStore {

    override suspend fun load(): GuideBackupGraph = dao.load().toGraph()

    override suspend fun replace(graph: GuideBackupGraph) = dao.replace(graph.toRows())

    override suspend fun add(graph: GuideBackupGraph) = dao.add(graph.toRows())
}

internal fun GuideGraphRows.toGraph(): GuideBackupGraph {
    val draftNodesByDraft = draftNodes.groupBy { it.draftId }
    val revisionNodesByRevision = revisionNodes.groupBy { it.revisionId }
    val framesByExecution = currentFrames.groupBy { it.executionId }
    val occurrencesByExecution = completedOccurrences.groupBy { it.executionId }
    val occurrenceFrames = completedOccurrenceFrames.groupBy { it.executionId to it.addressSignature }
    return GuideBackupGraph(
        guides = guides.map {
            GuideRecord(it.id, it.projectId, it.libraryItemId, it.sizeLabel, it.name, it.notes, it.createdAt, it.updatedAt)
        },
        drafts = drafts.map { draft ->
            GuideDraftRecord(
                id = draft.id,
                guideId = draft.guideId,
                baseRevisionId = draft.baseRevisionId,
                createdAt = draft.createdAt,
                updatedAt = draft.updatedAt,
                version = draft.version,
                nodes = draftNodesByDraft[draft.id].orEmpty().map { it.toRecord() }
            ).canonical()
        },
        revisions = revisions.map { revision ->
            GuideRevisionRecord(
                id = revision.id,
                guideId = revision.guideId,
                revisionNumber = revision.revisionNumber,
                createdAt = revision.createdAt,
                nodes = revisionNodesByRevision[revision.id].orEmpty().map { it.toRecord() }
            ).canonical()
        },
        executions = executions.map { execution ->
            ExecutionRecord(
                id = execution.id,
                guideId = execution.guideId,
                definitionRevisionId = execution.definitionRevisionId,
                projectId = execution.projectId,
                status = execution.status,
                currentInstructionNodeId = execution.currentInstructionNodeId,
                createdAt = execution.createdAt,
                updatedAt = execution.updatedAt,
                completedAt = execution.completedAt,
                version = execution.version,
                currentFrames = framesByExecution[execution.id].orEmpty().map {
                    AddressFrameRecord(it.frameOrder, it.containerNodeId, it.frameType, it.frameValue)
                },
                completedOccurrences = occurrencesByExecution[execution.id].orEmpty().map { occurrence ->
                    CompletedOccurrenceRecord(
                        addressSignature = occurrence.addressSignature,
                        instructionNodeId = occurrence.instructionNodeId,
                        frames = occurrenceFrames[execution.id to occurrence.addressSignature].orEmpty().map {
                            AddressFrameRecord(it.frameOrder, it.containerNodeId, it.frameType, it.frameValue)
                        }
                    )
                }
            ).canonical()
        },
        activeExecutions = activeExecutions.map { ActiveExecutionRecord(it.guideId, it.projectKey, it.executionId) },
        projectGuides = projectGuides.map { ProjectGuideLink(it.projectId, it.guideId) }
    )
}

internal fun GuideBackupGraph.toRows(): GuideGraphRows = GuideGraphRows(
    guides = guides.map {
        GuideEntity(
            id = it.id,
            projectId = it.projectId,
            libraryItemId = it.libraryItemId,
            sizeLabel = it.sizeLabel,
            name = it.name,
            notes = it.notes,
            createdAt = it.createdAt,
            updatedAt = it.updatedAt
        )
    },
    drafts = drafts.map { GuideDraftEntity(it.id, it.guideId, it.baseRevisionId, it.createdAt, it.updatedAt, it.version) },
    draftNodes = drafts.flatMap { draft ->
        draft.nodes.map {
            DraftNodeEntity(
                draftId = draft.id,
                nodeId = it.nodeId,
                parentNodeId = it.parentNodeId,
                childOrder = it.childOrder,
                type = it.type,
                title = it.title,
                instructionText = it.instructionText,
                rangeUnitLabel = it.rangeUnitLabel,
                rangeStartInclusive = it.rangeStartInclusive,
                rangeEndInclusive = it.rangeEndInclusive,
                repeatCount = it.repeatCount,
                repeatLabel = it.repeatLabel
            )
        }
    },
    revisions = revisions.map { DefinitionRevisionEntity(it.id, it.guideId, it.revisionNumber, it.createdAt) },
    revisionNodes = revisions.flatMap { revision ->
        revision.nodes.map {
            RevisionNodeEntity(
                revisionId = revision.id,
                nodeId = it.nodeId,
                parentNodeId = it.parentNodeId,
                childOrder = it.childOrder,
                type = it.type,
                title = it.title,
                instructionText = it.instructionText,
                rangeUnitLabel = it.rangeUnitLabel,
                rangeStartInclusive = it.rangeStartInclusive,
                rangeEndInclusive = it.rangeEndInclusive,
                repeatCount = it.repeatCount,
                repeatLabel = it.repeatLabel
            )
        }
    },
    executions = executions.map {
        ExecutionEntity(
            id = it.id,
            guideId = it.guideId,
            definitionRevisionId = it.definitionRevisionId,
            status = it.status,
            currentInstructionNodeId = it.currentInstructionNodeId,
            createdAt = it.createdAt,
            updatedAt = it.updatedAt,
            completedAt = it.completedAt,
            version = it.version,
            projectId = it.projectId
        )
    },
    currentFrames = executions.flatMap { execution ->
        execution.currentFrames.map {
            ExecutionCurrentAddressFrameEntity(execution.id, it.frameOrder, it.containerNodeId, it.frameType, it.frameValue)
        }
    },
    completedOccurrences = executions.flatMap { execution ->
        execution.completedOccurrences.map {
            ExecutionCompletedOccurrenceEntity(execution.id, it.addressSignature, it.instructionNodeId)
        }
    },
    completedOccurrenceFrames = executions.flatMap { execution ->
        execution.completedOccurrences.flatMap { occurrence ->
            occurrence.frames.map {
                ExecutionCompletedOccurrenceFrameEntity(
                    execution.id,
                    occurrence.addressSignature,
                    it.frameOrder,
                    it.containerNodeId,
                    it.frameType,
                    it.frameValue
                )
            }
        }
    },
    activeExecutions = activeExecutions.map { ActiveExecutionEntity(it.guideId, it.projectKey, it.executionId) },
    projectGuides = projectGuides.map { ProjectGuideLinkEntity(it.projectId, it.guideId) }
)

private fun DraftNodeEntity.toRecord() = GuideNodeRecord(
    nodeId, parentNodeId, childOrder, type, title, instructionText,
    rangeUnitLabel, rangeStartInclusive, rangeEndInclusive, repeatCount, repeatLabel
)

private fun RevisionNodeEntity.toRecord() = GuideNodeRecord(
    nodeId, parentNodeId, childOrder, type, title, instructionText,
    rangeUnitLabel, rangeStartInclusive, rangeEndInclusive, repeatCount, repeatLabel
)
