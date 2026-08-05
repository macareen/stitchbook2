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
                                ParsedInstruction("Yarn over, knit two together across.", source(2)),
                                ParsedInstruction("Purl across.", source(3))
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
