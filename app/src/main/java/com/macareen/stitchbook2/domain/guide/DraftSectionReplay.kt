package com.macareen.stitchbook2.domain.guide

import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.parsing.PatternPieceReplay

data class SectionReplayChoice(val sourceId: NodeId, val title: String, val nodes: List<DraftNode>, val prefixOnly: Boolean,
    val depths: Map<NodeId, Int>, val rootNodeIds: List<NodeId>)

/** Copies structure and wording with fresh identities; publications and executions are not touched. */
object DraftSectionReplay {
    fun choices(draft: GuideDraft, targetId: NodeId): List<SectionReplayChoice> {
        val byId = draft.nodes.associateBy { it.id }
        if (byId.size != draft.nodes.size) return emptyList()
        val target = byId[targetId] ?: return emptyList()
        if (target.type != DraftNodeType.INSTRUCTION || target.children.isNotEmpty()) return emptyList()
        fun choice(source: DraftNode, roots: List<NodeId>, prefix: Boolean, title: String): SectionReplayChoice? {
            val nodes = mutableListOf<DraftNode>()
            val depths = mutableMapOf<NodeId, Int>()
            val visited = mutableSetOf(source.id)
            fun visit(id: NodeId, depth: Int): Boolean {
                if (id == targetId || !visited.add(id)) return false
                val node = byId[id] ?: return false
                nodes += node
                depths[id] = depth
                return node.children.all { visit(it, depth + 1) }
            }
            return if (roots.isEmpty() || !roots.all { visit(it, 0) } || nodes.none { it.type == DraftNodeType.INSTRUCTION }) null
            else SectionReplayChoice(source.id, title, nodes, prefix, depths.toMap(), roots.toList())
        }
        val sections = draft.nodes.filter { it.type == DraftNodeType.SECTION }.mapNotNull { source ->
            val prefix = targetId in source.children
            val roots = if (prefix) source.children.takeWhile { it != targetId } else source.children
            choice(source, roots, prefix, source.title.orEmpty())
        }
        // Older imports could store a plain "Sleeves" heading as an instruction. Offer
        // its bounded sibling span explicitly instead of copying the enclosing body work.
        val siblings = (listOf(draft.rootNodeIds) + draft.nodes.map { it.children }).singleOrNull { targetId in it }
        val before = siblings?.takeWhile { it != targetId }.orEmpty()
        val markerIndex = before.indexOfLast { id ->
            val node = byId[id]
            node?.type == DraftNodeType.INSTRUCTION && PatternPieceReplay.isPieceHeading(
                node.instructionText.orEmpty().replace(Regex("""\s*\(p\.\d+(?:\s+l\.\d+)?\)\s*$"""), "").trim())
        }
        val legacy = if (markerIndex >= 0) {
            val marker = byId.getValue(before[markerIndex])
            choice(marker, before.drop(markerIndex + 1), true, marker.instructionText.orEmpty())
        } else null
        return listOfNotNull(legacy) + sections
    }

    fun replace(draft: GuideDraft, targetId: NodeId, sourceId: NodeId, newNodeId: () -> String): GuideDraft? {
        val choice = choices(draft, targetId).firstOrNull { it.sourceId == sourceId } ?: return null
        val target = draft.nodes.first { it.id == targetId }
        val used = draft.nodes.map { it.id }.toMutableSet()
        fun fresh(): NodeId = NodeId(newNodeId()).also { require(used.add(it)) { "A copied step needs a fresh identity." } }
        val ids = choice.nodes.associate { it.id to fresh() }
        val copied = choice.nodes.map { it.copy(id = ids.getValue(it.id), children = it.children.map(ids::getValue)) }
        val wrapper = DraftNode(id = fresh(), type = DraftNodeType.SECTION,
            title = "${target.instructionText.orEmpty()} — ${choice.title}", children = choice.rootNodeIds.map(ids::getValue))
        val nodes = draft.nodes.filterNot { it.id == targetId }.map { node ->
            node.copy(children = node.children.map { if (it == targetId) wrapper.id else it })
        } + wrapper + copied
        return draft.copy(nodes = nodes, rootNodeIds = draft.rootNodeIds.map { if (it == targetId) wrapper.id else it })
    }
}
