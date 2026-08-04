package com.macareen.stitchbook2.data.database

import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.GuideDefinition
import com.macareen.stitchbook2.domain.execution.GuideDefinitionValidator
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.GuideNode
import com.macareen.stitchbook2.domain.execution.Instruction
import com.macareen.stitchbook2.domain.execution.InvalidGuideDefinitionException
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.execution.Range
import com.macareen.stitchbook2.domain.execution.Repeat
import com.macareen.stitchbook2.domain.execution.Section
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.domain.guide.DraftId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.guide.GuideDraft

data class DraftAggregate(
    val draft: GuideDraftEntity,
    val nodes: List<DraftNodeEntity>
)

data class RevisionAggregate(
    val revision: DefinitionRevisionEntity,
    val nodes: List<RevisionNodeEntity>
)

class MalformedPersistedDefinitionException(
    message: String
) : IllegalStateException(message)

class InvalidDraftForPublicationException(
    message: String
) : IllegalArgumentException(message)

class InvalidDraftTreeException(
    message: String
) : IllegalArgumentException(message)

fun GuideEntity.toDomain() = Guide(
    id = GuideId(id),
    projectId = projectId,
    name = name,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Guide.toEntity() = GuideEntity(
    id = id.value,
    projectId = projectId,
    name = name,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun DraftAggregate.toDomain(): GuideDraft {
    val childrenByParent = nodes
        .groupBy(DraftNodeEntity::parentNodeId)
        .mapValues { (_, children) ->
            children.sortedDraftNodeRows().map { NodeId(it.nodeId) }
        }

    return GuideDraft(
        id = DraftId(draft.id),
        guideId = GuideId(draft.guideId),
        baseRevisionId = draft.baseRevisionId?.let(::DefinitionRevisionId),
        createdAt = draft.createdAt,
        updatedAt = draft.updatedAt,
        version = draft.version,
        rootNodeIds = childrenByParent[null].orEmpty(),
        nodes = nodes.map { row ->
            DraftNode(
                id = NodeId(row.nodeId),
                type = row.type.toDraftNodeType(),
                title = row.title,
                instructionText = row.instructionText,
                rangeUnitLabel = row.rangeUnitLabel,
                rangeStartInclusive = row.rangeStartInclusive,
                rangeEndInclusive = row.rangeEndInclusive,
                repeatCount = row.repeatCount,
                repeatLabel = row.repeatLabel,
                children = childrenByParent[row.nodeId].orEmpty()
            )
        }
    )
}

fun GuideDraft.toNodeEntities(): List<DraftNodeEntity> {
    val placement = nodePlacement()
    return nodes.map { node ->
        val location = checkNotNull(placement[node.id])
        DraftNodeEntity(
            draftId = id.value,
            nodeId = node.id.value,
            parentNodeId = location.parentNodeId?.value,
            childOrder = location.childOrder,
            type = node.type.name,
            title = node.title,
            instructionText = node.instructionText,
            rangeUnitLabel = node.rangeUnitLabel,
            rangeStartInclusive = node.rangeStartInclusive,
            rangeEndInclusive = node.rangeEndInclusive,
            repeatCount = node.repeatCount,
            repeatLabel = node.repeatLabel
        )
    }
}

fun GuideDraft.toGuideDefinitionForPublication(
    revisionId: DefinitionRevisionId
): GuideDefinition {
    val definition = GuideDefinition(
        guideId = guideId,
        revisionId = revisionId,
        rootNodeIds = rootNodeIds,
        nodes = nodes.map(DraftNode::toExecutableNode)
    )
    return GuideDefinitionValidator.validate(definition).definition
}

fun RevisionAggregate.toDomain(): DefinitionRevision {
    val childrenByParent = nodes
        .groupBy(RevisionNodeEntity::parentNodeId)
        .mapValues { (_, children) ->
            children.sortedRevisionNodeRows().map { NodeId(it.nodeId) }
        }

    val definition = GuideDefinition(
        guideId = GuideId(revision.guideId),
        revisionId = DefinitionRevisionId(revision.id),
        rootNodeIds = childrenByParent[null].orEmpty(),
        nodes = nodes.map { row ->
            row.toExecutableNode(childrenByParent[row.nodeId].orEmpty())
        }
    )
    val validated = try {
        GuideDefinitionValidator.validate(definition).definition
    } catch (error: InvalidGuideDefinitionException) {
        throw MalformedPersistedDefinitionException(
            "Stored revision ${revision.id} is invalid: ${error.message}"
        )
    }

    return DefinitionRevision(
        id = DefinitionRevisionId(revision.id),
        guideId = GuideId(revision.guideId),
        revisionNumber = revision.revisionNumber,
        createdAt = revision.createdAt,
        definition = validated
    )
}

fun DraftNodeEntity.toRevisionNode(revisionId: String) = RevisionNodeEntity(
    revisionId = revisionId,
    nodeId = nodeId,
    parentNodeId = parentNodeId,
    childOrder = childOrder,
    type = type,
    title = title,
    instructionText = instructionText,
    rangeUnitLabel = rangeUnitLabel,
    rangeStartInclusive = rangeStartInclusive,
    rangeEndInclusive = rangeEndInclusive,
    repeatCount = repeatCount,
    repeatLabel = repeatLabel
)

fun RevisionNodeEntity.toDraftNode(draftId: String) = DraftNodeEntity(
    draftId = draftId,
    nodeId = nodeId,
    parentNodeId = parentNodeId,
    childOrder = childOrder,
    type = type,
    title = title,
    instructionText = instructionText,
    rangeUnitLabel = rangeUnitLabel,
    rangeStartInclusive = rangeStartInclusive,
    rangeEndInclusive = rangeEndInclusive,
    repeatCount = repeatCount,
    repeatLabel = repeatLabel
)

private fun DraftNode.toExecutableNode(): GuideNode {
    return when (type) {
        DraftNodeType.SECTION -> Section(
            id = id,
            title = title ?: invalidDraft(id, "Section title is missing."),
            children = children
        )

        DraftNodeType.RANGE -> Range(
            id = id,
            unitLabel = rangeUnitLabel
                ?: invalidDraft(id, "Range unit label is missing."),
            startInclusive = rangeStartInclusive
                ?: invalidDraft(id, "Range start is missing."),
            endInclusive = rangeEndInclusive
                ?: invalidDraft(id, "Range end is missing."),
            children = children
        )

        DraftNodeType.REPEAT -> Repeat(
            id = id,
            count = repeatCount
                ?: invalidDraft(id, "Repeat count is missing."),
            label = repeatLabel,
            children = children
        )

        DraftNodeType.INSTRUCTION -> Instruction(
            id = id,
            text = instructionText
                ?: invalidDraft(id, "Instruction text is missing.")
        )
    }
}

private fun RevisionNodeEntity.toExecutableNode(
    children: List<NodeId>
): GuideNode {
    val nodeId = NodeId(nodeId)
    return when (type.toDraftNodeType()) {
        DraftNodeType.SECTION -> Section(
            id = nodeId,
            title = title
                ?: malformed(nodeId, "Section title is missing."),
            children = children
        )

        DraftNodeType.RANGE -> Range(
            id = nodeId,
            unitLabel = rangeUnitLabel
                ?: malformed(nodeId, "Range unit label is missing."),
            startInclusive = rangeStartInclusive
                ?: malformed(nodeId, "Range start is missing."),
            endInclusive = rangeEndInclusive
                ?: malformed(nodeId, "Range end is missing."),
            children = children
        )

        DraftNodeType.REPEAT -> Repeat(
            id = nodeId,
            count = repeatCount
                ?: malformed(nodeId, "Repeat count is missing."),
            label = repeatLabel,
            children = children
        )

        DraftNodeType.INSTRUCTION -> Instruction(
            id = nodeId,
            text = instructionText
                ?: malformed(nodeId, "Instruction text is missing.")
        )
    }
}

private fun GuideDraft.nodePlacement(): Map<NodeId, NodePlacement> {
    val nodesById = nodes.groupBy(DraftNode::id)
    val duplicate = nodesById.entries.firstOrNull { it.value.size > 1 }
    if (duplicate != null) {
        throw InvalidDraftTreeException("Duplicate draft node ID: ${duplicate.key.value}")
    }

    val placement = mutableMapOf<NodeId, NodePlacement>()
    fun place(nodeId: NodeId, parentNodeId: NodeId?, order: Int) {
        if (nodeId !in nodesById) {
            throw InvalidDraftTreeException("Draft references missing node: ${nodeId.value}")
        }
        if (placement.put(nodeId, NodePlacement(parentNodeId, order)) != null) {
            throw InvalidDraftTreeException(
                "Draft node has more than one parent: ${nodeId.value}"
            )
        }
    }

    rootNodeIds.forEachIndexed { index, nodeId -> place(nodeId, null, index) }
    nodes.forEach { parent ->
        parent.children.forEachIndexed { index, childId ->
            place(childId, parent.id, index)
        }
    }

    val unplaced = nodesById.keys - placement.keys
    if (unplaced.isNotEmpty()) {
        throw InvalidDraftTreeException(
            "Draft contains unplaced nodes: ${unplaced.joinToString { it.value }}"
        )
    }

    val reachable = mutableSetOf<NodeId>()
    fun visit(nodeId: NodeId) {
        if (!reachable.add(nodeId)) return
        nodesById.getValue(nodeId).single().children.forEach(::visit)
    }
    rootNodeIds.forEach(::visit)
    val unreachable = nodesById.keys - reachable
    if (unreachable.isNotEmpty()) {
        throw InvalidDraftTreeException(
            "Draft nodes are not reachable from a root: " +
                unreachable.joinToString { it.value }
        )
    }
    return placement
}

private fun String.toDraftNodeType(): DraftNodeType {
    return try {
        DraftNodeType.valueOf(this)
    } catch (_: IllegalArgumentException) {
        throw MalformedPersistedDefinitionException("Unknown guide node type: $this")
    }
}

private fun invalidDraft(nodeId: NodeId, message: String): Nothing {
    throw InvalidDraftForPublicationException(
        "Draft node ${nodeId.value}: $message"
    )
}

private fun malformed(nodeId: NodeId, message: String): Nothing {
    throw MalformedPersistedDefinitionException(
        "Revision node ${nodeId.value}: $message"
    )
}

private data class NodePlacement(
    val parentNodeId: NodeId?,
    val childOrder: Int
)

private fun List<DraftNodeEntity>.sortedDraftNodeRows(): List<DraftNodeEntity> {
    return sortedWith(compareBy(DraftNodeEntity::childOrder, DraftNodeEntity::nodeId))
}

private fun List<RevisionNodeEntity>.sortedRevisionNodeRows(): List<RevisionNodeEntity> {
    return sortedWith(
        compareBy(RevisionNodeEntity::childOrder, RevisionNodeEntity::nodeId)
    )
}
