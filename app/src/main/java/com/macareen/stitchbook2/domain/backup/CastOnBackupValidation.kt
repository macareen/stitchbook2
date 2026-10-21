package com.macareen.stitchbook2.domain.backup

import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.guide.WorkingInstruction

internal fun validateCastOnBackup(execution: ExecutionRecord, revision: GuideRevisionRecord): Boolean = runCatching {
    val children = revision.nodes.groupBy { it.parentNodeId }.mapValues { (_, list) -> list.sortedBy { it.childOrder }.map { NodeId(it.nodeId) } }
    val nodes = revision.nodes.map { node ->
        val id = NodeId(node.nodeId)
        when (node.type) {
            "SECTION" -> Section(id, requireNotNull(node.title), children[id.value].orEmpty())
            "RANGE" -> Range(id, requireNotNull(node.rangeUnitLabel), requireNotNull(node.rangeStartInclusive), requireNotNull(node.rangeEndInclusive), children[id.value].orEmpty())
            "REPEAT" -> Repeat(id, requireNotNull(node.repeatCount), node.repeatLabel, children[id.value].orEmpty())
            "INSTRUCTION" -> Instruction(id, requireNotNull(node.instructionText))
            else -> error("Unknown guide node")
        }
    }
    val revisionId = DefinitionRevisionId(revision.id)
    val traversal = GuideTraversal(GuideDefinitionValidator.validate(GuideDefinition(GuideId(revision.guideId), revisionId, children[null].orEmpty(), nodes)))
    CastOnCounts.decode(execution.castOnCountsJson, revisionId).forEach { (address, count) ->
        require(count >= 0 && WorkingInstruction.of(traversal.resolve(address).instruction.text).castOnTarget != null)
    }
    true
}.getOrDefault(false)
