package com.macareen.stitchbook2.domain.model

/**
 * A reservation of part of a [StashItem] for a project (PRODUCT_SPEC.md 6.7,
 * "Allocated and unallocated quantities" / "Associated projects";
 * ARCHITECTURE.md §9's projects-yarn join entity).
 *
 * Quantities are in the stash item's own unit ([StashItem.unitLabel]), so
 * no conversion is ever implied. [quantityReserved] is still on hand in the
 * stash; [quantityUsed] is what has been consumed from this allocation and
 * already subtracted from [StashItem.quantity].
 */
data class YarnAllocation(
    val id: String,
    val projectId: String,
    val stashItemId: String,
    val quantityReserved: Double,
    val quantityUsed: Double,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/** On-hand quantity not reserved by any allocation. Never negative. */
fun unallocatedQuantity(item: StashItem, allocations: List<YarnAllocation>): Double {
    val reserved = allocations.filter { it.stashItemId == item.id }.sumOf { it.quantityReserved }
    return roundQuantity((item.quantity - reserved).coerceAtLeast(0.0))
}

enum class AllocationError {
    NOT_POSITIVE,
    EXCEEDS_UNALLOCATED,
    EXCEEDS_RESERVED_AND_ON_HAND
}

/**
 * Checks reserving [requested] units of [item]. [replacingAllocationId]
 * excludes an allocation being edited so its own current reservation
 * doesn't count against itself.
 */
fun validateReservation(
    item: StashItem,
    allocations: List<YarnAllocation>,
    requested: Double,
    replacingAllocationId: String? = null
): AllocationError? {
    if (requested.isNaN() || requested <= 0.0) return AllocationError.NOT_POSITIVE
    val others = allocations.filter { it.id != replacingAllocationId }
    if (requested > unallocatedQuantity(item, others) + QUANTITY_EPSILON) {
        return AllocationError.EXCEEDS_UNALLOCATED
    }
    return null
}

/** The new stash and allocation state after consuming some yarn. */
data class ConsumptionResult(
    val item: StashItem,
    val allocation: YarnAllocation
)

/**
 * Records [amount] units used by [allocation]'s project. Consumption draws
 * on the reservation first, and may exceed it only out of unreserved stock
 * -- but can never drive [StashItem.quantity] below zero (PRODUCT_SPEC.md
 * 6.7: "prevent impossible negative quantities"). A deliberate correction
 * is made by editing the stash item itself instead.
 */
fun consume(
    item: StashItem,
    allocation: YarnAllocation,
    allAllocations: List<YarnAllocation>,
    amount: Double,
    now: Long
): Result<ConsumptionResult> {
    if (amount.isNaN() || amount <= 0.0) {
        return Result.failure(AllocationException(AllocationError.NOT_POSITIVE))
    }
    // This allocation may use its own reservation plus any unreserved
    // stock: on hand minus what *other* allocations hold.
    val available = unallocatedQuantity(item, allAllocations.filter { it.id != allocation.id })
    if (amount > available + QUANTITY_EPSILON || amount > item.quantity + QUANTITY_EPSILON) {
        return Result.failure(AllocationException(AllocationError.EXCEEDS_RESERVED_AND_ON_HAND))
    }
    // Weighed partial-skein records are scaled down proportionally so an
    // estimate derived from them stays consistent with the new quantity.
    val newQuantity = roundQuantity((item.quantity - amount).coerceAtLeast(0.0))
    val newRemainingWeight = item.remainingWeightGrams?.let { weight ->
        if (item.quantity <= 0.0) weight else roundQuantity(weight * newQuantity / item.quantity)
    }
    return Result.success(
        ConsumptionResult(
            item = item.copy(quantity = newQuantity, remainingWeightGrams = newRemainingWeight, updatedAt = now),
            allocation = allocation.copy(
                quantityReserved = roundQuantity((allocation.quantityReserved - amount).coerceAtLeast(0.0)),
                quantityUsed = roundQuantity(allocation.quantityUsed + amount),
                updatedAt = now
            )
        )
    )
}

class AllocationException(val error: AllocationError) : IllegalArgumentException(error.name)

private const val QUANTITY_EPSILON = 1e-9

/** Explicit rounding (PRODUCT_SPEC.md 6.7) to 2 decimals, avoiding float drift like 0.30000000000000004. */
fun roundQuantity(value: Double): Double = Math.round(value * 100.0) / 100.0

/**
 * A pattern linked to a project (PRODUCT_SPEC.md 6.5: "One pattern may link
 * to multiple projects"). A pure membership record like
 * project-tool assignments.
 */
data class ProjectPatternLink(
    val projectId: String,
    val libraryItemId: String
)
