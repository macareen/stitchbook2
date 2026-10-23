package com.macareen.stitchbook2.domain.guide

import com.macareen.stitchbook2.domain.execution.NodeId

/** Review keeps existing nodes and order; no PDF wording is converted into execution implicitly. */
object DraftTargetCycles {
    fun problem(draft: GuideDraft): String? {
        val cycles = draft.nodes.filter { it.type == DraftNodeType.SECTION && it.target != null }
        if (cycles.isEmpty()) return null
        val refusal = "Use one independent target around a finite body. Resolve nested, dependent or simultaneous conditions before adding this target."
        if (cycles.size != 1 || draft.nodes.any { it.type != DraftNodeType.SECTION && it.target != null }) return refusal
        val byId = draft.nodes.associateBy { it.id }
        val seen = mutableSetOf<NodeId>()
        val sizes = mutableMapOf<NodeId, Long>()
        val condition = Regex("""\b(?:until|while|at the same time|simultaneously|as before|as established|depending|unless)\b""", RegexOption.IGNORE_CASE)
        val simultaneous = Regex("""\b(?:while|at the same time|simultaneously|depending|unless)\b""", RegexOption.IGNORE_CASE)
        if (draft.nodes.any { simultaneous.containsMatchIn(it.instructionText.orEmpty()) || simultaneous.containsMatchIn(it.title.orEmpty()) ||
                simultaneous.containsMatchIn(it.repeatLabel.orEmpty()) }) return refusal
        val parents = draft.nodes.flatMap { parent -> parent.children.map { it to parent } }.toMap()
        val ancestors = mutableSetOf<NodeId>()
        var ancestor = parents[cycles.single().id]
        while (ancestor != null) {
            if (!ancestors.add(ancestor.id) || condition.containsMatchIn(ancestor.title.orEmpty()) ||
                condition.containsMatchIn(ancestor.repeatLabel.orEmpty())) return refusal
            ancestor = parents[ancestor.id]
        }
        fun finite(id: NodeId): Boolean {
            val node = byId[id] ?: return false
            if (!seen.add(id) || node.target != null || condition.containsMatchIn(node.instructionText.orEmpty()) ||
                condition.containsMatchIn(node.title.orEmpty()) || condition.containsMatchIn(node.repeatLabel.orEmpty()) ||
                node.instructionText.orEmpty().startsWith("Review needed:")) return false
            val valid = when (node.type) {
                DraftNodeType.INSTRUCTION -> node.children.isEmpty() && !node.instructionText.isNullOrBlank()
                DraftNodeType.RANGE -> !node.rangeUnitLabel.isNullOrBlank() && node.rangeStartInclusive != null &&
                    node.rangeEndInclusive != null && node.rangeStartInclusive <= node.rangeEndInclusive && node.children.isNotEmpty() && node.children.all(::finite)
                DraftNodeType.REPEAT -> (node.repeatCount ?: 0) > 0 && node.children.isNotEmpty() && node.children.all(::finite)
                DraftNodeType.SECTION -> node.children.isNotEmpty() && node.children.all(::finite)
            }
            val multiplier = when (node.type) {
                DraftNodeType.RANGE -> (node.rangeEndInclusive?.toLong() ?: 0) - (node.rangeStartInclusive ?: 0) + 1
                DraftNodeType.REPEAT -> node.repeatCount?.toLong() ?: 0
                else -> 1L
            }
            sizes[id] = ((if (node.type == DraftNodeType.INSTRUCTION) 1 else node.children.sumOf { sizes[it] ?: 10_001L }) * multiplier).coerceAtMost(10_001)
            return valid && sizes.getValue(id) <= 10_000
        }
        return if (condition.containsMatchIn(cycles.single().title.orEmpty()) || cycles.single().children.isEmpty() ||
            !cycles.single().children.all(::finite) || cycles.single().children.sumOf { sizes.getValue(it) } > 10_000) refusal else null
    }

    fun reviewText(draft: GuideDraft, id: NodeId): String {
        val byId = draft.nodes.associateBy { it.id }
        val visited = mutableSetOf<NodeId>()
        fun lines(nodeId: NodeId, depth: Int): String {
            if (!visited.add(nodeId)) return "Invalid repeated node"
            val node = byId[nodeId] ?: return "Missing step"
            val text = when (node.type) {
                DraftNodeType.INSTRUCTION -> node.instructionText.orEmpty()
                DraftNodeType.SECTION -> node.title.orEmpty()
                DraftNodeType.RANGE -> "${node.rangeUnitLabel} ${node.rangeStartInclusive}–${node.rangeEndInclusive}"
                DraftNodeType.REPEAT -> "${node.repeatLabel.orEmpty()} Repeat ${node.repeatCount} times"
            }
            return "${"  ".repeat(depth)}$text" + node.children.joinToString("") { "\n" + lines(it, depth + 1) }
        }
        return lines(id, 0)
    }
}
