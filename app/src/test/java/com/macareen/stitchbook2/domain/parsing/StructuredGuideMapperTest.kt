package com.macareen.stitchbook2.domain.parsing

import com.macareen.stitchbook2.domain.guide.DraftNodeType
import com.macareen.stitchbook2.domain.model.Craft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredGuideMapperTest {

    @Test fun assistantTextExplicitlyMarksUnreadablePagesBetweenReadablePages() {
        val document = ExtractedDocument(3, listOf(ExtractedLine("Work a pass.", SourceReference(1, 1)),
            ExtractedLine("Fasten off.", SourceReference(3, 1))))
        val text = StructuredGuidePrompt.patternText(document)
        assertTrue(text.contains("[page 2]\n[No readable text extracted."))
        assertTrue(text.indexOf("[page 1]") < text.indexOf("[page 2]"))
        assertTrue(text.indexOf("[page 2]") < text.indexOf("[page 3]"))
        assertTrue(text.contains("do not invent missing instructions"))
    }

    @Test
    fun stepsBecomeTheMatchingDraftNodes() {
        val guide = StructuredGuide(
            name = "Hat",
            steps = listOf(
                StructuredStep.Section(
                    "Brim",
                    listOf(
                        StructuredStep.Rows("round", 1, 10, listOf(StructuredStep.Instruction("K2, p2 around."))),
                        StructuredStep.Repeat(
                            4,
                            "Rounds 11-12",
                            listOf(
                                StructuredStep.Rows("round", 11, 11, listOf(StructuredStep.Instruction("Knit."))),
                                StructuredStep.Rows("round", 12, 12, listOf(StructuredStep.Instruction("Purl.")))
                            )
                        )
                    )
                )
            )
        )

        val result = map(guide)
        val byId = result.nodes.associateBy { it.id }

        val section = byId.getValue(result.rootNodeIds.single())
        assertEquals(DraftNodeType.SECTION, section.type)
        assertEquals("Brim", section.title)
        val range = byId.getValue(section.children[0])
        assertEquals(DraftNodeType.RANGE, range.type)
        assertEquals("round", range.rangeUnitLabel)
        assertEquals(1, range.rangeStartInclusive)
        assertEquals(10, range.rangeEndInclusive)
        assertEquals("K2, p2 around.", byId.getValue(range.children.single()).instructionText)
        val repeat = byId.getValue(section.children[1])
        assertEquals(DraftNodeType.REPEAT, repeat.type)
        assertEquals(4, repeat.repeatCount)
        assertEquals("Rounds 11-12", repeat.repeatLabel)
        assertEquals(2, repeat.children.size)
    }

    @Test
    fun preparationGoesFirstAndReviewItemsLastSoNothingIsLost() {
        val guide = StructuredGuide(
            name = null,
            materials = listOf("DK yarn", "4 mm needles"),
            abbreviations = listOf(Abbreviation("k", "knit"), Abbreviation("p", "purl")),
            notes = listOf("Gauge: 22 sts = 10 cm"),
            steps = listOf(StructuredStep.Instruction("Cast on 88.")),
            review = listOf("Size M count is unclear.")
        )

        val result = map(guide)
        val byId = result.nodes.associateBy { it.id }
        val roots = result.rootNodeIds.map(byId::getValue)

        assertEquals(2, roots.size)
        assertEquals("Cast on 88.", roots[0].instructionText)
        assertEquals("Review needed: Size M count is unclear.", roots[1].instructionText)
        val notes = com.macareen.stitchbook2.domain.guide.GuideReference.notes(guide)!!
        assertTrue(notes.contains("DK yarn"))
        assertEquals(2, com.macareen.stitchbook2.domain.guide.GuideReference.glossary(notes).size)
    }

    @Test
    fun aGuideWithOnlyStepsHasNoPreparationSection() {
        val result = map(StructuredGuide(name = null, steps = listOf(StructuredStep.Instruction("Knit."))))

        assertEquals(1, result.nodes.size)
        assertTrue(result.nodes.none { it.type == DraftNodeType.SECTION })
    }

    @Test
    fun promptCarriesTheCraftTheNameAndThePatternTextWithPageMarks() {
        val document = ExtractedDocument(
            pageCount = 2,
            lines = listOf(
                ExtractedLine("Round 1: ch 4.", SourceReference(1, 1)),
                ExtractedLine("Round 2: 2 sc in each.", SourceReference(2, 1))
            )
        )
        val text = StructuredGuidePrompt.patternText(document)
        val prompt = StructuredGuidePrompt.build(Craft.TUNISIAN_CROCHET, "Wrap", text)

        assertEquals("[page 1]\nRound 1: ch 4.\n\n[page 2]\nRound 2: 2 sc in each.", text)
        assertTrue(prompt.contains("Tunisian crochet pattern"))
        assertTrue(prompt.contains("Guide name: Wrap"))
        assertTrue(prompt.endsWith("<<<\n$text\n>>>"))
    }

    private fun map(guide: StructuredGuide): DraftMappingResult {
        var next = 0
        return StructuredGuideMapper.toDraftNodes(guide) { "n${next++}" }
    }
}
