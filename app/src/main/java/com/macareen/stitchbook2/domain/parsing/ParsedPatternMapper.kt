package com.macareen.stitchbook2.domain.parsing

import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType

/** The result of [ParsedPatternMapper.toDraftNodes]: a ready-to-save Draft node tree. */
data class DraftMappingResult(val rootNodeIds: List<NodeId>, val nodes: List<DraftNode>)

/**
 * Maps a [ParsedPattern] into the flattened [DraftNode] tree shape the
 * Draft editor and `GuideRepository.saveDraft` already work with (see
 * ROADMAP.md's "Parser foundation" item 3, "mapping parser output into
 * GuideDraft creation with provenance"). This is a pure, one-way
 * translation -- it never calls the repository itself, so it stays testable
 * without Room or Android.
 *
 * Every generated Instruction's visible text is suffixed with its page
 * ([SourceReference.pageNumber]; review items keep the line too) so provenance stays visible directly in the existing
 * Draft editor, per PRODUCT_SPEC.md 6.6's "show source references for
 * generated steps" -- no separate review UI or schema change is needed for
 * that. Each [ParsingIssue] similarly becomes its own plain, clearly marked
 * Instruction appended at the root, so ambiguity is explicit and directly
 * editable/deletable like any other node, rather than hidden or silently
 * resolved (PRODUCT_SPEC.md 6.6's "require user review rather than
 * silently trusting generated output").
 */
object ParsedPatternMapper {

    fun toDraftNodes(pattern: ParsedPattern, newNodeId: () -> String): DraftMappingResult {
        val nodes = mutableListOf<DraftNode>()
        val rootIds = mutableListOf<NodeId>()
        var lastPage = 0

        // Steps note their page only where it changes, so the text stays readable.
        fun withProvenance(text: String, source: SourceReference): String {
            if (source.pageNumber == lastPage) return text
            lastPage = source.pageNumber
            return "$text (p.${source.pageNumber})"
        }

        // A heading, range, or repeat with nothing under it is dropped: it would
        // only block publishing, and its words carry no step to work.
        fun mapNode(node: ParsedNode): NodeId? {
            val id = NodeId(newNodeId())
            when (node) {
                is ParsedSection -> {
                    val childIds = node.children.mapNotNull(::mapNode)
                    if (childIds.isEmpty()) return null
                    nodes += DraftNode(id = id, type = DraftNodeType.SECTION, title = node.title, children = childIds)
                }

                is ParsedRange -> {
                    val childIds = node.children.mapNotNull(::mapNode)
                    if (childIds.isEmpty()) return null
                    nodes += DraftNode(
                        id = id,
                        type = DraftNodeType.RANGE,
                        rangeUnitLabel = node.unitLabel,
                        rangeStartInclusive = node.startInclusive,
                        rangeEndInclusive = node.endInclusive,
                        children = childIds
                    )
                }

                is ParsedRepeat -> {
                    val childIds = node.children.mapNotNull(::mapNode)
                    if (childIds.isEmpty()) return null
                    nodes += DraftNode(
                        id = id,
                        type = DraftNodeType.REPEAT,
                        repeatCount = node.count,
                        children = childIds
                    )
                }

                is ParsedInstruction -> {
                    nodes += DraftNode(
                        id = id,
                        type = DraftNodeType.INSTRUCTION,
                        instructionText = withProvenance(node.text, node.source)
                    )
                }
            }
            return id
        }

        pattern.rootNodes.forEach { node -> mapNode(node)?.let { rootIds += it } }
        pattern.issues.forEach { issue ->
            val id = NodeId(newNodeId())
            nodes += DraftNode(
                id = id,
                type = DraftNodeType.INSTRUCTION,
                instructionText = "Review needed: ${issue.message} ${formatSource(issue.source)}"
            )
            rootIds += id
        }

        return DraftMappingResult(rootNodeIds = rootIds.toList(), nodes = nodes.toList())
    }

    private fun formatSource(source: SourceReference) = "(p.${source.pageNumber} l.${source.lineNumber})"
}
