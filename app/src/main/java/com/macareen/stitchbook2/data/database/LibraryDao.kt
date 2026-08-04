package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query(
        """
        SELECT * FROM library_items
        ORDER BY updated_at DESC, title COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeAll(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<LibraryItemEntity?>

    @Upsert
    suspend fun upsert(item: LibraryItemEntity)

    @Delete
    suspend fun delete(item: LibraryItemEntity)
}
