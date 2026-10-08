package com.macareen.stitchbook2.domain.usecase

import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.domain.guide.DraftId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.guide.GuideDraft
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.ExtractedLine
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractionException
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.parsing.SourceReference
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateGuideFromPdfUseCaseTest {

    private fun idGenerator(): () -> String {
        var counter = 0
        return { "node-${counter++}" }
    }

    @Test
    fun `a PDF with a text layer creates a guide with a populated draft`() = runBlocking {
        val document = ExtractedDocument(
            pageCount = 1,
            lines = listOf(
                ExtractedLine("Cast on 80 stitches.", SourceReference(1, 1)),
                ExtractedLine("Rows 1-10: Knit all stitches.", SourceReference(1, 2))
            )
        )
        val repository = FakeGuideRepository()
        val useCase = CreateGuideFromPdfUseCase(
            textExtractor = FakePdfTextExtractor(document),
            guideRepository = repository,
            newNodeId = idGenerator()
        )

        val result = useCase("project-1", "Imported pattern", ByteArrayInputStream(ByteArray(0)))

        val success = result as CreateGuideFromPdfUseCase.Result.Success
        assertEquals(0, success.issueCount)
        val savedDraft = repository.lastSavedDraft
        assertTrue(savedDraft != null)
        assertEquals(2, savedDraft!!.rootNodeIds.size)
    }

    @Test
    fun `a pattern guide fills the pattern's empty details but keeps what the user wrote`() = runBlocking {
        val lines = listOf(
            "A cosy hat by Ana Example",
            "A quick weekend knit for chilly mornings.",
            "Gauge: 20 sts = 10 cm",
            "Needles: 4.5 mm circular",
            "Yarn: approx. 200 yds of worsted",
            "Cast on 88 sts.",
            "Rounds 1-10: K2, p2."
        )
        val document = ExtractedDocument(
            pageCount = 1,
            lines = lines.mapIndexed { index, text -> ExtractedLine(text, SourceReference(1, index + 1)) }
        )
        val pattern = LibraryItem(
            id = "pattern-1", title = "Cosy hat", craft = Craft.KNITTING, author = null, sourceUrl = null,
            tags = emptyList(), notes = null, bookmarked = false, createdAt = 0, updatedAt = 0,
            gauge = "My own gauge note"
        )
        val library = FakeLibraryRepository(pattern)
        val repository = FakeGuideRepository()
        val useCase = CreateGuideFromPdfUseCase(
            textExtractor = FakePdfTextExtractor(document),
            guideRepository = repository,
            newNodeId = idGenerator(),
            libraryRepository = library
        )

        useCase.forPattern("pattern-1", "M", null, "Cosy hat · M", ByteArrayInputStream(ByteArray(0)))

        val saved = library.item!!
        assertEquals("Ana Example", saved.author)
        assertEquals("A quick weekend knit for chilly mornings.\n\nYarn: approx. 200 yds of worsted", saved.notes)
        assertEquals("My own gauge note", saved.gauge)
        assertEquals("4.5 mm circular", saved.recommendedTools)
        assertEquals(200.0, saved.yardageRequired!!, 0.0)
        // Only the knitting became steps (the cast-on and the range), plus the note that no size list was found.
        assertEquals(3, repository.lastSavedDraft!!.rootNodeIds.size)
    }

    @Test
    fun `a PDF with no extractable text is reported without creating a guide`() = runBlocking {
        val document = ExtractedDocument(pageCount = 3, lines = emptyList())
        val repository = FakeGuideRepository()
        val useCase = CreateGuideFromPdfUseCase(
            textExtractor = FakePdfTextExtractor(document),
            guideRepository = repository,
            newNodeId = idGenerator()
        )

        val result = useCase("project-1", "Imported pattern", ByteArrayInputStream(ByteArray(0)))

        assertEquals(CreateGuideFromPdfUseCase.Result.NoExtractableText, result)
        assertEquals(0, repository.createGuideCallCount)
    }

    @Test
    fun `an unreadable PDF surfaces the extraction failure without creating a guide`() = runBlocking {
        val repository = FakeGuideRepository()
        val useCase = CreateGuideFromPdfUseCase(
            textExtractor = FailingPdfTextExtractor(),
            guideRepository = repository,
            newNodeId = idGenerator()
        )

        val result = useCase("project-1", "Imported pattern", ByteArrayInputStream(ByteArray(0)))

        assertTrue(result is CreateGuideFromPdfUseCase.Result.ExtractionFailed)
        assertEquals(0, repository.createGuideCallCount)
    }

    @Test
    fun `parsing issues are counted but do not prevent guide creation`() = runBlocking {
        val document = ExtractedDocument(
            pageCount = 1,
            lines = listOf(ExtractedLine("Repeat rows 1-2 6 times.", SourceReference(1, 1)))
        )
        val repository = FakeGuideRepository()
        val useCase = CreateGuideFromPdfUseCase(
            textExtractor = FakePdfTextExtractor(document),
            guideRepository = repository,
            newNodeId = idGenerator()
        )

        val result = useCase("project-1", "Imported pattern", ByteArrayInputStream(ByteArray(0)))

        val success = result as CreateGuideFromPdfUseCase.Result.Success
        assertEquals(1, success.issueCount)
    }
    @Test
    fun `a pattern guide keeps only its size's numbers`() = runBlocking {
        val document = ExtractedDocument(
            pageCount = 1,
            lines = listOf(
                ExtractedLine("Sizes: S (M, L)", SourceReference(1, 1)),
                ExtractedLine("Cast on 60 (66, 72) sts.", SourceReference(1, 2))
            )
        )
        val repository = FakeGuideRepository()
        val useCase = CreateGuideFromPdfUseCase(FakePdfTextExtractor(document), repository, idGenerator())

        val result = useCase.forPattern("pattern-1", "m", null, "Cardigan · M", ByteArrayInputStream(ByteArray(0)))

        assertEquals(0, (result as CreateGuideFromPdfUseCase.Result.Success).issueCount)
        assertEquals(listOf("pattern-1" to "m"), repository.patternGuidesCreated)
        val texts = repository.lastSavedDraft!!.nodes.mapNotNull { it.instructionText }
        assertTrue(texts.toString(), texts.any { it.startsWith("Cast on 66 sts.") })
        // A guide is one size, so the pattern's list of sizes isn't a step.
        assertTrue(texts.toString(), texts.none { it.startsWith("Sizes:") })
    }

    @Test
    fun `the Library sizes are used when the PDF has no size list`() = runBlocking {
        val document = ExtractedDocument(1, listOf(ExtractedLine("Cast on 60 (66, 72) sts.", SourceReference(1, 1))))
        val repository = FakeGuideRepository()
        val useCase = CreateGuideFromPdfUseCase(FakePdfTextExtractor(document), repository, idGenerator())

        useCase.forPattern("pattern-1", "L", "S, M, L", "Cardigan · L", ByteArrayInputStream(ByteArray(0)))

        val texts = repository.lastSavedDraft!!.nodes.mapNotNull { it.instructionText }
        assertTrue(texts.toString(), texts.any { it.startsWith("Cast on 72 sts.") })
    }

    @Test
    fun `a size the pattern doesn't list keeps every number and says so`() = runBlocking {
        val document = ExtractedDocument(
            pageCount = 1,
            lines = listOf(
                ExtractedLine("Sizes: S (M, L)", SourceReference(1, 1)),
                ExtractedLine("Cast on 60 (66, 72) sts.", SourceReference(1, 2))
            )
        )
        val repository = FakeGuideRepository()
        val useCase = CreateGuideFromPdfUseCase(FakePdfTextExtractor(document), repository, idGenerator())

        val result = useCase.forPattern("pattern-1", "XL", null, "Cardigan · XL", ByteArrayInputStream(ByteArray(0)))

        assertEquals(1, (result as CreateGuideFromPdfUseCase.Result.Success).issueCount)
        val texts = repository.lastSavedDraft!!.nodes.mapNotNull { it.instructionText }
        assertTrue(texts.any { it.startsWith("Cast on 60 (66, 72) sts.") })
        assertTrue(texts.toString(), texts.any { "Size XL isn't one of the pattern's sizes (S, M, L)" in it })
    }
}

