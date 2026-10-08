package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CraftingSessionDao {

    @Query("SELECT * FROM crafting_sessions ORDER BY started_at DESC, id ASC")
    fun observeAll(): Flow<List<CraftingSessionEntity>>

    @Query("SELECT * FROM crafting_sessions WHERE project_id = :projectId ORDER BY started_at DESC, id ASC")
    fun observeForProject(projectId: String): Flow<List<CraftingSessionEntity>>

    @Query("SELECT * FROM crafting_sessions WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    fun observeActive(): Flow<CraftingSessionEntity?>

    @Upsert
    suspend fun upsert(session: CraftingSessionEntity)

    @Delete
    suspend fun delete(session: CraftingSessionEntity)
}
