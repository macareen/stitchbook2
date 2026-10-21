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

    @Test fun aSelectionStartingMidSectionKeepsLostContextAsAnExplicitReviewInstruction() = runBlocking {
        val original = ExtractedDocument(2, listOf(ExtractedLine("Cast on 24 sts.", SourceReference(1, 1)),
            ExtractedLine("Bind off.", SourceReference(2, 1))))
        val repository = FakeGuideRepository()
        val result = CreateGuideFromPdfUseCase(FakePdfTextExtractor(original), repository, idGenerator()).forPattern(
            "pattern", "One size", "One size", "Sample", ByteArrayInputStream(ByteArray(0)),
            com.macareen.stitchbook2.domain.parsing.ReviewedPdfSource(original, original.copy(lines = original.lines.drop(1)),
                com.macareen.stitchbook2.domain.parsing.PdfSourceSelection(listOf(com.macareen.stitchbook2.domain.parsing.PdfSourceRegion(2))))) as CreateGuideFromPdfUseCase.Result.Success
        assertTrue(result.issueCount >= 1)
        assertTrue(repository.lastSavedDraft!!.nodes.any { it.instructionText?.contains("restore this instruction after checking the PDF: Bind off.") == true })
    }

    @Test fun reviewedSelectionRetainsFullDocumentSizesButOnlyCreatesChosenInstructionsInOrder() = runBlocking {
        val original = ExtractedDocument(4, listOf(
            ExtractedLine("Sizes: S, M", SourceReference(1, 1)),
            ExtractedLine("Cast on 20 (24) sts.", SourceReference(2, 1)),
            ExtractedLine("Advertisement", SourceReference(3, 1)),
            ExtractedLine("Row 2: Purl.", SourceReference(4, 1))))
        val selected = original.copy(lines = listOf(original.lines[3], original.lines[1]))
        val selection = com.macareen.stitchbook2.domain.parsing.PdfSourceSelection(listOf(
            com.macareen.stitchbook2.domain.parsing.PdfSourceRegion(4), com.macareen.stitchbook2.domain.parsing.PdfSourceRegion(2)))
        val repository = FakeGuideRepository()
        val extractor = object : PdfTextExtractor {
            override suspend fun extract(input: InputStream): ExtractedDocument = error("Must consume reviewed snapshot")
        }
        val useCase = CreateGuideFromPdfUseCase(extractor, repository, idGenerator())
        val result = useCase.forPattern("pattern", "M", null, "Sample", ByteArrayInputStream(ByteArray(0)),
            com.macareen.stitchbook2.domain.parsing.ReviewedPdfSource(original, selected, selection)) as CreateGuideFromPdfUseCase.Result.Success
        assertEquals(0, result.issueCount)
        assertEquals(listOf("Row 2: Purl. (p.4)", "Cast on 24 sts. (p.2)"), repository.lastSavedDraft!!.nodes.mapNotNull { it.instructionText })
        val notes = repository.getGuide(result.guideId)!!.notes!!
        assertTrue(notes.contains("1. p.4"))
        assertTrue(notes.contains("Excluded areas remain in the original PDF"))
    }

    @Test fun patternSetupCannotOverwriteEditsMadeWhileItsPdfIsRead() = runBlocking {
        val original = LibraryItem("pattern", "Sample", Craft.KNITTING, null, null, emptyList(), null, false, 0, 0,
            pdfUri = "content://sample/old.pdf")
        val library = FakeLibraryRepository(original)
        val extractor = object : PdfTextExtractor {
            override suspend fun extract(input: InputStream): ExtractedDocument {
                library.item = original.copy(notes = "Keep my edit", pdfUri = "content://sample/new.pdf")
                return ExtractedDocument(1, listOf(ExtractedLine("Gauge: 20 sts over 10 cm", SourceReference(1, 1))))
            }
        }
        val useCase = CreateGuideFromPdfUseCase(extractor, FakeGuideRepository(), idGenerator(), library)
        useCase.setupPattern("pattern", ByteArrayInputStream(ByteArray(0)), original.pdfUri)
        assertEquals("Keep my edit", library.item!!.notes)
        assertEquals("content://sample/new.pdf", library.item!!.pdfUri)
        assertEquals(null, library.item!!.gauge)
    }

    @Test fun `detected PDF title repairs filename titles but preserves custom names and original files`() = runBlocking {
        val document = ExtractedDocument(1, listOf("AUTUMN CARDIGAN", "Sizes: S, M, L", "BODY", "Cast on 40 (44, 48) sts.")
            .mapIndexed { i, text -> ExtractedLine(text, SourceReference(1, i + 1)) })
        for (custom in listOf(false, true)) {
            val oldTitle = if (custom) "My autumn knit" else "random-123"
            val pattern = LibraryItem(id = "p", title = oldTitle, craft = Craft.KNITTING, author = null,
                sourceUrl = null, tags = emptyList(), notes = null, bookmarked = false, createdAt = 0, updatedAt = 0,
                pdfUri = "content://patterns/123", pdfFileName = "random-123.pdf")
            val library = FakeLibraryRepository(pattern)
            val repository = FakeGuideRepository()
            val useCase = CreateGuideFromPdfUseCase(FakePdfTextExtractor(document), repository, idGenerator(), library)
            val result = useCase.forPattern("p", "M", null, "$oldTitle \u00b7 M", ByteArrayInputStream(ByteArray(0))) as CreateGuideFromPdfUseCase.Result.Success
            val expected = if (custom) oldTitle else "AUTUMN CARDIGAN"
            assertEquals(expected, library.item!!.title)
            assertEquals("$expected \u00b7 M", repository.getGuide(result.guideId)!!.name)
            assertEquals(pattern.pdfUri, library.item!!.pdfUri)
            assertEquals(pattern.pdfFileName, library.item!!.pdfFileName)
            assertTrue(repository.lastSavedDraft!!.nodes.none { it.instructionText?.contains("AUTUMN CARDIGAN") == true })
        }
    }

    @Test fun `automatic project imports use document titles while explicit guide names are kept`() = runBlocking {
        val document = ExtractedDocument(1, listOf("AUTUMN CARDIGAN", "Cast on 40 sts.")
            .mapIndexed { i, text -> ExtractedLine(text, SourceReference(1, i + 1)) })
        for (prefer in listOf(false, true)) {
            val repository = FakeGuideRepository()
            val useCase = CreateGuideFromPdfUseCase(FakePdfTextExtractor(document), repository, idGenerator())
            val result = useCase("p", "random-123", ByteArrayInputStream(ByteArray(0)), prefer) as CreateGuideFromPdfUseCase.Result.Success
            assertEquals(if (prefer) "AUTUMN CARDIGAN" else "random-123", repository.getGuide(result.guideId)!!.name)
        }
    }

    @Test fun unreadablePagesBecomeLeadingReviewStepsWithoutLosingReadableWork() = runBlocking {
        val document = ExtractedDocument(3, listOf(ExtractedLine("Cast on 20 sts.", SourceReference(1, 1)),
            ExtractedLine("Bind off.", SourceReference(3, 1))))
        val repository = FakeGuideRepository()
        val result = CreateGuideFromPdfUseCase(FakePdfTextExtractor(document), repository, idGenerator())(
            "project", "Sample", ByteArrayInputStream(ByteArray(0))) as CreateGuideFromPdfUseCase.Result.Success
        assertEquals(1, result.issueCount)
        val draft = repository.lastSavedDraft!!
        val first = draft.nodes.first { it.id == draft.rootNodeIds.first() }.instructionText!!
        assertTrue(first.startsWith("Review needed: No readable text was found on PDF page 2."))
        assertTrue(first.endsWith("(p.2 l.1)"))
        assertTrue(draft.nodes.any { it.instructionText == "Cast on 20 sts. (p.1)" })
        assertTrue(draft.nodes.any { it.instructionText == "Bind off. (p.3)" })
    }

    @Test fun sizeFilteringDoesNotMistakeAnIntentionallyExcludedPageForExtractionLoss() = runBlocking {
        val document = ExtractedDocument(3, listOf(ExtractedLine("Sizes: S, M", SourceReference(1, 1)),
            ExtractedLine("Size S only: Cast on 20 sts.", SourceReference(2, 1)),
            ExtractedLine("Size M only: Cast on 24 sts.", SourceReference(3, 1))))
        val repository = FakeGuideRepository()
        val result = CreateGuideFromPdfUseCase(FakePdfTextExtractor(document), repository, idGenerator()).forPattern(
            "pattern", "M", null, "Sample", ByteArrayInputStream(ByteArray(0))) as CreateGuideFromPdfUseCase.Result.Success
        assertEquals(0, result.issueCount)
        assertTrue(repository.lastSavedDraft!!.nodes.none { it.instructionText?.startsWith("Review needed:") == true })
        assertTrue(repository.lastSavedDraft!!.nodes.any { it.instructionText?.contains("24 sts.") == true })
    }

    @Test fun patternGuideAlsoWarnsWhenAnOriginalPageHasNoText() = runBlocking {
        val document = ExtractedDocument(2, listOf(ExtractedLine("Sizes: S, M", SourceReference(1, 1)),
            ExtractedLine("Cast on 20 (24) sts.", SourceReference(1, 2))))
        val repository = FakeGuideRepository()
        val result = CreateGuideFromPdfUseCase(FakePdfTextExtractor(document), repository, idGenerator()).forPattern(
            "pattern", "M", null, "Sample", ByteArrayInputStream(ByteArray(0))) as CreateGuideFromPdfUseCase.Result.Success
        assertEquals(1, result.issueCount)
        val draft = repository.lastSavedDraft!!
        assertTrue(draft.nodes.first { it.id == draft.rootNodeIds.first() }.instructionText!!.contains("page 2"))
        assertTrue(draft.nodes.any { it.instructionText?.startsWith("Cast on 24 sts.") == true })
    }

    private fun idGenerator(): () -> String {
        var counter = 0
        return { "node-${counter++}" }
    }

    @Test fun aPatternDraftIncludesOnlyItsExplicitSizeProseAndKeepsRepeatStructure() = runBlocking {
        val text = listOf("Sizes: S (M, L)", "BODY", "Size S only: Row 1: Knit.",
            "Size M only: Row 1: Purl.", "Size L only: Row 1: K2, p2.",
            "All sizes: Row 2: Knit.", "Repeat rows 1-2 2 times.")
        val document = ExtractedDocument(1, text.mapIndexed { index, line ->
            ExtractedLine(line, SourceReference(1, index + 1)) })
        val repository = FakeGuideRepository()
        val useCase = CreateGuideFromPdfUseCase(FakePdfTextExtractor(document), repository, idGenerator())
        val result = useCase.forPattern("pattern", "M", null, "Sample cardigan", ByteArrayInputStream(ByteArray(0)))
        assertEquals(0, (result as CreateGuideFromPdfUseCase.Result.Success).issueCount)
        val nodes = requireNotNull(repository.lastSavedDraft).nodes
        assertTrue(nodes.any { it.instructionText == "Row 1: Purl. (p.1)" })
        assertTrue(nodes.none { it.instructionText?.contains("K2, p2") == true })
        assertTrue(nodes.none { it.instructionText?.startsWith("Size ") == true })
        assertTrue(nodes.any { it.repeatCount == 2 })
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
    fun `a project PDF guide removes its known title on every page`() = runBlocking {
        val document = ExtractedDocument(2, listOf(
            ExtractedLine("APRIL CARDIGAN", SourceReference(1, 1)),
            ExtractedLine("BODY", SourceReference(1, 2)),
            ExtractedLine("Cast on 60 sts.", SourceReference(1, 3)),
            ExtractedLine("APRIL CARDIGAN", SourceReference(2, 1)),
            ExtractedLine("Row 1: Knit.", SourceReference(2, 2))
        ))
        val repository = FakeGuideRepository()
        val useCase = CreateGuideFromPdfUseCase(FakePdfTextExtractor(document), repository, idGenerator())
        useCase("project", "April Cardigan", ByteArrayInputStream(ByteArray(0)))
        assertEquals(1, repository.lastSavedDraft!!.rootNodeIds.size)
        assertTrue(repository.lastSavedDraft!!.nodes.any { it.instructionText == "Cast on 60 sts. (p.1)" })
        assertTrue(repository.lastSavedDraft!!.nodes.any { it.instructionText == "Row 1: Knit. (p.2)" })
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
        assertEquals("## Description\nA quick weekend knit for chilly mornings.\n\n## Suggested yarn\napprox. 200 yds of worsted", saved.notes)
        assertEquals("My own gauge note", saved.gauge)
        assertEquals("4.5 mm circular", saved.recommendedTools)
        assertEquals(200.0, saved.yardageRequired!!, 0.0)
        // Only the knitting became steps (the cast-on and the range), plus the note that no size list was found.
        assertEquals(3, repository.lastSavedDraft!!.rootNodeIds.size)
    }

    @Test
    fun `measurements and abbreviations are saved as portable notes with all sizes`() = runBlocking {
        val lines = listOf("APRIL CARDIGAN", "Sizes: S (M) L", "Bust circumference: 91 (97) 101 cm",
            "M1R", "From the RS: Lift the strand and knit it.", "BODY", "Cast on 60 (66) 72 sts.")
        val document = ExtractedDocument(1, lines.mapIndexed { index, text -> ExtractedLine(text, SourceReference(1, index + 1)) })
        val library = FakeLibraryRepository(LibraryItem("pattern", "April Cardigan", Craft.KNITTING,
            null, null, emptyList(), null, false, 0, 0))
        val guides = FakeGuideRepository()
        val useCase = CreateGuideFromPdfUseCase(FakePdfTextExtractor(document), guides, idGenerator(), library)
        useCase.forPattern("pattern", "M", null, "April Cardigan", ByteArrayInputStream(ByteArray(0)))
        assertTrue(requireNotNull(library.item?.notes).contains("Bust circumference: 91 (97) 101 cm"))
        assertTrue(requireNotNull(library.item?.notes).contains("## Glossary\nM1R:"))
        assertTrue(guides.lastSavedDraft!!.nodes.any { it.instructionText == "Cast on 66 sts. (p.1)" })
        assertTrue(guides.lastSavedDraft!!.nodes.none { it.instructionText?.contains("From the RS") == true })
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
        guides[guideId.value]?.copy(name = name, notes = notes)?.also { guides[guideId.value] = it }

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
    override suspend fun saveIfUnchanged(expected: LibraryItem, replacement: LibraryItem): Boolean {
        if (item != expected) return false
        item = replacement
        return true
    }
    override suspend fun deleteLibraryItem(item: LibraryItem) {
        this.item = null
    }
}
