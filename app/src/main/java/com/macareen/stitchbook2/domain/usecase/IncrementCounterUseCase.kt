package com.macareen.stitchbook2.domain.usecase

import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.repository.CounterRepository
import kotlinx.coroutines.CancellationException

/**
 * Increments a counter by one, then applies its two independent
 * increment-only behaviors on top of that (PRODUCT_SPEC.md 6.3): bumping a
 * linked counter every N increments, and auto-resetting back to 0 once a
 * goal is reached. Decrementing or manually resetting a counter never
 * triggers either behavior -- only this use case's forward increment does.
 *
 * This spans a second counter's write (the link target) plus real
 * branching (is a link due, is the goal reached), so it is a use case per
 * ARCHITECTURE.md 4 rather than logic duplicated in every screen that
 * offers an increment action -- both the Counters screen and Focus Mode's
 * active-crafting counters section call this same instance.
 */
class IncrementCounterUseCase(
    private val repository: CounterRepository
) {
    suspend operator fun invoke(counter: Counter) {
        val newValue = counter.currentValue + 1
        // The link's "every N increments" interval is evaluated against
        // newValue itself, before any auto-reset below -- reaching the
        // goal and resetting back to 0 must never interfere with that
        // count; the two rules act independently on the same increment.
        triggerLinkIfDue(counter, newValue)
        val goalReached = counter.autoResetOnGoal && counter.goal?.let { newValue >= it } == true
        val finalValue = if (goalReached) 0 else newValue
        // Always persists, even if finalValue happens to equal the
        // original currentValue (e.g. goal=1 with auto-reset enabled,
        // incrementing from 0 straight back to 0) -- every tap is a real
        // user action that should update updatedAt, not a redundant write
        // to skip.
        persist(counter.copy(currentValue = finalValue, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun triggerLinkIfDue(counter: Counter, newValue: Int) {
        val targetId = counter.linkedCounterId ?: return
        val interval = counter.linkIncrementInterval ?: return
        val amount = counter.linkIncrementAmount ?: return
        if (interval <= 0 || newValue % interval != 0) return
        try {
            // An atomic SQL increment, not a read-then-write: two counters
            // linked to the same target (or two rapid-fire triggers) can
            // never clobber each other's update.
            repository.incrementCounterValue(targetId, amount, System.currentTimeMillis())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Best-effort: the list re-renders from persisted state on the
            // next emission either way.
        }
    }

    private suspend fun persist(counter: Counter) {
        try {
            repository.saveCounter(counter)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Best-effort, same rationale as triggerLinkIfDue above.
        }
    }
}
