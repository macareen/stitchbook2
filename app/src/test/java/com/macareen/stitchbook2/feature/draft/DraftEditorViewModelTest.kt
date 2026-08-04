package com.macareen.stitchbook2.feature.draft

import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.domain.guide.DraftId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.guide.GuideDraft
import com.macareen.stitchbook2.domain.repository.DraftValidationException
import com.macareen.stitchbook2.domain.repository.DraftVersionConflictException
import com.macareen.stitchbook2.domain.repository.GuideRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every edit here is expected to persist immediately via
 * [GuideRepository.saveDraft] -- these tests assert on [DraftEditorViewModel]
 * only, never on real Room; repository/publish-time invariants belong to
 * the repository test suites.
 */
class DraftEditorViewModelTest {

    // Dispatchers.Unconfined runs launched coroutines synchronously up to
    // their first real suspension point, matching the convention already
    // established for GuideFocusViewModelTest/ProjectDetailViewModelTest.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val guideId = GuideId("guide-1")
    private val guide = Guide(
        id = guideId,
        projectId = "project",
        name = "Everyday cardigan",
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    @Test
    fun missingGuideIsNotFound() {
        val repository = FakeGuideRepository(guide = null, draft = null)
        val viewModel = viewModel(repository)

        assertTrue(viewModel.uiState.value is DraftEditorUiState.NotFound)
    }

    @Test
    fun loadFailureShowsLoadError() {
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        repository.failLoad = true
        val viewModel = viewModel(repository)

        assertTrue(viewModel.uiState.value is DraftEditorUiState.LoadError)
    }

    @Test
    fun emptyDraftShowsGuideNameAndNoRows() {
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        val viewModel = viewModel(repository)

        val content = contentState(viewModel)
        assertEquals("Everyday cardigan", content.guideName)
        assertTrue(content.rows.isEmpty())
    }

    @Test
    fun addingARootInstructionAppendsItAndPersists() {
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        val viewModel = viewModel(repository)

        viewModel.addNode(
            type = DraftNodeType.INSTRUCTION,
            parentId = null,
            instructionText = "Cast on 40 stitches"
        )

        val content = contentState(viewModel)
        assertEquals(1, content.rows.size)
        assertEquals("Cast on 40 stitches", content.rows.single().node.instructionText)
        assertEquals(0, content.rows.single().depth)
        assertEquals(1, repository.savedDrafts.size)
    }

    @Test
    fun addingAChildNestsUnderTheChosenParent() {
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        val viewModel = viewModel(repository)

        viewModel.addNode(type = DraftNodeType.SECTION, parentId = null, title = "Body")
        val sectionId = contentState(viewModel).rows.single().node.id

        viewModel.addNode(
            type = DraftNodeType.INSTRUCTION,
            parentId = sectionId,
            instructionText = "Knit all stitches"
        )

        val rows = contentState(viewModel).rows
        assertEquals(2, rows.size)
        assertEquals(0, rows[0].depth)
        assertEquals(1, rows[1].depth)
        assertEquals("Knit all stitches", rows[1].node.instructionText)
    }

    @Test
    fun updatingANodeChangesItsFieldsWithoutMovingIt() {
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        val viewModel = viewModel(repository)

        viewModel.addNode(
            type = DraftNodeType.RANGE,
            parentId = null,
            rangeUnitLabel = "row",
            rangeStartInclusive = 1,
            rangeEndInclusive = 2
        )
        val rangeId = contentState(viewModel).rows.single().node.id

        viewModel.updateNode(
            rangeId,
            rangeUnitLabel = "round",
            rangeStartInclusive = 1,
            rangeEndInclusive = 4
        )

        val node = contentState(viewModel).rows.single().node
        assertEquals("round", node.rangeUnitLabel)
        assertEquals(4, node.rangeEndInclusive)
    }

    @Test
    fun deletingAContainerRemovesItsDescendantsToo() {
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        val viewModel = viewModel(repository)

        viewModel.addNode(type = DraftNodeType.SECTION, parentId = null, title = "Body")
        val sectionId = contentState(viewModel).rows.single().node.id
        viewModel.addNode(type = DraftNodeType.INSTRUCTION, parentId = sectionId, instructionText = "Knit")
        assertEquals(2, contentState(viewModel).rows.size)

        viewModel.deleteNode(sectionId)

        assertTrue(contentState(viewModel).rows.isEmpty())
    }

    @Test
    fun movingARootNodeDownSwapsItWithItsNextSibling() {
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        val viewModel = viewModel(repository)

        viewModel.addNode(type = DraftNodeType.INSTRUCTION, parentId = null, instructionText = "First")
        viewModel.addNode(type = DraftNodeType.INSTRUCTION, parentId = null, instructionText = "Second")
        val firstId = contentState(viewModel).rows[0].node.id

        viewModel.moveDown(firstId)

        val rows = contentState(viewModel).rows
        assertEquals("Second", rows[0].node.instructionText)
        assertEquals("First", rows[1].node.instructionText)
    }

    @Test
    fun movingTheFirstRootNodeUpIsANoOp() {
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        val viewModel = viewModel(repository)

        viewModel.addNode(type = DraftNodeType.INSTRUCTION, parentId = null, instructionText = "First")
        viewModel.addNode(type = DraftNodeType.INSTRUCTION, parentId = null, instructionText = "Second")
        val firstId = contentState(viewModel).rows[0].node.id
        val savesBefore = repository.saveCallCount

        viewModel.moveUp(firstId)

        assertEquals(savesBefore, repository.saveCallCount)
        assertEquals("First", contentState(viewModel).rows[0].node.instructionText)
    }

    @Test
    fun saveFailureFromValidationSurfacesMessageAndKeepsPreviousRows() {
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        val viewModel = viewModel(repository)
        viewModel.addNode(type = DraftNodeType.INSTRUCTION, parentId = null, instructionText = "First step")
        assertEquals(1, contentState(viewModel).rows.size)

        repository.nextSaveError = DraftValidationException("Draft references missing node: bogus")
        viewModel.addNode(type = DraftNodeType.INSTRUCTION, parentId = null, instructionText = "Second step")

        val content = contentState(viewModel)
        assertEquals("Draft references missing node: bogus", content.errorMessage)
        assertEquals(1, content.rows.size)
        assertFalse(content.isSaving)
    }

    @Test
    fun saveConflictReloadsFromRepository() {
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        val viewModel = viewModel(repository)

        repository.nextSaveError = DraftVersionConflictException(guideId)
        repository.persistedDraft = draftWithOneInstruction()

        viewModel.addNode(type = DraftNodeType.SECTION, parentId = null, title = "Body")

        val content = contentState(viewModel)
        assertEquals("This changed elsewhere. Showing the current draft.", content.errorMessage)
        assertEquals(1, content.rows.size)
        assertEquals("Cast on 40 stitches", content.rows.single().node.instructionText)
    }

    @Test
    fun saveConflictWhenTheDraftNoLongerExistsTransitionsToNotFoundRatherThanStayingStuckBusy() {
        // If the repository's reload-after-conflict finds nothing (the
        // Guide/Draft itself is gone, not merely changed), isSaving must
        // still be cleared one way or another -- silently returning would
        // leave every control disabled forever.
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        val viewModel = viewModel(repository)

        repository.nextSaveError = DraftVersionConflictException(guideId)
        repository.persistedDraft = null

        viewModel.addNode(type = DraftNodeType.SECTION, parentId = null, title = "Body")

        assertTrue(viewModel.uiState.value is DraftEditorUiState.NotFound)
    }

    @Test
    fun repeatedAddCallsWhileFirstSaveInFlightPersistOnlyOnce() {
        val repository = FakeGuideRepository(guide = guide, draft = emptyDraft())
        repository.saveGate = CompletableDeferred()
        val viewModel = viewModel(repository)

        viewModel.addNode(type = DraftNodeType.INSTRUCTION, parentId = null, instructionText = "Cast on 40 stitches")
        assertTrue(contentState(viewModel).isSaving)

        viewModel.addNode(type = DraftNodeType.INSTRUCTION, parentId = null, instructionText = "Ignored while busy")
        assertEquals(1, repository.saveCallCount)

        repository.saveGate?.complete(Unit)

        assertEquals(1, repository.saveCallCount)
        assertEquals(1, contentState(viewModel).rows.size)
        assertFalse(contentState(viewModel).isSaving)
    }

    private fun contentState(viewModel: DraftEditorViewModel): DraftEditorUiState.Content {
        return viewModel.uiState.value as DraftEditorUiState.Content
    }

    private fun emptyDraft() = GuideDraft(
        id = DraftId("draft-1"),
        guideId = guideId,
        baseRevisionId = null,
        createdAt = 0,
        updatedAt = 0,
        version = 0,
        rootNodeIds = emptyList(),
        nodes = emptyList()
    )

    private fun draftWithOneInstruction() = GuideDraft(
        id = DraftId("draft-1"),
        guideId = guideId,
        baseRevisionId = null,
        createdAt = 0,
        updatedAt = 5,
        version = 5,
        rootNodeIds = listOf(NodeId("instruction")),
        nodes = listOf(
            DraftNode(
                id = NodeId("instruction"),
                type = DraftNodeType.INSTRUCTION,
                instructionText = "Cast on 40 stitches"
            )
        )
    )

    private fun viewModel(repository: FakeGuideRepository): DraftEditorViewModel {
        var counter = 0
        return DraftEditorViewModel(
            guideId = guideId,
            guideRepository = repository,
            externalScope = scope,
            newNodeId = { "generated-${counter++}" }
        )
    }
}

private class FakeGuideRepository(
    guide: Guide?,
    draft: GuideDraft?
) : GuideRepository {
    private var guide: Guide? = guide
    var persistedDraft: GuideDraft? = draft
    var failLoad = false
    var nextSaveError: Exception? = null
    var saveGate: CompletableDeferred<Unit>? = null
    var saveCallCount = 0
    val savedDrafts = mutableListOf<GuideDraft>()

    override fun observeGuides(projectId: String): Flow<List<Guide>> =
        flowOf(guide?.let { listOf(it) }.orEmpty())

    override suspend fun getGuide(guideId: GuideId): Guide? {
        if (failLoad) throw IllegalStateException("Simulated load failure")
        return guide
    }

    override suspend fun createGuide(projectId: String, name: String, notes: String?): Guide =
        throw UnsupportedOperationException("Not used by DraftEditorViewModel")

    override suspend fun updateGuideMetadata(guideId: GuideId, name: String, notes: String?): Guide? =
        throw UnsupportedOperationException("Not used by DraftEditorViewModel")

    override suspend fun deleteGuide(guideId: GuideId): Unit =
        throw UnsupportedOperationException("Not used by DraftEditorViewModel")

    override suspend fun loadDraft(guideId: GuideId): GuideDraft? {
        if (failLoad) throw IllegalStateException("Simulated load failure")
        return persistedDraft
    }

    override suspend fun saveDraft(draft: GuideDraft): GuideDraft {
        saveCallCount++
        saveGate?.await()
        nextSaveError?.let {
            nextSaveError = null
            throw it
        }
        val saved = draft.copy(version = draft.version + 1)
        persistedDraft = saved
        savedDrafts += saved
        return saved
    }

    override suspend fun createDraftFromLatestRevision(guideId: GuideId): GuideDraft =
        throw UnsupportedOperationException("Not used by DraftEditorViewModel")

    override suspend fun listRevisions(guideId: GuideId): List<DefinitionRevision> =
        throw UnsupportedOperationException("Not used by DraftEditorViewModel")

    override suspend fun loadRevision(revisionId: DefinitionRevisionId): DefinitionRevision? =
        throw UnsupportedOperationException("Not used by DraftEditorViewModel")

    override suspend fun getLatestRevision(guideId: GuideId): DefinitionRevision? =
        throw UnsupportedOperationException("Not used by DraftEditorViewModel")

    override suspend fun publishDraft(guideId: GuideId): DefinitionRevision =
        throw UnsupportedOperationException("Not used by DraftEditorViewModel")
}
