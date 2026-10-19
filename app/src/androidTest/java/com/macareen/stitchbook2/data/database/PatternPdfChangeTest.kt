package com.macareen.stitchbook2.data.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.PatternPdfChangeResult
import com.macareen.stitchbook2.domain.model.PickedDocument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PatternPdfChangeTest {
    private lateinit var db: StitchbookDatabase
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            StitchbookDatabase::class.java).build()
    }
    @After fun close() = db.close()
    private val original = LibraryItemEntity("pattern", "Sample pattern", "TUNISIAN_CROCHET", null, null, "", "My notes", false, 0, 0,
        pdfUri = "content://original", pdfFileName = "original.pdf", pdfLastViewedPage = 4)

    @Test fun replaceAndUnlinkKeepGuideDraftRevisionPieceAndCompletedWork() = runBlocking<Unit> {
        val dao = db.libraryDao()
        dao.upsert(original)
        db.projectDao().upsert(ProjectEntity("project", "Project", "TUNISIAN_CROCHET", "OTHER", "ACTIVE", null, 0, 0))
        val draft = GuideDraftEntity("draft", "guide", null, 0, 0, 0)
        db.guideDao().insertGuideWithDraft(GuideEntity("guide", null, "pattern", null, "Guide", null, 0, 0, false), draft)
        db.guideDao().replaceDraft(draft.copy(version = 1), 0, listOf(DraftNodeEntity(
            "draft", "step", null, 0, "INSTRUCTION", null, "Work the next pass.", null, null, null, null, null)))
        db.guideDao().publishDraft("guide", "revision", 1)
        db.guideDao().insertProjectGuideLink(ProjectGuideLinkEntity("project", "guide"))
        db.executionDao().createPiece(ProjectPieceEntity("piece", "project", "guide", "Panel", false, 0, 0))
        db.executionDao().createExecution("guide", "revision", "work", 2, projectId = "project", pieceId = "piece")
        db.executionDao().applyComplete("work", 0, 3)
        val guideBefore = db.guideDao().getGuide("guide")
        val draftBefore = db.guideDao().getDraft("guide")
        val versionsBefore = db.guideDao().getRevisions("guide")
        val workBefore = db.executionDao().getScopeExecutions("guide", "project", "piece")
        val pieceBefore = db.executionDao().getPiece("piece")
        assertEquals(PatternPdfChangeResult.Applied, dao.changePdfReviewed(original.toDomain(), PickedDocument("content://new", "new.pdf"), 5))
        val replaced = dao.observeById("pattern").first()!!
        assertEquals(original.copy(pdfUri = "content://new", pdfFileName = "new.pdf", pdfLastViewedPage = null, updatedAt = 5), replaced)
        assertEquals(PatternPdfChangeResult.Applied, dao.changePdfReviewed(replaced.toDomain(), null, 6))
        assertEquals(original.copy(pdfUri = null, pdfFileName = null, pdfLastViewedPage = null, updatedAt = 6), dao.observeById("pattern").first())
        assertEquals(guideBefore, db.guideDao().getGuide("guide"))
        assertEquals(draftBefore, db.guideDao().getDraft("guide"))
        assertEquals(versionsBefore, db.guideDao().getRevisions("guide"))
        assertEquals(workBefore, db.executionDao().getScopeExecutions("guide", "project", "piece"))
        assertEquals(pieceBefore, db.executionDao().getPiece("piece"))
    }

    @Test fun fullSnapshotRejectsEditsAndMissingPatternWhileSameDocumentKeepsPage() = runBlocking<Unit> {
        val dao = db.libraryDao()
        dao.upsert(original)
        val changed = original.copy(notes = "New note") // timestamp deliberately unchanged
        dao.upsert(changed)
        assertEquals(PatternPdfChangeResult.Changed(changed.toDomain()), dao.changePdfReviewed(original.toDomain(), null, 10))
        assertEquals(changed, dao.observeById("pattern").first())
        assertEquals(PatternPdfChangeResult.Applied, dao.changePdfReviewed(changed.toDomain(), PickedDocument("content://original", "renamed.pdf"), 10))
        assertEquals(4, dao.observeById("pattern").first()!!.pdfLastViewedPage)
        dao.delete(original)
        assertEquals(PatternPdfChangeResult.Missing, dao.changePdfReviewed(changed.toDomain(), PickedDocument("content://new", "new.pdf"), 11))
        assertNull(dao.observeById("pattern").first())
    }

    @Test fun metadataUpdatePreservesPdfAndRejectsConcurrentChangeOrRemovedPattern() = runBlocking<Unit> {
        val dao = db.libraryDao()
        dao.upsert(original)
        val updated = original.toDomain().copy(title = "Personal title", craft = com.macareen.stitchbook2.domain.model.Craft.CROCHET,
            gauge = "12 rounds", sizes = "M", yardageRequired = 321.0, updatedAt = 8)
        assertTrue(dao.saveIfUnchanged(original.toDomain(), updated))
        assertEquals(updated, dao.observeById("pattern").first()!!.toDomain())
        assertEquals("content://original", updated.pdfUri)
        assertFalse(dao.saveIfUnchanged(original.toDomain(), original.toDomain().copy(title = "Stale title")))
        assertEquals(updated, dao.observeById("pattern").first()!!.toDomain())
        dao.delete(original)
        assertFalse(dao.saveIfUnchanged(updated, updated.copy(notes = "Do not recreate")))
        assertNull(dao.observeById("pattern").first())
    }

    @Test fun writeFailureRollsBackTheEntireLink() = runBlocking<Unit> {
        val dao = db.libraryDao()
        dao.upsert(original)
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_pdf_change AFTER UPDATE ON library_items
            BEGIN SELECT RAISE(ABORT, 'Test rollback'); END""")
        var failed = false
        try { dao.changePdfReviewed(original.toDomain(), null, 10) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(original, dao.observeById("pattern").first())
    }
}
