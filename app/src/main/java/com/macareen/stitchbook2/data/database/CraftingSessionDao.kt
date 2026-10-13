package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import com.macareen.stitchbook2.domain.model.SessionTimer
import com.macareen.stitchbook2.domain.model.CraftingSessionSource
import com.macareen.stitchbook2.domain.repository.SessionStartResult
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CraftingSessionDao {

    @Query("SELECT * FROM crafting_sessions ORDER BY started_at DESC, id ASC")
    abstract fun observeAll(): Flow<List<CraftingSessionEntity>>

    @Query("SELECT * FROM crafting_sessions WHERE project_id = :projectId ORDER BY started_at DESC, id ASC")
    abstract fun observeForProject(projectId: String): Flow<List<CraftingSessionEntity>>

    @Query("SELECT * FROM crafting_sessions WHERE ended_at IS NULL ORDER BY started_at DESC, id ASC LIMIT 1")
    abstract fun observeActive(): Flow<CraftingSessionEntity?>

    @Query("SELECT * FROM crafting_sessions WHERE ended_at IS NULL")
    protected abstract suspend fun getUnfinished(): List<CraftingSessionEntity>

    @Query("SELECT * FROM crafting_sessions WHERE id = :id LIMIT 1")
    protected abstract suspend fun getById(id: String): CraftingSessionEntity?

    @Transaction
    open suspend fun startSession(session: CraftingSessionEntity, replaceActive: Boolean): SessionStartResult {
        val newSession = session.toDomain()
        require(newSession.isRunning && newSession.source == CraftingSessionSource.TIMED)
        require(getById(session.id) == null) { "A new timer cannot overwrite a recorded session." }
        val active = getUnfinished()
        if (!replaceActive && active.isNotEmpty()) return SessionStartResult(false)
        active.forEach { upsert(SessionTimer.stop(it.toDomain(), session.startedAt).toEntity()) }
        upsert(session)
        return SessionStartResult(true, active.map { it.projectId }.toSet())
    }

    @Transaction
    open suspend fun saveIfUnchanged(expected: CraftingSessionEntity, changed: CraftingSessionEntity): Boolean {
        require(expected.id == changed.id)
        if (getById(expected.id) != expected) return false
        upsert(changed)
        return true
    }

    @Upsert
    abstract suspend fun upsert(session: CraftingSessionEntity)

    @Delete
    abstract suspend fun delete(session: CraftingSessionEntity)
}
