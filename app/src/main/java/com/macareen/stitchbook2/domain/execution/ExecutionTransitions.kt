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

    fun adjustCastOn(state: ExecutionState, amount: Int): ExecutionTransitionResult {
        validate(state)
        require(amount == 1 || amount == -1)
        val address = requireNotNull(state.currentAddress)
        require(com.macareen.stitchbook2.domain.guide.WorkingInstruction.of(traversal.resolve(address).instruction.text).castOnTarget != null)
        val previous = state.castOnCounts[address] ?: 0
        val next = Math.addExact(previous, amount).coerceAtLeast(0)
        if (next == previous) return ExecutionTransitionResult.NoChange(state, NoChangeReason.ALREADY_AT_TARGET)
        val changed = state.copy(castOnCounts = state.castOnCounts + (address to next))
        validate(changed)
        return ExecutionTransitionResult.Changed(changed)
    }

    fun complete(state: ExecutionState): ExecutionTransitionResult {
        validate(state)
        val address = state.currentAddress
        val cycle = address?.let(traversal::targetCycle)
        if (cycle != null && address == cycle.last) {
            require(cycle.canConfirm(state)) { "Complete every instruction in this cycle first." }
            val work = state.targetWork[cycle.key] ?: TargetWork()
            val changed = state.copy(currentAddress = cycle.key,
                completedAddresses = state.completedAddresses - cycle.body.toSet(),
                castOnCounts = if (cycle.containerId != null) state.castOnCounts - cycle.body.toSet() else state.castOnCounts,
                targetWork = state.targetWork +
                (cycle.key to work.copy(cycle = Math.addExact(work.cycle, 1), reached = false)))
            validate(changed)
            return ExecutionTransitionResult.Changed(changed)
        }
        return finishOccurrence(state)
    }

    fun observeTarget(state: ExecutionState, value: Double): ExecutionTransitionResult {
        validate(state)
        val address = requireNotNull(state.currentAddress)
        val cycle = requireNotNull(traversal.targetCycle(address))
        val target = cycle.target
        require(target.accepts(value)) { "Enter a non-negative observation in the target's units." }
        val work = state.targetWork[cycle.key] ?: TargetWork()
        val changed = state.copy(targetWork = state.targetWork + (cycle.key to work.copy(
            observations = work.observations + TargetObservation(work.cycle, value), reached = false)))
        validate(changed)
        return ExecutionTransitionResult.Changed(changed)
    }

    fun confirmTarget(state: ExecutionState): ExecutionTransitionResult {
        validate(state)
        val address = requireNotNull(state.currentAddress)
        val cycle = requireNotNull(traversal.targetCycle(address))
        require(cycle.canConfirm(state)) { "Complete every instruction in this cycle first." }
        val target = cycle.target
        val work = requireNotNull(state.targetWork[cycle.key])
        require(work.observations.lastOrNull { it.cycle == work.cycle }?.value?.let(target::isReachedBy) == true) {
            "Record a current-cycle observation that meets the target before confirming."
        }
        return finishOccurrence(state, targetConfirmed = true)
    }

    private fun finishOccurrence(state: ExecutionState, targetConfirmed: Boolean = false): ExecutionTransitionResult {
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
            targetWork = if (targetConfirmed) {
                val key = requireNotNull(traversal.targetCycle(currentAddress)).key
                state.targetWork + (key to requireNotNull(state.targetWork[key]).copy(reached = true))
            } else state.targetWork,
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
        state.currentAddress?.let { address ->
            val cycle = traversal.targetCycle(address)
            val work = cycle?.let { state.targetWork[it.key] }
            if (cycle != null && address == cycle.key && work != null && work.cycle > 1) {
                val changed = state.copy(currentAddress = cycle.last,
                    completedAddresses = (state.completedAddresses - cycle.body.toSet()) + cycle.body.dropLast(1),
                    castOnCounts = if (cycle.containerId != null) state.castOnCounts - cycle.body.toSet() else state.castOnCounts,
                    targetWork = state.targetWork + (cycle.key to work.copy(cycle = work.cycle - 1, reached = false)))
                validate(changed)
                return ExecutionTransitionResult.Changed(changed)
            }
        }
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
            completedAddresses = reopenedCompleted(state, previousAddress) - previousAddress,
            targetWork = reopenedTargetWork(state, previousAddress),
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
            completedAddresses = reopenedCompleted(state, targetAddress),
            targetWork = reopenedTargetWork(state, targetAddress),
            status = ExecutionStatus.ACTIVE
        )
        validate(updatedState)
        return ExecutionTransitionResult.Changed(updatedState)
    }

    private fun validate(state: ExecutionState) {
        ExecutionStateValidator.validate(state, guide)
    }

    private fun reopenedTargetWork(state: ExecutionState, address: ExecutionAddress): Map<ExecutionAddress, TargetWork> {
        val key = traversal.targetCycle(address)?.key ?: return state.targetWork
        val work = state.targetWork[key] ?: return state.targetWork
        return state.targetWork + (key to work.copy(reached = false))
    }

    private fun reopenedCompleted(state: ExecutionState, address: ExecutionAddress): Set<ExecutionAddress> {
        val cycle = traversal.targetCycle(address) ?: return state.completedAddresses
        // Reopening invalidates the suffix, so Jump cannot skip unfinished body instructions.
        return state.completedAddresses - cycle.body.dropWhile { it != address }.toSet()
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
