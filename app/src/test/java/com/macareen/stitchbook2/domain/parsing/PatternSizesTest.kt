package com.macareen.stitchbook2.domain.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatternSizesTest {

    @Test fun repeatedGroupedSizeLabelsAreFlattenedWithoutBracketFragments() {
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G"), PatternSizes.labelsFrom("A (B, C, D) (E, F, G)"))
        assertEquals(listOf("XS", "S", "M", "L", "XL"), PatternSizes.labelsFrom("XS [S; M] [L; XL]"))
        assertEquals(listOf("Small", "Medium", "Large"), PatternSizes.labelsFrom("Small (Medium, Large)"))
        assertEquals(emptyList<String>(), PatternSizes.labelsFrom("S (M, L"))
        assertEquals(emptyList<String>(), PatternSizes.labelsFrom("S ((M, L))"))
    }

    @Test fun everyValueInRepeatedNumericGroupsResolvesForItsSize() {
        val values = listOf("42", "48", "54", "60", "66", "72", "78")
        values.indices.forEach { index ->
            assertEquals("Cast on ${values[index]} sts.",
                PatternSizes.resolve("Cast on 42 (48, 54, 60) (66, 72, 78) sts.", index, values.size))
            assertEquals("Cast on ${values[index]} sts.",
                PatternSizes.resolve("Cast on 42 [48; 54; 60] [66; 72; 78] sts.", index, values.size))
        }
    }

    @Test fun aLongerGroupedRunNeverPartiallyResolvesAsAShorterSizeList() {
        val original = "Work 12 (14, 16) (18, 20, 22) rows."
        assertEquals(original, PatternSizes.resolve(original, 1, 3))
        assertEquals(original, PatternSizes.resolve(original, 1, 5))
    }

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


    @Test
    fun `alternating cardigan sizes retain all nine labels`() {
        assertEquals(listOf("XS", "S", "M", "L", "XL", "2XL", "3XL", "4XL", "5XL"),
            PatternSizes.labelsFrom("XS (S) M (L) XL (2XL) 3XL (4XL) 5XL"))
    }

    @Test
    fun `cardigan number runs resolve every size and multiple runs`() {
        val text = "Work 8 (8) 9 (10) 11 (11) 12 (12) 12 times to 91 (97) 101 (106) 111 (117) 122 (127) 132 cm."
        val repeats = listOf(8, 8, 9, 10, 11, 11, 12, 12, 12)
        val lengths = listOf(91, 97, 101, 106, 111, 117, 122, 127, 132)
        repeats.indices.forEach { index ->
            assertEquals("Work ${repeats[index]} times to ${lengths[index]} cm.", PatternSizes.resolve(text, index, 9))
        }
    }

    @Test
    fun `longer alternating runs cannot be partially resolved as two sizes`() {
        val text = "Work 8 (8) 9 (10) 11 times."
        assertEquals(text, PatternSizes.resolve(text, 1, 2))
    }

    @Test
    fun `alternating decimals fractions dashes and stitch prefixes resolve`() {
        assertEquals("k4 to 3/4 in, repeat - times (RS).",
            PatternSizes.resolve("k2 (3) 4 to 1/4 (1/2) 3/4 in, repeat 2 (3) - times (RS).", 2, 3))
        assertEquals("Work 11.5 cm.", PatternSizes.resolve("Work 10.5 (11) 11.5 cm.", 2, 3))
    }

    @Test
    fun `standalone sizes heading reads the next line and prefers letters over measurements`() {
        val found = requireNotNull(PatternSizes.findInDocument(document(
            "Finished size: 91 (97) 101 cm", "Sizes", "XS (S) M", "Cast on 60 (66) 72 sts.")))
        assertEquals(listOf("XS", "S", "M"), found.first)
        assertEquals(3, found.second.source.lineNumber)
        assertEquals(listOf("91", "97", "101"), PatternSizes.labelsFrom("91 (97) 101 cm"))
    }

    @Test
    fun `without letter sizes the first measurement list is offered without units`() {
        val found = requireNotNull(PatternSizes.findInDocument(document(
            "Bust circumference: 91 (97, 101) cm / 36 (38, 40) inches",
            "Length: 50 (52, 54) cm"
        )))
        assertEquals(listOf("91", "97", "101"), found.first)
        assertEquals(1, found.second.source.lineNumber)
    }

    private fun document(vararg lines: String) = ExtractedDocument(
        pageCount = 1,
        lines = lines.mapIndexed { index, text -> ExtractedLine(text, SourceReference(1, index + 1)) }
    )
}
