package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.data.database.CraftingSessionDao
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.data.database.toEntity
import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalSessionRepository(
    private val dao: CraftingSessionDao
) : SessionRepository {

    override fun observeSessions(): Flow<List<CraftingSession>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeSessionsForProject(projectId: String): Flow<List<CraftingSession>> =
        dao.observeForProject(projectId).map { rows -> rows.map { it.toDomain() } }

    override fun observeActiveSession(): Flow<CraftingSession?> =
        dao.observeActive().map { it?.toDomain() }

    override suspend fun saveSession(session: CraftingSession) = dao.upsert(session.toEntity())

    override suspend fun deleteSession(session: CraftingSession) = dao.delete(session.toEntity())
}
