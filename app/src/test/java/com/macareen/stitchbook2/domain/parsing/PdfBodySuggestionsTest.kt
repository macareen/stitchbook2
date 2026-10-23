package com.macareen.stitchbook2.domain.parsing

import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.guide.*
import org.junit.Assert.*
import org.junit.Test

class PdfBodySuggestionsTest {
    private fun mapped(vararg lines: String): DraftMappingResult {
        var next = 0
        val source = SourceReference(1, 1)
        val children = lines.mapIndexed { index, text -> ParsedInstruction(text, source.copy(lineNumber = index + 1)) }
        return ParsedPatternMapper.toDraftNodes(ParsedPattern(children, emptyList())) { "n${next++}" }
    }
    private fun base(condition: String = "Repeat rows 1-2 until piece measures 12 cm.") =
        mapped("Row 1: Knit 10 stitches.", "Row 2: Purl all stitches.", condition)

    @Test fun reviewWrapsExistingBodyAndRetainsEveryOriginalInstructionAndOrder() {
        val mapped = base()
        val suggestion = PdfTargetSuggestions.bodySuggestions(mapped.nodes, mapped.rootNodeIds).values.single()
        assertEquals(ManualTarget(TargetUnit.CENTIMETRES, 12.0), suggestion.target)
        assertEquals(mapped.rootNodeIds.take(2), suggestion.bodyIds)
        assertTrue(mapped.nodes.all { it.target == null })
        val draft = GuideDraft(DraftId("draft"), GuideId("guide"), null, 0, 0, 3, mapped.rootNodeIds, mapped.nodes)
        val updated = PdfTargetSuggestions.applyBody(draft, suggestion, NodeId("body"), suggestion.target)
        assertEquals(mapped.nodes, updated.nodes.filter { it.type == DraftNodeType.INSTRUCTION })
        assertEquals(listOf(NodeId("body"), suggestion.conditionId), updated.rootNodeIds)
        assertNull(DraftTargetCycles.problem(updated))
        assertEquals(suggestion, PdfTargetSuggestions.bodySuggestions(mapped.nodes.reversed(), mapped.rootNodeIds).values.single())
    }

    @Test fun refusesUnresolvedAmbiguousDependentAndMultipleConditionsWithoutLosingWording() {
        listOf("Repeat rows 1-3 until piece measures 12 cm.",
            "Repeat rows 2-1 until piece measures 12 cm.",
            "Repeat rows 1-2 until piece measures 12 mm.",
            "Repeat rows 1-2 until 40 stitches remain.",
            "Repeat rows 1-2 until piece measures 12 cm while shaping.",
            "Repeat rows 1-2 until piece measures 12 cm or 40 stitches remain.",
            "Repeat rows 1-2 until piece measures 0 cm.",
            "Repeat rows 1-999999999999 until piece measures 12 cm.",
            "Repeat rows 1-33 until piece measures 12 cm.").forEach { text ->
            val mapped = base(text)
            assertTrue(text, PdfTargetSuggestions.bodySuggestions(mapped.nodes, mapped.rootNodeIds).isEmpty())
            assertTrue(mapped.nodes.last().instructionText!!.contains(text))
        }
        listOf("While shaping, knit.", "Review needed: Check sizes.", "Knit until desired length.").forEach { text ->
            val mapped = base()
            assertTrue(PdfTargetSuggestions.bodySuggestions(mapped.nodes + DraftNode(NodeId("other"), DraftNodeType.INSTRUCTION, instructionText = text)).isEmpty())
        }
        val mapped = base()
        val parent = DraftNode(NodeId("parent"), DraftNodeType.REPEAT, repeatCount = 2, repeatLabel = "Until ready", children = mapped.rootNodeIds)
        assertTrue(PdfTargetSuggestions.bodySuggestions(mapped.nodes + parent).isEmpty())
        assertTrue(PdfTargetSuggestions.bodySuggestions(mapped.nodes.map { if (it == mapped.nodes.first()) it.copy(instructionText = "Row 1: Knit until desired length.") else it }).isEmpty())
    }

    @Test fun explicitRoundStitchComparisonAndFiniteRangesAreSupported() {
        val mapped = mapped("Round 1: Decrease one stitch.", "Round 2: Knit all stitches.", "Repeat rounds 1-2 until at most 40 stitches remain.")
        assertEquals(ManualTarget(TargetUnit.STITCHES, 40.0, TargetComparison.AT_MOST), PdfTargetSuggestions.bodySuggestions(mapped.nodes).values.single().target)
        val instruction = DraftNode(NodeId("work"), DraftNodeType.INSTRUCTION, instructionText = "Knit. (p.1)")
        val range = DraftNode(NodeId("range"), DraftNodeType.RANGE, rangeUnitLabel = "row", rangeStartInclusive = 1, rangeEndInclusive = 2, children = listOf(instruction.id))
        val condition = base().nodes.last()
        assertEquals(listOf(range.id), PdfTargetSuggestions.bodySuggestions(listOf(instruction, range, condition)).values.single().bodyIds)
        val nested = DraftNode(NodeId("nested"), DraftNodeType.REPEAT, repeatCount = 10001, children = listOf(instruction.id))
        assertTrue(PdfTargetSuggestions.bodySuggestions(listOf(instruction, nested, range.copy(children = listOf(nested.id)), condition)).isEmpty())
    }

    @Test fun conditionIssuesAreSuppressedOnlyForAResolvedIndependentReference() {
        val source = SourceReference(2, 3)
        val rows = listOf(ParsedInstruction("Row 1: Knit.", source), ParsedInstruction("Row 2: Purl.", source))
        val condition = ParsedInstruction("Repeat rows 1-2 until piece measures 12 cm.", source)
        assertTrue(PdfTargetSuggestions.reviewIssues(rows + condition).isEmpty())
        assertEquals(1, PdfTargetSuggestions.reviewIssues(rows.take(1) + condition).size)
        assertEquals(1, PdfTargetSuggestions.reviewIssues(listOf(ParsedSection("While shaping", rows + condition, source))).size)
    }
}
