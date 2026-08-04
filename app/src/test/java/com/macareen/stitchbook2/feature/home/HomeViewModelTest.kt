package com.macareen.stitchbook2.feature.home

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
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers only the aggregation [HomeViewModel] adds on top of persisted
 * repository state (counts, craft-type count, and which Guide's active
 * Execution was most recently updated). It never decides completion or
 * traversal itself -- that belongs to the execution engine.
 */
class HomeViewModelTest {

    // Dispatchers.Unconfined runs launched coroutines synchronously up to
    // their first real suspension point. Every fake call below is a plain
    // in-memory operation with no real suspension, so uiState settles
    // before the constructor call returns -- no manual idling needed.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun countsAndCraftTypesReflectAllProjects() {
        val projects = listOf(
            project("active-knitting", ProjectStatus.ACTIVE, Craft.KNITTING),
            project("active-crochet", ProjectStatus.ACTIVE, Craft.CROCHET),
            project("planned-knitting", ProjectStatus.PLANNED, Craft.KNITTING)
        )
        val viewModel = viewModel(projects = projects, guidesByProject = emptyMap())

        val content = contentState(viewModel)
        assertEquals(3, content.totalProjectCount)
        assertEquals(2, content.activeProjectCount)
        assertEquals(2, content.craftCount)
        assertEquals(2, content.activeProjects.size)
    }

    @Test
    fun noResumeGuideWhenNoActiveExecutionsExist() {
        val active = project("active", ProjectStatus.ACTIVE, Craft.KNITTING)
        val guide = guide("guide-1", active.id)
        val viewModel = viewModel(
            projects = listOf(active),
            guidesByProject = mapOf(active.id to listOf(guide)),
            executions = FakeExecutionRepository()
        )

        assertNull(contentState(viewModel).resumeGuide)
    }

    @Test
    fun resumeGuidePicksTheMostRecentlyUpdatedActiveExecutionAcrossProjects() {
        val projectA = project("project-a", ProjectStatus.ACTIVE, Craft.KNITTING)
        val projectB = project("project-b", ProjectStatus.ACTIVE, Craft.CROCHET)
        val olderGuide = guide("older-guide", projectA.id)
        val newerGuide = guide("newer-guide", projectB.id)

        val executions = FakeExecutionRepository()
            .withActiveExecution(olderGuide.id, updatedAt = 100)
            .withActiveExecution(newerGuide.id, updatedAt = 200)

        val viewModel = viewModel(
            projects = listOf(projectA, projectB),
            guidesByProject = mapOf(
                projectA.id to listOf(olderGuide),
                projectB.id to listOf(newerGuide)
            ),
            executions = executions
        )

        val resumeGuide = contentState(viewModel).resumeGuide
        assertEquals(newerGuide.id.value, resumeGuide?.guideId)
        assertEquals(projectB.name, resumeGuide?.projectName)
    }

    @Test
    fun onlyActiveProjectsAreScannedForAResumeGuide() {
        val pausedProject = project("paused", ProjectStatus.PAUSED, Craft.KNITTING)
        val guideOnPausedProject = guide("guide-on-paused", pausedProject.id)
        val executions = FakeExecutionRepository()
            .withActiveExecution(guideOnPausedProject.id, updatedAt = 100)

        val viewModel = viewModel(
            projects = listOf(pausedProject),
            guidesByProject = mapOf(pausedProject.id to listOf(guideOnPausedProject)),
            executions = executions
        )

        assertNull(contentState(viewModel).resumeGuide)
    }

    private fun contentState(viewModel: HomeViewModel): HomeUiState.Content {
        return viewModel.uiState.value as HomeUiState.Content
    }

    private fun project(id: String, status: ProjectStatus, craft: Craft) = Project(
        id = id,
        name = id,
        craft = craft,
        projectType = ProjectType.OTHER,
        status = status,
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    private fun guide(id: String, projectId: String) = Guide(
        id = GuideId(id),
        projectId = projectId,
        name = id,
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    private fun viewModel(
        projects: List<Project>,
        guidesByProject: Map<String, List<Guide>>,
        executions: FakeExecutionRepository = FakeExecutionRepository()
    ): HomeViewModel {
        val viewModel = HomeViewModel(
            projectRepository = FakeProjectRepository(projects),
            guideRepository = FakeGuideRepository(guidesByProject),
            executionRepository = executions,
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

private class FakeProjectRepository(private val projects: List<Project>) : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> = flowOf(projects)
    override fun observeProject(id: String): Flow<Project?> =
        flowOf(projects.firstOrNull { it.id == id })
    override suspend fun saveProject(project: Project) = Unit
    override suspend fun deleteProject(project: Project) = Unit
}

private class FakeGuideRepository(
    private val guidesByProject: Map<String, List<Guide>>
) : GuideRepository {
    override fun observeGuides(projectId: String): Flow<List<Guide>> =
        flowOf(guidesByProject[projectId].orEmpty())

    override suspend fun getGuide(guideId: GuideId): Guide? =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun createGuide(projectId: String, name: String, notes: String?): Guide =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun updateGuideMetadata(guideId: GuideId, name: String, notes: String?): Guide? =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun deleteGuide(guideId: GuideId): Unit =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun loadDraft(guideId: GuideId): GuideDraft? =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun saveDraft(draft: GuideDraft): GuideDraft =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun createDraftFromLatestRevision(guideId: GuideId): GuideDraft =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun listRevisions(guideId: GuideId): List<DefinitionRevision> =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun loadRevision(revisionId: DefinitionRevisionId): DefinitionRevision? =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun getLatestRevision(guideId: GuideId): DefinitionRevision? =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun publishDraft(guideId: GuideId): DefinitionRevision =
        throw UnsupportedOperationException("Not used by HomeViewModel")
}

private class FakeExecutionRepository : ExecutionRepository {
    private val activeExecutionByGuide = mutableMapOf<GuideId, PersistedExecution>()

    fun withActiveExecution(guideId: GuideId, updatedAt: Long): FakeExecutionRepository {
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
            createdAt = updatedAt,
            updatedAt = updatedAt,
            completedAt = null
        )
        return this
    }

    override suspend fun createExecution(
        guideId: GuideId,
        revisionId: DefinitionRevisionId
    ): PersistedExecution = throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun loadExecution(executionId: ExecutionId): PersistedExecution? =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun getActiveExecution(guideId: GuideId): PersistedExecution? =
        activeExecutionByGuide[guideId]

    override suspend fun listExecutions(guideId: GuideId): List<PersistedExecution> =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun applyComplete(
        executionId: ExecutionId,
        expectedVersion: Long
    ): PersistedExecutionTransitionResult =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun applyPrevious(
        executionId: ExecutionId,
        expectedVersion: Long
    ): PersistedExecutionTransitionResult =
        throw UnsupportedOperationException("Not used by HomeViewModel")

    override suspend fun applyJump(
        executionId: ExecutionId,
        expectedVersion: Long,
        targetAddress: ExecutionAddress
    ): PersistedExecutionTransitionResult =
        throw UnsupportedOperationException("Not used by HomeViewModel")
}
