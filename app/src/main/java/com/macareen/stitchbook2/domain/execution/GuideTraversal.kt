package com.macareen.stitchbook2.domain.execution

sealed interface ExecutionAddressError {
    data class RevisionMismatch(
        val expected: DefinitionRevisionId,
        val actual: DefinitionRevisionId
    ) : ExecutionAddressError

    data class InstructionNodeMissing(val nodeId: NodeId) : ExecutionAddressError
    data class TargetIsNotInstruction(val nodeId: NodeId) : ExecutionAddressError
    data class MalformedAncestryFrame(
        val frame: AncestryFrame,
        val reason: String
    ) : ExecutionAddressError

    data class DuplicateAncestryFrame(val containerNodeId: NodeId) :
        ExecutionAddressError

    data class UnresolvedAddress(val address: ExecutionAddress) : ExecutionAddressError
}

class InvalidExecutionAddressException(
    val error: ExecutionAddressError
) : IllegalArgumentException("Invalid execution address: $error")

class GuideTraversal(
    private val guide: ValidatedGuideDefinition
) {
    fun occurrences(): Sequence<ExecutableOccurrence> {
        return occurrenceRecords().map { record ->
            ExecutableOccurrence(
                address = record.address,
                instruction = record.instruction
            )
        }
    }

    fun first(): ExecutableOccurrence = occurrences().first()

    fun last(): ExecutableOccurrence = occurrences().last()

    fun next(address: ExecutionAddress): ExecutableOccurrence? {
        validateAddressShape(address)
        var found = false
        occurrences().forEach { occurrence ->
            if (found) return occurrence
            if (occurrence.address == address) found = true
        }
        if (!found) unresolved(address)
        return null
    }

    fun previous(address: ExecutionAddress): ExecutableOccurrence? {
        validateAddressShape(address)
        var previous: ExecutableOccurrence? = null
        occurrences().forEach { occurrence ->
            if (occurrence.address == address) return previous
            previous = occurrence
        }
        unresolved(address)
    }

    fun resolve(address: ExecutionAddress): ExecutableOccurrence {
        validateAddressShape(address)
        return occurrences().firstOrNull { it.address == address }
            ?: unresolved(address)
    }

    /**
     * The full ordered node path from a root down to and including the
     * Instruction at [address]: every Section, Range, and Repeat ancestor
     * in traversal order, followed by the Instruction itself.
     *
     * This is a structural lookup, not a progress calculation: it exists
     * because a Section never contributes an [AncestryFrame], so container
     * context such as Section titles cannot be read off an
     * [ExecutionAddress] alone. Callers such as Focus Mode use this to
     * render breadcrumbs; it does not compute or duplicate completion or
     * container progress.
     */
    fun ancestryNodePath(address: ExecutionAddress): List<NodeId> {
        validateAddressShape(address)
        return occurrenceRecords().firstOrNull { it.address == address }?.nodePath
            ?: unresolved(address)
    }

    fun contains(address: ExecutionAddress): Boolean {
        if (address.definitionRevisionId != guide.definition.revisionId) {
            return false
        }
        return try {
            resolve(address)
            true
        } catch (_: InvalidExecutionAddressException) {
            false
        }
    }

    internal fun occurrenceRecords(): Sequence<OccurrenceRecord> = sequence {
        guide.definition.rootNodeIds.forEach { rootNodeId ->
            yieldAll(
                walk(
                    nodeId = rootNodeId,
                    frames = emptyList(),
                    nodePath = emptyList()
                )
            )
        }
    }

    private fun walk(
        nodeId: NodeId,
        frames: List<AncestryFrame>,
        nodePath: List<NodeId>
    ): Sequence<OccurrenceRecord> = sequence {
        val node = checkNotNull(guide.nodesById[nodeId])
        val path = nodePath + node.id

        when (node) {
            is Section -> {
                node.children.forEach { childId ->
                    yieldAll(walk(childId, frames, path))
                }
            }

            is Range -> {
                for (value in node.startInclusive..node.endInclusive) {
                    val rangeFrames = frames + AncestryFrame.RangeValue(
                        containerNodeId = node.id,
                        value = value
                    )
                    node.children.forEach { childId ->
                        yieldAll(walk(childId, rangeFrames, path))
                    }
                }
            }

            is Repeat -> {
                for (iteration in 1..node.count) {
                    val repeatFrames = frames + AncestryFrame.RepeatIteration(
                        containerNodeId = node.id,
                        iteration = iteration
                    )
                    node.children.forEach { childId ->
                        yieldAll(walk(childId, repeatFrames, path))
                    }
                }
            }

            is Instruction -> {
                yield(
                    OccurrenceRecord(
                        address = ExecutionAddress(
                            definitionRevisionId = guide.definition.revisionId,
                            instructionNodeId = node.id,
                            ancestryFrames = frames
                        ),
                        instruction = node,
                        nodePath = path
                    )
                )
            }
        }
    }

    private fun validateAddressShape(address: ExecutionAddress) {
        if (address.definitionRevisionId != guide.definition.revisionId) {
            throw InvalidExecutionAddressException(
                ExecutionAddressError.RevisionMismatch(
                    expected = guide.definition.revisionId,
                    actual = address.definitionRevisionId
                )
            )
        }

        when (guide.nodesById[address.instructionNodeId]) {
            null -> throw InvalidExecutionAddressException(
                ExecutionAddressError.InstructionNodeMissing(address.instructionNodeId)
            )

            !is Instruction -> throw InvalidExecutionAddressException(
                ExecutionAddressError.TargetIsNotInstruction(address.instructionNodeId)
            )

            is Instruction -> Unit
        }

        val duplicateFrame = address.ancestryFrames
            .groupBy(AncestryFrame::containerNodeId)
            .entries
            .firstOrNull { it.value.size > 1 }
        if (duplicateFrame != null) {
            throw InvalidExecutionAddressException(
                ExecutionAddressError.DuplicateAncestryFrame(duplicateFrame.key)
            )
        }

        address.ancestryFrames.forEach { frame ->
            val reason = when (frame) {
                is AncestryFrame.RangeValue -> {
                    val range = guide.nodesById[frame.containerNodeId] as? Range
                    when {
                        range == null -> "Frame does not reference a Range."
                        frame.value !in range.startInclusive..range.endInclusive ->
                            "Range value is outside the inclusive bounds."

                        else -> null
                    }
                }

                is AncestryFrame.RepeatIteration -> {
                    val repeat = guide.nodesById[frame.containerNodeId] as? Repeat
                    when {
                        repeat == null -> "Frame does not reference a Repeat."
                        frame.iteration !in 1..repeat.count ->
                            "Repeat iteration is outside the configured count."

                        else -> null
                    }
                }
            }
            if (reason != null) {
                throw InvalidExecutionAddressException(
                    ExecutionAddressError.MalformedAncestryFrame(frame, reason)
                )
            }
        }
    }

    private fun unresolved(address: ExecutionAddress): Nothing {
        throw InvalidExecutionAddressException(
            ExecutionAddressError.UnresolvedAddress(address)
        )
    }
}

internal data class OccurrenceRecord(
    val address: ExecutionAddress,
    val instruction: Instruction,
    val nodePath: List<NodeId>
)
