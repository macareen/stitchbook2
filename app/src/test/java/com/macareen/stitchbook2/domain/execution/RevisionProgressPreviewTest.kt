package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.*
import org.junit.Test

class RevisionProgressPreviewTest {
    private val leaf = NodeId("leaf")
    private val repeat = NodeId("repeat")
    private val section = NodeId("section")
    private fun definition(revision: String, text: String = "Knit.", count: Int = 2,
                           title: String = "Sleeve", unit: String? = null, leafId: NodeId = leaf,
                           guide: String = "guide"): GuideDefinition = GuideDefinition(GuideId(guide),
        DefinitionRevisionId(revision), listOf(section), listOf(
            Section(section, title, listOf(repeat)),
            if (unit == null) Repeat(repeat, count, "Repeat", listOf(leafId))
            else Range(repeat, unit, 1, count, listOf(leafId)),
            Instruction(leafId, text)))
    private fun addresses(definition: GuideDefinition) =
        GuideTraversal(GuideDefinitionValidator.validate(definition)).occurrences().map { it.address }.toList()
    private fun preview(target: GuideDefinition): RevisionProgressPreview {
        val source = definition("old")
        return RevisionProgressComparison.preview(source, target, addresses(source).toSet())
    }

    @Test fun unchangedCompletedOccurrencesCarryWithExactIterationAndStableOrder() {
        val source = definition("old")
        val completed = addresses(source).reversed().toSet()
        val result = RevisionProgressComparison.preview(source, definition("new"), completed)
        assertEquals(addresses(source), result.candidates.map { it.source })
        assertEquals(addresses(definition("new")), result.candidates.map { it.target })
        assertTrue(result.unmatched.isEmpty())
        assertEquals(2, result.targetOccurrenceCount)
        assertEquals(DefinitionRevisionId("old"), source.revisionId)
    }
    @Test fun editedInstructionsAreNeverProposed() {
        val result = preview(definition("new", text = "Purl."))
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.unmatched.all { it.reason == UnmatchedProgressReason.CHANGED_INSTRUCTION })
    }
    @Test fun identicalTextWithNewIdentityIsNotGuessed() {
        val result = preview(definition("new", leafId = NodeId("replacement")))
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.unmatched.all { it.reason == UnmatchedProgressReason.REMOVED_OCCURRENCE })
    }
    @Test fun changedRepeatCountRejectsEvenItsRemainingIteration() {
        val result = preview(definition("new", count = 1))
        assertTrue(result.candidates.isEmpty())
        assertEquals(listOf(UnmatchedProgressReason.CHANGED_CONTEXT, UnmatchedProgressReason.REMOVED_OCCURRENCE), result.unmatched.map { it.reason })
    }
    @Test fun renamedSectionRequiresReviewAsChangedContext() {
        assertTrue(preview(definition("new", title = "Body")).unmatched.all { it.reason == UnmatchedProgressReason.CHANGED_CONTEXT })
    }
    @Test fun rangeUnitsAndRepeatKindsAreNotInterchangeable() {
        assertTrue(preview(definition("new", unit = "round")).candidates.isEmpty())
        val source = definition("old", unit = "row")
        val result = RevisionProgressComparison.preview(source, definition("new", unit = "round"), addresses(source).toSet())
        assertTrue(result.unmatched.all { it.reason == UnmatchedProgressReason.CHANGED_CONTEXT })
    }
    @Test fun incompleteOccurrencesAreNotAddedToTheProposal() {
        val source = definition("old")
        val result = RevisionProgressComparison.preview(source, definition("new"), setOf(addresses(source).first()))
        assertEquals(1, result.candidates.size)
        assertEquals(2, result.targetOccurrenceCount)
    }
    @Test fun wrongGuideRevisionOrCompletedAddressIsRejected() {
        val source = definition("old")
        assertThrows(IllegalArgumentException::class.java) { RevisionProgressComparison.preview(source, definition("new", guide = "other"), emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { RevisionProgressComparison.preview(source, source, emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { RevisionProgressComparison.preview(source, definition("new"), addresses(definition("wrong")).toSet()) }
    }
    @Test fun oversizedExpansionFailsInsteadOfOfferingPartialCarryover() {
        assertThrows(RevisionPreviewTooLargeException::class.java) { preview(definition("new", count = 10001)) }
    }
}
