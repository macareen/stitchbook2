package com.macareen.stitchbook2.feature.library

import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.GuideDefinition
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.domain.guide.DraftId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.guide.GuideDraft
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.ExtractedLine
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.parsing.SourceReference
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternGuidesViewModelTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val pattern = LibraryItem(
        id = "pattern",
        title = "Seaside Cardigan",
        craft = Craft.KNITTING,
        author = null,
        sourceUrl = null,
        tags = emptyList(),
        notes = null,
        bookmarked = false,
        createdAt = 0,
        updatedAt = 0
    )

    @Test
    fun showsThePatternsGuidesBySizeAndWhetherTheyAreReady() {
        val guides = FakePatternGuides()
        guides.add(guide("m", "M"), published = true)
        guides.add(guide("l", "L"), published = false)
        val viewModel = viewModel(guides)

        val content = viewModel.uiState.value as PatternGuidesUiState.Content
        assertEquals(listOf("M" to true, "L" to false), content.guides.map { it.guide.sizeLabel to it.isPublished })
    }

    @Test
    fun aNewSizeGuideIsNamedAfterThePatternAndOpensForEditing() {
        val guides = FakePatternGuides()
        val viewModel = viewModel(guides)

        viewModel.createGuide(" S ", "")

        assertEquals("Seaside Cardigan · S", guides.created.single().name)
        assertEquals("created-1", viewModel.createdGuideId.value)
        viewModel.consumeCreatedGuide()
        assertEquals(null, viewModel.createdGuideId.value)
    }

    @Test
    fun aGuideFilledFromThePatternReadsItsPdfForThatSize() {
        val guides = FakePatternGuides()
        val read = mutableListOf<String>()
        val document = ExtractedDocument(1, listOf(ExtractedLine("Cast on 60 (66) sts.", SourceReference(1, 1))))
        val viewModel = PatternGuidesViewModel(
            "pattern",
            FakeLibrary(listOf(pattern.copy(pdfUri = "content://pattern.pdf", sizes = "S, M"))),
            guides,
            scope,
            createGuideFromPdf = CreateGuideFromPdfUseCase(FixedText(document), guides, newNodeId = { "n" }),
            readPatternFile = { uri -> read += uri; ByteArray(1) }
        ).also { vm -> scope.launch { vm.uiState.collect {} } }

        assertEquals(listOf("S", "M"), (viewModel.uiState.value as PatternGuidesUiState.Content).sizeChoices)
        viewModel.createGuide("M", "", fromPattern = true)

        // Text extraction runs on the IO dispatcher, so wait for the new guide.
        val createdId = runBlocking { withTimeout(5_000) { viewModel.createdGuideId.first { it != null } } }

        assertEquals(listOf("content://pattern.pdf"), read)
        assertEquals("created-1", createdId)
        assertEquals("Cast on 66 sts. (p.1)", guides.savedDraft?.nodes?.single()?.instructionText)
    }

    @Test
    fun aPatternFileThatCantBeOpenedSaysSoAndCreatesNothing() {
        val guides = FakePatternGuides()
        val viewModel = PatternGuidesViewModel(
            "pattern",
            FakeLibrary(listOf(pattern.copy(pdfUri = "content://gone.pdf"))),
            guides,
            scope,
            createGuideFromPdf = CreateGuideFromPdfUseCase(FixedText(ExtractedDocument(0, emptyList())), guides, newNodeId = { "n" }),
            readPatternFile = { null }
        ).also { vm -> scope.launch { vm.uiState.collect {} } }

        viewModel.createGuide("M", "", fromPattern = true)

        assertEquals(PatternPdfProblem.UNREADABLE, (viewModel.uiState.value as PatternGuidesUiState.Content).pdfProblem)
        assertTrue(guides.created.isEmpty())
    }

    @Test
    fun aMissingPatternSaysSo() {
        val viewModel = PatternGuidesViewModel("gone", FakeLibrary(emptyList()), FakePatternGuides(), scope)
        scope.launch { viewModel.uiState.collect {} }

        assertTrue(viewModel.uiState.value is PatternGuidesUiState.Missing)
    }

    private fun viewModel(guides: FakePatternGuides): PatternGuidesViewModel =
        PatternGuidesViewModel("pattern", FakeLibrary(listOf(pattern)), guides, scope).also { vm ->
            scope.launch { vm.uiState.collect {} }
        }

    private fun guide(id: String, size: String) =
        Guide(GuideId(id), null, "Seaside Cardigan · $size", null, 0, 0, libraryItemId = "pattern", sizeLabel = size)
}

private class FakeLibrary(items: List<LibraryItem>) : LibraryRepository {
    private val flow = MutableStateFlow(items)
    override fun observeLibraryItems(): Flow<List<LibraryItem>> = flow
    override fun observeLibraryItem(id: String): Flow<LibraryItem?> = flow.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun saveLibraryItem(item: LibraryItem) = Unit
    override suspend fun deleteLibraryItem(item: LibraryItem) = Unit
}

private class FakePatternGuides : GuideRepository {
    private val guides = MutableStateFlow<List<Guide>>(emptyList())
    private val published = mutableSetOf<String>()
    val created = mutableListOf<Guide>()
    private val drafts = mutableMapOf<String, GuideDraft>()
    var savedDraft: GuideDraft? = null
        private set

    fun add(guide: Guide, published: Boolean) {
        guides.value = guides.value + guide
        if (published) this.published += guide.id.value
    }

    override fun observePatternGuides(libraryItemId: String): Flow<List<Guide>> =
        guides.map { list -> list.filter { it.libraryItemId == libraryItemId } }

    override suspend fun createPatternGuide(libraryItemId: String, sizeLabel: String, name: String): Guide {
        val guide = Guide(GuideId("created-${created.size + 1}"), null, name, null, 0, 0, libraryItemId, sizeLabel.trim())
        created += guide
        guides.value = guides.value + guide
        drafts[guide.id.value] = GuideDraft(DraftId("d-${created.size}"), guide.id, null, 0, 0, 0, emptyList(), emptyList())
        return guide
    }

    override suspend fun getLatestRevision(guideId: GuideId): DefinitionRevision? =
        if (guideId.value in published) {
            DefinitionRevision(DefinitionRevisionId("r"), guideId, 1, 0, GuideDefinition(guideId, DefinitionRevisionId("r"), emptyList(), emptyList()))
        } else {
            null
        }

    override fun observeGuides(projectId: String): Flow<List<Guide>> = flowOf(emptyList())
    override suspend fun getGuide(guideId: GuideId): Guide? = unused()
    override suspend fun createGuide(projectId: String, name: String, notes: String?): Guide = unused()
    override suspend fun updateGuideMetadata(guideId: GuideId, name: String, notes: String?): Guide? = unused()
    override suspend fun deleteGuide(guideId: GuideId): Unit = unused()
    override suspend fun loadDraft(guideId: GuideId): GuideDraft? = drafts[guideId.value]
    override suspend fun saveDraft(draft: GuideDraft): GuideDraft = draft.also { savedDraft = it }
    override suspend fun createDraftFromLatestRevision(guideId: GuideId): GuideDraft = unused()
    override suspend fun listRevisions(guideId: GuideId): List<DefinitionRevision> = unused()
    override suspend fun loadRevision(revisionId: DefinitionRevisionId): DefinitionRevision? = unused()
    override suspend fun publishDraft(guideId: GuideId): DefinitionRevision = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Not used by the pattern screen")
}

private class FixedText(private val document: ExtractedDocument) : PdfTextExtractor {
    override suspend fun extract(input: InputStream): ExtractedDocument = document
}
