package com.macareen.stitchbook2.domain.execution

import com.macareen.stitchbook2.domain.execution.ExecutionEngineFixtures.nodeId
import com.macareen.stitchbook2.domain.execution.ExecutionEngineFixtures.validated
import org.junit.Assert.*
import org.junit.Test

class GuideNoteContextTest {
    @Test fun recordsSectionRepeatAndActualRangeValue() {
        val guide = validated(listOf(nodeId("body")),
            Section(nodeId("body"), "Body", listOf(nodeId("repeat"))),
            Repeat(nodeId("repeat"), 2, children = listOf(nodeId("rows"))),
            Range(nodeId("rows"), "round", 3, 4, listOf(nodeId("step"))), Instruction(nodeId("step"), "Work round."))
        val addresses = GuideTraversal(guide).occurrences().map { it.address }.toList()
        assertEquals("Body \u00b7 Repeat 2 of 2 \u00b7 round 4", guideNoteContext(guide, addresses.last()))
    }
    @Test fun unnumberedInstructionsDoNotInventRows() {
        val guide = validated(listOf(nodeId("step")), Instruction(nodeId("step"), "Try it on."))
        assertNull(guideNoteContext(guide, GuideTraversal(guide).occurrences().first().address))
    }
}
