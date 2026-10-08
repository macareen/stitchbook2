package com.macareen.stitchbook2.domain.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Everything that hangs off a project, counted. The project screen draws
 * these as nodes around the project (PRODUCT_SPEC.md: the project is the
 * hub every pattern, tool, yarn, counter, and note connects to).
 */
data class ProjectConnections(
    val guides: Int = 0,
    val patterns: Int = 0,
    val yarns: Int = 0,
    val tools: Int = 0,
    val counters: Int = 0,
    val journal: Int = 0,
    val workedMillis: Long = 0
) {
    fun count(node: ProjectNode): Int = when (node) {
        ProjectNode.GUIDES -> guides
        ProjectNode.PATTERNS -> patterns
        ProjectNode.YARN -> yarns
        ProjectNode.TOOLS -> tools
        ProjectNode.COUNTERS -> counters
        ProjectNode.JOURNAL -> journal
        ProjectNode.TIME -> if (workedMillis > 0) 1 else 0
    }
}

/** The nodes around a project, in the order they sit clockwise from the top. */
enum class ProjectNode {
    GUIDES,
    PATTERNS,
    YARN,
    TOOLS,
    COUNTERS,
    JOURNAL,
    TIME
}

/** A node's centre as fractions (0..1) of the map's width and height. */
data class NodePosition(val x: Float, val y: Float)

/**
 * Places [count] nodes evenly on an ellipse around the centre, starting at
 * the top and going clockwise. Pure, so the layout is the same on every
 * screen size and can be tested without Compose.
 */
fun hubNodePositions(count: Int, radiusX: Float = 0.38f, radiusY: Float = 0.38f): List<NodePosition> {
    if (count <= 0) return emptyList()
    return List(count) { index ->
        val angle = -PI / 2 + 2 * PI * index / count
        NodePosition(
            x = (0.5 + radiusX * cos(angle)).toFloat(),
            y = (0.5 + radiusY * sin(angle)).toFloat()
        )
    }
}

/** What the project screen offers as the single obvious next action. */
sealed interface ProjectNextStep {
    /** A guide already in progress: pick up where you left off. */
    data class ContinueGuide(val guideId: String, val guideName: String) : ProjectNextStep

    /** A published guide with nothing in progress. */
    data class StartGuide(val guideId: String, val guideName: String) : ProjectNextStep

    /** Only drafts exist: finish writing one. */
    data class FinishDraft(val guideId: String, val guideName: String) : ProjectNextStep

    /** No guide yet. */
    data object AddGuide : ProjectNextStep
}

/** A guide as the next-step rule sees it. */
data class GuideStatus(val guideId: String, val name: String, val inProgress: Boolean, val published: Boolean)

/**
 * In-progress work wins, then anything ready to start, then a draft to
 * finish. Ties keep the caller's order, which is the guide list's order.
 */
fun nextStepFor(guides: List<GuideStatus>): ProjectNextStep {
    guides.firstOrNull { it.inProgress }?.let { return ProjectNextStep.ContinueGuide(it.guideId, it.name) }
    guides.firstOrNull { it.published }?.let { return ProjectNextStep.StartGuide(it.guideId, it.name) }
    guides.firstOrNull()?.let { return ProjectNextStep.FinishDraft(it.guideId, it.name) }
    return ProjectNextStep.AddGuide
}

/**
 * The tool categories worth offering first when adding a tool from a
 * project of this craft, most likely first. Each craft gets its own
 * vocabulary rather than knitting's needle list for everyone.
 */
fun Craft.suggestedToolCategories(): List<ToolCategory> = when (this) {
    Craft.KNITTING -> listOf(
        ToolCategory.CIRCULAR_NEEDLES,
        ToolCategory.STRAIGHT_NEEDLES,
        ToolCategory.DPN_SET,
        ToolCategory.INTERCHANGEABLE_TIP,
        ToolCategory.CABLE_NEEDLE,
        ToolCategory.STITCH_MARKER,
        ToolCategory.OTHER_NOTION
    )
    Craft.CROCHET -> listOf(ToolCategory.CROCHET_HOOK, ToolCategory.STITCH_MARKER, ToolCategory.OTHER_NOTION)
    Craft.TUNISIAN_CROCHET -> listOf(
        ToolCategory.TUNISIAN_HOOK,
        ToolCategory.INTERCHANGEABLE_CABLE,
        ToolCategory.STITCH_MARKER,
        ToolCategory.OTHER_NOTION
    )
    Craft.LOOM_KNITTING -> listOf(ToolCategory.LOOM, ToolCategory.STITCH_MARKER, ToolCategory.OTHER_NOTION)
    Craft.OTHER -> ToolCategory.entries.toList()
}
