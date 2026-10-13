package com.macareen.stitchbook2.domain.library

import com.macareen.stitchbook2.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class PatternProjectStatisticsTest {
    private fun project(id: String, status: ProjectStatus) = Project(id, id, Craft.CROCHET,
        ProjectType.CARDIGAN, status, null, 0, 0)
    @Test fun onlyLinkedCompletedProjectsCount() {
        val projects = listOf(project("done", ProjectStatus.COMPLETED), project("active", ProjectStatus.ACTIVE),
            project("planned", ProjectStatus.PLANNED), project("other", ProjectStatus.COMPLETED))
        val links = listOf("done", "active", "planned").map { ProjectPatternLink(it, "p") } + ProjectPatternLink("other", "q")
        assertEquals(1, completedProjectCount("p", projects, links))
    }
    @Test fun duplicateLinksAndProjectSnapshotsNeverDoubleCount() {
        val done = project("done", ProjectStatus.COMPLETED)
        assertEquals(1, completedProjectCount("p", listOf(done, done), List(3) { ProjectPatternLink("done", "p") }))
    }
    @Test fun danglingLinksAndCompletionDatesWithoutCompletedStatusDoNotCount() {
        val active = project("active", ProjectStatus.ACTIVE).copy(completedDate = "2026-01-01")
        assertEquals(0, completedProjectCount("p", listOf(active), listOf(ProjectPatternLink("gone", "p"), ProjectPatternLink("active", "p"))))
    }
    @Test fun completionAndUnlinkingChangeTheRecordedCount() {
        val active = project("one", ProjectStatus.ACTIVE)
        val links = listOf(ProjectPatternLink("one", "p"))
        assertEquals(0, completedProjectCount("p", listOf(active), links))
        assertEquals(1, completedProjectCount("p", listOf(active.copy(status = ProjectStatus.COMPLETED)), links))
        assertEquals(0, completedProjectCount("p", listOf(active.copy(status = ProjectStatus.COMPLETED)), emptyList()))
    }
}
