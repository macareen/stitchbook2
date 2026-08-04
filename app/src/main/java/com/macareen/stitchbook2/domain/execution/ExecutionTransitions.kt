package com.macareen.stitchbook2.domain.execution

sealed interface ExecutionTransitionResult {
    val state: ExecutionState

    data class Changed(
        override val state: ExecutionState
    ) : ExecutionTransitionResult

    data class NoChange(
        override val state: ExecutionState,
        val reason: NoChangeReason
    ) : ExecutionTransitionResult
}

enum class NoChangeReason {
    ALREADY_COMPLETE,
    ALREADY_AT_FIRST_OCCURRENCE,
    ALREADY_AT_TARGET
}

class ExecutionEngine private constructor(
    private val guide: ValidatedGuideDefinition
) {
    private val traversal = GuideTraversal(guide)

    fun newExecution(executionId: ExecutionId): ExecutionState {
        return ExecutionState(
            executionId = executionId,
            guideId = guide.definition.guideId,
            definitionRevisionId = guide.definition.revisionId,
            currentAddress = traversal.first().address,
            completedAddresses = emptySet(),
            status = ExecutionStatus.ACTIVE
        )
    }

    fun complete(state: ExecutionState): ExecutionTransitionResult {
        validate(state)
        if (state.status == ExecutionStatus.COMPLETED) {
            return ExecutionTransitionResult.NoChange(
                state = state,
                reason = NoChangeReason.ALREADY_COMPLETE
            )
        }

        val currentAddress = checkNotNull(state.currentAddress)
        val completedAddresses = state.completedAddresses + currentAddress
        val nextAddress = nextIncompleteAddress(
            currentAddress = currentAddress,
            completedAddresses = completedAddresses
        )
        val updatedState = state.copy(
            currentAddress = nextAddress,
            completedAddresses = completedAddresses,
            status = if (nextAddress == null) {
                ExecutionStatus.COMPLETED
            } else {
                ExecutionStatus.ACTIVE
            }
        )
        validate(updatedState)
        return ExecutionTransitionResult.Changed(updatedState)
    }

    fun previous(state: ExecutionState): ExecutionTransitionResult {
        validate(state)
        val previousAddress = when (state.status) {
            ExecutionStatus.ACTIVE -> {
                traversal.previous(checkNotNull(state.currentAddress))?.address
            }

            ExecutionStatus.COMPLETED -> traversal.last().address
        }

        if (previousAddress == null) {
            return ExecutionTransitionResult.NoChange(
                state = state,
                reason = NoChangeReason.ALREADY_AT_FIRST_OCCURRENCE
            )
        }

        val updatedState = state.copy(
            currentAddress = previousAddress,
            completedAddresses = state.completedAddresses - previousAddress,
            status = ExecutionStatus.ACTIVE
        )
        validate(updatedState)
        return ExecutionTransitionResult.Changed(updatedState)
    }

    fun jump(
        state: ExecutionState,
        targetAddress: ExecutionAddress
    ): ExecutionTransitionResult {
        validate(state)
        traversal.resolve(targetAddress)

        if (state.status == ExecutionStatus.ACTIVE &&
            state.currentAddress == targetAddress
        ) {
            return ExecutionTransitionResult.NoChange(
                state = state,
                reason = NoChangeReason.ALREADY_AT_TARGET
            )
        }

        val updatedState = state.copy(
            currentAddress = targetAddress,
            status = ExecutionStatus.ACTIVE
        )
        validate(updatedState)
        return ExecutionTransitionResult.Changed(updatedState)
    }

    private fun validate(state: ExecutionState) {
        ExecutionStateValidator.validate(state, guide)
    }

    private fun nextIncompleteAddress(
        currentAddress: ExecutionAddress,
        completedAddresses: Set<ExecutionAddress>
    ): ExecutionAddress? {
        var isAfterCurrent = false
        var earliestIncomplete: ExecutionAddress? = null

        traversal.occurrences().forEach { occurrence ->
            val address = occurrence.address
            if (address == currentAddress) {
                isAfterCurrent = true
            } else if (address !in completedAddresses) {
                if (earliestIncomplete == null) {
                    earliestIncomplete = address
                }
                if (isAfterCurrent) {
                    return address
                }
            }
        }

        return earliestIncomplete
    }

    companion object {
        fun forDefinition(definition: GuideDefinition): ExecutionEngine {
            return ExecutionEngine(GuideDefinitionValidator.validate(definition))
        }

        fun forValidatedDefinition(
            guide: ValidatedGuideDefinition
        ): ExecutionEngine {
            return ExecutionEngine(guide)
        }
    }
}
