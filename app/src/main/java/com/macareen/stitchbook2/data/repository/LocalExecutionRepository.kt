package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.data.database.ExecutionConflictException
import kotlinx.coroutines.flow.map
import com.macareen.stitchbook2.data.database.ProjectPieceEntity
import com.macareen.stitchbook2.domain.model.ProjectPiece
import com.macareen.stitchbook2.domain.model.normalizedPieceName
import com.macareen.stitchbook2.data.database.ExecutionDao
import com.macareen.stitchbook2.data.database.ExecutionTransitionRow
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.ExecutionAddress
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.ExecutionTransitionResult
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.PersistedExecution
import com.macareen.stitchbook2.domain.execution.PersistedExecutionTransitionResult
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.ExecutionVersionConflictException
import java.util.UUID

class LocalExecutionRepository(
    private val executionDao: ExecutionDao,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : ExecutionRepository {

    override suspend fun listExecutionsForScope(guideId: GuideId, projectId: String?, pieceId: String?) =
        executionDao.getScopeExecutions(guideId.value, projectId, pieceId).map { it.toDomain() }

    override suspend fun applyReviewedRevision(review: com.macareen.stitchbook2.domain.execution.ReviewedRevisionChange,
        selected: Set<ExecutionAddress>): PersistedExecution = translatingVersionConflict(review.source.state.executionId) {
        executionDao.applyReviewedRevision(review, selected, newId(), currentTimeMillis()).toDomain()
    }

    override suspend fun resumeReviewedPlace(review: com.macareen.stitchbook2.domain.execution.ReviewedSavedPlace): PersistedExecution =
        translatingVersionConflict(review.current.state.executionId) { executionDao.resumeReviewedPlace(review, currentTimeMillis()).toDomain() }

    override fun observeWorkChanges() = executionDao.observeWorkChangeCount().map { Unit }
    override fun observePieces(projectId: String) = executionDao.observePieces(projectId).map { rows -> rows.map { it.toDomain() } }
    override suspend fun loadPiece(pieceId: String) = executionDao.getPiece(pieceId)?.toDomain()
    override suspend fun createPiece(projectId: String, guideId: GuideId, name: String): ProjectPiece {
        val now = currentTimeMillis()
        val piece = ProjectPieceEntity(newId(), projectId, guideId.value,
            requireNotNull(normalizedPieceName(name)), false, now, now)
        executionDao.createPiece(piece)
        return piece.toDomain()
    }
    override suspend fun updatePiece(expected: ProjectPiece, name: String, archived: Boolean): Boolean =
        executionDao.updatePiece(expected.id, expected.projectId, expected.guideId,
            requireNotNull(normalizedPieceName(name)), archived, currentTimeMillis(), expected.name,
            expected.isArchived, expected.updatedAt) == 1

    override suspend fun createPieceExecution(pieceId: String, revisionId: DefinitionRevisionId): PersistedExecution {
        val piece = requireNotNull(executionDao.getPiece(pieceId))
        return executionDao.createExecution(piece.guideId, revisionId.value, newId(), currentTimeMillis(), piece.projectId, piece.id).toDomain()
    }
    override suspend fun getActivePieceExecution(pieceId: String) = executionDao.getActivePieceExecution(pieceId)?.toDomain()
    override suspend fun getLatestPieceExecution(pieceId: String) = executionDao.getLatestPieceExecution(pieceId)?.toDomain()

    override suspend fun getActiveProjectWork(guideId: GuideId, projectId: String) = executionDao.getActiveProjectWork(guideId.value, projectId)?.toDomain()

    override suspend fun createExecution(
        guideId: GuideId,
        revisionId: DefinitionRevisionId,
        projectId: String?
    ): PersistedExecution {
        return executionDao.createExecution(
            guideId = guideId.value,
            revisionId = revisionId.value,
            executionId = newId(),
            createdAt = currentTimeMillis(),
            projectId = projectId
        ).toDomain()
    }

    override suspend fun loadExecution(executionId: ExecutionId): PersistedExecution? {
        return executionDao.getExecutionAggregate(executionId.value)?.toDomain()
    }

    override suspend fun getActiveExecution(guideId: GuideId, projectId: String?): PersistedExecution? {
        return executionDao.getActiveExecutionAggregate(guideId.value, projectId)?.toDomain()
    }

    override suspend fun getLatestExecutionForProject(guideId: GuideId, projectId: String): PersistedExecution? =
        (executionDao.getActiveExecutionAggregate(guideId.value, projectId) ?: executionDao.getLatestProjectExecutionAggregate(guideId.value, projectId))?.toDomain()

    override suspend fun hasActiveExecutionAnywhere(guideId: GuideId): Boolean =
        executionDao.hasAnyActiveExecution(guideId.value)

    override suspend fun listExecutions(guideId: GuideId): List<PersistedExecution> {
        return executionDao.getExecutionAggregatesForGuide(guideId.value)
            .map { it.toDomain() }
    }

    override suspend fun applyComplete(
        executionId: ExecutionId,
        expectedVersion: Long
    ): PersistedExecutionTransitionResult = translatingVersionConflict(executionId) {
        executionDao.applyComplete(
            executionId = executionId.value,
            expectedVersion = expectedVersion,
            now = currentTimeMillis()
        ).toDomainResult()
    }

    override suspend fun applyPrevious(
        executionId: ExecutionId,
        expectedVersion: Long
    ): PersistedExecutionTransitionResult = translatingVersionConflict(executionId) {
        executionDao.applyPrevious(
            executionId = executionId.value,
            expectedVersion = expectedVersion,
            now = currentTimeMillis()
        ).toDomainResult()
    }

    override suspend fun applyJump(
        executionId: ExecutionId,
        expectedVersion: Long,
        targetAddress: ExecutionAddress
    ): PersistedExecutionTransitionResult = translatingVersionConflict(executionId) {
        executionDao.applyJump(
            executionId = executionId.value,
            expectedVersion = expectedVersion,
            now = currentTimeMillis(),
            targetAddress = targetAddress
        ).toDomainResult()
    }

    /**
     * Translates the storage-specific [ExecutionConflictException] into the
     * domain-facing [ExecutionVersionConflictException] so callers outside
     * the data layer never need to depend on a `data.database` type.
     */
    private inline fun <T> translatingVersionConflict(
        executionId: ExecutionId,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (_: ExecutionConflictException) {
            throw ExecutionVersionConflictException(executionId)
        }
    }

    private fun ExecutionTransitionRow.toDomainResult(): PersistedExecutionTransitionResult {
        val persisted = aggregate.toDomain()
        return when (val transition = result) {
            is ExecutionTransitionResult.Changed ->
                PersistedExecutionTransitionResult.Changed(persisted)

            is ExecutionTransitionResult.NoChange ->
                PersistedExecutionTransitionResult.NoChange(persisted, transition.reason)
        }
    }
}
