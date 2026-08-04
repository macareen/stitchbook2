package com.macareen.stitchbook2.domain.execution

sealed interface ExecutionStateError {
    data class GuideMismatch(
        val expected: GuideId,
        val actual: GuideId
    ) : ExecutionStateError

    data class RevisionMismatch(
        val expected: DefinitionRevisionId,
        val actual: DefinitionRevisionId
    ) : ExecutionStateError

    data class InvalidCurrentAddress(
        val address: ExecutionAddress,
        val cause: ExecutionAddressError
    ) : ExecutionStateError

    data class InvalidCompletedAddress(
        val address: ExecutionAddress,
        val cause: ExecutionAddressError
    ) : ExecutionStateError

    data object ActiveExecutionWithoutCurrentAddress : ExecutionStateError
    data class CompletedExecutionWithCurrentAddress(
        val currentAddress: ExecutionAddress
    ) : ExecutionStateError

    data class IncompleteExecutionWithoutCurrentAddress(
        val completedCount: Int,
        val totalCount: Int
    ) : ExecutionStateError
}

class InvalidExecutionStateException(
    val errors: List<ExecutionStateError>
) : IllegalArgumentException(
    errors.joinToString(
        prefix = "Invalid execution state: ",
        separator = "; "
    )
)

object ExecutionStateValidator {

    fun validate(
        state: ExecutionState,
        guide: ValidatedGuideDefinition
    ): ExecutionState {
        val errors = mutableListOf<ExecutionStateError>()
        val definition = guide.definition
        val traversal = GuideTraversal(guide)

        if (state.guideId != definition.guideId) {
            errors += ExecutionStateError.GuideMismatch(
                expected = definition.guideId,
                actual = state.guideId
            )
        }
        if (state.definitionRevisionId != definition.revisionId) {
            errors += ExecutionStateError.RevisionMismatch(
                expected = definition.revisionId,
                actual = state.definitionRevisionId
            )
        }

        state.currentAddress?.let { address ->
            try {
                traversal.resolve(address)
            } catch (exception: InvalidExecutionAddressException) {
                errors += ExecutionStateError.InvalidCurrentAddress(
                    address = address,
                    cause = exception.error
                )
            }
        }

        state.completedAddresses.forEach { address ->
            try {
                traversal.resolve(address)
            } catch (exception: InvalidExecutionAddressException) {
                errors += ExecutionStateError.InvalidCompletedAddress(
                    address = address,
                    cause = exception.error
                )
            }
        }

        when {
            state.status == ExecutionStatus.ACTIVE &&
                state.currentAddress == null -> {
                errors += ExecutionStateError.ActiveExecutionWithoutCurrentAddress
            }

            state.status == ExecutionStatus.COMPLETED &&
                state.currentAddress != null -> {
                errors += ExecutionStateError.CompletedExecutionWithCurrentAddress(
                    state.currentAddress
                )
            }
        }

        if (state.status == ExecutionStatus.COMPLETED &&
            state.currentAddress == null &&
            state.guideId == definition.guideId &&
            state.definitionRevisionId == definition.revisionId
        ) {
            val allAddresses = traversal.occurrences().map { it.address }.toSet()
            val completedInDefinition = state.completedAddresses.count(allAddresses::contains)
            if (!state.completedAddresses.containsAll(allAddresses)) {
                errors += ExecutionStateError.IncompleteExecutionWithoutCurrentAddress(
                    completedCount = completedInDefinition,
                    totalCount = allAddresses.size
                )
            }
        }

        if (errors.isNotEmpty()) {
            throw InvalidExecutionStateException(errors)
        }
        return state
    }
}
