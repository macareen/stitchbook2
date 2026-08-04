package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.data.database.StashDao
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.data.database.toEntity
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.repository.StashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalStashRepository(
    private val stashDao: StashDao
) : StashRepository {

    override fun observeStashItems(): Flow<List<StashItem>> {
        return stashDao.observeAll().map { items ->
            items.map { it.toDomain() }
        }
    }

    override fun observeStashItem(id: String): Flow<StashItem?> {
        return stashDao.observeById(id).map { it?.toDomain() }
    }

    override suspend fun saveStashItem(item: StashItem) {
        stashDao.upsert(item.toEntity())
    }

    override suspend fun deleteStashItem(item: StashItem) {
        stashDao.delete(item.toEntity())
    }
}
