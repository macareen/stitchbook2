package com.macareen.stitchbook2.domain.execution

/** Frozen facts reviewed by the user; apply must recheck these inside its transaction. */
data class ReviewedRevisionChange(val projectId: String?, val source: PersistedExecution,
    val sourceDefinition: GuideDefinition, val sourceRevisionNumber: Int,
    val targetDefinition: GuideDefinition, val targetRevisionNumber: Int)

data class ReviewedSavedPlace(val projectId: String?, val current: PersistedExecution,
    val currentDefinition: GuideDefinition, val saved: PersistedExecution, val savedDefinition: GuideDefinition)

/** Flat storage order is irrelevant; root and child order and all node content remain meaningful. */
fun GuideDefinition.hasSameContentAs(other: GuideDefinition): Boolean =
    guideId == other.guideId && revisionId == other.revisionId && rootNodeIds == other.rootNodeIds &&
        nodes.size == other.nodes.size && nodes.associateBy { it.id } == other.nodes.associateBy { it.id }

fun revisionChangeSeed(review: ReviewedRevisionChange, selected: Set<ExecutionAddress>, id: ExecutionId): ExecutionState {
    require(review.source.state.guideId == review.sourceDefinition.guideId &&
        review.source.state.definitionRevisionId == review.sourceDefinition.revisionId)
    val comparison = RevisionProgressComparison.preview(review.sourceDefinition, review.targetDefinition,
        review.source.state.completedAddresses)
    val allowed = comparison.candidates.map { it.target }.toSet()
    require(selected.all { it in allowed }) { "Only reviewed matching occurrences may be carried over." }
    val target = GuideDefinitionValidator.validate(review.targetDefinition)
    val first = GuideTraversal(target).occurrences().firstOrNull { it.address !in selected }?.address
    return ExecutionState(id, review.targetDefinition.guideId, review.targetDefinition.revisionId,
        first, selected, if (first == null) ExecutionStatus.COMPLETED else ExecutionStatus.ACTIVE)
}
