package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.domain.execution.GuideDefinitionError
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.repository.DraftProblem
import org.junit.Assert.assertEquals
import org.junit.Test

class DraftProblemTest {

    @Test
    fun emptyHeadingsReadAsEmptySections() {
        assertEquals(DraftProblem.EMPTY_SECTION, problemOf(listOf(GuideDefinitionError.EmptyChildren(NodeId("a")))))
        assertEquals(
            DraftProblem.EMPTY_SECTION,
            problemOf(listOf(GuideDefinitionError.ContainerWithoutExecutableDescendant(NodeId("a"))))
        )
    }

    @Test
    fun anEmptyDraftAsksForAStepFirst() {
        assertEquals(
            DraftProblem.NOTHING_TO_KNIT,
            problemOf(listOf(GuideDefinitionError.EmptyDefinition, GuideDefinitionError.EmptyChildren(NodeId("a"))))
        )
    }

    @Test
    fun badRangesAndRepeatsAreMissingDetails() {
        assertEquals(
            DraftProblem.MISSING_DETAIL,
            problemOf(listOf(GuideDefinitionError.NonPositiveRepeatCount(NodeId("r"), 0)))
        )
        assertEquals(DraftProblem.OTHER, problemOf(listOf(GuideDefinitionError.CycleDetected(NodeId("c")))))
    }
}
