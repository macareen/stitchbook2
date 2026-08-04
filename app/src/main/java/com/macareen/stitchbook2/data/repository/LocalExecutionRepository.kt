package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.data.database.ExecutionConflictException
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

    override suspend fun createExecution(
        guideId: GuideId,
        revisionId: DefinitionRevisionId
    ): PersistedExecution {
        return executionDao.createExecution(
            guideId = guideId.value,
            revisionId = revisionId.value,
            executionId = newId(),
            createdAt = currentTimeMillis()
        ).toDomain()
    }

    override suspend fun loadExecution(executionId: ExecutionId): PersistedExecution? {
        return executionDao.getExecutionAggregate(executionId.value)?.toDomain()
    }

    override suspend fun getActiveExecution(guideId: GuideId): PersistedExecution? {
        return executionDao.getActiveExecutionAggregate(guideId.value)?.toDomain()
    }

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
