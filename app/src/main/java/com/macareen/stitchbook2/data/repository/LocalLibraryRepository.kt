package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.data.database.LibraryDao
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.data.database.toEntity
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalLibraryRepository(
    private val libraryDao: LibraryDao
) : LibraryRepository {

    override fun observeLibraryItems(): Flow<List<LibraryItem>> {
        return libraryDao.observeAll().map { items ->
            items.map { it.toDomain() }
        }
    }

    override fun observeLibraryItem(id: String): Flow<LibraryItem?> {
        return libraryDao.observeById(id).map { it?.toDomain() }
    }

    override suspend fun saveLibraryItem(item: LibraryItem) {
        libraryDao.upsert(item.toEntity())
    }

    override suspend fun deleteLibraryItem(item: LibraryItem) {
        libraryDao.delete(item.toEntity())
    }
}
