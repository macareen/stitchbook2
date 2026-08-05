package com.macareen.stitchbook2.domain.model

/**
 * A single manually-tracked count (PRODUCT_SPEC.md 6.3) -- a project's row
 * counter, a standalone stitch-marker-free repeat tracker, and so on.
 *
 * Unlike [StashCategory]/[ToolCategory], there is deliberately no closed
 * category enum here: PRODUCT_SPEC.md 6.3 explicitly wants counters for
 * "user-defined purposes" and labels that "support craft language such as
 * rows, rounds, motifs, squares, forward/return passes, pegs, panels, and
 * user-defined terms" -- an inherently open vocabulary, not a taxonomy to
 * validate against. [name] (what this counter is for, e.g. "Right Sleeve")
 * and [unitLabel] (what is being counted, e.g. "rows") are both free text.
 *
 * This is the persistence/data-model foundation only (ROADMAP.md Phase 3):
 * increment/decrement/reset actions, automatic reset rules, linked
 * behavior between counters, repeating schedules, and notifications are
 * later increments of the same phase, not represented here.
 */
data class Counter(
    val id: String,
    /** Null for a standalone counter; otherwise the Project this counter tracks progress for. */
    val projectId: String?,
    val name: String,
    val unitLabel: String,
    val currentValue: Int,
    val goal: Int?,
    val createdAt: Long,
    val updatedAt: Long
)

fun normalizedCounterName(value: String): String? = value.trim().takeIf { it.isNotEmpty() }
