package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.PatternPdfChangeResult
import com.macareen.stitchbook2.domain.model.PickedDocument
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.PatternRemovalPreview
import com.macareen.stitchbook2.domain.model.PatternRemovalResult
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeLibraryItems(): Flow<List<LibraryItem>>

    fun observeLibraryItem(id: String): Flow<LibraryItem?>

    suspend fun saveLibraryItem(item: LibraryItem)

    /** Updates an existing record only if the full snapshot still matches; never recreates a removed item. */
    suspend fun saveIfUnchanged(expected: LibraryItem, replacement: LibraryItem): Boolean =
        throw UnsupportedOperationException("Reviewed pattern editing is unavailable")

    suspend fun deleteLibraryItem(item: LibraryItem)

    /** Checks the full reviewed pattern atomically; null document unlinks only the PDF. */
    suspend fun changePdfReviewed(
        expected: LibraryItem,
        document: PickedDocument?,
        now: Long
    ): PatternPdfChangeResult =
        throw UnsupportedOperationException("Reviewed PDF changes are unavailable")

    suspend fun previewRemoval(id: String): PatternRemovalPreview? =
        throw UnsupportedOperationException("Removal review is unavailable")

    /** Atomically checks the reviewed snapshot before removing only its Library index entry. */
    suspend fun removeReviewed(preview: PatternRemovalPreview): PatternRemovalResult =
        throw UnsupportedOperationException("Reviewed removal is unavailable")
}
