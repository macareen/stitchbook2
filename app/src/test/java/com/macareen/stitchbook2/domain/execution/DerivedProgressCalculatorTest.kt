package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DerivedProgressCalculatorTest {

    @Test
    fun noCompletedDescendantsIsNotStarted() {
        val guide = ExecutionEngineFixtures.knitTenRoundsGuide()

        val progress = DerivedProgressCalculator(guide).progressFor(
            containerNodeId = id("body"),
            completedAddresses = emptySet()
        )

        assertEquals(ContainerProgressStatus.NOT_STARTED, progress.status)
        assertEquals(0, progress.completedCount)
        assertEquals(10, progress.totalCount)
    }

    @Test
    fun someCompletedDescendantsIsInProgress() {
        val guide = ExecutionEngineFixtures.knitTenRoundsGuide()
        val occurrences = GuideTraversal(guide).occurrences().toList()

        val progress = DerivedProgressCalculator(guide).progressFor(
            containerNodeId = id("rounds"),
            completedAddresses = occurrences.take(3).map { it.address }.toSet()
        )

        assertEquals(ContainerProgressStatus.IN_PROGRESS, progress.status)
        assertEquals(3, progress.completedCount)
        assertEquals(10, progress.totalCount)
    }

    @Test
    fun allCompletedDescendantsIsComplete() {
        val guide = ExecutionEngineFixtures.laceRepeatGuide()
        val completed = GuideTraversal(guide)
            .occurrences()
            .map { it.address }
            .toSet()

        val progress = DerivedProgressCalculator(guide).progressFor(
            containerNodeId = id("lace-repeat"),
            completedAddresses = completed
        )

        assertEquals(ContainerProgressStatus.COMPLETE, progress.status)
        assertEquals(12, progress.completedCount)
        assertEquals(12, progress.totalCount)
    }

    @Test
    fun nonContiguousCompletionUsesActualLeafState() {
        val guide = ExecutionEngineFixtures.knitTenRoundsGuide()
        val occurrences = GuideTraversal(guide).occurrences().toList()
        val completed = setOf(
            occurrences[0].address,
            occurrences[4].address,
            occurrences[9].address
        )

        val progress = DerivedProgressCalculator(guide).progressFor(
            containerNodeId = id("rounds"),
            completedAddresses = completed
        )

        assertEquals(ContainerProgressStatus.IN_PROGRESS, progress.status)
        assertEquals(3, progress.completedCount)
        assertEquals(10, progress.totalCount)
    }

    @Test
    fun currentRangeValueIsProjectedFromCurrentAddress() {
        val guide = ExecutionEngineFixtures.knitTenRoundsGuide()
        val current = GuideTraversal(guide).occurrences().elementAt(6).address

        val progress = DerivedProgressCalculator(guide).progressFor(
            containerNodeId = id("rounds"),
            completedAddresses = emptySet(),
            currentAddress = current
        )

        assertEquals(7, progress.currentRangeValue)
        assertNull(progress.currentRepeatIteration)
    }

    @Test
    fun currentRepeatIterationIsProjectedFromCurrentAddress() {
        val guide = ExecutionEngineFixtures.laceRepeatGuide()
        val current = GuideTraversal(guide).occurrences().elementAt(7).address

        val progress = DerivedProgressCalculator(guide).progressFor(
            containerNodeId = id("lace-repeat"),
            completedAddresses = emptySet(),
            currentAddress = current
        )

        assertEquals(4, progress.currentRepeatIteration)
        assertNull(progress.currentRangeValue)
    }

    @Test
    fun nestedContainerCountsOnlyItsExecutableDescendants() {
        val guide = ExecutionEngineFixtures.repeatContainingRangeGuide()
        val occurrences = GuideTraversal(guide).occurrences().toList()

        val progress = DerivedProgressCalculator(guide).progressFor(
            containerNodeId = id("band-rounds"),
            completedAddresses = setOf(
                occurrences[0].address,
                occurrences[8].address
            ),
            currentAddress = occurrences[9].address
        )

        assertEquals(12, progress.totalCount)
        assertEquals(2, progress.completedCount)
        assertEquals(2, progress.currentRangeValue)
    }

    private fun id(value: String) = ExecutionEngineFixtures.nodeId(value)
}
