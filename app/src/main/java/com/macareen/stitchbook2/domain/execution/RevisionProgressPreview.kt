package com.macareen.stitchbook2.domain.execution

/** Suggestions only: none of these results changes a revision pin or persisted progress. */
data class ProgressCarryCandidate(val source: ExecutionAddress, val target: ExecutionAddress, val instruction: String)
enum class UnmatchedProgressReason { REMOVED_OCCURRENCE, CHANGED_INSTRUCTION, CHANGED_CONTEXT }
data class UnmatchedProgress(val source: ExecutionAddress, val instruction: String, val reason: UnmatchedProgressReason)
data class RevisionProgressPreview(
    val sourceRevision: DefinitionRevisionId,
    val targetRevision: DefinitionRevisionId,
    val candidates: List<ProgressCarryCandidate>,
    val unmatched: List<UnmatchedProgress>,
    val targetOccurrenceCount: Int
)
class RevisionPreviewTooLargeException : IllegalArgumentException("This guide is too large for a progress comparison.")

object RevisionProgressComparison {
    // Bound expansion and memory. A partial scan must never become a partial carryover proposal.
    private const val MAX_OCCURRENCES = 10_000

    fun preview(source: GuideDefinition, target: GuideDefinition,
                completed: Set<ExecutionAddress>): RevisionProgressPreview {
        require(source.guideId == target.guideId) { "Progress comparisons require the same guide." }
        require(source.revisionId != target.revisionId) { "Select a different revision." }
        val sourceGuide = GuideDefinitionValidator.validate(source)
        val targetGuide = GuideDefinitionValidator.validate(target)
        val oldRecords = records(sourceGuide)
        val newRecords = records(targetGuide)
        val oldByAddress = oldRecords.associateBy { it.address }
        val newByAddress = newRecords.associateBy { it.address }
        require(completed.all(oldByAddress::containsKey)) { "Completed work does not belong to the source revision." }
        val candidates = mutableListOf<ProgressCarryCandidate>()
        val unmatched = mutableListOf<UnmatchedProgress>()
        // Source traversal order keeps a review stable regardless of Set iteration order.
        oldRecords.filter { it.address in completed }.forEach { old ->
            val address = old.address.copy(definitionRevisionId = target.revisionId)
            val next = newByAddress[address]
            val reason = when {
                next == null -> UnmatchedProgressReason.REMOVED_OCCURRENCE
                old.instruction != next.instruction || hasTarget(old, sourceGuide) ||
                    hasTarget(next, targetGuide) -> UnmatchedProgressReason.CHANGED_INSTRUCTION
                context(old, sourceGuide) != context(next, targetGuide) -> UnmatchedProgressReason.CHANGED_CONTEXT
                else -> null
            }
            if (reason == null) candidates += ProgressCarryCandidate(old.address, address, old.instruction.text)
            else unmatched += UnmatchedProgress(old.address, old.instruction.text, reason)
        }
        return RevisionProgressPreview(source.revisionId, target.revisionId,
            candidates.toList(), unmatched.toList(), newRecords.size)
    }

    private fun records(guide: ValidatedGuideDefinition): List<OccurrenceRecord> {
        val records = GuideTraversal(guide).occurrenceRecords().take(MAX_OCCURRENCES + 1).toList()
        if (records.size > MAX_OCCURRENCES) throw RevisionPreviewTooLargeException()
        return records
    }

    // The preview already has each occurrence's path; do not re-expand a target body for every match.
    private fun hasTarget(record: OccurrenceRecord, guide: ValidatedGuideDefinition): Boolean =
        record.instruction.target != null || record.nodePath.any { (guide.node(it) as? Section)?.target != null }

    private fun context(record: OccurrenceRecord, guide: ValidatedGuideDefinition): List<GuideNode> =
        record.nodePath.dropLast(1).map { id ->
            when (val node = requireNotNull(guide.node(id))) {
                is Section -> Section(node.id, node.title, emptyList(), node.target)
                is Range -> Range(node.id, node.unitLabel, node.startInclusive, node.endInclusive, emptyList())
                is Repeat -> Repeat(node.id, node.count, node.label, emptyList())
                is Instruction -> error("An instruction cannot contain an occurrence.")
            }
        }
}