private class FakePdfTextExtractor(private val document: ExtractedDocument) : PdfTextExtractor {
    override suspend fun extract(input: InputStream): ExtractedDocument = document
}

private class FailingPdfTextExtractor : PdfTextExtractor {
    override suspend fun extract(input: InputStream): ExtractedDocument {
        throw PdfTextExtractionException("Simulated unreadable PDF.")
    }
}

private class FakeGuideRepository : GuideRepository {
    private var nextId = 0
    private val guides = mutableMapOf<String, Guide>()
    private val drafts = mutableMapOf<String, GuideDraft>()
    var createGuideCallCount = 0
        private set
    var lastSavedDraft: GuideDraft? = null
        private set

    override fun observeGuides(projectId: String): Flow<List<Guide>> =
        flowOf(guides.values.filter { it.projectId == projectId })

    override suspend fun getGuide(guideId: GuideId): Guide? = guides[guideId.value]

    override suspend fun createGuide(projectId: String, name: String, notes: String?): Guide {
        createGuideCallCount++
        nextId++
        val id = GuideId("guide-$nextId")
        val guide = Guide(id = id, projectId = projectId, name = name, notes = notes, createdAt = 0, updatedAt = 0)
        guides[id.value] = guide
        drafts[id.value] = GuideDraft(
            id = DraftId("draft-$nextId"),
            guideId = id,
            baseRevisionId = null,
            createdAt = 0,
            updatedAt = 0,
            version = 0,
            rootNodeIds = emptyList(),
            nodes = emptyList()
        )
        return guide
    }

