package com.macareen.stitchbook2.domain.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SizeSpecificInstructionsTest {
    private val labels = listOf("XS", "S", "M", "L", "XL")
    private fun document(vararg lines: String) = ExtractedDocument(1,
        lines.mapIndexed { index, text -> ExtractedLine(text, SourceReference(1, index + 1)) })
    private fun text(result: SizeSpecificInstructions.Resolution) = result.document.lines.map { it.text }

    @Test fun ambiguousProseKeepsEverySizesNumbersWithoutRewritingTheOriginal() {
        val inline = "Size XXL only: Cast on 60 (66) 72 (78) 84 sts."
        assertEquals(listOf(inline), text(SizeSpecificInstructions.select(document(inline), labels, 2)))
        val unbounded = document("Size S only:", "Cast on 60 (66) 72 (78) 84 sts.")
        assertEquals(unbounded.lines, SizeSpecificInstructions.select(unbounded, labels, 2).document.lines)
        val unknownBlock = document("Size XXL only:", "Cast on 60 (66) 72 (78) 84 sts.",
            "Size M only:", "Work 4 rows.", "All sizes:", "Bind off.")
        val result = SizeSpecificInstructions.select(unknownBlock, labels, 2)
        assertEquals(unknownBlock.lines.take(4) + unknownBlock.lines.last(), result.document.lines)
        assertEquals(1, result.issues.size)
    }

    @Test fun inlineSizeListsAndRangesKeepOnlyTheChosenProseAndPreserveSources() {
        val doc = document("Size S only: Work 2 rows.", "For sizes M and L only: Work 4 rows.",
            "XS-S only: Work 1 round.", "Sizes M/XL: Cast on 60 (66) 72 (78) 84 sts.",
            "Row 5 (RS): Knit to end.")
        val result = SizeSpecificInstructions.select(doc, labels, 2)
        assertEquals(listOf("Work 4 rows.", "Cast on 72 sts.", "Row 5 (RS): Knit to end."), text(result))
        assertEquals(listOf(2, 4, 5), result.document.lines.map { it.source.lineNumber })
        assertTrue(result.issues.isEmpty())
    }

    @Test fun boundedBranchesKeepTheirRowsAndCommonInstructionsForEverySize() {
        val doc = document("Sizes XS-S only:", "Row 1: Knit.", "Row 2: Purl.",
            "Sizes M-XL only", "Row 1: K2, p2.", "Row 2: P2, k2.",
            "All sizes:", "Repeat rows 1-2 3 times.", "Bind off.")
        labels.indices.forEach { size ->
            val result = SizeSpecificInstructions.select(doc, labels, size)
            val firstRows = if (size < 2) listOf("Row 1: Knit.", "Row 2: Purl.")
                else listOf("Row 1: K2, p2.", "Row 2: P2, k2.")
            assertEquals(firstRows + listOf("Repeat rows 1-2 3 times.", "Bind off."), text(result))
            assertTrue(result.issues.isEmpty())
            val parsed = PatternTextParser.parse(result.document)
            assertTrue(parsed.issues.isEmpty())
            assertTrue(parsed.rootNodes.first() is ParsedRepeat)
        }
    }

    @Test fun unknownOrReversedSelectorsStayIntactWithReviewIssues() {
        listOf("Size XXL only: Work 4 rows.", "Sizes XL-S only: Work 4 rows.").forEach { original ->
            val result = SizeSpecificInstructions.select(document(original), labels, 2)
            assertEquals(listOf(original), text(result))
            assertEquals(1, result.issues.size)
        }
    }

    @Test fun anUnknownBranchPreservesTheWholeBoundedGroup() {
        val doc = document("Size S only:", "Row 1: Knit.", "Size XXL only:", "Row 1: Purl.",
            "All sizes: Bind off.")
        val result = SizeSpecificInstructions.select(doc, labels, 2)
        assertEquals(doc.lines.take(4).map { it.text } + "Bind off.", text(result))
        assertEquals(1, result.issues.size)
        assertEquals(3, result.issues.single().source.lineNumber)
    }

    @Test fun anUnboundedBlockIsKeptForReviewInsteadOfDroppingFollowingWork() {
        val doc = document("Size S only:", "Row 1: Knit.", "FINISHING", "Weave in ends.")
        val result = SizeSpecificInstructions.select(doc, labels, 2)
        assertEquals(doc.lines, result.document.lines)
        assertEquals(1, result.issues.size)
    }

    @Test fun ordinaryProseAndTechniqueParenthesesAreNeverSizeSelectors() {
        val doc = document("Work the body only: Knit to end.", "Use (RS) and (WS) markers.",
            "All sizes: Weave in ends.")
        assertEquals(listOf("Work the body only: Knit to end.", "Use (RS) and (WS) markers.",
            "Weave in ends."), text(SizeSpecificInstructions.select(doc, labels, 2)))
    }
}
