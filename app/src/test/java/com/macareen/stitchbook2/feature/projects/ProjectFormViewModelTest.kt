package com.macareen.stitchbook2.feature.projects

import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
}

private object UnusedProjectRepository : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> = emptyFlow()

    override fun observeProject(id: String): Flow<Project?> = emptyFlow()

    override suspend fun saveProject(project: Project) = Unit

    override suspend fun deleteProject(project: Project) = Unit
}
