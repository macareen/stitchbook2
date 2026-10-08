package com.macareen.stitchbook2.domain.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatternSizesTest {

    @Test
    fun `size names are read in bracket, comma and slash forms`() {
        assertEquals(listOf("XS", "S", "M", "L"), PatternSizes.labelsFrom("XS (S, M, L)"))
        assertEquals(listOf("1", "2", "3"), PatternSizes.labelsFrom("1 [2, 3]"))
        assertEquals(listOf("S", "M", "L"), PatternSizes.labelsFrom("S, M, L"))
        assertEquals(listOf("S", "M", "L"), PatternSizes.labelsFrom("S/M/L"))
        assertEquals(emptyList<String>(), PatternSizes.labelsFrom("One size"))
    }

    @Test
    fun `the pattern's own sizes line is found with where it is`() {
        val document = document("Seaside Cardigan", "Sizes: S (M, L, XL)", "Cast on 60 (66, 72, 78) sts.")

        val (labels, line) = requireNotNull(PatternSizes.findInDocument(document))

        assertEquals(listOf("S", "M", "L", "XL"), labels)
        assertEquals(2, line.source.lineNumber)
    }

    @Test
    fun `a size is matched ignoring case and spacing`() {
        val labels = listOf("XS", "S", "M", "2X")
        assertEquals(2, PatternSizes.indexOf(" m ", labels))
        assertEquals(3, PatternSizes.indexOf("2x", labels))
        assertNull(PatternSizes.indexOf("XL", labels))
    }

    @Test
    fun `every per-size group keeps only the chosen size`() {
        val text = "Cast on 60 (66, 72) sts. Work 4 [5, 6] rounds, then k2 (3, 4) and knit to 10.5 (11, 12) cm."

        assertEquals(
            "Cast on 66 sts. Work 5 rounds, then k3 and knit to 11 cm.",
            PatternSizes.resolve(text, sizeIndex = 1, sizeCount = 3)
        )
    }

    @Test
    fun `stitch counts, sides and groups of another length are kept`() {
        val text = "Row 2 (RS): Knit to end. (24 sts) Repeat 3 (4) times."

        assertEquals(text, PatternSizes.resolve(text, sizeIndex = 2, sizeCount = 3))
    }

    @Test
    fun `fractions and dashes resolve too`() {
        assertEquals("Knit 2 1/2 in.", PatternSizes.resolve("Knit 2 (2 1/2, 3) in.", 1, 3))
        assertEquals("Decrease - times.", PatternSizes.resolve("Decrease 2 (-, 4) times.", 1, 3))
    }

    @Test
    fun `a document keeps its sources when resolved`() {
        val resolved = PatternSizes.forSize(document("Cast on 60 (66) sts."), sizeIndex = 0, sizeCount = 2)

        assertEquals("Cast on 60 sts.", resolved.lines.single().text)
        assertEquals(SourceReference(1, 1), resolved.lines.single().source)
    }

    private fun document(vararg lines: String) = ExtractedDocument(
        pageCount = 1,
        lines = lines.mapIndexed { index, text -> ExtractedLine(text, SourceReference(1, index + 1)) }
    )
}