    val patternGuidesCreated = mutableListOf<Pair<String, String>>()

    override suspend fun createPatternGuide(libraryItemId: String, sizeLabel: String, name: String): Guide {
        patternGuidesCreated += libraryItemId to sizeLabel
        val guide = createGuide("unused", name, null)
        val owned = guide.copy(projectId = null, libraryItemId = libraryItemId, sizeLabel = sizeLabel)
        guides[guide.id.value] = owned
        return owned
    }

    override suspend fun updateGuideMetadata(guideId: GuideId, name: String, notes: String?): Guide? =
        throw UnsupportedOperationException("Not used by CreateGuideFromPdfUseCase")

    override suspend fun deleteGuide(guideId: GuideId): Unit =
        throw UnsupportedOperationException("Not used by CreateGuideFromPdfUseCase")

    override suspend fun loadDraft(guideId: GuideId): GuideDraft? = drafts[guideId.value]

    override suspend fun saveDraft(draft: GuideDraft): GuideDraft {
        val saved = draft.copy(version = draft.version + 1)
        drafts[draft.guideId.value] = saved
        lastSavedDraft = saved
        return saved
    }

    override suspend fun createDraftFromLatestRevision(guideId: GuideId): GuideDraft =
        throw UnsupportedOperationException("Not used by CreateGuideFromPdfUseCase")

    override suspend fun listRevisions(guideId: GuideId): List<DefinitionRevision> =
        throw UnsupportedOperationException("Not used by CreateGuideFromPdfUseCase")

    override suspend fun loadRevision(revisionId: DefinitionRevisionId): DefinitionRevision? =
        throw UnsupportedOperationException("Not used by CreateGuideFromPdfUseCase")

    override suspend fun getLatestRevision(guideId: GuideId): DefinitionRevision? = null

    override suspend fun publishDraft(guideId: GuideId): DefinitionRevision =
        throw UnsupportedOperationException("Not used by CreateGuideFromPdfUseCase")
}

private class FakeLibraryRepository(var item: LibraryItem?) : LibraryRepository {
    override fun observeLibraryItems(): Flow<List<LibraryItem>> = flowOf(listOfNotNull(item))
    override fun observeLibraryItem(id: String): Flow<LibraryItem?> = flowOf(item?.takeIf { it.id == id })
    override suspend fun saveLibraryItem(item: LibraryItem) {
        this.item = item
    }
    override suspend fun deleteLibraryItem(item: LibraryItem) {
        this.item = null
    }
}
