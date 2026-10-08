package com.macareen.stitchbook2.domain.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Real-world PDF layout the deterministic parser has to see through. */
class PatternTextCleanupTest {

    private fun page(page: Int, vararg lines: String) =
        lines.mapIndexed { index, text -> ExtractedLine(text, SourceReference(page, index + 1)) }

    private fun parse(pageCount: Int, lines: List<ExtractedLine>) =
        PatternTextParser.parse(ExtractedDocument(pageCount, lines))

    private fun texts(nodes: List<ParsedNode>): List<String> = nodes.flatMap { node ->
        when (node) {
            is ParsedSection -> listOf("# ${node.title}") + texts(node.children)
            is ParsedRange -> listOf("${node.unitLabel} ${node.startInclusive}-${node.endInclusive}") + texts(node.children)
            is ParsedRepeat -> listOf("x${node.count}") + texts(node.children)
            is ParsedInstruction -> listOf(node.text)
        }
    }

    @Test
    fun headersFootersAndPageNumbersAreDropped() {
        val lines = page(1, "Cosy Hat - page 1", "Row 1: Knit.", "Row 2: Purl.", "1") +
            page(2, "Cosy Hat - page 2", "Row 3: Knit.", "Row 4: Purl.", "2")

        assertEquals(
            listOf("Row 1: Knit.", "Row 2: Purl.", "Row 3: Knit.", "Row 4: Purl."),
            texts(parse(2, lines).rootNodes)
        )
    }

    @Test
    fun identicalRowsOnDifferentPagesAreKept() {
        val lines = page(1, "Row 1: Knit.", "Row 2: Knit.") + page(2, "Row 3: Knit.", "Row 4: Knit.")

        assertEquals(4, parse(2, lines).rootNodes.size)
    }

    @Test
    fun wrappedLinesAreRejoinedIncludingHyphenatedWords() {
        val lines = page(
            1,
            "Round 3: sc in next 2 sts, 2 sc in",
            "next st; repeat around. (24 sts)",
            "Weave in all the remaining tails, block gen-",
            "tly to measurements."
        )

        assertEquals(
            listOf(
                "Round 3: sc in next 2 sts, 2 sc in next st; repeat around. (24 sts)",
                "Weave in all the remaining tails, block gently to measurements."
            ),
            texts(parse(1, lines).rootNodes)
        )
    }

    @Test
    fun commonRowSpellingsAreRecognised() {
        val lines = page(
            1,
            "R1: ch 2, 6 sc in 2nd ch from hook.",
            "Rnd 2 - 2 sc in each st around.",
            "Row 3 (RS): Knit.",
            "Rows 4–8 (WS): Purl.",
            "Rounds 9 to 12. Sc around."
        )

        assertEquals(
            listOf(
                "Row 1: ch 2, 6 sc in 2nd ch from hook.",
                "Round 2: 2 sc in each st around.",
                "Row 3 (RS): Knit.",
                "row 4-8", "(WS) Purl.",
                "round 9-12", "Sc around."
            ),
            texts(parse(1, lines).rootNodes)
        )
    }

    @Test
    fun headingsStartSectionsAndAreNeverJoinedOnto() {
        val lines = page(
            1,
            "MATERIALS",
            "worsted yarn, 5 mm hook",
            "Body:",
            "Rnd 1: magic ring, 6 sc.",
            "Rep rnd 1 2 times."
        )

        assertEquals(
            listOf("# MATERIALS", "worsted yarn, 5 mm hook", "# Body", "x2", "Round 1: magic ring, 6 sc."),
            texts(parse(1, lines).rootNodes)
        )
    }

    @Test
    fun headingRulesLeaveInstructionsAlone() {
        assertEquals("FINISHING", PatternTextParser.headingTitle("FINISHING"))
        assertEquals("Sleeves (make 2)", PatternTextParser.headingTitle("Sleeves (make 2):"))
        assertNull(PatternTextParser.headingTitle("K2TOG"))
        assertNull(PatternTextParser.headingTitle("Knit to end."))
        assertNull(PatternTextParser.headingTitle("Note: keep tension loose."))
        assertNull(PatternTextParser.headingTitle("Work in pattern as set until the piece measures 20 cm from cast on:"))
    }
}
