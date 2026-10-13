package com.macareen.stitchbook2.domain.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternTextParserTest {

    private fun documentOf(vararg lines: String): ExtractedDocument {
        return ExtractedDocument(
            pageCount = 1,
            lines = lines.mapIndexed { index, text ->
                ExtractedLine(text, SourceReference(pageNumber = 1, lineNumber = index + 1))
            }
        )
    }

    private fun source(line: Int) = SourceReference(pageNumber = 1, lineNumber = line)

    @Test fun capitalizedMarkerContinuationStaysInsideItsRowAndRepeat() {
        val original = documentOf("BODY", "Row 1 (RS):", "Knit across, slip", "Marker 1, knit to the end.",
            "Row 2 (WS):", "Purl across, slip", "Marker 1, purl to the end.",
            "Work rows 1 and 2 a total of 3 (5, 7) times.")
        val selected = SizeSpecificInstructions.select(original, listOf("S", "M", "L"), 1)
        val parsed = PatternTextParser.parse(selected.document)
        assertTrue(parsed.issues.isEmpty())
        val repeat = (parsed.rootNodes.single() as ParsedSection).children.single() as ParsedRepeat
        assertEquals(5, repeat.count)
        assertEquals(2, repeat.children.size)
        assertTrue((repeat.children[0] as ParsedInstruction).text.contains("slip Marker 1"))
    }

    @Test fun anExplicitNoteDoesNotJoinADanglingRowSentence() {
        val parsed = PatternTextParser.parse(documentOf("Row 1: Slip", "Note: Keep the edge loose.", "Row 2: Purl across.",
            "Work rows 1 and 2 a total of 5 times."))
        assertEquals(1, parsed.issues.size)
        assertTrue(parsed.rootNodes.none { it is ParsedRepeat })
        assertTrue(parsed.rootNodes.filterIsInstance<ParsedInstruction>().any { it.text.startsWith("Note:") })
    }

    @Test fun sizeAdviceProseDoesNotBecomeAnUnboundedSizeSelector() {
        val original = documentOf("Size guide", "For example, a", "size S sweater has a circumference that gives comfortable room to move.",
            "BODY", "Row 1 (RS):", "Knit across.", "Row 2 (WS):", "Purl across.",
            "Work rows 1 and 2 a total of 3 (5, 7) times.")
        val selected = SizeSpecificInstructions.select(original, listOf("S", "M", "L"), 1)
        assertTrue(selected.issues.isEmpty())
        val (pattern, details) = PatternTextParser.parseWithDetails(selected.document)
        assertTrue(details.description!!.contains("size S sweater"))
        assertEquals(5, ((pattern.rootNodes.single() as ParsedSection).children.single() as ParsedRepeat).count)
    }

    @Test fun rowLabelsOnTheirOwnPdfLinesBecomeTwoInstructionsInsideTheRepeat() {
        val original = documentOf("Size guide", "Choose your fit.", "BODY", "Row 1 (RS):", "Knit across.",
            "Row 2 (WS):", "Purl across.", "Work rows 1 and 2 a total of 3 (5, 7) times.")
        val selected = SizeSpecificInstructions.select(original, listOf("S", "M", "L"), 1)
        val section = PatternTextParser.parseWithDetails(selected.document).first.rootNodes.single() as ParsedSection
        val repeat = section.children.single() as ParsedRepeat
        assertEquals(5, repeat.count)
        assertEquals(2, repeat.children.size)
        assertEquals("Row 1 (RS): Knit across.", (repeat.children[0] as ParsedInstruction).text)
        assertEquals("Row 2 (WS): Purl across.", (repeat.children[1] as ParsedInstruction).text)
        assertEquals(source(4), repeat.children[0].source)
        assertEquals(source(6), repeat.children[1].source)
    }

    @Test fun anEndedNumericSentenceIsNotJoinedToAnotherNumericSentence() {
        val selected = SizeSpecificInstructions.select(documentOf("Row 1: Knit 3 (5, 7).", "10 cm is the target."), listOf("S", "M", "L"), 1)
        assertEquals(2, selected.document.lines.size)
        assertEquals("Row 1: Knit 5.", selected.document.lines[0].text)
    }

    @Test fun aSizeGuideHeadingDoesNotProtectAllFollowingNumbersFromResolution() {
        val original = documentOf("Size guide", "Choose a size that fits comfortably.", "BODY",
            "Row 1: Knit across.", "Row 2: Purl across.", "Work rows 1 and 2 a total of 3 (5, 7) times.")
        val selected = SizeSpecificInstructions.select(original, listOf("S", "M", "L"), 1)
        val (parsed, details) = PatternTextParser.parseWithDetails(selected.document)
        assertTrue(selected.issues.isEmpty())
        assertTrue(parsed.issues.isEmpty())
        assertTrue(details.description!!.contains("Choose a size"))
        val section = parsed.rootNodes.single() as ParsedSection
        assertEquals(5, (section.children.single() as ParsedRepeat).count)
    }

    @Test fun wrappedNumericGroupsResolveBeforeTotalRepeatsAreParsed() {
        val original = documentOf("Row 1: Knit across.", "Row 2: Purl across.",
            "Work rows 1 and 2 a total of 3 (4) 5 (6)", "7 (8) 9 (10) 11 times.")
        val labels = listOf("XS", "S", "M", "L", "XL", "2XL", "3XL", "4XL", "5XL")
        labels.indices.forEach { index ->
            val sized = SizeSpecificInstructions.select(original, labels, index)
            val repeat = PatternTextParser.parse(sized.document).rootNodes.single() as ParsedRepeat
            assertEquals(index + 3, repeat.count)
            assertEquals(source(3), repeat.source)
        }
    }

    @Test fun overflowingTotalRepeatCountsAreKeptForReview() {
        val pattern = PatternTextParser.parse(documentOf("Row 1: Knit across.", "Row 2: Purl across.",
            "Work rows 1 and 2 a total of 9999999999999999999 times."))
        assertEquals(1, pattern.issues.size)
        assertEquals(3, pattern.rootNodes.size)
    }

    @Test fun totalWorkRepeatWrapsTwoRowsAndKeepsTheAfterRepeatNote() {
        val pattern = PatternTextParser.parse(documentOf("Row 1: Knit across.", "Row 2: Purl across.",
            "Work Rows 1 and 2 a total of 9 times (keep the edge loose). There are now 50 stitches."))
        val repeat = pattern.rootNodes.first() as ParsedRepeat
        assertEquals(9, repeat.count)
        assertEquals(listOf("Row 1: Knit across.", "Row 2: Purl across."), repeat.children.map { (it as ParsedInstruction).text })
        assertEquals(2, pattern.rootNodes.size)
        assertTrue((pattern.rootNodes.last() as ParsedInstruction).text.contains("50 stitches"))
        assertTrue(pattern.issues.isEmpty())
    }

    @Test fun totalWorkRepeatUsesTheSelectedSizeAndDoesNotAddAnExtraCycle() {
        val sizes = listOf("XS", "S", "M", "L", "XL", "2XL", "3XL", "4XL", "5XL")
        sizes.indices.forEach { index ->
            val document = documentOf("Row 1: Knit across.", "Row 2: Purl across.",
                "Work rows 1 and 2 a total of 3 (4) 5 (6) 7 (8) 9 (10) 11 times.")
            val sized = PatternSizes.forSize(document, index, sizes.size)
            val repeat = PatternTextParser.parse(sized).rootNodes.single() as ParsedRepeat
            assertEquals(index + 3, repeat.count)
            assertEquals(2, repeat.children.size)
        }
    }

    @Test fun aWrappedTotalWorkRepeatStartsItsOwnLineAndPreservesUnresolvedContent() {
        val pattern = PatternTextParser.parse(documentOf("Row 1: Knit", "across.",
            "Row 2: Purl across.", "Work rows 1 and 2 a total of", "5 times."))
        assertEquals(5, (pattern.rootNodes.single() as ParsedRepeat).count)
        val unresolved = PatternTextParser.parse(documentOf("Work rows 1 and 2 a total of 9 times."))
        assertEquals(1, unresolved.issues.size)
        assertEquals("Work rows 1 and 2 a total of 9 times.", (unresolved.rootNodes.single() as ParsedInstruction).text)
    }

    @Test
    fun `knit ten rounds -- EXECUTION_ENGINE_SPEC 14-1`() {
        val document = documentOf(
            "Section: Body",
            "Rounds 1-10: Knit all stitches."
        )

        val pattern = PatternTextParser.parse(document)

        assertTrue(pattern.issues.isEmpty())
        assertEquals(
            listOf(
                ParsedSection(
                    title = "Body",
                    children = listOf(
                        ParsedRange(
                            unitLabel = "round",
                            startInclusive = 1,
                            endInclusive = 10,
                            children = listOf(ParsedInstruction("Knit all stitches.", source(2))),
                            source = source(2)
                        )
                    ),
                    source = source(1)
                )
            ),
            pattern.rootNodes
        )
    }

    @Test
    fun `repeated two-row lace sequence -- EXECUTION_ENGINE_SPEC 14-2`() {
        val document = documentOf(
            "Section: Lace panel",
            "Row 1: Yarn over, knit two together across.",
            "Row 2: Purl across.",
            "Repeat rows 1-2 6 times."
        )

        val pattern = PatternTextParser.parse(document)

        assertTrue(pattern.issues.isEmpty())
        assertEquals(
            listOf(
                ParsedSection(
                    title = "Lace panel",
                    children = listOf(
                        ParsedRepeat(
                            count = 6,
                            children = listOf(
                                ParsedInstruction("Row 1: Yarn over, knit two together across.", source(2)),
                                ParsedInstruction("Row 2: Purl across.", source(3))
                            ),
                            source = source(4)
                        )
                    ),
                    source = source(1)
                )
            ),
            pattern.rootNodes
        )
    }

    @Test
    fun `repeat referencing 'and' phrasing resolves the same as a dash range`() {
        val document = documentOf(
            "Row 1: Yarn over, knit two together across.",
            "Row 2: Purl across.",
            "Repeat rows 1 and 2 6 times."
        )

        val pattern = PatternTextParser.parse(document)

        assertTrue(pattern.issues.isEmpty())
        assertEquals(1, pattern.rootNodes.size)
        assertTrue(pattern.rootNodes.single() is ParsedRepeat)
        assertEquals(6, (pattern.rootNodes.single() as ParsedRepeat).count)
    }

    @Test
    fun `repeat containing a range -- EXECUTION_ENGINE_SPEC 14-3`() {
        val document = documentOf(
            "Section: Textured band",
            "Rounds 1-4: Work texture round.",
            "Repeat rounds 1-4 3 times."
        )

        val pattern = PatternTextParser.parse(document)

        assertTrue(pattern.issues.isEmpty())
        assertEquals(
            listOf(
                ParsedSection(
                    title = "Textured band",
                    children = listOf(
                        ParsedRepeat(
                            count = 3,
                            children = listOf(
                                ParsedRange(
                                    unitLabel = "round",
                                    startInclusive = 1,
                                    endInclusive = 4,
                                    children = listOf(ParsedInstruction("Work texture round.", source(2))),
                                    source = source(2)
                                )
                            ),
                            source = source(3)
                        )
                    ),
                    source = source(1)
                )
            ),
            pattern.rootNodes
        )
    }

    @Test
    fun `content with no Section line lives at the document root`() {
        val document = documentOf("Cast on 80 stitches.", "Rows 1-10: Knit all stitches.")

        val pattern = PatternTextParser.parse(document)

        assertTrue(pattern.issues.isEmpty())
        assertEquals(2, pattern.rootNodes.size)
        assertTrue(pattern.rootNodes[0] is ParsedInstruction)
        assertTrue(pattern.rootNodes[1] is ParsedRange)
    }

    @Test
    fun `a plain preamble line between numbered rows is not swallowed by a later repeat`() {
        val document = documentOf(
            "Row 1: Yarn over, knit two together across.",
            "Note: keep tension loose.",
            "Row 2: Purl across.",
            "Repeat rows 1-2 6 times."
        )

        val pattern = PatternTextParser.parse(document)

        // Row 1 and Row 2 are no longer the trailing, contiguous content
        // (the note sits between them), so the repeat cannot be resolved --
        // it must be surfaced as an issue rather than silently reordering
        // the note out of the way.
        assertEquals(1, pattern.issues.size)
        assertEquals(3, pattern.rootNodes.size)
    }

    @Test
    fun `a repeat with no matching preceding rows is an issue, not a guess`() {
        val document = documentOf("Repeat rows 1-2 6 times.")

        val pattern = PatternTextParser.parse(document)

        assertEquals(1, pattern.issues.size)
        assertEquals(source(1), pattern.issues.single().source)
        assertTrue(pattern.rootNodes.isEmpty())
    }

    @Test
    fun `a repeat count of zero is an issue`() {
        val document = documentOf(
            "Row 1: Knit all stitches.",
            "Repeat row 1 0 times."
        )

        val pattern = PatternTextParser.parse(document)

        assertEquals(1, pattern.issues.size)
        assertEquals(1, pattern.rootNodes.size)
    }

    @Test
    fun `a row range with end before start is an issue and keeps the raw line`() {
        val document = documentOf("Rows 10-1: Knit all stitches.")

        val pattern = PatternTextParser.parse(document)

        assertEquals(1, pattern.issues.size)
        assertEquals(
            listOf(ParsedInstruction("Rows 10-1: Knit all stitches.", source(1))),
            pattern.rootNodes
        )
    }

    @Test
    fun `multiple sections each collect their own content`() {
        val document = documentOf(
            "Section: Body",
            "Cast on 80 stitches.",
            "Section: Finishing",
            "Bind off all stitches."
        )

        val pattern = PatternTextParser.parse(document)

        assertTrue(pattern.issues.isEmpty())
        assertEquals(2, pattern.rootNodes.size)
        assertEquals("Body", (pattern.rootNodes[0] as ParsedSection).title)
        assertEquals("Finishing", (pattern.rootNodes[1] as ParsedSection).title)
    }
}
