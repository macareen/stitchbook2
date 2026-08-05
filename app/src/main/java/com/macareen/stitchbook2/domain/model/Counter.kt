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
 * ROADMAP.md Phase 3 built this up incrementally: value/goal, then
 * increment/decrement/reset, then an outgoing link to another counter
 * ([linkedCounterId]), automatic reset on reaching goal ([autoResetOnGoal]),
 * and a repeating reset schedule ([repeatIntervalDays]). An active crafting
 * screen (Focus Mode's counters strip) and Focus-Mode-scoped persistent
 * notifications ([com.macareen.stitchbook2.data.notification.CounterFocusNotificationService])
 * were both built on top of this model without needing new fields here.
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
    val autoResetOnGoal: Boolean = false,
    /**
     * If set, this counter resets to 0 on its own every [repeatIntervalDays]
     * days (PRODUCT_SPEC.md 6.3, "Repeating schedules") -- e.g. a daily
     * practice-row counter. [lastRepeatResetAt] is the baseline the next
     * reset is measured from: it starts null (meaning [createdAt] is the
     * baseline) and is set to the actual reset time every time the
     * schedule fires. See [dueForRepeatingReset] for the exact rule.
     *
     * This app has no background-execution mechanism (no WorkManager or
     * AlarmManager usage anywhere), so a due schedule only actually fires
     * the next time the app checks for it (in practice, whenever Counters
     * loads) -- not at the exact scheduled moment in the background. Both
     * fields are null together (no schedule) or [repeatIntervalDays] is set
     * (a schedule exists, whether or not it has fired yet).
     */
    val repeatIntervalDays: Int? = null,
    val lastRepeatResetAt: Long? = null
)

fun normalizedCounterName(value: String): String? = value.trim().takeIf { it.isNotEmpty() }

/**
 * True if [counter]'s repeating schedule has been due for at least one
 * reset as of [now] -- i.e. at least [Counter.repeatIntervalDays] days have
 * passed since its baseline ([Counter.lastRepeatResetAt], or [Counter.createdAt]
 * if the schedule has never fired yet). False if there's no schedule
 * ([Counter.repeatIntervalDays] is null) or the interval is non-positive.
 */
fun dueForRepeatingReset(counter: Counter, now: Long): Boolean {
    val intervalDays = counter.repeatIntervalDays ?: return false
    if (intervalDays <= 0) return false
    val baseline = counter.lastRepeatResetAt ?: counter.createdAt
    val intervalMillis = intervalDays.toLong() * 24 * 60 * 60 * 1000
    return now - baseline >= intervalMillis
}

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
