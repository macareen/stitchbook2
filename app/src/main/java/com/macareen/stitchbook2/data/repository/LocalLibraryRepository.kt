package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.domain.model.PickedDocument
import com.macareen.stitchbook2.data.database.LibraryDao
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.data.database.toEntity
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.PatternRemovalPreview
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

    override suspend fun saveIfUnchanged(expected: LibraryItem, replacement: LibraryItem) =
        libraryDao.saveIfUnchanged(expected, replacement)

    override suspend fun deleteLibraryItem(item: LibraryItem) {
        libraryDao.delete(item.toEntity())
    }

    override suspend fun changePdfReviewed(expected: LibraryItem,
        document: PickedDocument?, now: Long) =
        libraryDao.changePdfReviewed(expected, document, now)

    override suspend fun previewRemoval(id: String) = libraryDao.previewRemoval(id)

    override suspend fun removeReviewed(preview: PatternRemovalPreview) = libraryDao.removeReviewed(preview)
}
