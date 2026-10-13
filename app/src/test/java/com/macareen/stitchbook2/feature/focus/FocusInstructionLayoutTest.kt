package com.macareen.stitchbook2.feature.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusInstructionLayoutTest {
    @Test fun clearKnittingActionsHaveTheirOwnLines() {
        assertEquals(listOf("Row 1 (RS): Knit to marker 1", "m1r", "slip marker 1", "knit 2", "m1l."),
            FocusInstructionLayout.actionLines("Row 1 (RS): Knit to marker 1, m1r, slip marker 1, knit 2, m1l."))
    }
    @Test fun numericGroupsAndStitchRepeatsStayTogether() {
        val text = "Cast on 40 (44, 48) stitches; work [k2, p2] to end, turn."
        assertEquals(listOf("Cast on 40 (44, 48) stitches", "work [k2, p2] to end", "turn."), FocusInstructionLayout.actionLines(text))
    }
    @Test fun quotedAndStarredSequencesStayTogether() {
        assertEquals(listOf("Repeat *k2, p2* to end", "turn."), FocusInstructionLayout.actionLines("Repeat *k2, p2* to end, turn."))
        assertEquals(listOf("Work \"knit 2, purl 2\" to end", "turn."), FocusInstructionLayout.actionLines("Work \"knit 2, purl 2\" to end, turn."))
    }
    @Test fun conditionalQualificationsStayWithTheirAction() {
        val text = "Knit to the end, if working the smaller size, then turn."
        assertEquals(listOf("Knit to the end, if working the smaller size", "then turn."), FocusInstructionLayout.actionLines(text))
    }
    @Test fun proseListsAndDecimalMeasurementsAreNotSplit() {
        val text = "Use red, green, blue yarn for 2.5 cm."
        assertEquals(listOf(text), FocusInstructionLayout.actionLines(text))
    }
    @Test fun crochetAndTunisianActionsUseTheSamePresentation() {
        assertEquals(listOf("Ch 2", "dc in next stitch", "skip 1", "turn."), FocusInstructionLayout.actionLines("Ch 2, dc in next stitch, skip 1, turn."))
        assertEquals(listOf("Tss 4", "tps 2", "draw through two loops."), FocusInstructionLayout.actionLines("Tss 4, tps 2, draw through two loops."))
    }
    @Test fun malformedGroupsKeepTheirOriginalTextForReview() {
        listOf("Knit (2, purl 2", "Knit 2], purl 2", "Repeat *k2, p2, turn.").forEach {
            assertEquals(listOf(it), FocusInstructionLayout.actionLines(it))
        }
    }
    @Test fun existingLinesAndSentencesAreReadableWithoutChangingTheSource() {
        val text = "Knit 2. Purl 2.\n\nSlip 1, knit 1."
        assertEquals(listOf("Knit 2.", "Purl 2.", "Slip 1", "knit 1."), FocusInstructionLayout.actionLines(text))
        assertEquals("Knit 2. Purl 2.\n\nSlip 1, knit 1.", text)
    }
}
