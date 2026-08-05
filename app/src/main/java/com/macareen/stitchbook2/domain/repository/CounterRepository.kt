package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.Counter
import kotlinx.coroutines.flow.Flow

interface CounterRepository {
    fun observeCounters(): Flow<List<Counter>>

    fun observeCountersByProject(projectId: String): Flow<List<Counter>>

    fun observeCounter(id: String): Flow<Counter?>

    suspend fun saveCounter(counter: Counter)

    /**
     * Atomically adds [amount] to the counter at [id]'s current value
     * without a separate read first, so two concurrent callers targeting
     * the same counter can never lose one of their updates. A no-op if
     * [id] doesn't match any counter (e.g. it was deleted).
     */
    suspend fun incrementCounterValue(id: String, amount: Int, updatedAt: Long)

    suspend fun deleteCounter(counter: Counter)
}
