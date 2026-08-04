package com.macareen.stitchbook2.data.database

import com.macareen.stitchbook2.domain.execution.AncestryFrame
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.ExecutionAddress
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.ExecutionState
import com.macareen.stitchbook2.domain.execution.ExecutionStateValidator
import com.macareen.stitchbook2.domain.execution.ExecutionStatus
import com.macareen.stitchbook2.domain.execution.GuideDefinitionValidator
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.InvalidExecutionStateException
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.execution.PersistedExecution

private const val FRAME_TYPE_RANGE_VALUE = "RANGE_VALUE"
private const val FRAME_TYPE_REPEAT_ITERATION = "REPEAT_ITERATION"

class MalformedPersistedExecutionStateException(
    message: String
) : IllegalStateException(message)

/**
 * Everything needed to reconstruct one persisted Execution: its own row,
 * its current-address and completed-occurrence rows, and the immutable
 * Definition Revision (with its node rows) it is permanently pinned to.
 */
data class ExecutionAggregate(
    val execution: ExecutionEntity,
    val currentFrames: List<ExecutionCurrentAddressFrameEntity>,
    val completedOccurrences: List<ExecutionCompletedOccurrenceEntity>,
    val completedOccurrenceFrames: List<ExecutionCompletedOccurrenceFrameEntity>,
    val revision: DefinitionRevisionEntity,
    val revisionNodes: List<RevisionNodeEntity>
)

/**
 * A derived, persistence-only encoding of an [ExecutionAddress]'s
 * completed-occurrence identity — its Instruction Node ID and ordered
 * ancestry frames. (Definition Revision identity is deliberately not part
 * of this encoding: it is fixed once per Execution and stored on the
 * owning [ExecutionEntity] row, never duplicated per address.)
 *
 * This is never display text, never a list index, and never parsed back
 * into an [ExecutionAddress] anywhere in this codebase — the normalized
 * frame rows ([ExecutionCompletedOccurrenceEntity] /
 * [ExecutionCompletedOccurrenceFrameEntity]) remain the sole canonical
 * persisted representation. The signature exists only so the database can
 * enforce completed-address set uniqueness with an ordinary primary key
 * and so frame rows can be grouped back to their occurrence.
 *
 * Every variable-length component ([NodeId] values, in particular) is
 * length-prefixed (`"<length>:<value>"`) rather than joined with a fixed
 * delimiter. A [NodeId] is only required to be non-blank, so it may
 * legally contain `:`, `|`, `#`, digits, or anything else; a delimiter-only
 * join could let one field's content be misread as spanning a field
 * boundary. Because each component is preceded by its own exact character
 * count, decoding — if anything ever needed to — would always consume
 * precisely that many characters for that field before moving to the
 * next, regardless of what those characters are. That makes this encoding
 * an injective function of (instructionNodeId, ancestryFrames): distinct
 * inputs are guaranteed to produce distinct signatures.
 */
fun ExecutionAddress.toSignature(): String {
    val fields = buildList {
        add(instructionNodeId.value)
        add(ancestryFrames.size.toString())
        ancestryFrames.forEach { frame ->
            add(frame.frameType())
            add(frame.containerNodeId.value)
            add(frame.frameValue().toString())
        }
    }
    return fields.joinToString(separator = "") { field -> "${field.length}:$field" }
}

fun ExecutionAggregate.toDomain(): PersistedExecution {
    val definitionRevision = RevisionAggregate(revision, revisionNodes).toDomain()
    val validatedGuide = GuideDefinitionValidator.validate(definitionRevision.definition)

    val revisionId = DefinitionRevisionId(execution.definitionRevisionId)
    val currentAddress = execution.currentInstructionNodeId?.let { instructionNodeId ->
        ExecutionAddress(
            definitionRevisionId = revisionId,
            instructionNodeId = NodeId(instructionNodeId),
            ancestryFrames = currentFrames
                .sortedBy(ExecutionCurrentAddressFrameEntity::frameOrder)
                .map { it.toAncestryFrame() }
        )
    }

    val framesByOccurrence = completedOccurrenceFrames
        .groupBy(ExecutionCompletedOccurrenceFrameEntity::addressSignature)
        .mapValues { (_, frames) ->
            frames.sortedBy(ExecutionCompletedOccurrenceFrameEntity::frameOrder)
                .map { it.toAncestryFrame() }
        }

    val completedAddresses = completedOccurrences.map { occurrence ->
        ExecutionAddress(
            definitionRevisionId = revisionId,
            instructionNodeId = NodeId(occurrence.instructionNodeId),
            ancestryFrames = framesByOccurrence[occurrence.addressSignature].orEmpty()
        )
    }.toSet()

    val state = ExecutionState(
        executionId = ExecutionId(execution.id),
        guideId = GuideId(execution.guideId),
        definitionRevisionId = revisionId,
        currentAddress = currentAddress,
        completedAddresses = completedAddresses,
        status = execution.status.toExecutionStatus()
    )

    val validated = try {
        ExecutionStateValidator.validate(state, validatedGuide)
    } catch (error: InvalidExecutionStateException) {
        throw MalformedPersistedExecutionStateException(
            "Stored execution ${execution.id} is invalid: ${error.message}"
        )
    }

    return PersistedExecution(
        state = validated,
        version = execution.version,
        createdAt = execution.createdAt,
        updatedAt = execution.updatedAt,
        completedAt = execution.completedAt
    )
}

