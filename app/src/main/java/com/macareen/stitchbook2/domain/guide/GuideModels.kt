package com.macareen.stitchbook2.domain.guide

import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.GuideDefinition
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.NodeId

@JvmInline
value class DraftId(val value: String) {
    init {
        require(value.isNotBlank()) { "Draft ID must not be blank." }
    }
}

data class Guide(
    val id: GuideId,
    val projectId: String,
    val name: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

enum class DraftNodeType {
    SECTION,
    RANGE,
    REPEAT,
    INSTRUCTION
}

data class DraftNode(
    val id: NodeId,
    val type: DraftNodeType,
    val title: String? = null,
    val instructionText: String? = null,
    val rangeUnitLabel: String? = null,
    val rangeStartInclusive: Int? = null,
    val rangeEndInclusive: Int? = null,
    val repeatCount: Int? = null,
    val repeatLabel: String? = null,
    val children: List<NodeId> = emptyList()
)

data class GuideDraft(
    val id: DraftId,
    val guideId: GuideId,
    val baseRevisionId: DefinitionRevisionId?,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val rootNodeIds: List<NodeId>,
    val nodes: List<DraftNode>
)

data class DefinitionRevision(
    val id: DefinitionRevisionId,
    val guideId: GuideId,
    val revisionNumber: Int,
    val createdAt: Long,
    val definition: GuideDefinition
)
