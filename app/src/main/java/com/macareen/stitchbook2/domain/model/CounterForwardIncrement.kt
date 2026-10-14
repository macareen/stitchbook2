package com.macareen.stitchbook2.domain.model

/** One forward tap, evaluated against the persisted source before its optional reset. */
data class CounterForwardIncrement(val sourceValue: Int, val targetId: String?, val targetAmount: Int?)

fun Counter.forwardIncrement(): CounterForwardIncrement {
    val next = currentValue.toLong() + 1
    val reset = autoResetOnGoal && goal?.let { next >= it } == true
    val interval = linkIncrementInterval
    val linkDue = linkedCounterId != null && linkedCounterId != id && interval != null &&
        interval > 0 && linkIncrementAmount != null && next % interval == 0L
    return CounterForwardIncrement(
        if (reset) 0 else Math.toIntExact(next),
        linkedCounterId.takeIf { linkDue },
        linkIncrementAmount.takeIf { linkDue }
    )
}
