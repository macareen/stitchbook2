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
    val updatedAt: Long,
    /**
     * A counter's optional single outgoing link (PRODUCT_SPEC.md 6.3, "Linked
     * behavior between counters"): every [linkIncrementInterval] increments of
     * *this* counter, the counter at [linkedCounterId] is incremented by
     * [linkIncrementAmount]. All three are null together (no link) or
     * non-null together (a configured link); decrementing or resetting this
     * counter never triggers the link -- only forward increments do. See
     * [wouldCreateCycle] for the invariant every write path must check before
     * saving a link.
     */
    val linkedCounterId: String?,
    val linkIncrementInterval: Int?,
    val linkIncrementAmount: Int?,
    /**
     * If true and [goal] is set, reaching the goal via increment
     * automatically resets [currentValue] back to 0 in the same step --
     * the common "row counter resets each repeat" pattern (PRODUCT_SPEC.md
     * 6.3, "Automatic reset rules"). Like the outgoing link above, this
     * only ever fires on a forward increment, never on decrement or a
     * manual reset. A counter's own auto-reset and its outgoing link are
     * independent: both are evaluated against the same increment, so a
     * counter can bump a linked target *and* auto-reset on the same tap.
     */
    val autoResetOnGoal: Boolean = false
)

fun normalizedCounterName(value: String): String? = value.trim().takeIf { it.isNotEmpty() }

/**
 * True if pointing [editingCounterId]'s link at [proposedTargetId] would
 * create a cycle -- either directly (a counter linking to itself) or
 * transitively through [proposedTargetId]'s own chain of outgoing links
 * eventually reaching back to [editingCounterId]. Since each counter has at
 * most one outgoing link, that chain is a simple walk, not a general graph
 * traversal. [editingCounterId] is null when creating a brand-new counter,
 * which can never complete a cycle since nothing can point at it yet.
 */
fun wouldCreateCycle(
    counters: List<Counter>,
    editingCounterId: String?,
    proposedTargetId: String?
): Boolean {
    if (editingCounterId == null || proposedTargetId == null) return false
    if (proposedTargetId == editingCounterId) return true

    val countersById = counters.associateBy { it.id }
    val visited = mutableSetOf<String>()
    var currentId: String? = proposedTargetId
    while (currentId != null && visited.add(currentId)) {
        if (currentId == editingCounterId) return true
        currentId = countersById[currentId]?.linkedCounterId
    }
    return false
}
