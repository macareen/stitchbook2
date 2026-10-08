package com.macareen.stitchbook2.domain.parsing

import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType

/**
 * Turns a validated [StructuredGuide] into the Draft node tree the Draft
 * editor already edits. Materials, abbreviations, and notes become one
 * "Before you start" section so nothing the helper read is lost, and every
 * review item becomes a visible "Review needed" step the user can fix or
 * delete. The result is always a draft; publishing stays a user action.
 */
object StructuredGuideMapper {

    const val BEFORE_YOU_START_TITLE = "Before you start"
    const val REVIEW_PREFIX = "Review needed: "

    fun toDraftNodes(guide: StructuredGuide, newNodeId: () -> String): DraftMappingResult {
        val nodes = mutableListOf<DraftNode>()

        fun instruction(text: String): NodeId {
            val id = NodeId(newNodeId())
            nodes += DraftNode(id = id, type = DraftNodeType.INSTRUCTION, instructionText = text)
            return id
        }

        fun map(step: StructuredStep): NodeId {
            val id = NodeId(newNodeId())
            when (step) {
                is StructuredStep.Section -> {
                    val children = step.steps.map(::map)
                    nodes += DraftNode(id = id, type = DraftNodeType.SECTION, title = step.title, children = children)
                }

                is StructuredStep.Rows -> {
                    val children = step.steps.map(::map)
                    nodes += DraftNode(
                        id = id,
                        type = DraftNodeType.RANGE,
                        rangeUnitLabel = step.unit,
                        rangeStartInclusive = step.from,
                        rangeEndInclusive = step.to,
                        children = children
                    )
                }

                is StructuredStep.Repeat -> {
                    val children = step.steps.map(::map)
                    nodes += DraftNode(
                        id = id,
                        type = DraftNodeType.REPEAT,
                        repeatCount = step.times,
                        repeatLabel = step.label,
                        children = children
                    )
                }

                is StructuredStep.Instruction -> {
                    nodes += DraftNode(id = id, type = DraftNodeType.INSTRUCTION, instructionText = step.text)
                }
            }
            return id
        }

        val roots = mutableListOf<NodeId>()

        val preparation = buildList {
            if (guide.materials.isNotEmpty()) add("Materials: " + guide.materials.joinToString("; "))
            if (guide.abbreviations.isNotEmpty()) {
                add("Abbreviations: " + guide.abbreviations.joinToString("; ") { "${it.term} = ${it.meaning}" })
            }
            addAll(guide.notes)
        }
        if (preparation.isNotEmpty()) {
            val children = preparation.map(::instruction)
            val id = NodeId(newNodeId())
            nodes += DraftNode(id = id, type = DraftNodeType.SECTION, title = BEFORE_YOU_START_TITLE, children = children)
            roots += id
        }

        guide.steps.forEach { roots += map(it) }
        guide.review.forEach { roots += instruction(REVIEW_PREFIX + it) }

        return DraftMappingResult(rootNodeIds = roots.toList(), nodes = nodes.toList())
    }
}
