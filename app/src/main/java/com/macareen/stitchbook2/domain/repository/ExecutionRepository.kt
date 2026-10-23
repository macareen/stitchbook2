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
    suspend fun observeTarget(executionId: ExecutionId, expectedVersion: Long, value: Double): PersistedExecutionTransitionResult = throw UnsupportedOperationException()
    suspend fun confirmTarget(executionId: ExecutionId, expectedVersion: Long): PersistedExecutionTransitionResult = throw UnsupportedOperationException()

    suspend fun adjustCastOn(executionId: ExecutionId, expectedVersion: Long, amount: Int): PersistedExecutionTransitionResult =
        throw UnsupportedOperationException("Cast-on tracking unavailable")


    suspend fun listExecutionsForScope(guideId: GuideId, projectId: String?, pieceId: String?): List<PersistedExecution> = emptyList()
    suspend fun applyReviewedRevision(review: com.macareen.stitchbook2.domain.execution.ReviewedRevisionChange,
        selected: Set<ExecutionAddress>): PersistedExecution = throw UnsupportedOperationException()
    suspend fun resumeReviewedPlace(review: com.macareen.stitchbook2.domain.execution.ReviewedSavedPlace): PersistedExecution = throw UnsupportedOperationException()

    fun observeWorkChanges(): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.flowOf(Unit)
    fun observePieces(projectId: String): kotlinx.coroutines.flow.Flow<List<com.macareen.stitchbook2.domain.model.ProjectPiece>> = kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun loadPiece(pieceId: String): com.macareen.stitchbook2.domain.model.ProjectPiece? = null
    suspend fun createPiece(projectId: String, guideId: GuideId, name: String): com.macareen.stitchbook2.domain.model.ProjectPiece = throw UnsupportedOperationException()
    /** Creates all named pieces in one transaction, or none. No executions are copied or started. */
    suspend fun createPieces(projectId: String, guideId: GuideId, names: List<String>): List<com.macareen.stitchbook2.domain.model.ProjectPiece> = throw UnsupportedOperationException()
    /** Rechecks the source metadata and guide eligibility; creates a fresh, unstarted piece. */
    suspend fun copyPiece(expected: com.macareen.stitchbook2.domain.model.ProjectPiece, name: String): com.macareen.stitchbook2.domain.model.ProjectPiece = throw UnsupportedOperationException()
    suspend fun updatePiece(expected: com.macareen.stitchbook2.domain.model.ProjectPiece, name: String, archived: Boolean): Boolean = throw UnsupportedOperationException()
    suspend fun createPieceExecution(pieceId: String, revisionId: DefinitionRevisionId): PersistedExecution = throw UnsupportedOperationException()
    suspend fun getActivePieceExecution(pieceId: String): PersistedExecution? = null
    suspend fun getLatestPieceExecution(pieceId: String): PersistedExecution? = null
    suspend fun getActiveProjectWork(guideId: GuideId, projectId: String): PersistedExecution? = getActiveExecution(guideId, projectId)

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
     * already has an ACTIVE Execution for the same [projectId].
     *
     * [projectId] is the project the knitting belongs to (null when the guide
     * is opened on its own), so one guide used by two projects keeps a
     * separate place in each.
     */
    suspend fun createExecution(
        guideId: GuideId,
        revisionId: DefinitionRevisionId,
        projectId: String? = null
    ): PersistedExecution

    /** Loads one Execution with its full persisted state, or null if absent. */
    suspend fun loadExecution(executionId: ExecutionId): PersistedExecution?

    /** Loads the ACTIVE Execution for [guideId] in [projectId] (null: opened on its own), or null if none. */
    suspend fun getActiveExecution(guideId: GuideId, projectId: String? = null): PersistedExecution?

    /** Current selected work for this project, otherwise its most recent history, including completed executions. */
    suspend fun getLatestExecutionForProject(guideId: GuideId, projectId: String): PersistedExecution? =
        getActiveExecution(guideId, projectId)

    /** Whether anyone, in any project or none, is part-way through [guideId]. */
    suspend fun hasActiveExecutionAnywhere(guideId: GuideId): Boolean = getActiveExecution(guideId) != null

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
