package com.macareen.stitchbook2.domain.execution

sealed interface AncestryFrame {
    val containerNodeId: NodeId

    data class RangeValue(
        override val containerNodeId: NodeId,
        val value: Int
    ) : AncestryFrame

    data class RepeatIteration(
        override val containerNodeId: NodeId,
        val iteration: Int
    ) : AncestryFrame
}

class ExecutionAddress(
    val definitionRevisionId: DefinitionRevisionId,
    val instructionNodeId: NodeId,
    ancestryFrames: List<AncestryFrame> = emptyList()
) {
    val ancestryFrames: List<AncestryFrame> = ancestryFrames.toList()

    fun copy(
        definitionRevisionId: DefinitionRevisionId = this.definitionRevisionId,
        instructionNodeId: NodeId = this.instructionNodeId,
        ancestryFrames: List<AncestryFrame> = this.ancestryFrames
    ) = ExecutionAddress(
        definitionRevisionId = definitionRevisionId,
        instructionNodeId = instructionNodeId,
        ancestryFrames = ancestryFrames
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExecutionAddress) return false
        return definitionRevisionId == other.definitionRevisionId &&
            instructionNodeId == other.instructionNodeId &&
            ancestryFrames == other.ancestryFrames
    }

    override fun hashCode(): Int {
        var result = definitionRevisionId.hashCode()
        result = 31 * result + instructionNodeId.hashCode()
        result = 31 * result + ancestryFrames.hashCode()
        return result
    }

    override fun toString(): String {
        return "ExecutionAddress(" +
            "definitionRevisionId=$definitionRevisionId, " +
            "instructionNodeId=$instructionNodeId, " +
            "ancestryFrames=$ancestryFrames" +
            ")"
    }
}

data class ExecutableOccurrence(
    val address: ExecutionAddress,
    val instruction: Instruction
)

enum class ExecutionStatus {
    ACTIVE,
    COMPLETED
}

class ExecutionState(
    val executionId: ExecutionId,
    val guideId: GuideId,
    val definitionRevisionId: DefinitionRevisionId,
    val currentAddress: ExecutionAddress?,
    completedAddresses: Set<ExecutionAddress>,
    val status: ExecutionStatus = if (currentAddress == null) {
        ExecutionStatus.COMPLETED
    } else {
        ExecutionStatus.ACTIVE
    }
) {
    val completedAddresses: Set<ExecutionAddress> = completedAddresses.toSet()

    val isComplete: Boolean
        get() = status == ExecutionStatus.COMPLETED

    fun copy(
        executionId: ExecutionId = this.executionId,
        guideId: GuideId = this.guideId,
        definitionRevisionId: DefinitionRevisionId = this.definitionRevisionId,
        currentAddress: ExecutionAddress? = this.currentAddress,
        completedAddresses: Set<ExecutionAddress> = this.completedAddresses,
        status: ExecutionStatus = this.status
    ) = ExecutionState(
        executionId = executionId,
        guideId = guideId,
        definitionRevisionId = definitionRevisionId,
        currentAddress = currentAddress,
        completedAddresses = completedAddresses,
        status = status
    )

    override fun equals(other: Any?): Boolean {
        return other is ExecutionState &&
            executionId == other.executionId &&
            guideId == other.guideId &&
            definitionRevisionId == other.definitionRevisionId &&
            currentAddress == other.currentAddress &&
            completedAddresses == other.completedAddresses &&
            status == other.status
    }

    override fun hashCode(): Int {
        var result = executionId.hashCode()
        result = 31 * result + guideId.hashCode()
        result = 31 * result + definitionRevisionId.hashCode()
        result = 31 * result + (currentAddress?.hashCode() ?: 0)
        result = 31 * result + completedAddresses.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }
}
