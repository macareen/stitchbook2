package com.macareen.stitchbook2.domain.usecase

import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.repository.CounterRepository
import kotlinx.coroutines.CancellationException

/** Use only the UI identity: persisted state governs forward, link and goal-reset rules. */
class IncrementCounterUseCase(private val repository: CounterRepository) {
    suspend operator fun invoke(counter: Counter) {
        try {
            repository.incrementCounterWithRules(counter.id, System.currentTimeMillis())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Best effort, as before. The transaction preserves both values on failure.
        }
    }
}
