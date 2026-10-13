package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import com.macareen.stitchbook2.domain.model.SessionTimer
import com.macareen.stitchbook2.domain.model.ProjectStatus
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

    @Query("SELECT status FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun storedStatus(projectId: String): String?

    @Query("SELECT * FROM crafting_sessions WHERE project_id = :projectId AND ended_at IS NULL")
    suspend fun openSessionsForProject(projectId: String): List<CraftingSessionEntity>

    @Upsert
    suspend fun upsertFinalizedSessions(sessions: List<CraftingSessionEntity>)

    /** Project status and its open sessions commit together; the domain timer owns pause accounting. */
    @Transaction
    suspend fun upsertAndFinalizeSessions(project: ProjectEntity, now: Long) {
        val wasCompleted = storedStatus(project.id) == ProjectStatus.COMPLETED.storageValue
        upsert(project)
        if (wasCompleted) return
        val open = openSessionsForProject(project.id)
        if (open.isNotEmpty()) upsertFinalizedSessions(open.map { SessionTimer.stop(it.toDomain(), now).toEntity() })
    }

    @Delete
    suspend fun delete(project: ProjectEntity)
}
