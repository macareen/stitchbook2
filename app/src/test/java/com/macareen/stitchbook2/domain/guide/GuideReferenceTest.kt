package com.macareen.stitchbook2.domain.guide

import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.parsing.PatternSizes
import org.junit.Assert.*
import org.junit.Test

class GuideReferenceTest {
    @Test fun glossaryKeepsPatternSpecificDefinitionsAndExcludesOtherNotes() {
        val entries = GuideReference.glossary("## Materials\nYarn: wool\n\n## Glossary\nk: knit\nkfb: knit front and back\n\n## Notes\nDesigner: Example")
        assertEquals(listOf("k", "kfb"), entries.map { it.term })
        assertEquals("knit front and back", entries.last().definition)
    }
    @Test fun metadataLabelsAreKeptInReferenceRatherThanHiddenAsDefinitions() {
        val node = DraftNode(NodeId("materials"), DraftNodeType.INSTRUCTION, instructionText = "Materials: DK yarn")
        val draft = GuideDraft(DraftId("draft"), GuideId("guide"), null, 0, 0, 1, listOf(node.id), listOf(node))
        val preview = GuideReferenceCleanup.preview(draft)
        assertTrue(preview.glossaryTexts.isEmpty())
        assertTrue(preview.appendTo(null).startsWith("## Reference"))
    }

    @Test fun compositeLegacyGlossaryKeepsUnlabelledTermsAvailable() {
        val nodes = listOf(DraftNode(NodeId("glossary"), DraftNodeType.INSTRUCTION, instructionText = "Abbreviations k knit kfb: Knit front and back; p purl"),
            DraftNode(NodeId("side"), DraftNodeType.INSTRUCTION, instructionText = "RS right side of your work skp slip 1 stitch"))
        val draft = GuideDraft(DraftId("draft"), GuideId("guide"), null, 0, 0, 1, nodes.map { it.id }, nodes)
        val preview = GuideReferenceCleanup.preview(draft)
        assertEquals(2, preview.movedTexts.size)
        val entries = GuideReference.glossary(preview.appendTo(null))
        assertTrue(entries.any { it.term == "RS" })
        assertTrue(entries.any { it.term == "k" && it.definition == "knit" })
        assertTrue(entries.any { it.term == "p" && it.definition.startsWith("purl") })
    }

    @Test fun headingsAndCastOnAreDisplayOnlyWithoutDroppingConditions() {
        val row = WorkingInstruction.of("Row 7 (WS): Purl until 2 stitches remain.")
        assertEquals("Row 7 (WS)", row.subtitle)
        assertEquals("Purl until 2 stitches remain.", row.action)
        assertEquals(6, WorkingInstruction.of("Cast on 6 sts on a 4 mm needle.").castOnTarget)
        assertNull(WorkingInstruction.of("Cast on the remaining stitches.").castOnTarget)
        assertEquals("Keep the edge loose.", WorkingInstruction.of("Row 1: Knit. Note: Keep the edge loose.").detail)
    }
    @Test fun reviewedCleanupPreservesIdsActionsAndReviewWarnings() {
        val texts = listOf("kfb: knit front and back", "Cast on 6 sts.", "Review needed: Check the chart.", "Warning: Do not twist.")
        val nodes = texts.mapIndexed { i, text -> DraftNode(NodeId("n$i"), DraftNodeType.INSTRUCTION, instructionText = text) }
        val draft = GuideDraft(DraftId("draft"), GuideId("guide"), null, 0, 0, 2, nodes.map { it.id }, nodes)
        val preview = GuideReferenceCleanup.preview(draft)
        assertEquals(listOf(texts.first()), preview.movedTexts)
        assertEquals(nodes.drop(1), preview.draft.nodes)
        assertEquals(draft, draft.copy())
        assertEquals("kfb", GuideReference.glossary(preview.appendTo(null)).single().term)
    }
    @Test fun selectedSizeResolvesOrdinalsAndImperialFractionsTogether() {
        val text = "Decrease every 10th (9th) 8th round to 38 (39) 40 cm [15 (15¼) 15¾ inches]."
        assertEquals("Decrease every 8th round to 40 cm [15¾ inches].", PatternSizes.resolve(text, 2, 3))
        assertEquals("Decrease every 10th round to 38 cm [15 inches].", PatternSizes.resolve(text, 0, 3))
    }
}
