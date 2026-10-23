package com.macareen.stitchbook2.data.parsing

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.backup.*
import com.macareen.stitchbook2.data.database.*
import com.macareen.stitchbook2.data.repository.*
import com.macareen.stitchbook2.domain.backup.*
import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import com.macareen.stitchbook2.feature.draft.*
import com.macareen.stitchbook2.feature.focus.*
import java.io.ByteArrayInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** Original synthetic PDFs, real extraction/Room/editor/Focus and portable JSON + Markdown. */
class PdfTargetWorkflowTest {
    @Test fun selectedLengthSurvivesReviewPublicationFocusAndExport() = workflow(
        "Knit all stitches until piece measures 10 (12, 14) cm.", ManualTarget(TargetUnit.CENTIMETRES, 12.0), 8.0)

    @Test fun selectedDecreasingStitchCountSurvivesReviewPublicationFocusAndExport() = workflow(
        "Decrease one stitch every round until 18 (20, 22) stitches remain.", ManualTarget(TargetUnit.STITCHES, 20.0, TargetComparison.AT_MOST), 24.0)

    private fun workflow(raw: String, expected: ManualTarget, before: Double) = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, StitchbookDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, StitchbookDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val projectEntity = ProjectEntity("project", "Sample", "KNITTING", "OTHER", "ACTIVE", null, 0, 0)
            val patternEntity = LibraryItemEntity("pattern", "Sample", "KNITTING", null, null, "", null, false, 0, 0, pdfUri = "content://synthetic/targets.pdf")
            db.projectDao().upsert(projectEntity)
            db.libraryDao().upsert(patternEntity)
            val guides = LocalGuideRepository(db.guideDao())
            val executions = LocalExecutionRepository(db.executionDao())
            val library = LocalLibraryRepository(db.libraryDao())
            val extractor = PdfBoxTextExtractor(context, object : PdfPageOcr {
                override suspend fun recognizeText(page: Bitmap): List<String> = emptyList()
            })
            val useCase = CreateGuideFromPdfUseCase(extractor, guides, { java.util.UUID.randomUUID().toString() }, library)
            val bytes = PdfFixtures.buildSyntheticPages(listOf(listOf("Sizes: S, M, L", "Section: Body"), listOf(raw, "Bind off.")))
            val originalBytes = bytes.copyOf()
            val result = useCase.forPattern("pattern", "M", null, "Sample M", ByteArrayInputStream(bytes)) as CreateGuideFromPdfUseCase.Result.Success
            assertEquals(0, result.issueCount)
            val draft = guides.loadDraft(result.guideId)!!
            assertTrue(draft.nodes.all { it.target == null })
            val oldRevision = guides.publishDraft(result.guideId)
            guides.useGuideInProject("project", result.guideId)
            val oldWork = executions.createExecution(result.guideId, oldRevision.id, "project")
            val editor = DraftEditorViewModel(result.guideId, guides, executions, externalScope = scope, projectId = "project")
            val editorState = withTimeout(10_000) { editor.uiState.first { it is DraftEditorUiState.Content } } as DraftEditorUiState.Content
            val row = editorState.rows.single { it.suggestedTarget != null }
            assertEquals(expected, row.suggestedTarget)
            assertTrue(row.node.instructionText!!.endsWith("(p.2 l.1)"))
            val saved = CompletableDeferred<Unit>()
            editor.setTarget(row.node, row.suggestedTarget, editorState.draftVersion) { saved.complete(Unit) }
            withTimeout(10_000) { saved.await() }
            assertEquals(row.node.instructionText, guides.loadDraft(result.guideId)!!.nodes.single { it.id == row.node.id }.instructionText)
            assertEquals(oldRevision, guides.getLatestRevision(result.guideId))
            val revision = guides.publishDraft(result.guideId)
            assertEquals(oldRevision, guides.loadRevision(oldRevision.id))
            assertEquals(oldWork, executions.loadExecution(oldWork.state.executionId))
            assertTrue(oldRevision.definition.nodes.filterIsInstance<Instruction>().all { it.target == null })
            val work = executions.applyReviewedRevision(ReviewedRevisionChange("project", oldWork,
                oldRevision.definition, oldRevision.revisionNumber, revision.definition, revision.revisionNumber), emptySet())
            val preservedOldWork = executions.loadExecution(oldWork.state.executionId)!!
            assertEquals(oldWork.state, preservedOldWork.state)
            fun model() = GuideFocusViewModel(result.guideId, guides, executions, LocalCounterRepository(db.counterDao()), externalScope = scope, projectId = "project")
            var focus = model()
            suspend fun state(version: Long = -1) = withTimeout(10_000) {
                focus.uiState.first { it is GuideFocusUiState.InProgress && !it.isBusy && it.version > version } as GuideFocusUiState.InProgress
            }
            var current = state()
            val address = executions.loadExecution(work.state.executionId)!!.state.currentAddress!!
            assertEquals(expected, current.target)
            assertEquals(2, current.pattern!!.page)
            focus.onObserveTarget(before); current = state(current.version)
            assertFalse(expected.isReachedBy(current.targetWork.observations.last().value))
            focus.onComplete(); current = state(current.version)
            assertEquals(2, current.targetWork.cycle)
            focus.onObserveTarget(expected.value); current = state(current.version)
            assertEquals(expected, current.target) // observation does not advance
            focus = model(); current = state()
            assertEquals(2, current.targetWork.observations.size)
            focus.onConfirmTarget(); current = state(current.version)
            assertEquals("Bind off.", current.instructionText)
            focus.onPrevious(); current = state(current.version)
            assertEquals(expected, current.target)
            assertEquals(2, current.targetWork.observations.size)
            focus.onConfirmTarget(); current = state(current.version)
            val savedWork = executions.loadExecution(work.state.executionId)!!
            assertTrue(savedWork.state.targetWork.getValue(address).reached)
            assertEquals(preservedOldWork, executions.loadExecution(oldWork.state.executionId))
            val graph = RoomGuideBackupStore(db.guideBackupDao()).load()
            val project = Project("project", "Sample", Craft.KNITTING, ProjectType.OTHER, ProjectStatus.ACTIVE, null, 0, 0)
            val snapshot = BackupSnapshot(CURRENT_BACKUP_FORMAT_VERSION, projects = listOf(project), libraryItems = library.observeLibraryItems().first()).withGuideGraph(graph)
            val decoded = decodeBackup(encodeBackup(snapshot, 10))
            assertEquals(snapshot, decoded)
            assertTrue(validateBackup(decoded, BackupSnapshot(CURRENT_BACKUP_FORMAT_VERSION), RestoreMode.MERGE).isEmpty())
            val markdown = projectMarkdown(project, decoded, 10)
            assertTrue(markdown.contains(raw))
            assertTrue(markdown.contains("(p.2 l.1)"))
            assertTrue(markdown.contains("total cycles unknown"))
            assertTrue(markdown.contains("Cycle 2:"))
            destination.projectDao().upsert(projectEntity)
            destination.libraryDao().upsert(patternEntity)
            RoomGuideBackupStore(destination.guideBackupDao()).replace(decoded.guideGraph()!!)
            assertEquals(savedWork, LocalExecutionRepository(destination.executionDao()).loadExecution(work.state.executionId))
            assertEquals(preservedOldWork, LocalExecutionRepository(destination.executionDao()).loadExecution(oldWork.state.executionId))
            assertArrayEquals(originalBytes, bytes)
            assertEquals(patternEntity.pdfUri, db.libraryDao().observeById("pattern").first()!!.pdfUri)
        } finally { scope.cancel(); destination.close(); db.close() }
    }
}
