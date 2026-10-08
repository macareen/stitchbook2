package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.ActiveExecutionRecord
import com.macareen.stitchbook2.domain.backup.AddressFrameRecord
import com.macareen.stitchbook2.domain.backup.CompletedOccurrenceRecord
import com.macareen.stitchbook2.domain.backup.ExecutionRecord
import com.macareen.stitchbook2.domain.backup.GuideBackupGraph
import com.macareen.stitchbook2.domain.backup.GuideBackupStore
import com.macareen.stitchbook2.domain.backup.GuideDraftRecord
import com.macareen.stitchbook2.domain.backup.GuideNodeRecord
import com.macareen.stitchbook2.domain.backup.GuideRecord
import com.macareen.stitchbook2.domain.backup.GuideRevisionRecord
import com.macareen.stitchbook2.domain.backup.ProjectGuideLink
import com.macareen.stitchbook2.domain.backup.canonicalNodes

/** A small guide graph: a project's own guide with draft, revision, and progress, plus a linked pattern guide. */
internal object GuideBackupFixtures {

    fun node(id: String, parent: String?, order: Int, type: String) = GuideNodeRecord(
        nodeId = id,
        parentNodeId = parent,
        childOrder = order,
        type = type,
        title = if (type == "SECTION") "Body" else null,
        instructionText = if (type == "INSTRUCTION") "Knit to end." else null,
        rangeUnitLabel = null,
        rangeStartInclusive = null,
        rangeEndInclusive = null,
        repeatCount = if (type == "REPEAT") 4 else null,
        repeatLabel = null
    )

    private val tree = listOf(
        node("section", null, 0, "SECTION"),
        node("repeat", "section", 0, "REPEAT"),
        node("step", "repeat", 0, "INSTRUCTION")
    ).canonicalNodes()

    val projectGuide = GuideRecord(
        id = "guide-1",
        projectId = "project-1",
        libraryItemId = "library-1",
        sizeLabel = "M",
        name = "Body",
        notes = "Size M",
        createdAt = 100,
        updatedAt = 200
    )

    val patternGuide = GuideRecord(
        id = "guide-2",
        projectId = null,
        libraryItemId = "library-1",
        sizeLabel = "L",
        name = "Body (L)",
        notes = null,
        createdAt = 100,
        updatedAt = 100
    )

    val revision = GuideRevisionRecord(id = "revision-1", guideId = "guide-1", revisionNumber = 1, createdAt = 150, nodes = tree)

    val draft = GuideDraftRecord(
        id = "draft-1",
        guideId = "guide-1",
        baseRevisionId = "revision-1",
        createdAt = 120,
        updatedAt = 160,
        version = 3,
        nodes = tree
    )

    val execution = ExecutionRecord(
        id = "execution-1",
        guideId = "guide-1",
        definitionRevisionId = "revision-1",
        projectId = "project-1",
        status = "ACTIVE",
        currentInstructionNodeId = "step",
        createdAt = 170,
        updatedAt = 180,
        completedAt = null,
        version = 5,
        currentFrames = listOf(AddressFrameRecord(0, "repeat", "REPEAT_ITERATION", 2)),
        completedOccurrences = listOf(
            CompletedOccurrenceRecord(
                addressSignature = "4:step1:x",
                instructionNodeId = "step",
                frames = listOf(AddressFrameRecord(0, "repeat", "REPEAT_ITERATION", 1))
            )
        )
    )

    val active = ActiveExecutionRecord(guideId = "guide-1", projectKey = "project-1", executionId = "execution-1")

    val link = ProjectGuideLink(projectId = "project-1", guideId = "guide-2")

    val graph = GuideBackupGraph(
        guides = listOf(projectGuide, patternGuide),
        drafts = listOf(draft),
        revisions = listOf(revision),
        executions = listOf(execution),
        activeExecutions = listOf(active),
        projectGuides = listOf(link)
    )
}

/** Holds the graph in memory with the store's replace/add semantics. */
internal class FakeGuideBackupStore(var graph: GuideBackupGraph = GuideBackupGraph()) : GuideBackupStore {
    var replaceCalls = 0
    var addCalls = 0

    override suspend fun load(): GuideBackupGraph = graph

    override suspend fun replace(graph: GuideBackupGraph) {
        replaceCalls++
        this.graph = graph
    }

    override suspend fun add(graph: GuideBackupGraph) {
        addCalls++
        val existingIds = this.graph.guides.map { it.id }.toSet()
        check(graph.guides.none { it.id in existingIds }) { "MERGE must only add new guides" }
        this.graph = GuideBackupGraph(
            guides = this.graph.guides + graph.guides,
            drafts = this.graph.drafts + graph.drafts,
            revisions = this.graph.revisions + graph.revisions,
            executions = this.graph.executions + graph.executions,
            activeExecutions = this.graph.activeExecutions + graph.activeExecutions,
            projectGuides = (this.graph.projectGuides + graph.projectGuides).distinct()
        )
    }
}
