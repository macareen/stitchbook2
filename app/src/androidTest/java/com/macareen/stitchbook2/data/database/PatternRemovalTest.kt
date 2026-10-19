package com.macareen.stitchbook2.data.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.PatternRemovalResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PatternRemovalTest {
    private lateinit var db: StitchbookDatabase
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            StitchbookDatabase::class.java).build()
    }
    @After fun close() = db.close()

    private suspend fun project(id: String) {
        db.projectDao().upsert(ProjectEntity(id, id, "CROCHET", "OTHER", "ACTIVE", null, 0, 0))
    }
    private suspend fun pattern() = db.libraryDao().upsert(LibraryItemEntity(
        "pattern", "Sample pattern", "CROCHET", null, null, "", null, false, 0, 0,
        pdfUri = "content://original/pattern", pdfFileName = "pattern.pdf"))

    private suspend fun guide(id: String, owner: String? = null, archived: Boolean = false) {
        val draft = GuideDraftEntity("draft-$id", id, null, 0, 0, 0)
        db.guideDao().insertGuideWithDraft(GuideEntity(id, owner, "pattern", null, id, null, 0, 0, archived), draft)
        db.guideDao().replaceDraft(draft.copy(version = 1), 0, listOf(DraftNodeEntity(
            draft.id, "step-$id", null, 0, "INSTRUCTION", null, "Work the next stitch.", null, null, null, null, null)))
        db.guideDao().publishDraft(id, "revision-$id", 1)
    }

    @Test fun previewIncludesDistinctDirectOwnedUsedPieceAndSavedWorkProjects() = runBlocking<Unit> {
        pattern()
        listOf("Direct", "Owner", "User", "Piece", "Saved", "Unrelated").forEach { project(it) }
        guide("shared")
        guide("owned", "Owner")
        guide("archived", archived = true)
        db.materialsDao().upsertPatternLink(ProjectPatternLinkEntity("Direct", "pattern"))
        db.guideDao().insertProjectGuideLink(ProjectGuideLinkEntity("Direct", "shared"))
        db.guideDao().insertProjectGuideLink(ProjectGuideLinkEntity("User", "shared"))
        db.guideDao().insertProjectGuideLink(ProjectGuideLinkEntity("Piece", "shared"))
        db.executionDao().createPiece(ProjectPieceEntity("piece", "Piece", "shared", "Piece", false, 0, 0))
        db.guideDao().deleteProjectGuideLink("Piece", "shared")
        db.executionDao().createExecution("shared", "revision-shared", "work", 2, projectId = "Saved")
        db.executionDao().applyComplete("work", 0, 3)
        val before = db.executionDao().getScopeExecutions("shared", "Saved", null)
        val preview = db.libraryDao().previewRemoval("pattern")!!
        assertEquals(listOf("Direct", "Owner", "Piece", "Saved", "User"), preview.projects.map { it.name })
        assertEquals(listOf("archived", "owned", "shared"), preview.guideIds)
        assertEquals(PatternRemovalResult.Removed, db.libraryDao().removeReviewed(preview))
        assertNull(db.libraryDao().observeById("pattern").first())
        assertTrue(db.materialsDao().observeAllPatternLinks().first().isEmpty())
        assertNull(db.guideDao().getGuide("shared")!!.libraryItemId)
        assertNotNull(db.guideDao().getDraft("shared"))
        assertEquals(1, db.guideDao().getRevisions("shared").size)
        assertEquals(before, db.executionDao().getScopeExecutions("shared", "Saved", null))
        assertNotNull(db.executionDao().getPiece("piece"))
        assertTrue(db.guideDao().observeByLibraryItemIncludingArchived("pattern").first().isEmpty())
        assertTrue(db.guideDao().getGuide("archived")!!.isArchived)
        assertEquals(1, db.guideDao().getRevisions("archived").size)
        assertEquals(PatternRemovalResult.Missing, db.libraryDao().removeReviewed(preview))
    }

    @Test fun changedProjectDependenciesAndMetadataAreRefreshedWithoutRemovingAnything() = runBlocking<Unit> {
        pattern()
        val initial = db.libraryDao().previewRemoval("pattern")!!
        project("New project")
        db.materialsDao().upsertPatternLink(ProjectPatternLinkEntity("New project", "pattern"))
        val changed = db.libraryDao().removeReviewed(initial) as PatternRemovalResult.Changed
        assertEquals(listOf("New project"), changed.preview.projects.map { it.name })
        val item = db.libraryDao().observeById("pattern").first()!!
        db.libraryDao().upsert(item.copy(title = "Renamed pattern"))
        val renamed = db.libraryDao().removeReviewed(changed.preview) as PatternRemovalResult.Changed
        assertEquals("Renamed pattern", renamed.preview.item.title)
        guide("new-guide")
        val addedGuide = db.libraryDao().removeReviewed(renamed.preview) as PatternRemovalResult.Changed
        assertEquals(listOf("new-guide"), addedGuide.preview.guideIds)
        assertNotNull(db.libraryDao().observeById("pattern").first())
    }

    @Test fun failedDeleteRollsBackGuideAndProjectAssociations() = runBlocking<Unit> {
        pattern()
        project("Project")
        guide("guide")
        db.materialsDao().upsertPatternLink(ProjectPatternLinkEntity("Project", "pattern"))
        val preview = db.libraryDao().previewRemoval("pattern")!!
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_pattern_removal AFTER DELETE ON library_items
            BEGIN SELECT RAISE(ABORT, 'Test rollback'); END""")
        var failed = false
        try { db.libraryDao().removeReviewed(preview) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(preview, db.libraryDao().previewRemoval("pattern"))
        assertEquals("pattern", db.guideDao().getGuide("guide")!!.libraryItemId)
        assertEquals(1, db.materialsDao().observeAllPatternLinks().first().size)
    }
}
