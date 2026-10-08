package com.macareen.stitchbook2.feature.projects

import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectDateError
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFormViewModelTest {

    @Test
    fun surroundingWhitespaceDoesNotMakeNewFormDirty() {
        val viewModel = ProjectFormViewModel(
            projectId = null,
            repository = UnusedProjectRepository
        )

        viewModel.updateName("   ")
        viewModel.updateNotes("\n\t")

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun meaningfulTextMakesNewFormDirtyAndRevertingClearsIt() {
        val viewModel = ProjectFormViewModel(
            projectId = null,
            repository = UnusedProjectRepository
        )

        viewModel.updateName("  New project  ")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.updateName(" ")
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun completingPrefillsTodayWithoutOverwritingAnExistingDate() {
        val viewModel = ProjectFormViewModel(projectId = null, repository = UnusedProjectRepository)

        viewModel.updateStatus(ProjectStatus.COMPLETED)
        assertEquals(LocalDate.now().toString(), viewModel.uiState.value.completedDate)

        viewModel.updateCompletedDate("2025-12-31")
        viewModel.updateStatus(ProjectStatus.ACTIVE)
        viewModel.updateStatus(ProjectStatus.COMPLETED)
        assertEquals("2025-12-31", viewModel.uiState.value.completedDate)
    }

    @Test
    fun invalidDatesBlockSavingAndEditingClearsTheError() {
        val viewModel = ProjectFormViewModel(projectId = null, repository = UnusedProjectRepository)
        viewModel.updateName("Sampler")
        viewModel.updateStartDate("next spring")

        viewModel.saveProject()

        assertEquals(setOf(ProjectDateError.INVALID_START), viewModel.uiState.value.dateErrors)
        assertFalse(viewModel.uiState.value.isSaving)

        viewModel.updateStartDate("2026-03-01")
        assertTrue(viewModel.uiState.value.dateErrors.isEmpty())
    }
}

private object UnusedProjectRepository : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> = emptyFlow()

    override fun observeProject(id: String): Flow<Project?> = emptyFlow()

    override suspend fun saveProject(project: Project) = Unit

    override suspend fun deleteProject(project: Project) = Unit
}
