package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.model.SessionTimer
import com.macareen.stitchbook2.domain.model.CraftingSessionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeSessions(): Flow<List<CraftingSession>>

    fun observeSessionsForProject(projectId: String): Flow<List<CraftingSession>>

    /** The single running or paused session, if any. */
    fun observeActiveSession(): Flow<CraftingSession?>

    /** Start only while idle, or explicitly replace all unfinished timers.
     * Persistent implementations must check and write in one transaction.
     */
    suspend fun startSession(session: CraftingSession, replaceActive: Boolean = false): SessionStartResult {
        require(session.isRunning && session.source == CraftingSessionSource.TIMED)
        val recorded = observeSessions().first()
        require(recorded.none { it.id == session.id })
        val active = recorded.filter { it.isActive }
        if (!replaceActive && active.isNotEmpty()) return SessionStartResult(false)
        active.forEach { saveSession(SessionTimer.stop(it, session.startedAt)) }
        saveSession(session)
        return SessionStartResult(true, active.map { it.projectId }.toSet())
    }

    /** Compare the entire record; stale reviews must not overwrite a newer correction or timer action. */
    suspend fun saveIfUnchanged(expected: CraftingSession, changed: CraftingSession): Boolean {
        require(expected.id == changed.id)
        if (observeSessions().first().firstOrNull { it.id == expected.id } != expected) return false
        saveSession(changed)
        return true
    }

    suspend fun saveSession(session: CraftingSession)

    suspend fun deleteSession(session: CraftingSession)
}

data class SessionStartResult(val started: Boolean, val stoppedProjectIds: Set<String?> = emptySet())
