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
    @Test
    fun multiPageCardiganKeepsTitleRulesMeasurementsAndTechniquesOutOfSteps() {
        // Original fixture based on the supplied numeric examples, not redistributed pattern text.
        val pages = listOf(
            listOf("APRIL CARDIGAN", "A seamless cardigan for everyday wear.",
                "Sizes: XS (S) M (L) XL (2XL) 3XL (4XL) 5XL",
                "Bust circumference: 91 (97) 101 (106) 111 (117) 122 (127) 132 cm",
                "Length: 50 (51) 52 (53) 54 (55) 56 (57) 58 cm",
                "Designed to fit with 10 cm of ease.", "Gauge: 22 sts and 30 rows = 10 cm on 4 mm [US6]",
                "needles after wash and blocking", "________________", "ABBREVIATIONS", "M1R",
                "From the RS: Lift the strand and knit into the front.",
                "From the WS: Lift the strand and purl into the back.", "M1L",
                "From the RS: Lift the strand and knit into the back.", "YOKE", "Cast on 60 sts.", "1"),
            listOf("APRIL CARDIGAN", "Row 1 (RS): Knit to end.", "Row 2 (WS): Purl to end.",
                "----------------", "BODY", "Work 8 (8) 9 (10) 11 (11) 12 (12) 12 rows.", "2")
        )
        val document = ExtractedDocument(2, pages.flatMapIndexed { page, texts ->
            texts.mapIndexed { line, text -> ExtractedLine(text, SourceReference(page + 1, line + 1)) }
        })
        val (pattern, details) = PatternTextParser.parseWithDetails(document, patternTitle = "April Cardigan")
        assertEquals(listOf("YOKE", "BODY"), pattern.rootNodes.filterIsInstance<ParsedSection>().map { it.title })
        assertEquals("22 sts and 30 rows = 10 cm on 4 mm [US6] needles after wash and blocking", details.gauge)
        assertTrue(requireNotNull(details.measurements).contains("Bust circumference: 91 (97)"))
        assertTrue(requireNotNull(details.measurements).contains("Designed to fit"))
        assertTrue(requireNotNull(details.abbreviations).contains("M1R"))
        assertTrue(requireNotNull(details.abbreviations).contains("From the WS"))
        val text = pattern.rootNodes.filterIsInstance<ParsedSection>().flatMap { it.children }.filterIsInstance<ParsedInstruction>().map { it.text }
        assertFalse(text.any { "From the" in it || "____" in it || "Gauge" in it || "circumference" in it })
        assertEquals(SourceReference(1, 17), (pattern.rootNodes.first() as ParsedSection).children.first().source)
    }

    @Test
    fun standaloneTechniqueDefinitionsAfterInstructionsBecomeDetails() {
        val document = documentOf("BODY", "Row 1: Knit.", "M1R", "From the RS: Lift and knit the strand.",
            "Row 2: Purl.", "Bust circumference: 100 cm", "Row 3: Knit.")
        val (pattern, details) = PatternTextParser.parseWithDetails(document)
        assertTrue(requireNotNull(details.abbreviations).contains("M1R"))
        assertEquals("Bust circumference: 100 cm", details.measurements)
        assertEquals(3, (pattern.rootNodes.single() as ParsedSection).children.size)
    }

    @Test
    fun aRealSectionNamedBodyIsKeptWhenItIsNotThePatternTitle() {
        val (pattern, _) = PatternTextParser.parseWithDetails(documentOf("BODY", "Cast on 60 sts."), patternTitle = "April Cardigan")
        assertEquals("BODY", (pattern.rootNodes.single() as ParsedSection).title)
    }

}
