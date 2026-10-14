package com.macareen.stitchbook2.feature.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusInstructionLayoutTest {
    @Test fun pageReferencesAreNotPurlActionsOrInlineStitchGroups() {
        assertEquals(listOf(FocusInstructionLayout.Block("See (p.3, p.4)"), FocusInstructionLayout.Block("then knit 2.")),
            FocusInstructionLayout.blocks("See (p.3, p.4), then knit 2."))
        assertEquals(listOf("See p.3,p.4", "p3", "p4."), FocusInstructionLayout.actionLines("See p.3,p.4; p3,p4."))
    }

    @Test fun compactActionsAlsoUseSeparateLines() {
        assertEquals(listOf("k2", "p2", "sl1."), FocusInstructionLayout.actionLines("k2,p2,sl1."))
    }
    @Test fun bracketGroupsKeepTheirBoundariesAndEndingOutsideTheActions() {
        assertEquals(listOf(
            FocusInstructionLayout.Block("Work [", listOf(FocusInstructionLayout.Block("k2"), FocusInstructionLayout.Block("p2")), "] to end"),
            FocusInstructionLayout.Block("turn.")), FocusInstructionLayout.blocks("Work [k2, p2] to end, turn."))
    }
    @Test fun nestedStarredRepeatKeepsItsOwnEnding() {
        val group = FocusInstructionLayout.blocks("Work [k2, repeat *p2, sl1* twice] to end").single()
        assertEquals("Work [", group.text)
        assertEquals("] to end", group.suffix)
        val inner = group.children[1]
        assertEquals("repeat *", inner.text)
        assertEquals(listOf(FocusInstructionLayout.Block("p2"), FocusInstructionLayout.Block("sl1")), inner.children)
        assertEquals("* twice", inner.suffix)
    }
    @Test fun numericAnnotationsAndQuotedGroupsAreNotReinterpreted() {
        val text = "Cast on 40 (44, 48) stitches"
        assertEquals(listOf(FocusInstructionLayout.Block(text)), FocusInstructionLayout.blocks(text))
        val quoted = "Work \"[knit 2, purl 2]\" to end"
        assertEquals(listOf(FocusInstructionLayout.Block(quoted)), FocusInstructionLayout.blocks(quoted))
    }
    @Test fun malformedWholeGroupsStayIntactForReview() {
        listOf("Work [k2, p2", "Repeat *k2, p2, turn.", "Work [(k2, p2])").forEach { text ->
            assertEquals(listOf(FocusInstructionLayout.Block(text)), FocusInstructionLayout.blocks(text))
        }
    }
    @Test fun multilineGroupsAndIndependentGroupsKeepTheirOrder() {
        val blocks = FocusInstructionLayout.blocks("Work [k2\np2] to end\nturn.")
        assertEquals(listOf(FocusInstructionLayout.Block("k2"), FocusInstructionLayout.Block("p2")), blocks.first().children)
        assertEquals(FocusInstructionLayout.Block("turn."), blocks.last())
        val separate = FocusInstructionLayout.blocks("Work [k2, p2] then [sl1, k1] to end")
        assertEquals(2, separate.size)
        assertEquals("]", separate.first().suffix)
        assertEquals("then [", separate.last().text)
        assertEquals("] to end", separate.last().suffix)
    }

    @Test fun stitchDistributionSeparatesMarkersAndCountsWithoutSplittingAnnotations() {
        assertEquals(listOf("Distribution: 12 sts (left front)", "Marker 1", "18 sts (sleeve)", "Marker 2."),
            FocusInstructionLayout.actionLines("Distribution: 12 sts (left front), Marker 1, 18 sts (sleeve), Marker 2."))
    }

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
