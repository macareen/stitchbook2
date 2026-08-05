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
import com.macareen.stitchbook2.domain.repository.GuideRepository
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
}

private class FakePdfTextExtractor(private val document: ExtractedDocument) : PdfTextExtractor {
    override fun extract(input: InputStream): ExtractedDocument = document
}

private class FailingPdfTextExtractor : PdfTextExtractor {
    override fun extract(input: InputStream): ExtractedDocument {
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
