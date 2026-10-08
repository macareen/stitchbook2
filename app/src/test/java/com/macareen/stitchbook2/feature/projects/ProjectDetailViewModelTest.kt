package com.macareen.stitchbook2.feature.projects

import com.macareen.stitchbook2.domain.backup.BackupImportResult
import com.macareen.stitchbook2.domain.backup.BackupPreview
import com.macareen.stitchbook2.domain.backup.BackupService
import com.macareen.stitchbook2.domain.backup.RestoreMode
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
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.ToolTemplate
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.ToolRepository
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    fun refreshingAfterAnExecutionStartsElsewhereSwitchesStartToContinue() {
        val guide = guide("guide-1")
        val guides = FakeGuideRepository(guides = listOf(guide))
            .withRevision(guide.id, revisionId("rev-1"))
        val executions = FakeExecutionRepository()
        val viewModel = viewModel(guides = guides, executions = executions)
        assertEquals(GuideEntryAction.START, contentState(viewModel).guideEntries.single().action)

        // Focus Mode starts the Execution; no guide row changes.
        executions.withActiveExecution(guide.id)
        assertEquals(GuideEntryAction.START, contentState(viewModel).guideEntries.single().action)

        viewModel.refreshGuideEntries()

        assertEquals(GuideEntryAction.CONTINUE, contentState(viewModel).guideEntries.single().action)
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

    @Test
    fun assignedToolsAreShownFromTheProjectToolRepository() {
        val tools = FakeToolRepository(listOf(toolItem("hook-1"), toolItem("hook-2")))
        val viewModel = viewModel(
            guides = FakeGuideRepository(guides = emptyList()),
            executions = FakeExecutionRepository(),
            tools = tools
        )

        assertEquals(listOf("hook-1", "hook-2"), contentState(viewModel).assignedTools.map { it.id })
    }

    @Test
    fun unassigningAToolRemovesItFromTheProjectsAssignedList() {
        val tools = FakeToolRepository(listOf(toolItem("hook-1")))
        val viewModel = viewModel(
            guides = FakeGuideRepository(guides = emptyList()),
            executions = FakeExecutionRepository(),
            tools = tools
        )

        viewModel.unassignTool(tools.assignedTools.value.single())

        assertTrue(contentState(viewModel).assignedTools.isEmpty())
    }

    @Test
    fun exportingHandsTheGeneratedContentToTheWriterAndReportsSaved() {
        val backup = FakeProjectBackupService(json = "{\"p\":1}", markdown = "# Project")
        val viewModel = viewModel(FakeGuideRepository(guides = emptyList()), FakeExecutionRepository(), backupService = backup)
        val written = mutableListOf<String>()

        viewModel.exportProject(ProjectExportFormat.MARKDOWN) { written += it }

        assertEquals(listOf("# Project"), written)
        assertEquals(listOf(project.id), backup.requestedIds)
        assertEquals(ProjectExportFeedback.SAVED, viewModel.exportFeedback.value)
        viewModel.dismissExportFeedback()
        assertEquals(null, viewModel.exportFeedback.value)
    }

    @Test
    fun aMissingProjectOrAFailedWriteReportsFailed() {
        val missing = viewModel(
            FakeGuideRepository(guides = emptyList()),
            FakeExecutionRepository(),
            backupService = FakeProjectBackupService(json = null, markdown = null)
        )
        missing.exportProject(ProjectExportFormat.JSON) { error("must not write") }
        assertEquals(ProjectExportFeedback.FAILED, missing.exportFeedback.value)

        val failingWrite = viewModel(
            FakeGuideRepository(guides = emptyList()),
            FakeExecutionRepository(),
            backupService = FakeProjectBackupService(json = "{}", markdown = null)
        )
        failingWrite.exportProject(ProjectExportFormat.JSON) { throw java.io.IOException("disk full") }
        assertEquals(ProjectExportFeedback.FAILED, failingWrite.exportFeedback.value)
    }

    @Test
    fun exportFileNamesAreFilesystemSafe() {
        assertEquals("everyday-cardigan.md", projectExportFileName("  Everyday Cardigan! ", ProjectExportFormat.MARKDOWN))
        assertEquals("pull-à-côtes.json", projectExportFileName("Pull à/côtes", ProjectExportFormat.JSON))
        assertEquals("project.json", projectExportFileName("???", ProjectExportFormat.JSON))
    }

    @Test
    fun addingANewToolFromTheProjectPutsItInTheToolboxAndLinksIt() {
        val tools = FakeToolRepository(emptyList())
        val viewModel = viewModel(FakeGuideRepository(guides = emptyList()), FakeExecutionRepository(), tools = tools)

        viewModel.addNewTool("  5 mm hook ", ToolCategory.CROCHET_HOOK)

        val created = tools.toolbox.value.single()
        assertEquals("5 mm hook", created.name)
        assertEquals(ToolCategory.CROCHET_HOOK, created.category)
        assertEquals(setOf(project.id), tools.projectIdsByTool[created.id])
        assertEquals(listOf(created), contentState(viewModel).assignedTools)
    }

    @Test
    fun linkingAToolboxToolKeepsItsOtherProjects() {
        val shared = toolItem("hook-1")
        val tools = FakeToolRepository(emptyList(), otherTools = listOf(shared))
        tools.projectIdsByTool[shared.id] = setOf("another-project")
        val viewModel = viewModel(FakeGuideRepository(guides = emptyList()), FakeExecutionRepository(), tools = tools)

        viewModel.assignTool(shared)

        assertEquals(setOf("another-project", project.id), tools.projectIdsByTool[shared.id])
    }

    @Test
    fun hubCountersAreProjectOwnedAndNeverGoBelowZero() {
        val counters = FakeHubCounterRepository()
        val viewModel = viewModel(FakeGuideRepository(guides = emptyList()), FakeExecutionRepository(), counters = counters)
        scope.launch { viewModel.projectCounters.collect {} }

        viewModel.addCounter("Squares", "", goal = 48)
        val created = counters.counters.value.single()
        assertEquals(project.id, created.projectId)
        assertEquals("rows", created.unitLabel)
        assertEquals(48, created.goal)

        viewModel.decrementCounter(created)
        assertEquals(0, counters.counters.value.single().currentValue)
        viewModel.incrementCounter(created)
        assertEquals(1, counters.counters.value.single().currentValue)
    }

    @Test
    fun connectionsCountWhatIsLinkedToTheProject() {
        val tools = FakeToolRepository(listOf(toolItem("hook-1"), toolItem("hook-2")))
        val counters = FakeHubCounterRepository()
        val viewModel = viewModel(
            FakeGuideRepository(guides = listOf(guide("guide-1"))),
            FakeExecutionRepository(),
            tools = tools,
            counters = counters
        )
        scope.launch { viewModel.connections.collect {} }
        viewModel.addCounter("Rounds", "rounds", goal = null)

        val connections = viewModel.connections.value
        assertEquals(1, connections.guides)
        assertEquals(2, connections.tools)
        assertEquals(1, connections.counters)
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

    private var idCounter = 0

    private fun viewModel(
        guides: FakeGuideRepository,
        executions: FakeExecutionRepository,
        tools: FakeToolRepository = FakeToolRepository(emptyList()),
        backupService: BackupService? = null,
        counters: CounterRepository? = null
    ): ProjectDetailViewModel {
        val viewModel = ProjectDetailViewModel(
            projectId = project.id,
            repository = FakeProjectRepository(project),
            guideRepository = guides,
            executionRepository = executions,
            toolRepository = tools,
            createGuideFromPdfUseCase = CreateGuideFromPdfUseCase(
                textExtractor = NeverCalledPdfTextExtractor,
                guideRepository = guides,
                newNodeId = { "unused" }
            ),
            backupService = backupService,
            counterRepository = counters,
            newId = { "id-${++idCounter}" },
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

private class FakeProjectBackupService(
    private val json: String?,
    private val markdown: String?
) : BackupService {
    val requestedIds = mutableListOf<String>()

    override suspend fun exportJson(): String = error("not used")
    override suspend fun exportProjectJson(projectId: String): String? = json.also { requestedIds += projectId }
    override suspend fun exportProjectMarkdown(projectId: String): String? = markdown.also { requestedIds += projectId }
    override suspend fun previewImport(json: String): BackupPreview = error("not used")
    override suspend fun importJson(json: String, mode: RestoreMode): BackupImportResult = error("not used")
    override suspend fun resetAllData() = error("not used")
}

private const val PROJECT_ID = "project"

private class FakeHubCounterRepository : CounterRepository {
    val counters = MutableStateFlow<List<Counter>>(emptyList())

    override fun observeCounters(): Flow<List<Counter>> = counters
    override fun observeCountersByProject(projectId: String): Flow<List<Counter>> =
        counters.map { list -> list.filter { it.projectId == projectId } }
    override fun observeCounter(id: String): Flow<Counter?> = counters.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun saveCounter(counter: Counter) {
        counters.value = counters.value.filterNot { it.id == counter.id } + counter
    }
    override suspend fun incrementCounterValue(id: String, amount: Int, updatedAt: Long) {
        counters.value = counters.value.map { if (it.id == id) it.copy(currentValue = it.currentValue + amount) else it }
    }
    override suspend fun deleteCounter(counter: Counter) {
        counters.value = counters.value.filterNot { it.id == counter.id }
    }
}

/** This test suite never exercises PDF import; every call would be a test bug. */
private object NeverCalledPdfTextExtractor : PdfTextExtractor {
    override suspend fun extract(input: InputStream): ExtractedDocument =
        throw AssertionError("PDF import is not exercised by ProjectDetailViewModelTest")
}

private class FakeProjectRepository(private val project: Project) : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> = flowOf(listOf(project))
    override fun observeProject(id: String): Flow<Project?> = flowOf(project.takeIf { it.id == id })
    override suspend fun saveProject(project: Project) = Unit
    override suspend fun deleteProject(project: Project) = Unit
}

private class FakeToolRepository(
    initialAssignedTools: List<ToolItem>,
    otherTools: List<ToolItem> = emptyList()
) : ToolRepository {
    val assignedTools = MutableStateFlow(initialAssignedTools)
    val toolbox = MutableStateFlow(initialAssignedTools + otherTools)
    val projectIdsByTool = mutableMapOf<String, Set<String>>()

    override fun observeToolItems(): Flow<List<ToolItem>> = toolbox
    override fun observeToolItem(id: String): Flow<ToolItem?> =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")
    override fun observeToolItemsBySet(setId: String): Flow<List<ToolItem>> =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")
    override suspend fun saveToolItem(item: ToolItem) {
        toolbox.value = toolbox.value.filterNot { it.id == item.id } + item
    }
    override suspend fun deleteToolItem(item: ToolItem) =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")
    override fun observeToolSets(): Flow<List<ToolSet>> =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")
    override fun observeToolSet(id: String): Flow<ToolSet?> =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")
    override suspend fun saveToolSet(set: ToolSet) =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")
    override suspend fun deleteToolSet(set: ToolSet) =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")
    override fun observeToolTemplates(): Flow<List<ToolTemplate>> =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")
    override suspend fun saveToolTemplate(template: ToolTemplate) =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")
    override suspend fun deleteToolTemplate(template: ToolTemplate) =
        throw UnsupportedOperationException("Not used by ProjectDetailViewModel")

    override fun observeToolItemsForProject(projectId: String): Flow<List<ToolItem>> = assignedTools

    override fun observeProjectIdsForToolItem(toolItemId: String): Flow<List<String>> =
        flowOf(projectIdsByTool[toolItemId].orEmpty().toList())

    override suspend fun setProjectAssignments(toolItemId: String, projectIds: Set<String>) {
        projectIdsByTool[toolItemId] = projectIds
        val tool = toolbox.value.first { it.id == toolItemId }
        if (PROJECT_ID in projectIds && assignedTools.value.none { it.id == toolItemId }) {
            assignedTools.value = assignedTools.value + tool
        }
    }

    override suspend fun unassignToolFromProject(toolItemId: String, projectId: String) {
        assignedTools.value = assignedTools.value.filterNot { it.id == toolItemId }
    }
}

private fun toolItem(id: String, name: String = id) = ToolItem(
    id = id,
    name = name,
    category = ToolCategory.CROCHET_HOOK,
    brand = null,
    material = null,
    sizeMetricMm = null,
    sizeLabel = null,
    lengthMm = null,
    statedCableLengthMm = null,
    cableLengthDefinition = null,
    approximateAssembledLengthMm = null,
    connectorFamily = null,
    compatibilityNotes = null,
    quantity = 1,
    storageLocation = null,
    notes = null,
    setId = null,
    createdAt = 0,
    updatedAt = 0
)

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
