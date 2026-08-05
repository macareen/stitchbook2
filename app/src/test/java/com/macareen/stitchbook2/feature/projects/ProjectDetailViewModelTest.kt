package com.macareen.stitchbook2.feature.projects

import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.ExecutionAddress
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.ExecutionState
import com.macareen.stitchbook2.domain.execution.GuideDefinition
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.execution.PersistedExecution
import com.macareen.stitchbook2.domain.execution.PersistedExecutionTransitionResult
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.guide.GuideDraft
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers only what this ViewModel adds on top of persisted repository
 * state: resolving each Guide's entry action (Continue/Start/not
 * executable). It never decides completion, traversal, or revision
 * selection -- those assertions belong to [GuideFocusViewModel] and the
 * repository test suites.
 */
class ProjectDetailViewModelTest {

    // Dispatchers.Unconfined runs launched coroutines synchronously up to
    // their first real suspension point. Every fake call below is a plain
    // in-memory operation with no real suspension, so uiState settles
    // before the constructor call returns -- no manual idling needed.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val project = Project(
        id = "project",
        name = "Everyday cardigan",
        craft = Craft.KNITTING,
        projectType = ProjectType.CARDIGAN,
        status = ProjectStatus.ACTIVE,
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    @Test
    fun guideWithNoRevisionAndNoActiveExecutionIsNotExecutable() {
        val guide = guide("guide-1")
        val viewModel = viewModel(
            guides = FakeGuideRepository(guides = listOf(guide)),
            executions = FakeExecutionRepository()
        )

        val entries = contentState(viewModel).guideEntries
        assertEquals(GuideEntryAction.NOT_EXECUTABLE, entries.single().action)
    }

    @Test
    fun guideWithPublishedRevisionAndNoActiveExecutionOffersStart() {
        val guide = guide("guide-1")
        val guides = FakeGuideRepository(guides = listOf(guide))
            .withRevision(guide.id, revisionId("rev-1"))
        val viewModel = viewModel(guides = guides, executions = FakeExecutionRepository())

        val entries = contentState(viewModel).guideEntries
        assertEquals(GuideEntryAction.START, entries.single().action)
    }

    @Test
    fun guideWithActiveExecutionOffersContinueEvenWithARevisionPresent() {
        val guide = guide("guide-1")
        val guides = FakeGuideRepository(guides = listOf(guide))
            .withRevision(guide.id, revisionId("rev-1"))
        val executions = FakeExecutionRepository().withActiveExecution(guide.id)
        val viewModel = viewModel(guides = guides, executions = executions)

        val entries = contentState(viewModel).guideEntries
        assertEquals(GuideEntryAction.CONTINUE, entries.single().action)
    }

    @Test
    fun eachGuideResolvesItsOwnEntryActionIndependently() {
        val continuable = guide("continuable")
        val startable = guide("startable")
        val draftOnly = guide("draft-only")

        val guides = FakeGuideRepository(guides = listOf(continuable, startable, draftOnly))
            .withRevision(continuable.id, revisionId("rev-continuable"))
            .withRevision(startable.id, revisionId("rev-startable"))
        val executions = FakeExecutionRepository().withActiveExecution(continuable.id)

        val viewModel = viewModel(guides = guides, executions = executions)

        val actionsById = contentState(viewModel).guideEntries.associate { it.guide.id to it.action }
        assertEquals(GuideEntryAction.CONTINUE, actionsById[continuable.id])
        assertEquals(GuideEntryAction.START, actionsById[startable.id])
        assertEquals(GuideEntryAction.NOT_EXECUTABLE, actionsById[draftOnly.id])
    }

    @Test
    fun creatingAGuideAddsItAndEmitsItsIdForNavigation() {
        val guides = FakeGuideRepository(guides = emptyList())
        val viewModel = viewModel(guides = guides, executions = FakeExecutionRepository())

        var emittedGuideId: String? = null
        scope.launch { viewModel.guideCreatedEvents.collect { emittedGuideId = it } }

        viewModel.createGuide("New Guide")

        assertEquals(1, guides.createGuideCallCount)
        val entries = contentState(viewModel).guideEntries
        assertEquals(1, entries.size)
        assertEquals("New Guide", entries.single().guide.name)
        assertEquals(entries.single().guide.id.value, emittedGuideId)
    }

