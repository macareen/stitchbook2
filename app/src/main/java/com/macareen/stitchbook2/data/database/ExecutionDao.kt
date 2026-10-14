package com.macareen.stitchbook2.data.database

import com.macareen.stitchbook2.domain.execution.ReviewedRevisionChange
import com.macareen.stitchbook2.domain.execution.ReviewedSavedPlace
import com.macareen.stitchbook2.domain.execution.revisionChangeSeed
import com.macareen.stitchbook2.domain.execution.hasSameContentAs
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.macareen.stitchbook2.domain.execution.ExecutionAddress
import com.macareen.stitchbook2.domain.execution.ExecutionEngine
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.ExecutionState
import com.macareen.stitchbook2.domain.execution.ExecutionStatus
import com.macareen.stitchbook2.domain.execution.ExecutionTransitionResult

class ExecutionNotFoundException(
    executionId: String
) : IllegalStateException("Execution not found: $executionId")

class RevisionNotFoundException(
    revisionId: String
) : IllegalStateException("Definition revision not found: $revisionId")

class RevisionGuideMismatchException(
    revisionId: String,
    guideId: String
) : IllegalArgumentException(
    "Definition revision $revisionId does not belong to guide $guideId"
)

class ActiveExecutionAlreadyExistsException(
    guideId: String
) : IllegalStateException("Guide already has an active execution: $guideId")

class ExecutionConflictException(
    executionId: String
) : IllegalStateException("Execution changed before it could be updated: $executionId")

/** DAO-internal transport of one transition's result plus its fresh persisted state. */
data class ExecutionTransitionRow(
    val result: ExecutionTransitionResult,
    val aggregate: ExecutionAggregate
)

@Dao
abstract class ExecutionDao {

    // ---- Guide/Revision reads, needed only to rebuild the pure domain engine ----

    @Query("SELECT * FROM guides WHERE id = :guideId LIMIT 1")
    protected abstract suspend fun getGuideEntity(guideId: String): GuideEntity?

    @Query("SELECT * FROM definition_revisions WHERE id = :revisionId LIMIT 1")
    protected abstract suspend fun getRevisionEntity(
        revisionId: String
    ): DefinitionRevisionEntity?

    @Query("SELECT * FROM revision_nodes WHERE revision_id = :revisionId")
    protected abstract suspend fun getRevisionNodeEntities(
        revisionId: String
    ): List<RevisionNodeEntity>

    // ---- Execution reads ----

    @Query("SELECT * FROM executions WHERE id = :executionId LIMIT 1")
    protected abstract suspend fun getExecutionEntity(executionId: String): ExecutionEntity?

    @Query(
        """
        SELECT * FROM executions
        WHERE guide_id = :guideId
        ORDER BY created_at ASC, id ASC
        """
    )
    protected abstract suspend fun getExecutionEntitiesForGuide(
        guideId: String
    ): List<ExecutionEntity>

    @Query("SELECT execution_id FROM active_executions WHERE guide_id = :guideId AND project_key = :projectKey AND piece_key = :pieceKey LIMIT 1")
    protected abstract suspend fun getActiveExecutionId(guideId: String, projectKey: String, pieceKey: String = ""): String?

    /** Whether anyone (any project, or none) is part-way through [guideId]. */
    @Query("SELECT EXISTS(SELECT 1 FROM active_executions WHERE guide_id = :guideId)")
    abstract suspend fun hasAnyActiveExecution(guideId: String): Boolean

    @Query(
        """
        SELECT * FROM execution_current_address_frames
        WHERE execution_id = :executionId
        ORDER BY frame_order ASC
        """
    )
    protected abstract suspend fun getCurrentFrameEntities(
        executionId: String
    ): List<ExecutionCurrentAddressFrameEntity>

    @Query("SELECT * FROM execution_completed_occurrences WHERE execution_id = :executionId")
    protected abstract suspend fun getCompletedOccurrenceEntities(
        executionId: String
    ): List<ExecutionCompletedOccurrenceEntity>