fun ExecutionState.toExecutionEntity(
    createdAt: Long,
    updatedAt: Long,
    completedAt: Long?,
    version: Long
) = ExecutionEntity(
    id = executionId.value,
    guideId = guideId.value,
    definitionRevisionId = definitionRevisionId.value,
    status = status.name,
    currentInstructionNodeId = currentAddress?.instructionNodeId?.value,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    version = version
)

fun ExecutionState.toCurrentFrameEntities(): List<ExecutionCurrentAddressFrameEntity> {
    val address = currentAddress ?: return emptyList()
    return address.ancestryFrames.mapIndexed { index, frame ->
        ExecutionCurrentAddressFrameEntity(
            executionId = executionId.value,
            frameOrder = index,
            containerNodeId = frame.containerNodeId.value,
            frameType = frame.frameType(),
            frameValue = frame.frameValue()
        )
    }
}

fun ExecutionState.toCompletedOccurrenceEntities(): List<ExecutionCompletedOccurrenceEntity> {
    return completedAddresses.map { address ->
        ExecutionCompletedOccurrenceEntity(
            executionId = executionId.value,
            addressSignature = address.toSignature(),
            instructionNodeId = address.instructionNodeId.value
        )
    }
}

fun ExecutionState.toCompletedOccurrenceFrameEntities(): List<ExecutionCompletedOccurrenceFrameEntity> {
    return completedAddresses.flatMap { address ->
        val signature = address.toSignature()
        address.ancestryFrames.mapIndexed { index, frame ->
            ExecutionCompletedOccurrenceFrameEntity(
                executionId = executionId.value,
                addressSignature = signature,
                frameOrder = index,
                containerNodeId = frame.containerNodeId.value,
                frameType = frame.frameType(),
                frameValue = frame.frameValue()
            )
        }
    }
}

private fun AncestryFrame.frameType(): String = when (this) {
    is AncestryFrame.RangeValue -> FRAME_TYPE_RANGE_VALUE
    is AncestryFrame.RepeatIteration -> FRAME_TYPE_REPEAT_ITERATION
}

private fun AncestryFrame.frameValue(): Int = when (this) {
    is AncestryFrame.RangeValue -> value
    is AncestryFrame.RepeatIteration -> iteration
}

private fun ExecutionCurrentAddressFrameEntity.toAncestryFrame(): AncestryFrame =
    buildAncestryFrame(containerNodeId, frameType, frameValue)

private fun ExecutionCompletedOccurrenceFrameEntity.toAncestryFrame(): AncestryFrame =
    buildAncestryFrame(containerNodeId, frameType, frameValue)

private fun buildAncestryFrame(
    containerNodeId: String,
    frameType: String,
    frameValue: Int
): AncestryFrame {
    val nodeId = NodeId(containerNodeId)
    return when (frameType) {
        FRAME_TYPE_RANGE_VALUE -> AncestryFrame.RangeValue(nodeId, frameValue)
        FRAME_TYPE_REPEAT_ITERATION -> AncestryFrame.RepeatIteration(nodeId, frameValue)
        else -> throw MalformedPersistedExecutionStateException(
            "Unknown ancestry frame type: $frameType"
        )
    }
}

private fun String.toExecutionStatus(): ExecutionStatus {
    return try {
        ExecutionStatus.valueOf(this)
    } catch (_: IllegalArgumentException) {
        throw MalformedPersistedExecutionStateException("Unknown execution status: $this")
    }
}
