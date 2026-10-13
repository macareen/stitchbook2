package com.macareen.stitchbook2.domain.execution

/** A readable, editable snapshot; absence of a row range never creates a fictional row number. */
fun guideNoteContext(guide: ValidatedGuideDefinition, address: ExecutionAddress): String? {
    val path = GuideTraversal(guide).ancestryNodePath(address)
    val parts = path.mapNotNull { id ->
        when (val node = guide.node(id)) {
            is Section -> node.title
            is Range -> address.ancestryFrames.filterIsInstance<AncestryFrame.RangeValue>()
                .firstOrNull { it.containerNodeId == id }?.let { "${node.unitLabel} ${it.value}" }
            is Repeat -> address.ancestryFrames.filterIsInstance<AncestryFrame.RepeatIteration>()
                .firstOrNull { it.containerNodeId == id }?.let { "${node.label ?: "Repeat"} ${it.iteration} of ${node.count}" }
            else -> null
        }
    }
    return parts.joinToString(" \u00b7 ").takeIf { it.isNotBlank() }
}
