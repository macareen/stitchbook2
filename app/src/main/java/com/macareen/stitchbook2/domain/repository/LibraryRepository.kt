package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.PatternRemovalPreview
import com.macareen.stitchbook2.domain.model.PatternRemovalResult
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeLibraryItems(): Flow<List<LibraryItem>>

    fun observeLibraryItem(id: String): Flow<LibraryItem?>

    suspend fun saveLibraryItem(item: LibraryItem)

    suspend fun deleteLibraryItem(item: LibraryItem)

    suspend fun previewRemoval(id: String): PatternRemovalPreview? =
        throw UnsupportedOperationException("Removal review is unavailable")

    /** Atomically checks the reviewed snapshot before removing only its Library index entry. */
    suspend fun removeReviewed(preview: PatternRemovalPreview): PatternRemovalResult =
        throw UnsupportedOperationException("Reviewed removal is unavailable")
}