    @Query(
        """
        SELECT * FROM execution_completed_occurrence_frames
        WHERE execution_id = :executionId
        ORDER BY address_signature ASC, frame_order ASC
        """
    )
    protected abstract suspend fun getCompletedOccurrenceFrameEntities(
        executionId: String
    ): List<ExecutionCompletedOccurrenceFrameEntity>

    // ---- Execution writes ----

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertExecutionEntity(entity: ExecutionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertActiveExecutionPointer(
        entity: ActiveExecutionEntity
    )

    @Query("DELETE FROM active_executions WHERE guide_id = :guideId AND project_key = :projectKey AND piece_key = :pieceKey")
    protected abstract suspend fun deleteActiveExecutionPointer(guideId: String, projectKey: String, pieceKey: String = "")

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCurrentFrameEntities(
        frames: List<ExecutionCurrentAddressFrameEntity>
    )

    @Query("DELETE FROM execution_current_address_frames WHERE execution_id = :executionId")
    protected abstract suspend fun deleteCurrentFrameEntities(executionId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCompletedOccurrenceEntities(
        rows: List<ExecutionCompletedOccurrenceEntity>
    )

    @Query("DELETE FROM execution_completed_occurrences WHERE execution_id = :executionId")
    protected abstract suspend fun deleteCompletedOccurrenceEntities(executionId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCompletedOccurrenceFrameEntities(
        rows: List<ExecutionCompletedOccurrenceFrameEntity>
    )

    @Query(
        """
        UPDATE executions
        SET status = :status,
            current_instruction_node_id = :currentInstructionNodeId,
            updated_at = :updatedAt,
            completed_at = :completedAt,
            version = :newVersion
        WHERE id = :executionId AND version = :expectedVersion
        """
    )
    protected abstract suspend fun updateExecutionEntity(
        executionId: String,
        status: String,
        currentInstructionNodeId: String?,
        updatedAt: Long,
        completedAt: Long?,
        expectedVersion: Long,
        newVersion: Long
    ): Int

    // ---- Aggregate loading ----

    @Transaction
    open suspend fun getExecutionAggregate(executionId: String): ExecutionAggregate? {
        val execution = getExecutionEntity(executionId) ?: return null
        return loadAggregate(execution)
    }

    @Transaction
    open suspend fun getActiveExecutionAggregate(guideId: String, projectId: String?): ExecutionAggregate? {
        val executionId = getActiveExecutionId(guideId, projectKey(projectId)) ?: return null
        return getExecutionAggregate(executionId)
    }

    @Query("SELECT * FROM executions WHERE guide_id = :guideId AND project_id = :projectId AND piece_id IS NULL ORDER BY created_at DESC, id DESC LIMIT 1")
    protected abstract suspend fun getLatestProjectExecutionEntity(guideId: String, projectId: String): ExecutionEntity?

    @Transaction
    open suspend fun getLatestProjectExecutionAggregate(guideId: String, projectId: String): ExecutionAggregate? =
        getLatestProjectExecutionEntity(guideId, projectId)?.let { loadAggregate(it) }

    @Transaction
    open suspend fun getExecutionAggregatesForGuide(
        guideId: String
    ): List<ExecutionAggregate> {
        return getExecutionEntitiesForGuide(guideId).map { loadAggregate(it) }
    }

    private suspend fun loadAggregate(execution: ExecutionEntity): ExecutionAggregate {
        val revision = getRevisionEntity(execution.definitionRevisionId)
            ?: throw MalformedPersistedExecutionStateException(
                "Execution ${execution.id} references missing revision " +
                    execution.definitionRevisionId
            )
        return ExecutionAggregate(
            execution = execution,
            currentFrames = getCurrentFrameEntities(execution.id),
            completedOccurrences = getCompletedOccurrenceEntities(execution.id),
            completedOccurrenceFrames = getCompletedOccurrenceFrameEntities(execution.id),
            revision = revision,
            revisionNodes = getRevisionNodeEntities(revision.id)
        )
    }

    @Query("SELECT (SELECT COUNT(*) FROM executions) + (SELECT COUNT(*) FROM project_pieces)")
    abstract fun observeWorkChangeCount(): kotlinx.coroutines.flow.Flow<Long>

    @Query("SELECT p.* FROM project_pieces p LEFT JOIN executions e ON e.piece_id = p.id WHERE p.project_id = :projectId GROUP BY p.id ORDER BY p.is_archived, p.created_at, p.id")
    abstract fun observePieces(projectId: String): kotlinx.coroutines.flow.Flow<List<ProjectPieceEntity>>

    @Query("SELECT * FROM project_pieces WHERE id = :id LIMIT 1")
    abstract suspend fun getPiece(id: String): ProjectPieceEntity?

    @Query("SELECT COUNT(*) FROM project_guides WHERE project_id = :projectId AND guide_id = :guideId")
    protected abstract suspend fun projectUsesGuide(projectId: String, guideId: String): Int

    @androidx.room.Insert
    protected abstract suspend fun insertPiece(piece: ProjectPieceEntity)

    @Transaction
    open suspend fun createPiece(piece: ProjectPieceEntity) {
        require(com.macareen.stitchbook2.domain.model.normalizedPieceName(piece.name) == piece.name) { "Enter a piece name of 1 to 100 characters." }
        val guide = getGuideEntity(piece.guideId) ?: throw GuideNotFoundException(piece.guideId)
        require(!guide.isArchived && (guide.projectId == piece.projectId ||
            (guide.projectId == null && projectUsesGuide(piece.projectId, piece.guideId) > 0))) { "Choose a guide used by this project." }
        insertPiece(piece)
    }

    @Query("""UPDATE project_pieces SET name = :name, is_archived = :archived, updated_at = :now
        WHERE id = :id AND project_id = :projectId AND guide_id = :guideId
        AND name = :oldName AND is_archived = :oldArchived AND updated_at = :oldUpdatedAt""")
    abstract suspend fun updatePiece(id: String, projectId: String, guideId: String, name: String,
        archived: Boolean, now: Long, oldName: String, oldArchived: Boolean, oldUpdatedAt: Long): Int

    @Transaction
    open suspend fun getActivePieceExecution(pieceId: String): ExecutionAggregate? {
        val piece = getPiece(pieceId) ?: return null
        val id = getActiveExecutionId(piece.guideId, piece.projectId, piece.id) ?: return null
        return getExecutionAggregate(id)
    }

    @Query("SELECT * FROM executions WHERE piece_id = :pieceId ORDER BY created_at DESC, id DESC LIMIT 1")
    protected abstract suspend fun getLatestPieceRow(pieceId: String): ExecutionEntity?

    @Transaction
    open suspend fun getLatestPieceExecution(pieceId: String): ExecutionAggregate? =
        getLatestPieceRow(pieceId)?.let { loadAggregate(it) }

    @Query("""SELECT e.* FROM executions e JOIN active_executions a ON a.execution_id = e.id
        LEFT JOIN project_pieces p ON p.id = e.piece_id
        WHERE e.guide_id = :guideId AND e.project_id = :projectId AND (p.id IS NULL OR p.is_archived = 0)
        ORDER BY e.updated_at DESC, e.id DESC LIMIT 1""")
    protected abstract suspend fun getActiveProjectWorkRow(guideId: String, projectId: String): ExecutionEntity?

    @Transaction
    open suspend fun getActiveProjectWork(guideId: String, projectId: String): ExecutionAggregate? =
        getActiveProjectWorkRow(guideId, projectId)?.let { loadAggregate(it) }

    // ---- Create ----

    @Transaction
    open suspend fun createExecution(
        guideId: String,
        revisionId: String,
        executionId: String,
        createdAt: Long,
        projectId: String? = null,
        pieceId: String? = null
    ): ExecutionAggregate {
        getGuideEntity(guideId) ?: throw GuideNotFoundException(guideId)
        if (pieceId != null) {
            val piece = requireNotNull(getPiece(pieceId)) { "This piece no longer exists." }
            require(piece.projectId == projectId && piece.guideId == guideId && !piece.isArchived) { "This piece is unavailable for this guide or project." }
        }
        val revision = getRevisionEntity(revisionId)
            ?: throw RevisionNotFoundException(revisionId)
        if (revision.guideId != guideId) {
            throw RevisionGuideMismatchException(revisionId, guideId)
        }
        if (getActiveExecutionId(guideId, projectKey(projectId), pieceId.orEmpty()) != null) {
            throw ActiveExecutionAlreadyExistsException(guideId)
        }

        val revisionNodes = getRevisionNodeEntities(revisionId)
        val definitionRevision = RevisionAggregate(revision, revisionNodes).toDomain()
        val engine = ExecutionEngine.forDefinition(definitionRevision.definition)
        val state = engine.newExecution(ExecutionId(executionId))

        insertExecutionEntity(
            state.toExecutionEntity(
                createdAt = createdAt,
                updatedAt = createdAt,
                completedAt = null,
                version = 0
            ).copy(projectId = projectId, pieceId = pieceId)
        )
        val currentFrames = state.toCurrentFrameEntities()
        if (currentFrames.isNotEmpty()) insertCurrentFrameEntities(currentFrames)
        insertActiveExecutionPointer(
            ActiveExecutionEntity(guideId = guideId, projectKey = projectKey(projectId), executionId = executionId, pieceKey = pieceId.orEmpty())
        )

        return ExecutionAggregate(
            execution = checkNotNull(getExecutionEntity(executionId)),
            currentFrames = currentFrames,
            completedOccurrences = emptyList(),
            completedOccurrenceFrames = emptyList(),
            revision = revision,
            revisionNodes = revisionNodes
        )
    }

    @Query("SELECT * FROM executions WHERE guide_id = :guideId AND project_id IS :projectId AND piece_id IS :pieceId ORDER BY created_at DESC, id DESC")
    protected abstract suspend fun getScopeRows(guideId: String, projectId: String?, pieceId: String?): List<ExecutionEntity>

    @Transaction
    open suspend fun getScopeExecutions(guideId: String, projectId: String?, pieceId: String?): List<ExecutionAggregate> =
        getScopeRows(guideId, projectId, pieceId).map { loadAggregate(it) }

    @Query("SELECT COUNT(*) FROM projects WHERE id = :id")
    protected abstract suspend fun scopeProjectExists(id: String): Int

    private suspend fun checkedCurrent(expected: com.macareen.stitchbook2.domain.execution.PersistedExecution,
        definition: com.macareen.stitchbook2.domain.execution.GuideDefinition, projectId: String?): ExecutionAggregate {
        val id = expected.state.executionId.value
        val entity = getExecutionEntity(id) ?: throw ExecutionConflictException(id)
        val aggregate = loadAggregate(entity)
        val actualDefinition = RevisionAggregate(aggregate.revision, aggregate.revisionNodes).toDomain().definition
        val pointer = getActiveExecutionId(entity.guideId, projectKey(projectId), entity.pieceId.orEmpty())
        val isCurrent = when (entity.status) {
            ExecutionStatus.ACTIVE.name -> pointer == id
            ExecutionStatus.COMPLETED.name -> pointer == null &&
                getScopeRows(entity.guideId, projectId, entity.pieceId).firstOrNull()?.id == id
            else -> false
        }
        if (entity.projectId != projectId || aggregate.toDomain() != expected ||
            !actualDefinition.hasSameContentAs(definition) || !isCurrent ||
            (projectId != null && scopeProjectExists(projectId) == 0) ||
            (entity.pieceId != null && getPiece(entity.pieceId)?.isArchived != false)) throw ExecutionConflictException(id)
        return aggregate
    }

    private suspend fun bumpVersion(entity: ExecutionEntity, now: Long) {
        if (updateExecutionEntity(entity.id, entity.status, entity.currentInstructionNodeId, now,
            entity.completedAt, entity.version, Math.addExact(entity.version, 1L)) != 1) throw ExecutionConflictException(entity.id)
    }

    @Transaction
    open suspend fun applyReviewedRevision(review: ReviewedRevisionChange,
        selected: Set<ExecutionAddress>, newId: String, now: Long): ExecutionAggregate {
        val current = checkedCurrent(review.source, review.sourceDefinition, review.projectId)
        val targetRevision = getRevisionEntity(review.targetDefinition.revisionId.value)
            ?: throw ExecutionConflictException(current.execution.id)
        val target = RevisionAggregate(targetRevision, getRevisionNodeEntities(targetRevision.id)).toDomain()
        if (targetRevision.guideId != current.execution.guideId ||
            targetRevision.revisionNumber != review.targetRevisionNumber ||
            current.revision.revisionNumber != review.sourceRevisionNumber ||
            !target.definition.hasSameContentAs(review.targetDefinition)) throw ExecutionConflictException(current.execution.id)
        val seed = revisionChangeSeed(review, selected, ExecutionId(newId))
        bumpVersion(current.execution, now)
        deleteActiveExecutionPointer(current.execution.guideId, projectKey(review.projectId), current.execution.pieceId.orEmpty())
        createExecution(current.execution.guideId, targetRevision.id, newId, now, review.projectId, current.execution.pieceId)
        return applyTransition(newId, 0, now) { _, _ -> ExecutionTransitionResult.Changed(seed) }.aggregate
    }

    @Transaction
    open suspend fun resumeReviewedPlace(review: ReviewedSavedPlace, now: Long): ExecutionAggregate {
        val current = checkedCurrent(review.current, review.currentDefinition, review.projectId)
        val saved = getExecutionEntity(review.saved.state.executionId.value)?.let { loadAggregate(it) }
            ?: throw ExecutionConflictException(current.execution.id)
        val savedDefinition = RevisionAggregate(saved.revision, saved.revisionNodes).toDomain().definition
        if (saved.execution.id == current.execution.id || saved.execution.guideId != current.execution.guideId ||
            saved.execution.projectId != current.execution.projectId || saved.execution.pieceId != current.execution.pieceId ||
            saved.execution.status != ExecutionStatus.ACTIVE.name || saved.toDomain() != review.saved ||
            !savedDefinition.hasSameContentAs(review.savedDefinition)) throw ExecutionConflictException(current.execution.id)
        bumpVersion(current.execution, now)
        bumpVersion(saved.execution, now)
        deleteActiveExecutionPointer(current.execution.guideId, projectKey(review.projectId), current.execution.pieceId.orEmpty())
        insertActiveExecutionPointer(ActiveExecutionEntity(current.execution.guideId, projectKey(review.projectId), saved.execution.id, current.execution.pieceId.orEmpty()))
        return requireNotNull(getExecutionAggregate(saved.execution.id))
    }

    // ---- Transitions ----

    @Transaction
    open suspend fun applyComplete(
        executionId: String,
        expectedVersion: Long,
        now: Long
    ): ExecutionTransitionRow {
        return applyTransition(executionId, expectedVersion, now) { engine, state ->
            engine.complete(state)
        }
    }

    @Transaction
    open suspend fun applyPrevious(
        executionId: String,
        expectedVersion: Long,
        now: Long
    ): ExecutionTransitionRow {
        return applyTransition(executionId, expectedVersion, now) { engine, state ->
            engine.previous(state)
        }
    }

    @Transaction
    open suspend fun applyJump(
        executionId: String,
        expectedVersion: Long,
        now: Long,
        targetAddress: ExecutionAddress
    ): ExecutionTransitionRow {
        return applyTransition(executionId, expectedVersion, now) { engine, state ->
            engine.jump(state, targetAddress)
        }
    }

    private suspend fun applyTransition(
        executionId: String,
        expectedVersion: Long,
        now: Long,
        transition: (ExecutionEngine, ExecutionState) -> ExecutionTransitionResult
    ): ExecutionTransitionRow {
        val execution = getExecutionEntity(executionId)
            ?: throw ExecutionNotFoundException(executionId)
        if (execution.version != expectedVersion ||
            (execution.status == ExecutionStatus.ACTIVE.name &&
                getActiveExecutionId(execution.guideId, projectKey(execution.projectId), execution.pieceId.orEmpty()) != executionId)) {
            throw ExecutionConflictException(executionId)
        }

        val aggregate = loadAggregate(execution)
        val persisted = aggregate.toDomain()
        val definitionRevision =
            RevisionAggregate(aggregate.revision, aggregate.revisionNodes).toDomain()
        val engine = ExecutionEngine.forDefinition(definitionRevision.definition)

        val result = transition(engine, persisted.state)
        if (result is ExecutionTransitionResult.NoChange) {
            return ExecutionTransitionRow(result = result, aggregate = aggregate)
        }

        val previousStatus = persisted.state.status
        val newState = result.state
        val newVersion = execution.version + 1
        val completedAt = if (newState.status == ExecutionStatus.COMPLETED) now else null
        val guideId = execution.guideId
        val key = projectKey(execution.projectId)

        val reopening = previousStatus == ExecutionStatus.COMPLETED &&
            newState.status == ExecutionStatus.ACTIVE
        if (reopening) {
            val activeExecutionId = getActiveExecutionId(guideId, key, execution.pieceId.orEmpty())
            if (activeExecutionId != null && activeExecutionId != executionId) {
                throw ActiveExecutionAlreadyExistsException(guideId)
            }
        }

        val updated = updateExecutionEntity(
            executionId = executionId,
            status = newState.status.name,
            currentInstructionNodeId = newState.currentAddress?.instructionNodeId?.value,
            updatedAt = now,
            completedAt = completedAt,
            expectedVersion = expectedVersion,
            newVersion = newVersion
        )
        if (updated != 1) throw ExecutionConflictException(executionId)

        deleteCurrentFrameEntities(executionId)
        val newCurrentFrames = newState.toCurrentFrameEntities()
        if (newCurrentFrames.isNotEmpty()) insertCurrentFrameEntities(newCurrentFrames)

        val existingSignatures = aggregate.completedOccurrences
            .map(ExecutionCompletedOccurrenceEntity::addressSignature)
            .toSet()
        val newOccurrenceEntities = newState.toCompletedOccurrenceEntities()
        val newSignatures = newOccurrenceEntities
            .map(ExecutionCompletedOccurrenceEntity::addressSignature)
            .toSet()
        if (existingSignatures != newSignatures) {
            deleteCompletedOccurrenceEntities(executionId)
            if (newOccurrenceEntities.isNotEmpty()) {
                insertCompletedOccurrenceEntities(newOccurrenceEntities)
                insertCompletedOccurrenceFrameEntities(
                    newState.toCompletedOccurrenceFrameEntities()
                )
            }
        }

        if (newState.status == ExecutionStatus.COMPLETED) {
            deleteActiveExecutionPointer(guideId, key, execution.pieceId.orEmpty())
        } else if (reopening) {
            insertActiveExecutionPointer(ActiveExecutionEntity(guideId, key, executionId, execution.pieceId.orEmpty()))
        }

        val updatedAggregate = ExecutionAggregate(
            execution = checkNotNull(getExecutionEntity(executionId)),
            currentFrames = getCurrentFrameEntities(executionId),
            completedOccurrences = getCompletedOccurrenceEntities(executionId),
            completedOccurrenceFrames = getCompletedOccurrenceFrameEntities(executionId),
            revision = aggregate.revision,
            revisionNodes = aggregate.revisionNodes
        )
        return ExecutionTransitionRow(result = result, aggregate = updatedAggregate)
    }

    /** The active-progress key for [projectId]: the project, or "" for a guide opened on its own. */
    protected fun projectKey(projectId: String?): String = projectId.orEmpty()
}
