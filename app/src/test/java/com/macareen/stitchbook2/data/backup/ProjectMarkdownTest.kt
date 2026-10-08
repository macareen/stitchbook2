package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.BackupSnapshot
import com.macareen.stitchbook2.domain.backup.CURRENT_BACKUP_FORMAT_VERSION
import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.JournalEntry
import com.macareen.stitchbook2.domain.model.Milestone
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.PhotoRole
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.YarnAllocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectMarkdownTest {

    private val project = Project(
        id = "p1",
        name = "Granny-square blanket",
        craft = Craft.CROCHET,
        projectType = ProjectType.OTHER,
        status = ProjectStatus.ACTIVE,
        notes = null,
        createdAt = 0,
        updatedAt = 0,
        customTypeLabel = "Throw",
        startDate = "2026-01-10"
    )

    private fun milestone(id: String, title: String, position: Int) = Milestone(
        id = id, projectId = "p1", title = title, reachedDate = null, notes = null,
        position = position, createdAt = 0, updatedAt = 0
    )

    private fun entry(id: String, date: String, body: String) = JournalEntry(
        id = id, projectId = "p1", entryDate = date, title = null, body = body, createdAt = 0, updatedAt = 0
    )

    private val yarn = StashItem(
        id = "s1", name = "Merino DK", category = StashCategory.YARN, brand = null, colorway = "Teal",
        dyeLot = null, weightCategory = null, fiberContent = null, quantity = 3.0, unitLabel = "skeins",
        yardagePerUnit = 230.0, notes = null, storageLocation = null, careInstructions = null,
        ravelryYarnId = null, purchaseSource = null, purchasePrice = null, purchaseDate = null,
        createdAt = 0, updatedAt = 0
    )

    private val snapshot = BackupSnapshot(
        formatVersion = CURRENT_BACKUP_FORMAT_VERSION,
        projects = listOf(project),
        stashItems = listOf(yarn),
        milestones = listOf(milestone("m2", "Border", 1), milestone("m1", "First square", 0)),
        journalEntries = listOf(entry("j1", "2026-01-11", "Older entry"), entry("j2", "2026-02-01", "Newer entry")),
        yarnAllocations = listOf(
            YarnAllocation(id = "a1", projectId = "p1", stashItemId = "s1", quantityReserved = 1.0, quantityUsed = 2.0, notes = null, createdAt = 0, updatedAt = 0)
        ),
        photos = listOf(
            Photo(
                id = "ph1", projectId = "p1", stashItemId = null, uri = "content://secret/doc/77", displayName = "square-1.jpg",
                caption = "First one", takenDate = null, milestoneId = null, role = PhotoRole.NONE, createdAt = 0, updatedAt = 0
            )
        ),
        sessions = listOf(
            CraftingSession(
                id = "s", projectId = "p1", startedAt = 0, endedAt = 5_400_000, pausedAt = null, pausedTotalMillis = 0,
                zoneId = "UTC", rowsCompleted = 10, stitchesPerRow = 12, notes = null, createdAt = 0, updatedAt = 0
            )
        )
    )

    private val markdown = projectMarkdown(project, snapshot, now = 1_767_225_600_000)

    @Test
    fun milestonesFollowTheUsersOrderAndJournalIsNewestFirst() {
        assertTrue(markdown.indexOf("1. First square") in 0 until markdown.indexOf("2. Border"))
        assertTrue(markdown.indexOf("Newer entry") in 0 until markdown.indexOf("Older entry"))
    }

    @Test
    fun usesTheCustomTypeLabelAndCraftNeutralWording() {
        assertTrue(markdown.contains("- **Type:** Throw"))
        assertTrue(markdown.contains("- **Craft:** Crochet"))
    }

    @Test
    fun estimatesAreLabelledAndRecordedValuesAreNot() {
        assertTrue(markdown.contains("- **Time worked:** 1 h 30 min"))
        assertTrue(markdown.contains("- **Rows/rounds recorded:** 10"))
        assertTrue(markdown.contains("Stitches (estimate)"))
        assertTrue(markdown.contains("about 460 yds used (estimate)"))
    }

    @Test
    fun photosAreListedByDisplayNameNeverByDeviceUri() {
        assertTrue(markdown.contains("- square-1.jpg — First one"))
        assertFalse(markdown.contains("content://"))
    }
}
