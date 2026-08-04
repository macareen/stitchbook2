package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query(
        """
        SELECT * FROM projects
        ORDER BY
            CASE status
                WHEN 'ACTIVE' THEN 0
                WHEN 'PLANNED' THEN 1
                WHEN 'PAUSED' THEN 2
                WHEN 'COMPLETED' THEN 3
                WHEN 'ABANDONED' THEN 4
                ELSE 5
            END,
            updated_at DESC,
            name COLLATE NOCASE ASC,
            id ASC
        """
    )
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ProjectEntity?>

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}
