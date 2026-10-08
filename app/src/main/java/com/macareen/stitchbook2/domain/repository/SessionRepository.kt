package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.CraftingSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeSessions(): Flow<List<CraftingSession>>

    fun observeSessionsForProject(projectId: String): Flow<List<CraftingSession>>

    /** The single running or paused session, if any. */
    fun observeActiveSession(): Flow<CraftingSession?>

    suspend fun saveSession(session: CraftingSession)

    suspend fun deleteSession(session: CraftingSession)
}
