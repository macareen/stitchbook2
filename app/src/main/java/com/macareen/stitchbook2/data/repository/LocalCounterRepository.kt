package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.data.database.CounterDao
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.data.database.toEntity
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.repository.CounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalCounterRepository(
    private val counterDao: CounterDao
) : CounterRepository {

    override fun observeCounters(): Flow<List<Counter>> {
        return counterDao.observeAll().map { counters ->
            counters.map { it.toDomain() }
        }
    }

    override fun observeCountersByProject(projectId: String): Flow<List<Counter>> {
        return counterDao.observeByProjectId(projectId).map { counters ->
            counters.map { it.toDomain() }
        }
    }

    override fun observeCounter(id: String): Flow<Counter?> {
        return counterDao.observeById(id).map { it?.toDomain() }
    }

    override suspend fun saveCounter(counter: Counter) {
        counterDao.upsert(counter.toEntity())
    }

    override suspend fun deleteCounter(counter: Counter) {
        counterDao.delete(counter.toEntity())
    }
}
