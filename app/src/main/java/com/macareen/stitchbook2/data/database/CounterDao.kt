package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {

    @Query(
        """
        SELECT * FROM counters
        ORDER BY updated_at DESC, name COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeAll(): Flow<List<CounterEntity>>

    @Query(
        """
        SELECT * FROM counters WHERE project_id = :projectId
        ORDER BY updated_at DESC, name COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeByProjectId(projectId: String): Flow<List<CounterEntity>>

    @Query("SELECT * FROM counters WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<CounterEntity?>

    @Upsert
    suspend fun upsert(counter: CounterEntity)

    @Delete
    suspend fun delete(counter: CounterEntity)
}