    @Test
    fun creatingAGuideWithABlankNameIsANoOp() {
        val guides = FakeGuideRepository(guides = emptyList())
        val viewModel = viewModel(guides = guides, executions = FakeExecutionRepository())

        viewModel.createGuide("   ")

        assertEquals(0, guides.createGuideCallCount)
        assertTrue(contentState(viewModel).guideEntries.isEmpty())
    }

    @Test
    fun creatingAGuideFailureSurfacesAsRecoverableStateWithoutCrashing() {
        val guides = FakeGuideRepository(guides = emptyList())
        guides.createGuideError = IllegalStateException("boom")
        val viewModel = viewModel(guides = guides, executions = FakeExecutionRepository())

        viewModel.createGuide("New Guide")

        assertTrue(contentState(viewModel).createGuideFailed)
        assertFalse(contentState(viewModel).isCreatingGuide)
    }

    @Test
    fun repeatedCreateCallsWhileTheFirstIsInFlightCreateOnlyOneGuide() {
        val guides = FakeGuideRepository(guides = emptyList())
        guides.createGuideGate = CompletableDeferred()
        val viewModel = viewModel(guides = guides, executions = FakeExecutionRepository())

        viewModel.createGuide("First")
        assertTrue(contentState(viewModel).isCreatingGuide)

        viewModel.createGuide("Second")
        assertEquals(1, guides.createGuideCallCount)

        guides.createGuideGate?.complete(Unit)

        assertEquals(1, guides.createGuideCallCount)
        assertEquals(1, contentState(viewModel).guideEntries.size)
        assertFalse(contentState(viewModel).isCreatingGuide)
    }

    private fun contentState(viewModel: ProjectDetailViewModel): ProjectDetailUiState.Content {
        return viewModel.uiState.value as ProjectDetailUiState.Content
    }

    private fun guide(id: String) = Guide(
        id = GuideId(id),
        projectId = project.id,
        name = id,
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    private fun revisionId(value: String) = DefinitionRevisionId(value)

    private fun viewModel(
        guides: FakeGuideRepository,
        executions: FakeExecutionRepository
    ): ProjectDetailViewModel {
        val viewModel = ProjectDetailViewModel(
            projectId = project.id,
            repository = FakeProjectRepository(project),
            guideRepository = guides,
            executionRepository = executions,
            createGuideFromPdfUseCase = CreateGuideFromPdfUseCase(
                textExtractor = NeverCalledPdfTextExtractor,
                guideRepository = guides,
                newNodeId = { "unused" }
            ),
            externalScope = scope
        )
        // uiState is built with SharingStarted.WhileSubscribed, so it only
        // starts (and its value only advances past the initial Loading
        // state) once it has an active collector. Under Dispatchers.Unconfined
        // this collection runs synchronously through every fake's
        // non-suspending emissions before this call returns.
        scope.launch { viewModel.uiState.collect {} }
        return viewModel
    }
}

/** This test suite never exercises PDF import; every call would be a test bug. */
private object NeverCalledPdfTextExtractor : PdfTextExtractor {
    override fun extract(input: InputStream): ExtractedDocument =
        throw AssertionError("PDF import is not exercised by ProjectDetailViewModelTest")
}

private class FakeProjectRepository(private val project: Project) : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> = flowOf(listOf(project))
    override fun observeProject(id: String): Flow<Project?> = flowOf(project.takeIf { it.id == id })
    override suspend fun saveProject(project: Project) = Unit
    override suspend fun deleteProject(project: Project) = Unit
}

private class FakeGuideRepository(guides: List<Guide>) : GuideRepository {
    private val guidesFlow = MutableStateFlow(guides)
    private val latestRevisionByGuide = mutableMapOf<String, DefinitionRevision>()
    var createGuideError: Exception? = null
    var createGuideCallCount = 0
    var createGuideGate: CompletableDeferred<Unit>? = null

