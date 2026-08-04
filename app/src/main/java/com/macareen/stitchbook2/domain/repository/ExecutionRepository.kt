package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.ExecutionAddress
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.PersistedExecution
import com.macareen.stitchbook2.domain.execution.PersistedExecutionTransitionResult

/**
 * Thrown by [ExecutionRepository]'s transition methods when a caller's
 * `expectedVersion` no longer matches what is actually persisted -- i.e.
 * another transition committed first.
 *
 * This is the domain-facing contract for that failure. Implementations
 * must translate any storage-specific conflict exception into this type
 * before it crosses the repository boundary; callers (ViewModels in
 * particular) should depend only on this type, never on a concrete
 * exception defined in the data layer.
 */
class ExecutionVersionConflictException(
    executionId: ExecutionId
) : IllegalStateException(
    "Execution changed before it could be updated: ${executionId.value}"
)

/**
 * Persists [com.macareen.stitchbook2.domain.execution.ExecutionState] and
 * applies transitions to it.
 *
 * All transition semantics (Complete, Previous, Jump) are delegated to the
 * pure [com.macareen.stitchbook2.domain.execution.ExecutionEngine]; this
 * repository only loads persisted state into that engine's domain model,
 * applies the engine's result, and persists it atomically. It never
 * reimplements traversal or transition rules.
 */
interface ExecutionRepository {

    /**
     * Creates a new ACTIVE Execution for [guideId] pinned to [revisionId].
     *
     * [revisionId] must reference an existing, immutable Definition Revision
     * that belongs to [guideId]. The Execution is initialized using the
     * existing execution-engine initialization behavior (current pointer at
     * the first executable occurrence, no completed occurrences).
     *
     * Fails atomically, without side effects, if [guideId] or [revisionId]
     * does not exist, if [revisionId] does not belong to [guideId], if the
     * stored revision is not a valid executable definition, or if [guideId]
     * already has an ACTIVE Execution.
     */
    suspend fun createExecution(
        guideId: GuideId,
        revisionId: DefinitionRevisionId
    ): PersistedExecution

    /** Loads one Execution with its full persisted state, or null if absent. */
    suspend fun loadExecution(executionId: ExecutionId): PersistedExecution?

    /** Loads the single ACTIVE Execution for [guideId], or null if none. */
    suspend fun getActiveExecution(guideId: GuideId): PersistedExecution?

    /**
     * Lists every Execution ever created for [guideId] (ACTIVE and
     * COMPLETED), ordered oldest first.
     */
    suspend fun listExecutions(guideId: GuideId): List<PersistedExecution>

    /**
     * Applies the domain engine's Complete transition to [executionId].
     *
     * [expectedVersion] must match [PersistedExecution.version] as last
     * observed by the caller; a mismatch means another transition committed
     * first and this call fails rather than silently applying against
     * state the caller never saw, throwing [ExecutionVersionConflictException].
     */
    suspend fun applyComplete(
        executionId: ExecutionId,
        expectedVersion: Long
    ): PersistedExecutionTransitionResult

    /**
     * Applies the domain engine's Previous transition to [executionId].
     *
     * Throws [ExecutionVersionConflictException] if [expectedVersion] no
     * longer matches what is persisted.
     */
    suspend fun applyPrevious(
        executionId: ExecutionId,
        expectedVersion: Long
    ): PersistedExecutionTransitionResult

    /**
     * Applies the domain engine's Jump transition to [executionId].
     *
     * Throws [ExecutionVersionConflictException] if [expectedVersion] no
     * longer matches what is persisted.
     */
    suspend fun applyJump(
        executionId: ExecutionId,
        expectedVersion: Long,
        targetAddress: ExecutionAddress
    ): PersistedExecutionTransitionResult
}
