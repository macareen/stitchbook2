package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.Counter
import kotlinx.coroutines.flow.Flow

interface CounterRepository {
    fun observeCounters(): Flow<List<Counter>>

    fun observeCountersByProject(projectId: String): Flow<List<Counter>>

    fun observeCounter(id: String): Flow<Counter?>

    suspend fun saveCounter(counter: Counter)

    suspend fun deleteCounter(counter: Counter)
}
