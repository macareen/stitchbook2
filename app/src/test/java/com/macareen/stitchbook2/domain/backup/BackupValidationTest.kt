package com.macareen.stitchbook2.domain.backup

import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.JournalEntry
import com.macareen.stitchbook2.domain.model.Milestone
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.PhotoRole
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidationTest {

    private fun project(id: String, name: String = id) = Project(
        id = id,
        name = name,
        craft = Craft.CROCHET,
        projectType = ProjectType.BLANKET,
        status = ProjectStatus.ACTIVE,
        notes = null,
        createdAt = 1,
        updatedAt = 2
    )

    private fun entry(id: String, projectId: String) = JournalEntry(
        id = id,
        projectId = projectId,
        entryDate = "2026-01-02",
        title = null,
        body = "Joined the border.",
        createdAt = 1,
        updatedAt = 1
    )

    private fun milestone(id: String, projectId: String) = Milestone(
        id = id,
        projectId = projectId,
        title = "Border done",
        reachedDate = null,
        notes = null,
        position = 0,
        createdAt = 1,
        updatedAt = 1
    )

    private fun photo(id: String, projectId: String?, milestoneId: String? = null) = Photo(
        id = id,
        projectId = projectId,
        stashItemId = null,
        uri = "content://example/$id",
        displayName = "$id.jpg",
        caption = null,
        takenDate = null,
        milestoneId = milestoneId,
        role = PhotoRole.NONE,
        createdAt = 1,
        updatedAt = 1
    )

    private fun session(id: String, projectId: String?, startedAt: Long = 1_000, endedAt: Long? = 2_000) = CraftingSession(
        id = id,
        projectId = projectId,
        startedAt = startedAt,
        endedAt = endedAt,
        pausedAt = null,
        pausedTotalMillis = 0,
        zoneId = "UTC",
        rowsCompleted = null,
        stitchesPerRow = null,
        notes = null,
        createdAt = 1,
        updatedAt = 1
    )

    private val empty = BackupSnapshot(formatVersion = CURRENT_BACKUP_FORMAT_VERSION)

    // --- validateBackup ---

    @Test
    fun aValidFileHasNoIssuesInEitherMode() {
        val file = empty.copy(projects = listOf(project("p1")), journalEntries = listOf(entry("j1", "p1")))
        assertTrue(validateBackup(file, empty, RestoreMode.REPLACE).isEmpty())
        assertTrue(validateBackup(file, empty, RestoreMode.MERGE).isEmpty())
    }

    @Test
    fun aNewerFormatIsRejectedBeforeAnythingElseIsChecked() {
        val file = BackupSnapshot(formatVersion = CURRENT_BACKUP_FORMAT_VERSION + 1, projects = listOf(project("p"), project("p")))
        val issues = validateBackup(file, empty, RestoreMode.MERGE)
        assertEquals(listOf(BackupIssueKind.NEWER_FORMAT), issues.map { it.kind })
    }

    @Test
    fun duplicateIdsWithinOneTypeAreReportedOncePerKey() {
        val file = empty.copy(projects = listOf(project("p"), project("p"), project("p"), project("q")))
        val issues = validateBackup(file, empty, RestoreMode.REPLACE)
        assertEquals(listOf(BackupIssue(BackupIssueKind.DUPLICATE_ID, BackupRecordType.PROJECTS, "p", "Appears more than once.")), issues)
    }

    @Test
    fun aReferenceToARecordInNeitherTheFileNorTheLibraryIsMissing() {
        val file = empty.copy(projects = emptyList(), journalEntries = listOf(entry("j1", "ghost")))
        val issue = validateBackup(file, empty, RestoreMode.MERGE).single()
        assertEquals(BackupIssueKind.MISSING_REFERENCE, issue.kind)
        assertEquals(BackupRecordType.JOURNAL_ENTRIES, issue.type)
        assertEquals("j1", issue.recordKey)
    }

    @Test
    fun mergeMayReferToLocalRecordsButReplaceMayNotWhenTheFileCarriesThatType() {
        val local = empty.copy(projects = listOf(project("local")))
        // The file carries projects (so REPLACE would remove "local") and a child of "local".
        val file = empty.copy(projects = listOf(project("p1")), milestones = listOf(milestone("m1", "local")))

        assertTrue(validateBackup(file, local, RestoreMode.MERGE).isEmpty())
        assertEquals(
            listOf(BackupIssueKind.MISSING_REFERENCE),
            validateBackup(file, local, RestoreMode.REPLACE).map { it.kind }
        )
    }

    @Test
    fun aTypeTheFileDoesNotCarryResolvesAgainstTheUntouchedLocalRecords() {
        val local = empty.copy(projects = listOf(project("local")))
        val file = empty.copy(journalEntries = listOf(entry("j1", "local")))
        assertTrue(validateBackup(file, local, RestoreMode.REPLACE).isEmpty())
    }

    @Test
    fun photoMilestoneReferencesAndSessionTimesAreChecked() {
        val file = empty.copy(
            projects = listOf(project("p1")),
            milestones = emptyList(),
            photos = listOf(photo("ph1", "p1", milestoneId = "gone")),
            sessions = listOf(session("s1", "p1", startedAt = 5_000, endedAt = 1_000), session("s2", null))
        )
        val issues = validateBackup(file, empty, RestoreMode.REPLACE)
        assertEquals(
            setOf(BackupRecordType.PHOTOS to BackupIssueKind.MISSING_REFERENCE, BackupRecordType.SESSIONS to BackupIssueKind.INVALID_VALUE),
            issues.map { it.type to it.kind }.toSet()
        )
        assertEquals(2, issues.size)
    }

    // --- compareBackup ---

    @Test
    fun comparisonCountsNewIdenticalConflictingAndLocalOnlyPerType() {
        val local = empty.copy(
            projects = listOf(project("same"), project("changed", name = "Local name"), project("localOnly")),
            journalEntries = listOf(entry("j1", "same"))
        )
        val file = empty.copy(projects = listOf(project("same"), project("changed", name = "File name"), project("new")))

        val (comparison, conflicts) = compareBackup(file, local)

        assertEquals(TypeComparison(incoming = 3, new = 1, identical = 1, conflicting = 1, onlyLocal = 1), comparison[BackupRecordType.PROJECTS])
        assertEquals(listOf(BackupConflict(BackupRecordType.PROJECTS, "changed")), conflicts)
        // Journal entries aren't in the file, so they aren't compared (and a REPLACE wouldn't touch them).
        assertNull(comparison[BackupRecordType.JOURNAL_ENTRIES])
    }

    @Test
    fun anEmptyListInTheFileIsComparedAndMarksEveryLocalRecordLocalOnly() {
        val local = empty.copy(projects = listOf(project("a"), project("b")))
        val (comparison, _) = compareBackup(empty.copy(projects = emptyList()), local)
        assertEquals(TypeComparison(incoming = 0, new = 0, identical = 0, conflicting = 0, onlyLocal = 2), comparison[BackupRecordType.PROJECTS])
    }

    // --- mergeAdditions ---

    @Test
    fun mergeKeepsOnlyRecordsWhoseIdentityIsNewAndNeverAConflict() {
        val local = empty.copy(projects = listOf(project("same"), project("changed", name = "Local name")))
        val file = empty.copy(
            projects = listOf(project("same"), project("changed", name = "File name"), project("new")),
            journalEntries = listOf(entry("j1", "changed"))
        )

        val merged = mergeAdditions(file, local)

        assertEquals(listOf(project("new")), merged.projects)
        assertEquals(listOf(entry("j1", "changed")), merged.journalEntries)
        // Types absent from the file stay absent, so the merge never touches them.
        assertNull(merged.milestones)
        assertNull(merged.sessions)
    }

    @Test
    fun mergeKeysJunctionRecordsByBothEnds() {
        val local = empty.copy(toolAssignments = listOf(ToolAssignment("p1", "t1")))
        val file = empty.copy(toolAssignments = listOf(ToolAssignment("p1", "t1"), ToolAssignment("p1", "t2")))
        assertEquals(listOf(ToolAssignment("p1", "t2")), mergeAdditions(file, local).toolAssignments)
    }
}
