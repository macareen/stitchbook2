package com.macareen.stitchbook2.data.database

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

    @Query("SELECT execution_id FROM active_executions WHERE guide_id = :guideId LIMIT 1")
    protected abstract suspend fun getActiveExecutionId(guideId: String): String?

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

    @Query("DELETE FROM active_executions WHERE guide_id = :guideId")
    protected abstract suspend fun deleteActiveExecutionPointer(guideId: String)

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
    open suspend fun getActiveExecutionAggregate(guideId: String): ExecutionAggregate? {
        val executionId = getActiveExecutionId(guideId) ?: return null
        return getExecutionAggregate(executionId)
    }

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

    // ---- Create ----

    @Transaction
    open suspend fun createExecution(
        guideId: String,
        revisionId: String,
        executionId: String,
        createdAt: Long
    ): ExecutionAggregate {
        getGuideEntity(guideId) ?: throw GuideNotFoundException(guideId)
        val revision = getRevisionEntity(revisionId)
            ?: throw RevisionNotFoundException(revisionId)
        if (revision.guideId != guideId) {
            throw RevisionGuideMismatchException(revisionId, guideId)
        }
        if (getActiveExecutionId(guideId) != null) {
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
            )
        )
        val currentFrames = state.toCurrentFrameEntities()
        if (currentFrames.isNotEmpty()) insertCurrentFrameEntities(currentFrames)
        insertActiveExecutionPointer(
            ActiveExecutionEntity(guideId = guideId, executionId = executionId)
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
        if (execution.version != expectedVersion) {
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

        val reopening = previousStatus == ExecutionStatus.COMPLETED &&
            newState.status == ExecutionStatus.ACTIVE
        if (reopening) {
            val activeExecutionId = getActiveExecutionId(guideId)
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
            deleteActiveExecutionPointer(guideId)
        } else if (reopening) {
            insertActiveExecutionPointer(ActiveExecutionEntity(guideId, executionId))
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
}
