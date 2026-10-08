package com.macareen.stitchbook2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectConnectionsTest {

    @Test
    fun nodesStartAtTheTopAndGoClockwiseOnTheRing() {
        val positions = hubNodePositions(4, radiusX = 0.4f, radiusY = 0.4f)

        assertEquals(NodePosition(0.5f, 0.1f), positions[0].rounded())
        assertEquals(NodePosition(0.9f, 0.5f), positions[1].rounded())
        assertEquals(NodePosition(0.5f, 0.9f), positions[2].rounded())
        assertEquals(NodePosition(0.1f, 0.5f), positions[3].rounded())
    }

    @Test
    fun everyProjectNodeFitsInsideTheMap() {
        hubNodePositions(ProjectNode.entries.size).forEach { position ->
            assertTrue(position.x in 0f..1f && position.y in 0f..1f)
        }
        assertTrue(hubNodePositions(0).isEmpty())
    }

    @Test
    fun inProgressWorkWinsThenReadyToStartThenDrafts() {
        val draft = GuideStatus("d", "Collar", inProgress = false, published = false)
        val ready = GuideStatus("r", "Sleeves", inProgress = false, published = true)
        val active = GuideStatus("a", "Body", inProgress = true, published = true)

        assertEquals(ProjectNextStep.ContinueGuide("a", "Body"), nextStepFor(listOf(draft, ready, active)))
        assertEquals(ProjectNextStep.StartGuide("r", "Sleeves"), nextStepFor(listOf(draft, ready)))
        assertEquals(ProjectNextStep.FinishDraft("d", "Collar"), nextStepFor(listOf(draft)))
        assertEquals(ProjectNextStep.AddGuide, nextStepFor(emptyList()))
    }

    @Test
    fun timeCountsAsConnectedOnlyOnceWorkIsRecorded() {
        assertEquals(0, ProjectConnections().count(ProjectNode.TIME))
        assertEquals(1, ProjectConnections(workedMillis = 60_000).count(ProjectNode.TIME))
        assertEquals(3, ProjectConnections(yarns = 3).count(ProjectNode.YARN))
    }

    @Test
    fun eachCraftSuggestsItsOwnToolsFirst() {
        assertEquals(ToolCategory.CROCHET_HOOK, Craft.CROCHET.suggestedToolCategories().first())
        assertEquals(ToolCategory.TUNISIAN_HOOK, Craft.TUNISIAN_CROCHET.suggestedToolCategories().first())
        assertEquals(ToolCategory.LOOM, Craft.LOOM_KNITTING.suggestedToolCategories().first())
        assertEquals(ToolCategory.CIRCULAR_NEEDLES, Craft.KNITTING.suggestedToolCategories().first())
        assertTrue(ToolCategory.CROCHET_HOOK !in Craft.KNITTING.suggestedToolCategories())
        assertEquals(ToolCategory.entries.toList(), Craft.OTHER.suggestedToolCategories())
    }

    private fun NodePosition.rounded() = NodePosition(Math.round(x * 100) / 100f, Math.round(y * 100) / 100f)
}
