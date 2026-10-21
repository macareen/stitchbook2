package com.macareen.stitchbook2.domain.guide

import com.macareen.stitchbook2.domain.execution.*
import org.junit.Assert.*
import org.junit.Test

class DraftSectionReplayTest {
    private fun id(value: String) = NodeId(value)
    private fun fixture(inside: Boolean = false): GuideDraft {
        val section = DraftNode(id("s"), DraftNodeType.SECTION, title = "First sleeve", children =
            listOf(id("co"), id("rep")) + if (inside) listOf(id("target"), id("later")) else emptyList())
        return GuideDraft(DraftId("d"), GuideId("g"), null, 1, 2, 7,
            if (inside) listOf(id("s")) else listOf(id("s"), id("target"), id("later")), listOf(section,
                DraftNode(id("co"), DraftNodeType.INSTRUCTION, instructionText = "Cast on 20 sts. (p.2)"),
                DraftNode(id("rep"), DraftNodeType.REPEAT, repeatCount = 3, repeatLabel = "Cuff", children = listOf(id("range"))),
                DraftNode(id("range"), DraftNodeType.RANGE, rangeUnitLabel = "row", rangeStartInclusive = 1, rangeEndInclusive = 2, children = listOf(id("row"))),
                DraftNode(id("row"), DraftNodeType.INSTRUCTION, instructionText = "Knit. (p.3)"),
                DraftNode(id("target"), DraftNodeType.INSTRUCTION, instructionText = "Repeat for second sleeve. (p.4)"),
                DraftNode(id("later"), DraftNodeType.INSTRUCTION, instructionText = "Sew seams.")))
    }

    @Test fun copiesCompleteNestedStructureWithFreshIdentitiesAndPreservesOriginalSiblings() {
        val original = fixture()
        var next = 0
        val updated = DraftSectionReplay.replace(original, id("target"), id("s")) { "copy-${next++}" }!!
        assertEquals(original.version, updated.version)
        assertEquals(original.nodes.filterNot { it.id == id("target") }, updated.nodes.filter { it.id in original.nodes.map { n -> n.id } })
        assertFalse(updated.nodes.any { it.id == id("target") })
        assertEquals(updated.nodes.size, updated.nodes.map { it.id }.distinct().size)
        val copiedRepeat = updated.nodes.single { it.id.value.startsWith("copy") && it.type == DraftNodeType.REPEAT }
        assertEquals(3, copiedRepeat.repeatCount)
        assertEquals("Cuff", copiedRepeat.repeatLabel)
        assertTrue(copiedRepeat.children.single().value.startsWith("copy"))
        assertEquals(2, updated.nodes.count { it.instructionText == "Knit. (p.3)" })
        assertEquals(1, updated.nodes.count { it.instructionText == "Sew seams." })
        assertTrue(updated.nodes.single { it.id == updated.rootNodeIds[1] }.title!!.contains("Repeat for second sleeve. (p.4)"))
    }

    @Test fun reminderInsideSourceCopiesOnlyPrecedingWorkWithoutItselfOrLaterFinishing() {
        val original = fixture(inside = true)
        val choice = DraftSectionReplay.choices(original, id("target")).single()
        assertTrue(choice.prefixOnly)
        assertEquals(0, choice.depths[id("rep")])
        assertEquals(1, choice.depths[id("range")])
        assertEquals(2, choice.depths[id("row")])
        assertFalse(choice.nodes.any { it.id in setOf(id("target"), id("later")) })
        var next = 0
        val updated = DraftSectionReplay.replace(original, id("target"), id("s")) { "copy-${next++}" }!!
        assertEquals(1, updated.nodes.count { it.instructionText == "Sew seams." })
        assertEquals(2, updated.nodes.count { it.instructionText == "Cast on 20 sts. (p.2)" })
    }

    @Test fun cyclicSourcesAndContainersContainingATargetDeeperThanADirectChildAreRefused() {
        val original = fixture(true)
        val cyclic = original.copy(nodes = original.nodes.map { if (it.id == id("row")) it.copy(children = listOf(id("s"))) else it })
        assertTrue(DraftSectionReplay.choices(cyclic, id("target")).isEmpty())
        val nested = original.copy(nodes = original.nodes.map { when (it.id) {
            id("s") -> it.copy(children = listOf(id("rep")))
            id("range") -> it.copy(children = listOf(id("target")))
            else -> it
        } })
        assertTrue(DraftSectionReplay.choices(nested, id("target")).isEmpty())
    }

    @Test fun collidingIdentitiesAreRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            DraftSectionReplay.replace(fixture(), id("target"), id("s")) { "s" }
        }
    }

    @Test fun oldPlainSleeveHeadingOffersOnlyItsWorkWithoutEarlierBodyOrLaterFinishing() {
        val base = fixture(true)
        val body = DraftNode(id("body"), DraftNodeType.INSTRUCTION, instructionText = "Work the body.")
        val marker = DraftNode(id("marker"), DraftNodeType.INSTRUCTION, instructionText = "Sleeves (p.2)")
        val original = base.copy(nodes = base.nodes.map { if (it.id == id("s"))
            it.copy(children = listOf(body.id, marker.id) + it.children) else it } + body + marker)
        val choice = DraftSectionReplay.choices(original, id("target")).first()
        assertEquals(marker.id, choice.sourceId)
        assertEquals(listOf(id("co"), id("rep")), choice.rootNodeIds)
        var next = 0
        val updated = DraftSectionReplay.replace(original, id("target"), marker.id) { "copy-${next++}" }!!
        assertEquals(2, updated.nodes.count { it.instructionText == "Cast on 20 sts. (p.2)" })
        assertEquals(1, updated.nodes.count { it.instructionText == "Work the body." })
        assertEquals(1, updated.nodes.count { it.instructionText == "Sew seams." })
        assertEquals(marker, updated.nodes.single { it.id == marker.id })
    }
}
