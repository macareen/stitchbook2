package com.macareen.stitchbook2.domain.execution

sealed interface GuideNode {
    val id: NodeId
}

sealed interface GuideContainer : GuideNode {
    val children: List<NodeId>
}

class Section(
    override val id: NodeId,
    val title: String,
    children: List<NodeId>
) : GuideContainer {
    override val children: List<NodeId> = children.toList()

    override fun equals(other: Any?): Boolean {
        return other is Section &&
            id == other.id &&
            title == other.title &&
            children == other.children
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + children.hashCode()
        return result
    }
}

class Range(
    override val id: NodeId,
    val unitLabel: String,
    val startInclusive: Int,
    val endInclusive: Int,
    children: List<NodeId>
) : GuideContainer {
    override val children: List<NodeId> = children.toList()

    override fun equals(other: Any?): Boolean {
        return other is Range &&
            id == other.id &&
            unitLabel == other.unitLabel &&
            startInclusive == other.startInclusive &&
            endInclusive == other.endInclusive &&
            children == other.children
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + unitLabel.hashCode()
        result = 31 * result + startInclusive
        result = 31 * result + endInclusive
        result = 31 * result + children.hashCode()
        return result
    }
}

class Repeat(
    override val id: NodeId,
    val count: Int,
    val label: String? = null,
    children: List<NodeId>
) : GuideContainer {
    override val children: List<NodeId> = children.toList()

    override fun equals(other: Any?): Boolean {
        return other is Repeat &&
            id == other.id &&
            count == other.count &&
            label == other.label &&
            children == other.children
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + count
        result = 31 * result + (label?.hashCode() ?: 0)
        result = 31 * result + children.hashCode()
        return result
    }
}

data class Instruction(
    override val id: NodeId,
    val text: String
) : GuideNode

class GuideDefinition(
    val guideId: GuideId,
    val revisionId: DefinitionRevisionId,
    rootNodeIds: List<NodeId>,
    nodes: List<GuideNode>
) {
    val rootNodeIds: List<NodeId> = rootNodeIds.toList()
    val nodes: List<GuideNode> = nodes.toList()

    override fun equals(other: Any?): Boolean {
        return other is GuideDefinition &&
            guideId == other.guideId &&
            revisionId == other.revisionId &&
            rootNodeIds == other.rootNodeIds &&
            nodes == other.nodes
    }

    override fun hashCode(): Int {
        var result = guideId.hashCode()
        result = 31 * result + revisionId.hashCode()
        result = 31 * result + rootNodeIds.hashCode()
        result = 31 * result + nodes.hashCode()
        return result
    }
}
