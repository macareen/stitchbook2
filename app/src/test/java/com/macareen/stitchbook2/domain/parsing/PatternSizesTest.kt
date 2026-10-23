package com.macareen.stitchbook2.domain.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternSizesTest {

    @Test fun completeAnnotatedListSurvivesInterleavedOcrColumns() {
        val doc = document("SIZES", "Tools", "ABBREVIATIONS", "k", "knit", "Measure around your foot.",
            "S(6/15cm) M (7\"/18cm) L(8\"/21cm) XL(9\"/24cm)")
        val found = requireNotNull(PatternSizes.findInDocument(doc))
        assertEquals(listOf("S", "M", "L", "XL"), found.first)
        assertEquals(SourceReference(1, 7), found.second.source)
    }

    @Test fun annotatedMentionsInsideInstructionsAreNotStandaloneDeclarations() {
        assertNull(PatternSizes.findInDocument(document("For S (15 cm) and M (18 cm), work to the marker.")))
        assertNull(PatternSizes.findInDocument(document("S (15 cm), S (18 cm)")))
    }

    @Test fun duplicateSizeLabelsAreNotOfferedAsSelectableSizes() {
        assertNull(PatternSizes.findInDocument(document("Sizes: S (s, M)")))
        assertNull(PatternSizes.findInDocument(document("SIZES", "S (M, S)")))
        assertNull(PatternSizes.findInDocument(document("S (6/15 cm) M (7/18 cm) S (8/21 cm)")))
    }

    @Test fun savedSizeListsWithDuplicateLabelsCannotSelectAPosition() {
        assertNull(PatternSizes.indexOf("S", listOf("S", " s ", "M")))
        assertNull(PatternSizes.indexOf("M", listOf("S", "s", "M")))
        assertNull(PatternSizes.indexOf("0-3 months", listOf("0-3 months", "0-3  MONTHS")))
    }

    @Test fun dimensionsAndBracketedUnitConversionsAreNotSizeChoices() {
        assertNull(PatternSizes.findInDocument(document(
            "Measurements: 5 1/2” (14 cm) diameter base x 15 3/8” (39 cm) height (without handles)")))
        assertEquals(listOf("Small", "Medium", "Large"), PatternSizes.findInDocument(document("Sizes: Small (Medium, Large)"))!!.first)
        assertNull(PatternSizes.findInDocument(document("Size 9 (U.S.)/", "5.5 mm crochet hook")))
        assertEquals(listOf("0-3 months", "3-6 months"), PatternSizes.findInDocument(document("Sizes: 0-3 months, 3-6 months"))!!.first)
    }

    @Test fun numericDeclarationBeatsLaterSizeBranchProse() {
        val lines = listOf("Sizes 1 (2; 3; 4) [5; 6; 7] detailed below. Choose the right fit.", "Sizes 1-4: Work the front, then back, then sleeves.")
        val doc = ExtractedDocument(1, lines.mapIndexed { i, text -> ExtractedLine(text, SourceReference(1, i + 1)) })
        assertEquals((1..7).map(Int::toString), PatternSizes.findInDocument(doc)!!.first)
    }

    @Test fun sizesBelowAnIntroKeepLetterLabelsWithoutMeasurementAnnotations() {
        val lines = listOf("SIZES", "Choose by your circumference.", "Measure before starting.", "S (16 cm) M (20 cm) L (24 cm) XL (28 cm)", "ABBREVIATIONS")
        val doc = ExtractedDocument(1, lines.mapIndexed { i, text -> ExtractedLine(text, SourceReference(1, i + 1)) })
        val found = PatternSizes.findInDocument(doc)!!
        assertEquals(listOf("S", "M", "L", "XL"), found.first)
        assertEquals(4, found.second.source.lineNumber)
    }

    @Test fun unresolvedExecutableAlternativesAreFlaggedWithoutGuessingOrChangingText() {
        val text = "Cast on 12 (14) 16 (18) 20 sts."
        val source = SourceReference(2, 7)
        val parsed = ParsedPattern(listOf(ParsedInstruction(text, source)), emptyList())
        assertEquals(1, PatternSizes.unresolvedIssues(parsed, 3).size)
        assertEquals(source, PatternSizes.unresolvedIssues(parsed, 3).single().source)
        assertEquals(text, parsed.rootNodes.filterIsInstance<ParsedInstruction>().single().text)
        assertTrue(PatternSizes.unresolvedIssues(parsed, 5).isEmpty())
    }

    @Test fun groupedAlternativesWithTooFewValuesAreFlaggedButInvariantRunsAreNot() {
        val tooFew = "Work 2 (3) times."
        val source = SourceReference(3, 4)
        val parsed = ParsedPattern(listOf(ParsedInstruction(tooFew, source)), emptyList())

        assertEquals(tooFew, PatternSizes.resolve(tooFew, sizeIndex = 2, sizeCount = 3))
        assertEquals(source, PatternSizes.unresolvedIssues(parsed, sizeCount = 3).single().source)
        assertTrue(PatternSizes.unresolvedIssues(parsed, sizeCount = 2).isEmpty())

        val invariant = ParsedPattern(
            listOf(ParsedInstruction("Work 2 (2) 2 (2) times.", source)),
            emptyList()
        )
        assertTrue(PatternSizes.unresolvedIssues(invariant, sizeCount = 3).isEmpty())
    }

    @Test fun commasBeforeAlternatingGroupsDoNotSplitOneSizeList() {
        val values = listOf(12, 14, 16, 18, 20, 22, 24)
        val text = "Cast on 12 (14) 16 (18) 20, (22) 24 sts."
        values.forEachIndexed { index, count -> assertEquals("Cast on $count sts.", PatternSizes.resolve(text, index, values.size)) }
        assertEquals("Cast on 24 sts.", PatternSizes.resolve("Cast on 12 (14), 16 (18), 20 (22), 24 sts.", 6, 7))
        assertEquals("Use 3,5 mm needles.", PatternSizes.resolve("Use 3,5 mm needles.", 0, 2))
        assertEquals("Knit 2, purl 4.", PatternSizes.resolve("Knit 2, purl 4.", 0, 2))
    }

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

    @Test fun numberedMarkerIsNotPartOfFollowingSizeValues() {
        val counts = listOf(20, 20, 24, 24, 16, 12, 12, 8, 4)
        counts.indices.forEach { index ->
            assertEquals("Marker 3 ${counts[index]} sts (right sleeve), Marker 4.",
                PatternSizes.resolve("Marker 3 20 (20) 24 (24) 16 (12) 12 (8) 4 sts (right sleeve), Marker 4.", index, 9))
        }
        assertEquals("Marker 3 20 (24) 28 (32) sts.", PatternSizes.resolve("Marker 3 20 (24) 28 (32) sts.", 1, 3))
    }

    @Test fun extraIdenticalAlternativesHaveAnInvariantSelectedValue() {
        val same = "Slip 1 (1) 1 (1) 1 (1) 1 (1) 1 (1) stitch."
        (0 until 9).forEach { index -> assertEquals("Slip 1 stitch.", PatternSizes.resolve(same, index, 9)) }
        val differing = "Slip 1 (1) 1 (1) 1 (1) 1 (1) 1 (2) stitches."
        assertEquals(differing, PatternSizes.resolve(differing, 3, 9))
    }

    @Test fun aSizeRangeIsOneAlternativeIncludingItsOriginalDashAndFractions() {
        val text = "Work to 10–12 (14, 16) cm, then repeat 2-3 [3–4; 4—5] times."
        val expected = listOf(
            "Work to 10–12 cm, then repeat 2-3 times.",
            "Work to 14 cm, then repeat 3–4 times.",
            "Work to 16 cm, then repeat 4—5 times."
        )
        expected.forEachIndexed { index, selected -> assertEquals(selected, PatternSizes.resolve(text, index, 3)) }
        assertEquals("Work to 2 1/2 – 3 in.", PatternSizes.resolve("Work to 2 – 2 1/2 (2 1/2 – 3, 3 – 3 1/2) in.", 1, 3))
        assertEquals("Work to 1/2-3/4 in.", PatternSizes.resolve("Work to 1/4-1/2 (1/2-3/4, 3/4-1) in.", 1, 3))
    }

    @Test fun incompleteRangeAlternativesStayWholeAndNeedSourceLinkedReview() {
        val text = "Work to 10-12 (14-16) cm."
        val source = SourceReference(2, 5)
        val parsed = ParsedPattern(listOf(ParsedInstruction(text, source)), emptyList())
        assertEquals(text, PatternSizes.resolve(text, 1, 3))
        assertEquals(source, PatternSizes.unresolvedIssues(parsed, 3).single().source)
        assertTrue(PatternSizes.unresolvedIssues(parsed, 2).isEmpty())
        val tooMany = "Work to 10-12 (12-14, 14-16) 16-18 cm."
        assertEquals(tooMany, PatternSizes.resolve(tooMany, 1, 3))
        assertEquals(source, PatternSizes.unresolvedIssues(
            ParsedPattern(listOf(ParsedInstruction(tooMany, source)), emptyList()), 3
        ).single().source)
        assertEquals("Repeat rows 2-4 (RS), then work 3 rounds (24 sts).",
            PatternSizes.resolve("Repeat rows 2-4 (RS), then work 3 rounds (24 sts).", 1, 3))
    }

    @Test fun endpointAlternativesAndIndependentScalarGroupsStillResolve() {
        assertEquals("Work to 12-14 cm, then make 6 motifs.",
            PatternSizes.resolve("Work to 10 (12, 14)-12 (14, 16) cm, then make 4 (6, 8) motifs.", 1, 3))
        assertEquals("Work to 12-14 cm.", PatternSizes.resolve("Work to 10-12 (12-14) 14-16 cm.", 1, 3))
    }

    private fun document(vararg lines: String) = ExtractedDocument(
        pageCount = 1,
        lines = lines.mapIndexed { index, text -> ExtractedLine(text, SourceReference(1, index + 1)) }
    )
}
