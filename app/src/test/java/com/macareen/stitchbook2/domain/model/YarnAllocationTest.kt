package com.macareen.stitchbook2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YarnAllocationTest {

    private val yarn = StashItem(
        id = "yarn",
        name = "Sock yarn",
        category = StashCategory.YARN,
        brand = null,
        colorway = null,
        dyeLot = null,
        weightCategory = null,
        fiberContent = null,
        quantity = 3.0,
        unitLabel = "skeins",
        yardagePerUnit = 400.0,
        notes = null,
        storageLocation = null,
        careInstructions = null,
        ravelryYarnId = null,
        purchaseSource = null,
        purchasePrice = null,
        purchaseDate = null,
        createdAt = 0,
        updatedAt = 0,
        weightPerUnitGrams = 100.0
    )

    private fun allocation(id: String, reserved: Double, used: Double = 0.0) = YarnAllocation(
        id = id,
        projectId = "project",
        stashItemId = "yarn",
        quantityReserved = reserved,
        quantityUsed = used,
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    @Test
    fun unallocatedSubtractsEveryReservationAndNeverGoesNegative() {
        assertEquals(1.5, unallocatedQuantity(yarn, listOf(allocation("a", 1.0), allocation("b", 0.5))), 0.0)
        assertEquals(0.0, unallocatedQuantity(yarn, listOf(allocation("a", 5.0))), 0.0)
    }

    @Test
    fun reservationCannotExceedUnallocatedButEditingExcludesItself() {
        val existing = listOf(allocation("a", 2.0))
        assertEquals(AllocationError.EXCEEDS_UNALLOCATED, validateReservation(yarn, existing, 1.5))
        assertNull(validateReservation(yarn, existing, 1.0))
        assertNull(validateReservation(yarn, existing, 3.0, replacingAllocationId = "a"))
        assertEquals(AllocationError.NOT_POSITIVE, validateReservation(yarn, existing, 0.0))
    }

    @Test
    fun consumptionDrawsDownReservationAndStashTogether() {
        val a = allocation("a", 1.0)
        val result = consume(yarn, a, listOf(a), 0.4, now = 10).getOrThrow()
        assertEquals(2.6, result.item.quantity, 0.0)
        assertEquals(0.6, result.allocation.quantityReserved, 0.0)
        assertEquals(0.4, result.allocation.quantityUsed, 0.0)
        assertEquals(10L, result.item.updatedAt)
    }

    @Test
    fun consumptionMayUseUnreservedStockButNotOtherReservationsOrBelowZero() {
        val a = allocation("a", 1.0)
        val b = allocation("b", 1.5)
        // On hand 3, b holds 1.5, so a may draw at most 1.5.
        assertTrue(consume(yarn, a, listOf(a, b), 1.5, 0).isSuccess)
        val tooMuch = consume(yarn, a, listOf(a, b), 1.6, 0).exceptionOrNull() as AllocationException
        assertEquals(AllocationError.EXCEEDS_RESERVED_AND_ON_HAND, tooMuch.error)
        val overdrawn = consume(yarn, a, listOf(a), 4.0, 0).exceptionOrNull() as AllocationException
        assertEquals(AllocationError.EXCEEDS_RESERVED_AND_ON_HAND, overdrawn.error)
    }

    @Test
    fun consumptionScalesWeighedRemainderProportionally() {
        val weighed = yarn.copy(quantity = 2.0, remainingWeightGrams = 180.0)
        val a = allocation("a", 1.0)
        val result = consume(weighed, a, listOf(a), 1.0, 0).getOrThrow()
        assertEquals(90.0, result.item.remainingWeightGrams!!, 0.0)
    }

    @Test
    fun estimatedLengthNeedsAllInputsAndIsRounded() {
        assertNull(yarn.estimatedRemainingYards())
        assertEquals(133.3, yarn.copy(remainingWeightGrams = 33.33).estimatedRemainingYards()!!, 0.0)
        assertNull(yarn.copy(remainingWeightGrams = 10.0, weightPerUnitGrams = 0.0).estimatedRemainingYards())
    }
}
