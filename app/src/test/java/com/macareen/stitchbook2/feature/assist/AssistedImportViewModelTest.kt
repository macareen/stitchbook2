package com.macareen.stitchbook2.feature.assist

import com.macareen.stitchbook2.data.parsing.StructuredGuideJsonDecoder
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.domain.guide.DraftId
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.guide.GuideDraft
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.ExtractedLine
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractionException
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.parsing.SourceReference
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromStructuredGuideUseCase
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistedImportViewModelTest {

    // Every fake below completes without suspending, so Unconfined settles state synchronously.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun theRequestUsesTheProjectsCraftAndThePdfText() {
        val viewModel = viewModel(extractor = FakeExtractor(document("Row 1: k2, p2.")))

        viewModel.updateGuideName("Scarf")
        viewModel.readPdf(byteArrayOf(1))

        val state = viewModel.uiState.value
        assertEquals("[page 1]\nRow 1: k2, p2.", state.patternText)
        assertTrue(state.prompt.contains("knitting pattern"))
        assertTrue(state.prompt.contains("Guide name: Scarf"))
    }

    @Test
    fun aPdfWithoutTextOrThatFailsIsReportedNotHidden() {
        val empty = viewModel(extractor = FakeExtractor(ExtractedDocument(pageCount = 1, lines = emptyList())))
        empty.readPdf(byteArrayOf(1))
        assertEquals(PatternTextError.NO_TEXT, empty.uiState.value.patternTextError)

        val broken = viewModel(extractor = FakeExtractor(failure = PdfTextExtractionException("bad")))
        broken.readPdf(byteArrayOf(1))
        assertEquals(PatternTextError.UNREADABLE, broken.uiState.value.patternTextError)
        assertFalse(broken.uiState.value.isReadingPdf)
    }

    @Test
    fun aValidReplyFillsAnEmptyNameAndCreatesADraftOnlyWhenAsked() {
        val guides = FakeGuideRepository()
        val viewModel = viewModel(guides = guides)

        viewModel.updateReply("""{"name": "Cowl", "steps": [{"type": "rows", "unit": "rounds", "from": 1, "to": 20, "text": "K1, p1 around."}]}""")
        viewModel.checkReply()

        assertTrue(viewModel.uiState.value.check is ReplyCheck.Ready)
        assertEquals("Cowl", viewModel.uiState.value.guideName)
        assertNull(guides.saved)

        viewModel.createDraft()

        assertEquals("guide-1", viewModel.createdGuideId.value)
        assertEquals("Cowl", guides.createdName)
        val range = guides.saved!!.nodes.first { it.type == DraftNodeType.RANGE }
        assertEquals(20, range.rangeEndInclusive)
    }

    @Test
    fun anInvalidReplyShowsProblemsAndCannotCreate() {
        val guides = FakeGuideRepository()
        val viewModel = viewModel(guides = guides)

        viewModel.updateReply("""{"steps": [{"type": "rows", "from": 0, "text": "Knit."}]}""")
        viewModel.checkReply()
        viewModel.createDraft()

        val check = viewModel.uiState.value.check as ReplyCheck.Problems
        assertEquals("steps[0].from", check.problems.single().path)
        assertNull(viewModel.createdGuideId.value)
        assertNull(guides.saved)
    }

    @Test
    fun editingTheReplyClearsTheEarlierCheck() {
        val viewModel = viewModel()
        viewModel.updateReply("""{"steps": ["Knit."]}""")
        viewModel.checkReply()

        viewModel.updateReply("""{"steps": ["Purl."]}""")

        assertNull(viewModel.uiState.value.check)
    }

    @Test
    fun aSaveFailureKeepsTheReplyAndSaysSo() {
        val viewModel = viewModel(guides = FakeGuideRepository(failOnCreate = true))
        viewModel.updateReply("""{"steps": ["Knit."]}""")
        viewModel.checkReply()

        viewModel.createDraft()

        assertTrue(viewModel.uiState.value.createFailed)
        assertFalse(viewModel.uiState.value.isCreating)
        assertEquals("""{"steps": ["Knit."]}""", viewModel.uiState.value.reply)
    }

    private fun viewModel(
        extractor: PdfTextExtractor = FakeExtractor(document("Row 1: knit.")),
        guides: FakeGuideRepository = FakeGuideRepository()
    ): AssistedImportViewModel {
        var next = 0
        return AssistedImportViewModel(
            projectId = PROJECT.id,
            projectRepository = FakeProjectRepository(),
            textExtractor = extractor,
            decoder = StructuredGuideJsonDecoder(),
            createGuide = CreateGuideFromStructuredGuideUseCase(guides) { "node-${next++}" },
            ioDispatcher = Dispatchers.Unconfined,
            externalScope = scope
        )
    }

    private fun document(vararg lines: String) = ExtractedDocument(
        pageCount = 1,
        lines = lines.mapIndexed { index, text -> ExtractedLine(text, SourceReference(1, index + 1)) }
    )
}

private val PROJECT = Project(
    id = "project",
    name = "Scarf",
    craft = Craft.KNITTING,
    projectType = ProjectType.OTHER,
    status = ProjectStatus.ACTIVE,
    notes = null,
    createdAt = 0,
    updatedAt = 0
)

private class FakeProjectRepository : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> = flowOf(listOf(PROJECT))
    override fun observeProject(id: String): Flow<Project?> = flowOf(PROJECT.takeIf { it.id == id })
    override suspend fun saveProject(project: Project) = Unit
    override suspend fun deleteProject(project: Project) = Unit
}

private class FakeExtractor(
    private val document: ExtractedDocument? = null,
    private val failure: Exception? = null
) : PdfTextExtractor {
    override suspend fun extract(input: InputStream): ExtractedDocument {
        failure?.let { throw it }
        return requireNotNull(document)
    }
}

private class FakeGuideRepository(private val failOnCreate: Boolean = false) : GuideRepository {
    var createdName: String? = null
    var saved: GuideDraft? = null

    override fun observeGuides(projectId: String): Flow<List<Guide>> = flowOf(emptyList())
    override suspend fun getGuide(guideId: GuideId): Guide? = null

    override suspend fun createGuide(projectId: String, name: String, notes: String?): Guide {
        check(!failOnCreate) { "Storage is full." }
        createdName = name
        return Guide(GuideId("guide-1"), projectId, name, notes, createdAt = 0, updatedAt = 0)
    }

    override suspend fun loadDraft(guideId: GuideId): GuideDraft = GuideDraft(
        id = DraftId("draft-1"),
        guideId = guideId,
        baseRevisionId = null,
        createdAt = 0,
        updatedAt = 0,
        version = 0,
        rootNodeIds = emptyList(),
        nodes = emptyList()
    )

    override suspend fun saveDraft(draft: GuideDraft): GuideDraft = draft.also { saved = it }

    override suspend fun updateGuideMetadata(guideId: GuideId, name: String, notes: String?): Guide? = unused()
    override suspend fun deleteGuide(guideId: GuideId): Unit = unused()
    override suspend fun createDraftFromLatestRevision(guideId: GuideId): GuideDraft = unused()
    override suspend fun listRevisions(guideId: GuideId): List<DefinitionRevision> = unused()
    override suspend fun loadRevision(revisionId: DefinitionRevisionId): DefinitionRevision? = unused()
    override suspend fun getLatestRevision(guideId: GuideId): DefinitionRevision? = unused()
    override suspend fun publishDraft(guideId: GuideId): DefinitionRevision = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Not used by assisted import")
}
