package com.macareen.stitchbook2.data.parsing

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.database.*
import com.macareen.stitchbook2.data.repository.*
import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.parsing.*
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import com.macareen.stitchbook2.feature.focus.*
import java.io.ByteArrayInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** Original synthetic PDF -> actual extractor/size parser/Room publication -> actual Focus ViewModel. */
class PdfGuideWorkflowTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val noTextOcr = object : PdfPageOcr {
        override suspend fun recognizeText(page: Bitmap): List<String> = emptyList()
    }
    @Test fun reviewedColumnsUseCoverSizesAndPublishOnlySelectedWork() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, StitchbookDatabase::class.java).build()
        try {
            db.libraryDao().upsert(LibraryItemEntity("pattern", "Sample wrap", "KNITTING", null, null, "", null, false, 0, 0, pdfUri = "content://synthetic/sample.pdf"))
            val guides = LocalGuideRepository(db.guideDao())
            val useCase = CreateGuideFromPdfUseCase(PdfBoxTextExtractor(context, noTextOcr), guides,
                { java.util.UUID.randomUUID().toString() }, LocalLibraryRepository(db.libraryDao()))
            val bytes = PdfFixtures.buildColumnReviewPdf()
            val original = useCase.readSource(ByteArrayInputStream(bytes))
            val preview = useCase.reviewSource(ByteArrayInputStream(bytes), original, PdfSourceSelection(listOf(
                PdfSourceRegion(2, 0, 0, 50, 50), PdfSourceRegion(2, 50, 0, 100, 50))))
            assertTrue(guides.observePatternGuides("pattern").first().isEmpty())
            val result = useCase.forPattern("pattern", "M", null, "Sample M", ByteArrayInputStream(bytes), preview) as CreateGuideFromPdfUseCase.Result.Success
            assertEquals(0, result.issueCount)
            val draft = guides.loadDraft(result.guideId)!!
            assertEquals(listOf("Cast on 24 sts. (p.2)", "Row 1: Knit. (p.2)", "Row 2: Purl. (p.2)", "Bind off. (p.2)"),
                draft.nodes.mapNotNull { it.instructionText })
            assertTrue(guides.getGuide(result.guideId)!!.notes!!.contains("left 50%"))
            val revision = guides.publishDraft(result.guideId)
            val traversal = GuideTraversal(GuideDefinitionValidator.validate(revision.definition))
            assertEquals(4, traversal.occurrences().count())
            assertFalse(draft.nodes.any { it.instructionText?.contains("Advertisement") == true })
            assertEquals("20 sts over 10 cm", db.libraryDao().observeById("pattern").first()!!.gauge)
        } finally { db.close() }
    }
    @Test fun selectedSizeAndPageReferencesSurviveRepeatedCyclesBackJumpAndReload() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, StitchbookDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            db.projectDao().upsert(ProjectEntity("project", "Sample project", "KNITTING", "OTHER", "ACTIVE", null, 0, 0))
            db.libraryDao().upsert(LibraryItemEntity("pattern", "Sample wrap", "KNITTING", null, null, "", null, false, 0, 0, pdfUri = "content://synthetic/sample.pdf"))
            val guides = LocalGuideRepository(db.guideDao())
            val executions = LocalExecutionRepository(db.executionDao())
            val useCase = CreateGuideFromPdfUseCase(PdfBoxTextExtractor(context, noTextOcr), guides,
                { java.util.UUID.randomUUID().toString() }, LocalLibraryRepository(db.libraryDao()))
            val bytes = PdfFixtures.buildSyntheticPages(listOf(listOf("Sizes: S, M, L", "Section: Body", "Cast on 20 (24, 28) sts.", "Row 1: Knit."),
                listOf("Row 2: Purl.", "Repeat rows 1-2 2 times.")))
            val result = useCase.forPattern("pattern", "M", null, "Sample wrap M", ByteArrayInputStream(bytes)) as CreateGuideFromPdfUseCase.Result.Success
            assertEquals(0, result.issueCount)
            val draft = guides.loadDraft(result.guideId)!!
            assertTrue(draft.nodes.any { it.instructionText == "Cast on 24 sts. (p.1)" })
            assertFalse(draft.nodes.any { it.instructionText?.contains("20 (24, 28)") == true })
            val revision = guides.publishDraft(result.guideId)
            guides.useGuideInProject("project", result.guideId)
            val work = executions.createExecution(result.guideId, revision.id, "project")
            fun model() = GuideFocusViewModel(result.guideId, guides, executions, LocalCounterRepository(db.counterDao()),
                externalScope = scope, projectId = "project")
            var focus = model()
            suspend fun state(version: Long = -1) = withTimeout(10_000) {
                focus.uiState.first { it is GuideFocusUiState.InProgress && it.version > version && !it.isBusy } as GuideFocusUiState.InProgress
            }
            var current = state()
            assertEquals(1, current.pattern!!.page)
            repeat(3) {
                val oldVersion = current.version
                focus.onComplete(); current = state(oldVersion)
            }
            assertEquals("Row 1: Knit.", current.instructionText)
            assertEquals(1, current.pattern!!.page) // second cycle returns from page2 to page1
            var oldVersion = current.version
            focus.onPrevious(); current = state(oldVersion)
            assertEquals(2, current.pattern!!.page)
            val traversal = GuideTraversal(GuideDefinitionValidator.validate(revision.definition))
            oldVersion = current.version
            focus.onJumpTo(traversal.occurrences().last().address); current = state(oldVersion)
            assertEquals(2, current.pattern!!.page)
            val saved = executions.loadExecution(work.state.executionId)!!
            focus = model()
            val restored = state()
            assertEquals(traversal.occurrences().last().address, saved.state.currentAddress)
            assertEquals(current.instructionText, restored.instructionText)
            assertEquals(current.positions, restored.positions)
            assertEquals(current.breadcrumbs, restored.breadcrumbs)
            assertEquals(saved.version, restored.version)
            assertEquals(2, restored.pattern!!.page)
        } finally { scope.cancel(); db.close() }
    }

    @Test fun failedOcrOnAnOtherwiseReadablePdfBecomesAFirstReviewStep() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, StitchbookDatabase::class.java).build()
        try {
            db.projectDao().upsert(ProjectEntity("project", "Sample", "CROCHET", "OTHER", "ACTIVE", null, 0, 0))
            val guides = LocalGuideRepository(db.guideDao())
            val failedOcr = object : PdfPageOcr {
                override suspend fun recognizeText(page: Bitmap): List<String> = error("Synthetic OCR failure")
            }
            val useCase = CreateGuideFromPdfUseCase(PdfBoxTextExtractor(context, failedOcr), guides,
                { java.util.UUID.randomUUID().toString() })
            val bytes = PdfFixtures.buildSyntheticPages(listOf(listOf("Round 1: Work 6 stitches."), emptyList(), listOf("Fasten off.")))
            val result = useCase("project", "Sample", ByteArrayInputStream(bytes)) as CreateGuideFromPdfUseCase.Result.Success
            assertEquals(1, result.issueCount)
            val draft = guides.loadDraft(result.guideId)!!
            val first = draft.nodes.first { it.id == draft.rootNodeIds.first() }.instructionText!!
            assertTrue(first.contains("No readable text was found on PDF page 2"))
            assertTrue(first.endsWith("(p.2 l.1)"))
            assertEquals(3, draft.nodes.count { it.type == com.macareen.stitchbook2.domain.guide.DraftNodeType.INSTRUCTION })
        } finally { db.close() }
    }
}
