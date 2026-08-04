package com.macareen.stitchbook2.domain.execution

sealed interface GuideDefinitionError {
    data object EmptyDefinition : GuideDefinitionError
    data class DuplicateNodeId(val nodeId: NodeId) : GuideDefinitionError
    data class MissingNode(
        val nodeId: NodeId,
        val referencedBy: NodeId?
    ) : GuideDefinitionError

    data class EmptyChildren(val containerNodeId: NodeId) : GuideDefinitionError
    data class ContainerWithoutExecutableDescendant(
        val containerNodeId: NodeId
    ) : GuideDefinitionError

    data class InvalidRangeBounds(
        val rangeNodeId: NodeId,
        val startInclusive: Int,
        val endInclusive: Int
    ) : GuideDefinitionError

    data class BlankRangeUnitLabel(val rangeNodeId: NodeId) : GuideDefinitionError
    data class NonPositiveRepeatCount(
        val repeatNodeId: NodeId,
        val count: Int
    ) : GuideDefinitionError

    data class CycleDetected(val nodeId: NodeId) : GuideDefinitionError
    data class MultipleParents(val nodeId: NodeId) : GuideDefinitionError
    data class UnreachableNode(val nodeId: NodeId) : GuideDefinitionError
}

class InvalidGuideDefinitionException(
    val errors: List<GuideDefinitionError>
) : IllegalArgumentException(
    errors.joinToString(
        prefix = "Invalid guide definition: ",
        separator = "; "
    )
)

class ValidatedGuideDefinition internal constructor(
    val definition: GuideDefinition,
    internal val nodesById: Map<NodeId, GuideNode>
) {
    fun node(nodeId: NodeId): GuideNode? = nodesById[nodeId]
}

object GuideDefinitionValidator {

    fun validate(definition: GuideDefinition): ValidatedGuideDefinition {
        val errors = mutableListOf<GuideDefinitionError>()
        val groupedNodes = definition.nodes.groupBy(GuideNode::id)

        groupedNodes
            .filterValues { it.size > 1 }
            .keys
            .forEach { errors += GuideDefinitionError.DuplicateNodeId(it) }

        val nodesById = groupedNodes.mapValues { (_, nodes) -> nodes.first() }

        if (definition.rootNodeIds.isEmpty()) {
            errors += GuideDefinitionError.EmptyDefinition
        }

        definition.nodes.forEach { node ->
            when (node) {
                is GuideContainer -> {
                    if (node.children.isEmpty()) {
                        errors += GuideDefinitionError.EmptyChildren(node.id)
                    }
                }

                is Instruction -> Unit
            }

            when (node) {
                is Range -> {
                    if (node.startInclusive > node.endInclusive) {
                        errors += GuideDefinitionError.InvalidRangeBounds(
                            rangeNodeId = node.id,
                            startInclusive = node.startInclusive,
                            endInclusive = node.endInclusive
                        )
                    }
                    if (node.unitLabel.isBlank()) {
                        errors += GuideDefinitionError.BlankRangeUnitLabel(node.id)
                    }
                }

                is Repeat -> {
                    if (node.count <= 0) {
                        errors += GuideDefinitionError.NonPositiveRepeatCount(
                            repeatNodeId = node.id,
                            count = node.count
                        )
                    }
                }

                is Section,
                is Instruction -> Unit
            }
        }

        definition.rootNodeIds.forEach { rootId ->
            if (rootId !in nodesById) {
                errors += GuideDefinitionError.MissingNode(
                    nodeId = rootId,
                    referencedBy = null
                )
            }
        }

        definition.nodes
            .filterIsInstance<GuideContainer>()
            .forEach { container ->
                container.children.forEach { childId ->
                    if (childId !in nodesById) {
                        errors += GuideDefinitionError.MissingNode(
                            nodeId = childId,
                            referencedBy = container.id
                        )
                    }
                }
            }

        val parentCounts = mutableMapOf<NodeId, Int>()
        definition.rootNodeIds.forEach { rootId ->
            parentCounts[rootId] = parentCounts.getOrDefault(rootId, 0) + 1
        }
        definition.nodes
            .filterIsInstance<GuideContainer>()
            .flatMap(GuideContainer::children)
            .forEach { childId ->
                parentCounts[childId] = parentCounts.getOrDefault(childId, 0) + 1
            }
        parentCounts
            .filterValues { it > 1 }
            .keys
            .forEach { errors += GuideDefinitionError.MultipleParents(it) }

        val visiting = mutableSetOf<NodeId>()
        val visited = mutableSetOf<NodeId>()

        fun visit(nodeId: NodeId) {
            if (nodeId in visiting) {
                errors += GuideDefinitionError.CycleDetected(nodeId)
                return
            }
            if (nodeId in visited) return

            val node = nodesById[nodeId] ?: return
            visiting += nodeId
            if (node is GuideContainer) {
                node.children.forEach(::visit)
            }
            visiting -= nodeId
            visited += nodeId
        }

        definition.rootNodeIds.forEach(::visit)
        definition.nodes.forEach { visit(it.id) }

        val reachable = mutableSetOf<NodeId>()

        fun markReachable(nodeId: NodeId) {
            if (!reachable.add(nodeId)) return
            val node = nodesById[nodeId] as? GuideContainer ?: return
            node.children.forEach(::markReachable)
        }

        definition.rootNodeIds.forEach(::markReachable)
        nodesById.keys
            .filterNot(reachable::contains)
            .forEach { errors += GuideDefinitionError.UnreachableNode(it) }

        if (errors.none { it is GuideDefinitionError.CycleDetected }) {
            val executableMemo = mutableMapOf<NodeId, Boolean>()

            fun hasExecutableDescendant(nodeId: NodeId): Boolean {
                executableMemo[nodeId]?.let { return it }
                val result = when (val node = nodesById[nodeId]) {
                    is Instruction -> true
                    is GuideContainer -> node.children.any(::hasExecutableDescendant)
                    null -> false
                }
                executableMemo[nodeId] = result
                return result
            }

            definition.nodes
                .filterIsInstance<GuideContainer>()
                .filterNot { hasExecutableDescendant(it.id) }
                .forEach {
                    errors += GuideDefinitionError.ContainerWithoutExecutableDescendant(it.id)
                }

            val hasAnyExecutableOccurrence = definition.rootNodeIds.any { rootId ->
                hasExecutableDescendant(rootId)
            }
            if (!hasAnyExecutableOccurrence &&
                errors.none { it is GuideDefinitionError.EmptyDefinition }
            ) {
                errors += GuideDefinitionError.EmptyDefinition
            }
        }

        if (errors.isNotEmpty()) {
            throw InvalidGuideDefinitionException(errors.distinct())
        }

        return ValidatedGuideDefinition(
            definition = definition,
            nodesById = nodesById
        )
    }
}
