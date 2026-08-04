package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.LibraryItem
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeLibraryItems(): Flow<List<LibraryItem>>

    fun observeLibraryItem(id: String): Flow<LibraryItem?>

    suspend fun saveLibraryItem(item: LibraryItem)

    suspend fun deleteLibraryItem(item: LibraryItem)
}
