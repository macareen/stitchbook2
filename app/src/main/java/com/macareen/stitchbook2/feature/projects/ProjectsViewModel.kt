package com.macareen.stitchbook2.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ProjectsUiState {
    data object Loading : ProjectsUiState
    data object Empty : ProjectsUiState
    data class Content(val projects: List<Project>) : ProjectsUiState
    data object Error : ProjectsUiState
}

class ProjectsViewModel(
    repository: ProjectRepository
) : ViewModel() {

    val uiState = repository.observeProjects()
        .map<List<Project>, ProjectsUiState> { projects ->
            if (projects.isEmpty()) {
                ProjectsUiState.Empty
            } else {
                ProjectsUiState.Content(projects)
            }
        }
        .catch { emit(ProjectsUiState.Error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProjectsUiState.Loading
        )

    companion object {
        fun factory(repository: ProjectRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProjectsViewModel(repository)
            }
        }
    }
}
