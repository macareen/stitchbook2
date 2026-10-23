package com.macareen.stitchbook2.data.parsing

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.backup.*
import com.macareen.stitchbook2.data.database.*
import com.macareen.stitchbook2.data.repository.*
import com.macareen.stitchbook2.domain.backup.*
import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import com.macareen.stitchbook2.feature.draft.*
import com.macareen.stitchbook2.feature.focus.*
import java.io.ByteArrayInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class PdfTargetBodyWorkflowTest {
    @Test fun reviewedPdfBodyTraversesCyclesAndRetainsOldProjectPieceRevisionAndPortableWork() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, StitchbookDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, StitchbookDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val project = ProjectEntity("project", "Sample", "KNITTING", "OTHER", "ACTIVE", null, 0, 0)
            val pattern = LibraryItemEntity("pattern", "Sample", "KNITTING", null, null, "", null, false, 0, 0, pdfUri = "content://synthetic/body.pdf")
            db.projectDao().upsert(project); db.libraryDao().upsert(pattern)
            val guides = LocalGuideRepository(db.guideDao())
            val executions = LocalExecutionRepository(db.executionDao())
            val library = LocalLibraryRepository(db.libraryDao())
            val extractor = PdfBoxTextExtractor(context, object : PdfPageOcr {
                override suspend fun recognizeText(page: Bitmap): List<String> = emptyList()
            })
            val bytes = PdfFixtures.buildSyntheticPages(listOf(listOf("Sizes: S, M, L", "Section: Body"),
                listOf("Row 1: Knit 8 (10, 12) stitches.", "Row 2: Purl all stitches.", "Section: Finishing", "Bind off.")))
            val original = bytes.copyOf()
            val result = CreateGuideFromPdfUseCase(extractor, guides, { java.util.UUID.randomUUID().toString() }, library)
                .forPattern("pattern", "M", null, "Sample M", ByteArrayInputStream(bytes)) as CreateGuideFromPdfUseCase.Result.Success
            val oldDraft = guides.loadDraft(result.guideId)!!
            assertTrue(oldDraft.nodes.all { it.target == null })
            val oldRevision = guides.publishDraft(result.guideId)
            guides.useGuideInProject("project", result.guideId)
            val oldWork = executions.createExecution(result.guideId, oldRevision.id, "project")
            val editor = DraftEditorViewModel(result.guideId, guides, executions, externalScope = scope, projectId = "project")
            val content = withTimeout(10_000) { editor.uiState.first { it is DraftEditorUiState.Content } } as DraftEditorUiState.Content
            val body = content.rows.single { it.node.type == DraftNodeType.SECTION && it.node.title == "Body" }.node
            assertTrue(editor.targetReviewText(body).contains("10 stitches"))
            // Ordinary parsed rows retain page-only provenance; condition/review steps also retain lines.
            val bodyInstructions = content.rows.filter { it.node.type == DraftNodeType.INSTRUCTION && it.node.instructionText!!.contains("(p.2)") }
            assertTrue(bodyInstructions.size >= 2)
            assertTrue(editor.targetReviewText(body).contains("(p.2)"))
            val target = ManualTarget(TargetUnit.CENTIMETRES, 12.0)
            val saved = CompletableDeferred<Unit>()
            editor.setTarget(body, target, content.draftVersion) { saved.complete(Unit) }
            withTimeout(10_000) { saved.await() }
            val revisedDraft = guides.loadDraft(result.guideId)!!
            assertEquals(oldDraft.nodes.filter { it.type == DraftNodeType.INSTRUCTION }, revisedDraft.nodes.filter { it.type == DraftNodeType.INSTRUCTION })
            val revision = guides.publishDraft(result.guideId)
            assertEquals(oldRevision, guides.loadRevision(oldRevision.id))
            assertEquals(oldWork, executions.loadExecution(oldWork.state.executionId))
            val work = executions.applyReviewedRevision(ReviewedRevisionChange("project", oldWork, oldRevision.definition,
                oldRevision.revisionNumber, revision.definition, revision.revisionNumber), emptySet())
            val piece = executions.createPiece("project", result.guideId, "Second piece")
            val other = executions.createPieceExecution(piece.id, revision.id)
            fun model() = GuideFocusViewModel(result.guideId, guides, executions, LocalCounterRepository(db.counterDao()), externalScope = scope, projectId = "project")
            var focus = model()
            suspend fun state(version: Long = -1) = withTimeout(10_000) {
                focus.uiState.first { it is GuideFocusUiState.InProgress && !it.isBusy && it.version > version } as GuideFocusUiState.InProgress
            }
            var current = state()
            assertEquals("1 / 2", current.targetBodyPosition)
            assertFalse(current.targetCanConfirm)
            focus.onObserveTarget(8.0); current = state(current.version)
            focus.onComplete(); current = state(current.version)
            assertEquals("2 / 2", current.targetBodyPosition)
            assertEquals(1, current.targetWork.cycle)
            focus.onComplete(); current = state(current.version)
            assertEquals("1 / 2", current.targetBodyPosition)
            assertEquals(2, current.targetWork.cycle)
            focus.onComplete(); current = state(current.version)
            focus.onObserveTarget(12.0); current = state(current.version)
            focus = model(); current = state()
            assertEquals("2 / 2", current.targetBodyPosition)
            assertEquals(2, current.targetWork.observations.size)
            focus.onJumpTo(work.state.currentAddress!!); current = state(current.version)
            assertEquals("1 / 2", current.targetBodyPosition)
            assertFalse(current.targetCanConfirm)
            focus.onComplete(); current = state(current.version)
            assertEquals("2 / 2", current.targetBodyPosition)
            assertEquals(2, current.targetWork.observations.size)
            focus.onConfirmTarget(); current = state(current.version)
            assertEquals("Bind off.", current.instructionText)
            focus.onPrevious(); current = state(current.version)
            assertEquals("2 / 2", current.targetBodyPosition)
            assertFalse(current.targetWork.reached)
            focus.onConfirmTarget(); current = state(current.version)
            var finalWork = executions.loadExecution(work.state.executionId)!!
            assertEquals(other, executions.loadExecution(other.state.executionId))
            assertEquals(oldWork.state, executions.loadExecution(oldWork.state.executionId)!!.state)
            // Save an unfinished body on its immutable revision, then explicitly return to it.
            finalWork = executions.applyPrevious(finalWork.state.executionId, finalWork.version).execution
            val nextDraft = guides.createDraftFromLatestRevision(result.guideId)
            guides.saveDraft(nextDraft.copy(nodes = nextDraft.nodes.map { if (it.id == body.id) it.copy(target = target.copy(value = 15.0)) else it }))
            val nextRevision = guides.publishDraft(result.guideId)
            val nextWork = executions.applyReviewedRevision(ReviewedRevisionChange("project", finalWork, revision.definition,
                revision.revisionNumber, nextRevision.definition, nextRevision.revisionNumber), emptySet())
            assertTrue(nextWork.state.targetWork.isEmpty())
            assertTrue(nextWork.state.completedAddresses.isEmpty())
            val savedBody = executions.loadExecution(finalWork.state.executionId)!!
            finalWork = executions.resumeReviewedPlace(ReviewedSavedPlace("project", nextWork, nextRevision.definition, savedBody, revision.definition))
            assertEquals(savedBody.state, finalWork.state)
            assertEquals(2, finalWork.state.targetWork.values.single().cycle)
            assertEquals(2, finalWork.state.targetWork.values.single().observations.size)
            val snapshot = BackupSnapshot(CURRENT_BACKUP_FORMAT_VERSION, projects = listOf(project.toDomain()),
                libraryItems = library.observeLibraryItems().first()).withGuideGraph(RoomGuideBackupStore(db.guideBackupDao()).load())
            val decoded = decodeBackup(encodeBackup(snapshot, 10))
            assertEquals(snapshot, decoded)
            assertTrue(validateBackup(decoded, BackupSnapshot(CURRENT_BACKUP_FORMAT_VERSION), RestoreMode.MERGE).toString(),
                validateBackup(decoded, BackupSnapshot(CURRENT_BACKUP_FORMAT_VERSION), RestoreMode.MERGE).isEmpty())
            val markdown = projectMarkdown(project.toDomain(), decoded, 10)
            assertTrue(markdown.contains("Cycle 2: 12 cm"))
            assertTrue(markdown.contains("Step ID:"))
            assertTrue(markdown.contains("Purl all stitches."))
            assertTrue(markdown.contains("(p.2)"))
            destination.projectDao().upsert(project); destination.libraryDao().upsert(pattern)
            RoomGuideBackupStore(destination.guideBackupDao()).replace(decoded.guideGraph()!!)
            assertEquals(finalWork, LocalExecutionRepository(destination.executionDao()).loadExecution(work.state.executionId))
            assertEquals(revision, LocalGuideRepository(destination.guideDao()).loadRevision(revision.id))
            assertArrayEquals(original, bytes)
        } finally { scope.cancel(); destination.close(); db.close() }
    }
}
