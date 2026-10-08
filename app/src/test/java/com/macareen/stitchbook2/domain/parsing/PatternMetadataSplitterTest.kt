package com.macareen.stitchbook2.domain.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternMetadataSplitterTest {

    private fun documentOf(vararg lines: String) = ExtractedDocument(
        pageCount = 1,
        lines = lines.mapIndexed { index, text -> ExtractedLine(text, SourceReference(pageNumber = 1, lineNumber = index + 1)) }
    )

    /** A typical first page: title, byline, intro, materials, then the knitting. */
    private val fluffyPattern = documentOf(
        "Meadow Cardigan",
        "A relaxed top-down cardigan by Ana Example",
        "This cardigan was inspired by slow summer mornings and long walks",
        "through the meadow behind my house, and it is knit seamlessly",
        "from the top down.",
        "MATERIALS",
        "Yarn: 4 (5, 6) skeins of Acme DK, 225 m per skein",
        "Needles: 4 mm circular, 60 cm; 3.5 mm circular for the ribbing",
        "Gauge: 22 sts and 30 rows = 10 cm in stockinette",
        "ABBREVIATIONS",
        "k - knit",
        "p - purl",
        "© 2026 Ana Example. All rights reserved. www.example.com",
        "YOKE",
        "Cast on 96 sts.",
        "Row 1 (RS): K2, p2 to end.",
        "Rows 2-10: Work in rib as set."
    )

    private fun split(document: ExtractedDocument): SplitPattern {
        val cleaned = PatternTextCleanup.clean(document, PatternTextParser::startsNewLine) {
            PatternTextParser.headingTitle(it) != null
        }
        return PatternMetadataSplitter.split(
            cleaned,
            isRowLine = { it.startsWith("Row", ignoreCase = true) },
            headingTitle = PatternTextParser::headingTitle
        )
    }

    @Test
    fun theIntroBecomesOneDescriptionParagraphAndTheBylineNamesTheDesigner() {
        val details = split(fluffyPattern).details

        assertEquals("Ana Example", details.designer)
        assertEquals(
            "Meadow Cardigan This cardigan was inspired by slow summer mornings and long walks through the meadow " +
                "behind my house, and it is knit seamlessly from the top down.",
            details.description
        )
    }

    @Test
    fun materialsFillGaugeYarnAndTools() {
        val details = split(fluffyPattern).details

        assertEquals("22 sts and 30 rows = 10 cm in stockinette", details.gauge)
        assertEquals("4 mm circular, 60 cm; 3.5 mm circular for the ribbing", details.tools)
        assertEquals("4 (5, 6) skeins of Acme DK, 225 m per skein", details.yarn)
        // 225 m is per skein, not the total, so no yardage is guessed.
        assertNull(details.yardage)
    }

    @Test
    fun aStatedTotalBecomesTheLargestSizesYardage() {
        val details = split(documentOf("Yarn: approx. 1000 (1200, 1400) m of fingering weight", "Row 1: Knit.")).details

        assertEquals(1531.0, details.yardage!!, 0.0)
    }

    @Test
    fun onlyTheKnittingBecomesSteps() {
        val steps = split(fluffyPattern).instructions.map { it.text }

        assertEquals(
            listOf("YOKE", "Cast on 96 sts.", "Row 1 (RS): K2, p2 to end.", "Rows 2-10: Work in rib as set."),
            steps
        )
        assertFalse(steps.any { "knit" == it || "rights reserved" in it })
    }

    @Test
    fun theWholeParserKeepsFluffOutOfTheDraft() {
        val (pattern, details) = PatternTextParser.parseWithDetails(fluffyPattern)

        val section = pattern.rootNodes.single() as ParsedSection
        assertEquals("YOKE", section.title)
        assertTrue(section.children.first() is ParsedInstruction)
        assertEquals("Cast on 96 sts.", (section.children.first() as ParsedInstruction).text)
        assertEquals("Ana Example", details.designer)
    }

    @Test
    fun aPatternWithNoFrontMatterIsAllSteps() {
        val result = split(documentOf("Row 1: Knit.", "Row 2: Purl."))

        assertTrue(result.details.isEmpty)
        assertEquals(2, result.instructions.size)
        assertNull(result.details.description)
    }
}