    fun withRevision(guideId: GuideId, revisionId: DefinitionRevisionId): FakeGuideRepository {
        latestRevisionByGuide[guideId.value] = DefinitionRevision(
            id = revisionId,
            guideId = guideId,
            revisionNumber = 1,
            createdAt = 0,
            definition = GuideDefinition(
                guideId = guideId,
                revisionId = revisionId,
                rootNodeIds = emptyList(),
                nodes = emptyList()
            )
        )
        return this
    }

    override fun observeGuides(projectId: String): Flow<List<Guide>> =
        guidesFlow

    override suspend fun getGuide(guideId: GuideId): Guide? =
        guidesFlow.value.firstOrNull { it.id == guideId }

    override suspend fun createGuide(projectId: String, name: String, notes: String?): Guide {
        createGuideCallCount++
        createGuideGate?.await()
        createGuideError?.let {
            createGuideError = null
            throw it
        }
        val guide = Guide(
            id = GuideId("created-$createGuideCallCount"),
            projectId = projectId,
            name = name,
            notes = notes,
            createdAt = 0,
            updatedAt = 0
        )
        guidesFlow.value = guidesFlow.value + guide
        return guide
    }

    override suspend fun updateGuideMetadata(guideId: GuideId, name: String, notes: String?): Guide? =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")

    override suspend fun deleteGuide(guideId: GuideId): Unit =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")

    override suspend fun loadDraft(guideId: GuideId): GuideDraft? =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")

    override suspend fun saveDraft(draft: GuideDraft): GuideDraft =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")

    override suspend fun createDraftFromLatestRevision(guideId: GuideId): GuideDraft =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")

    override suspend fun listRevisions(guideId: GuideId): List<DefinitionRevision> =
        latestRevisionByGuide[guideId.value]?.let { listOf(it) }.orEmpty()

    override suspend fun loadRevision(revisionId: DefinitionRevisionId): DefinitionRevision? =
        latestRevisionByGuide.values.firstOrNull { it.id == revisionId }

    override suspend fun getLatestRevision(guideId: GuideId): DefinitionRevision? =
        latestRevisionByGuide[guideId.value]

    override suspend fun publishDraft(guideId: GuideId): DefinitionRevision =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")
}

private class FakeExecutionRepository : ExecutionRepository {
    private val activeExecutionByGuide = mutableMapOf<GuideId, PersistedExecution>()

    fun withActiveExecution(guideId: GuideId): FakeExecutionRepository {
        val address = ExecutionAddress(
            definitionRevisionId = DefinitionRevisionId("revision"),
            instructionNodeId = NodeId("instruction")
        )
        val state = ExecutionState(
            executionId = ExecutionId("exec-${guideId.value}"),
            guideId = guideId,
            definitionRevisionId = address.definitionRevisionId,
            currentAddress = address,
            completedAddresses = emptySet()
        )
        activeExecutionByGuide[guideId] = PersistedExecution(
            state = state,
            version = 0,
            createdAt = 0,
            updatedAt = 0,
            completedAt = null
        )
        return this
    }

    override suspend fun createExecution(
        guideId: GuideId,
        revisionId: DefinitionRevisionId
    ): PersistedExecution = throw UnsupportedOperationException("Not used by ProjectDetailViewModel")

    override suspend fun loadExecution(executionId: ExecutionId): PersistedExecution? =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")

    override suspend fun getActiveExecution(guideId: GuideId): PersistedExecution? =
        activeExecutionByGuide[guideId]

    override suspend fun listExecutions(guideId: GuideId): List<PersistedExecution> =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")

    override suspend fun applyComplete(
        executionId: ExecutionId,
        expectedVersion: Long
    ): PersistedExecutionTransitionResult =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")

    override suspend fun applyPrevious(
        executionId: ExecutionId,
        expectedVersion: Long
    ): PersistedExecutionTransitionResult =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")

    override suspend fun applyJump(
        executionId: ExecutionId,
        expectedVersion: Long,
        targetAddress: ExecutionAddress
    ): PersistedExecutionTransitionResult =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")
}
