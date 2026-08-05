package com.macareen.stitchbook2.domain.model

/**
 * A note attached to a specific value a [Counter] passed through
 * (PRODUCT_SPEC.md 6.3, "Notes attached to particular values") -- e.g.
 * "Row 42: switched to smaller needles". [value] is a snapshot of whatever
 * the counter read when the note was written, not a live reference, so a
 * note's meaning survives the counter itself changing or resetting later.
 */
data class CounterNote(
    val id: String,
    val counterId: String,
    val value: Int,
    val note: String,
    val createdAt: Long
)
