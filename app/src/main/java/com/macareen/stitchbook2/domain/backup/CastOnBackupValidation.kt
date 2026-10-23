package com.macareen.stitchbook2.domain.backup

import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.guide.WorkingInstruction
import com.macareen.stitchbook2.domain.guide.*

/** Drafts can have unfinished work outside the reviewed body; restore must preserve that work too. */
internal fun validateDraftTargetBody(draft: GuideDraftRecord): Boolean = runCatching {
    val children = draft.nodes.groupBy { it.parentNodeId }.mapValues { (_, list) -> list.sortedBy { it.childOrder }.map { NodeId(it.nodeId) } }
    val model = GuideDraft(DraftId(draft.id), GuideId(draft.guideId), draft.baseRevisionId?.let(::DefinitionRevisionId),
        draft.createdAt, draft.updatedAt, draft.version, children[null].orEmpty(), draft.nodes.map { node ->
            DraftNode(NodeId(node.nodeId), DraftNodeType.valueOf(node.type), node.title, node.instructionText,
                node.rangeUnitLabel, node.rangeStartInclusive, node.rangeEndInclusive, node.repeatCount, node.repeatLabel,
                children[node.nodeId].orEmpty(), node.targetJson?.let(ManualTargets::decode))
        })
    DraftTargetCycles.problem(model) == null
}.getOrDefault(false)

internal fun backupDefinition(revision: GuideRevisionRecord): GuideDefinition {
    val children = revision.nodes.groupBy { it.parentNodeId }.mapValues { (_, list) -> list.sortedBy { it.childOrder }.map { NodeId(it.nodeId) } }
    val nodes = revision.nodes.map { node ->
        val id = NodeId(node.nodeId)
        when (node.type) {
            "SECTION" -> Section(id, requireNotNull(node.title), children[id.value].orEmpty(), node.targetJson?.let(ManualTargets::decode))
            "RANGE" -> Range(id, requireNotNull(node.rangeUnitLabel), requireNotNull(node.rangeStartInclusive), requireNotNull(node.rangeEndInclusive), children[id.value].orEmpty())
            "REPEAT" -> Repeat(id, requireNotNull(node.repeatCount), node.repeatLabel, children[id.value].orEmpty())
            "INSTRUCTION" -> Instruction(id, requireNotNull(node.instructionText), node.targetJson?.let(ManualTargets::decode))
            else -> error("Unknown guide node")
        }
    }
    return GuideDefinition(GuideId(revision.guideId), DefinitionRevisionId(revision.id), children[null].orEmpty(), nodes)
}

internal fun validateCastOnBackup(execution: ExecutionRecord, revision: GuideRevisionRecord): Boolean = runCatching {
    val revisionId = DefinitionRevisionId(revision.id)
    val traversal = GuideTraversal(GuideDefinitionValidator.validate(backupDefinition(revision)))
    CastOnCounts.decode(execution.castOnCountsJson, revisionId).forEach { (address, count) ->
        require(count >= 0 && WorkingInstruction.of(traversal.resolve(address).instruction.text).castOnTarget != null)
    }
    true
}.getOrDefault(false)


internal fun validateTargetBackup(execution: ExecutionRecord, revision: GuideRevisionRecord): Boolean = runCatching {
    val guide = GuideDefinitionValidator.validate(backupDefinition(revision))
    val revisionId = DefinitionRevisionId(revision.id)
    fun address(id: String, frames: List<AddressFrameRecord>) = ExecutionAddress(revisionId, NodeId(id), frames.sortedBy { it.frameOrder }.map {
        when (it.frameType) {
            "RANGE_VALUE" -> AncestryFrame.RangeValue(NodeId(it.containerNodeId), it.frameValue)
            "REPEAT_ITERATION" -> AncestryFrame.RepeatIteration(NodeId(it.containerNodeId), it.frameValue)
            else -> error("Unknown address frame")
        }
    })
    ExecutionStateValidator.validate(ExecutionState(ExecutionId(execution.id), GuideId(execution.guideId), revisionId,
        execution.currentInstructionNodeId?.let { address(it, execution.currentFrames) },
        execution.completedOccurrences.map { address(it.instructionNodeId, it.frames) }.toSet(),
        ExecutionStatus.valueOf(execution.status), targetWork = TargetWorkCodec.decode(execution.targetWorkJson, revisionId)), guide)
    true
}.getOrDefault(false)
