package com.macareen.stitchbook2.domain.execution

import com.macareen.stitchbook2.domain.execution.ExecutionEngineFixtures.nodeId
import com.macareen.stitchbook2.domain.execution.ExecutionEngineFixtures.validated
import org.junit.Assert.*
import org.junit.Test

class RepeatedRowContextTest {
    private fun context(first: String, second: String, step: Int = 0): RepeatedRowContext? {
        val guide = validated(listOf(nodeId("repeat")),
            Repeat(nodeId("repeat"), 9, children = listOf(nodeId("a"), nodeId("b"))),
            Instruction(nodeId("a"), first), Instruction(nodeId("b"), second))
        val address = GuideTraversal(guide).occurrences().map { it.address }.toList()[step]
        return RepeatedRowContext.of(guide, address)
    }
    @Test fun rowContextRestartsInsideEachRepeat() {
        assertEquals(RepeatedRowContext("row", 1, 1, 2), context("Row 1 (RS): Knit.", "Row 2 (WS): Purl."))
        assertEquals(RepeatedRowContext("row", 2, 1, 2), context("Row 1 (RS): Knit.", "Row 2 (WS): Purl.", 1))
        assertEquals(RepeatedRowContext("row", 1, 1, 2), context("Row 1 (RS): Knit.", "Row 2 (WS): Purl.", 2))
    }
    @Test fun numberedRoundsCanStartAboveOne() {
        assertEquals(RepeatedRowContext("round", 5, 4, 5), context("Round 4: Work.", "Round 5: Work.", 1))
    }
    @Test fun gapsDuplicatesMixedUnitsAndUnnumberedStepsAreNotGuessed() {
        assertNull(context("Row 1: Work.", "Row 3: Work."))
        assertNull(context("Row 1: Work.", "Row 1: Work."))
        assertNull(context("Row 1: Work.", "Round 2: Work."))
        assertNull(context("Knit.", "Purl."))
    }
    @Test fun existingRangeFramesKeepTheirOwnContext() {
        val guide = ExecutionEngineFixtures.repeatContainingRangeGuide()
        val address = GuideTraversal(guide).occurrences().first().address
        assertNull(RepeatedRowContext.of(guide, address))
    }
}
