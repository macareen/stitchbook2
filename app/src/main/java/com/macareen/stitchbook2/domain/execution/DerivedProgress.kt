package com.macareen.stitchbook2.domain.execution

enum class ContainerProgressStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETE
}

data class ContainerProgress(
    val status: ContainerProgressStatus,
    val completedCount: Int,
    val totalCount: Int,
    val currentRangeValue: Int?,
    val currentRepeatIteration: Int?
)

sealed interface ContainerProgressError {
    data class NodeMissing(val nodeId: NodeId) : ContainerProgressError
    data class NodeIsNotContainer(val nodeId: NodeId) : ContainerProgressError
}

class InvalidContainerProgressRequestException(
    val error: ContainerProgressError
) : IllegalArgumentException("Invalid container progress request: $error")

class DerivedProgressCalculator(
    private val guide: ValidatedGuideDefinition
) {
    private val traversal = GuideTraversal(guide)

    fun progressFor(
        containerNodeId: NodeId,
        completedAddresses: Set<ExecutionAddress>,
        currentAddress: ExecutionAddress? = null
    ): ContainerProgress {
        when (guide.node(containerNodeId)) {
            null -> throw InvalidContainerProgressRequestException(
                ContainerProgressError.NodeMissing(containerNodeId)
            )

            !is GuideContainer -> throw InvalidContainerProgressRequestException(
                ContainerProgressError.NodeIsNotContainer(containerNodeId)
            )

            is GuideContainer -> Unit
        }

        completedAddresses.forEach(traversal::resolve)
        currentAddress?.let(traversal::resolve)

        var totalCount = 0
        var completedCount = 0
        traversal.occurrenceRecords()
            .filter { containerNodeId in it.nodePath }
            .forEach { occurrence ->
                totalCount += 1
                if (occurrence.address in completedAddresses) {
                    completedCount += 1
                }
            }

        check(totalCount > 0) {
            "Validated containers must have at least one executable occurrence."
        }

        val status = when (completedCount) {
            0 -> ContainerProgressStatus.NOT_STARTED
            totalCount -> ContainerProgressStatus.COMPLETE
            else -> ContainerProgressStatus.IN_PROGRESS
        }

        val currentFrame = currentAddress
            ?.ancestryFrames
            ?.firstOrNull { it.containerNodeId == containerNodeId }

        return ContainerProgress(
            status = status,
            completedCount = completedCount,
            totalCount = totalCount,
            currentRangeValue = (currentFrame as? AncestryFrame.RangeValue)?.value,
            currentRepeatIteration =
                (currentFrame as? AncestryFrame.RepeatIteration)?.iteration
        )
    }
}
