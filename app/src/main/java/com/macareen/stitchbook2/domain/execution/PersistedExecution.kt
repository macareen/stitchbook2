package com.macareen.stitchbook2.domain.execution

/**
 * A persisted [ExecutionState] plus the storage metadata needed for
 * optimistic concurrency and history display.
 *
 * The [state] itself is exactly the pure domain model produced and consumed
 * by [ExecutionEngine]; persistence adds no fields to it beyond [version]
 * (for optimistic concurrency, mirroring `GuideDraft.version`) and the
 * timestamps below.
 */
data class PersistedExecution(
    val state: ExecutionState,
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?
)

/**
 * Repository-level mirror of [ExecutionTransitionResult] that additionally
 * carries the persisted [PersistedExecution] (including its bumped
 * [PersistedExecution.version]) rather than a bare [ExecutionState].
 *
 * A [NoChange] result is never written to storage: [PersistedExecution.version]
 * is unchanged from the value read at the start of the transition.
 */
sealed interface PersistedExecutionTransitionResult {
    val execution: PersistedExecution

    data class Changed(
        override val execution: PersistedExecution
    ) : PersistedExecutionTransitionResult

    data class NoChange(
        override val execution: PersistedExecution,
        val reason: NoChangeReason
    ) : PersistedExecutionTransitionResult
}
