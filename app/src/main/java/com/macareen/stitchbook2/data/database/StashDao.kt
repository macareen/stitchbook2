package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StashDao {

    @Query(
        """
        SELECT * FROM stash_items
        ORDER BY updated_at DESC, name COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeAll(): Flow<List<StashItemEntity>>

    @Query("SELECT * FROM stash_items WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<StashItemEntity?>

    @Upsert
    suspend fun upsert(item: StashItemEntity)

    @Delete
    suspend fun delete(item: StashItemEntity)
}
