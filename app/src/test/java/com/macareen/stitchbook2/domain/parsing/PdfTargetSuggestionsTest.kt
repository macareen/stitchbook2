package com.macareen.stitchbook2.domain.parsing

import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.guide.*
import org.junit.Assert.*
import org.junit.Test

class PdfTargetSuggestionsTest {
    @Test fun wrappedOriginalConditionNotesKeepTheUnsizedNumbersAndUnits() {
        val document = ExtractedDocument(1, listOf(ExtractedLine("Section: Body", SourceReference(1, 1)),
            ExtractedLine("Knit one row until piece measures", SourceReference(1, 2)),
            ExtractedLine("10 (12, 14) cm.", SourceReference(1, 3))))
        assertTrue(PdfTargetSuggestions.sourceNotes(document)!!.contains("(p.1 l.2) Knit one row until piece measures 10 (12, 14) cm."))
    }
    @Test fun independentCyclesRetainTheirUnitsAndDirection() {
        assertEquals(ManualTarget(TargetUnit.CENTIMETRES, 12.5), PdfTargetSuggestions.suggest("Knit all stitches until piece measures 12.5 cm. (p.2 l.3)"))
        assertEquals(ManualTarget(TargetUnit.INCHES, 8.0), PdfTargetSuggestions.suggest("Single crochet one round until the piece measures 8 inches."))
        assertEquals(ManualTarget(TargetUnit.STITCHES, 20.0, TargetComparison.AT_MOST), PdfTargetSuggestions.suggest("Decrease one stitch every row until 20 stitches remain."))
        assertEquals(ManualTarget(TargetUnit.STITCHES, 30.0), PdfTargetSuggestions.suggest("Increase one stitch every round until 30 stitches remain."))
    }

    @Test fun ambiguousDependentSimultaneousOrMultiStepWorkCannotBecomeASuggestion() {
        listOf("Work until long enough.", "Knit all stitches until piece measures 10-12 cm.",
            "Knit all stitches until piece measures 10 (12, 14) cm.",
            "Knit all stitches until piece measures 10 cm / 4 inches.",
            "Knit all stitches until piece measures 1/2 in.",
            "Knit all stitches until piece measures 10 cm from cast on.",
            "Knit all stitches until piece measures 10 cm less than desired length.",
            "Knit all stitches until piece measures 10 cm, then bind off.",
            "Knit all stitches until piece measures 10 cm while decreasing.",
            "Repeat rows 1-4 until piece measures 10 cm.",
            "Work as established until piece measures 10 cm.",
            "Decrease as before until 20 stitches remain.",
            "Decrease one stitch every row until 20.5 stitches remain.",
            "Knit all stitches until piece measures 0 cm.",
            "Knit all stitches until piece measures 1e3 cm.",
            "Knit all stitches until piece measures 10 mm.",
            "Knit all stitches until piece measures 10 cm. At the same time shape neckline."
        ).forEach { assertNull(it, PdfTargetSuggestions.suggest(it)) }
    }

    @Test fun separateLinesWithReviewWarningsOrSimultaneousWorkSuppressSuggestionsAcrossTheDraft() {
        val cycle = DraftNode(NodeId("cycle"), DraftNodeType.INSTRUCTION, instructionText = "Purl one row until piece measures 10 cm. (p.1 l.3)")
        assertEquals(1, PdfTargetSuggestions.forNodes(listOf(cycle)).size)
        listOf("At the same time, shape armhole.", "While working body, decrease neckline.",
            "Continue as before.", "Review needed: Check size counts. (p.1 l.2)").forEach { text ->
            assertTrue(PdfTargetSuggestions.forNodes(listOf(cycle, DraftNode(NodeId("context"), DraftNodeType.INSTRUCTION, instructionText = text))).isEmpty())
        }
        assertTrue(PdfTargetSuggestions.forNodes(listOf(cycle.copy(target = ManualTarget(TargetUnit.CENTIMETRES, 9.0)))).isEmpty())
    }
}
