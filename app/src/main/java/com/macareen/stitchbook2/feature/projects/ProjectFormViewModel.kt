package com.macareen.stitchbook2.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.normalizedProjectName
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ProjectFormUiState(
    val name: String = "",
    val craft: Craft = Craft.KNITTING,
    val projectType: ProjectType = ProjectType.OTHER,
    val status: ProjectStatus = ProjectStatus.PLANNED,
    val notes: String = "",
    val isLoading: Boolean = false,
    val isNotFound: Boolean = false,
    val loadFailed: Boolean = false,
    val nameIsBlank: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val hasUnsavedChanges: Boolean = false
)

private data class ProjectFormValues(
    val name: String,
    val craft: Craft,
    val projectType: ProjectType,
    val status: ProjectStatus,
    val notes: String
)

class ProjectFormViewModel(
    private val projectId: String?,
    private val repository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProjectFormUiState(isLoading = projectId != null)
    )
    val uiState: StateFlow<ProjectFormUiState> = _uiState.asStateFlow()

    private var originalProject: Project? = null
    private var initialValues = _uiState.value.toValues()

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val savedEvents = savedChannel.receiveAsFlow()

    val isEditing: Boolean = projectId != null

    init {
        if (projectId != null) {
            loadProject(projectId)
        }
    }

    fun updateName(value: String) {
        updateValues(
            _uiState.value.toValues().copy(name = value),
            clearNameError = true
        )
    }

    fun updateCraft(value: Craft) {
        updateValues(_uiState.value.toValues().copy(craft = value))
    }

    fun updateProjectType(value: ProjectType) {
        updateValues(_uiState.value.toValues().copy(projectType = value))
    }

    fun updateStatus(value: ProjectStatus) {
        updateValues(_uiState.value.toValues().copy(status = value))
    }

    fun updateNotes(value: String) {
        updateValues(_uiState.value.toValues().copy(notes = value))
    }

    fun saveProject() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading || current.isNotFound) return

        val normalizedName = normalizedProjectName(current.name)
        if (normalizedName == null) {
            _uiState.value = current.copy(nameIsBlank = true)
            return
        }

        _uiState.value = current.copy(
            isSaving = true,
            saveFailed = false,
            nameIsBlank = false
        )

        viewModelScope.launch {
            val normalizedNotes = current.notes.trim().ifEmpty { null }
            val normalizedValues = current.toValues().normalized()
            if (originalProject != null && normalizedValues == initialValues.normalized()) {
                _uiState.value = current.copy(
                    name = normalizedName,
                    notes = normalizedNotes.orEmpty(),
                    isSaving = false,
                    hasUnsavedChanges = false
                )
                savedChannel.send(Unit)
                return@launch
            }

            val now = System.currentTimeMillis()
            val project = originalProject?.copy(
                name = normalizedName,
                craft = current.craft,
                projectType = current.projectType,
                status = current.status,
                notes = normalizedNotes,
                updatedAt = now
            ) ?: Project(
                id = UUID.randomUUID().toString(),
                name = normalizedName,
                craft = current.craft,
                projectType = current.projectType,
                status = current.status,
                notes = normalizedNotes,
                createdAt = now,
                updatedAt = now
            )

            try {
                repository.saveProject(project)
                originalProject = project
                initialValues = current.toValues().copy(
                    name = normalizedName,
                    notes = normalizedNotes.orEmpty()
                )
                _uiState.value = current.copy(
                    name = normalizedName,
                    notes = normalizedNotes.orEmpty(),
                    isSaving = false,
                    hasUnsavedChanges = false
                )
                savedChannel.send(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = current.copy(
                    isSaving = false,
                    saveFailed = true
                )
            }
        }
    }

    private fun loadProject(id: String) {
        viewModelScope.launch {
            try {
                val project = repository.observeProject(id).first()
                if (project == null) {
                    _uiState.value = ProjectFormUiState(isNotFound = true)
                } else {
                    originalProject = project
                    initialValues = ProjectFormValues(
                        name = project.name,
                        craft = project.craft,
                        projectType = project.projectType,
                        status = project.status,
                        notes = project.notes.orEmpty()
                    )
                    _uiState.value = initialValues.toUiState()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = ProjectFormUiState(loadFailed = true)
            }
        }
    }

    private fun updateValues(
        values: ProjectFormValues,
        clearNameError: Boolean = false
    ) {
        val current = _uiState.value
        _uiState.value = values.toUiState().copy(
            nameIsBlank = if (clearNameError) false else current.nameIsBlank,
            saveFailed = false,
            hasUnsavedChanges = values.normalized() != initialValues.normalized()
        )
    }

    companion object {
        fun factory(
            projectId: String?,
            repository: ProjectRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProjectFormViewModel(projectId, repository)
            }
        }
    }
}

private fun ProjectFormUiState.toValues() = ProjectFormValues(
    name = name,
    craft = craft,
    projectType = projectType,
    status = status,
    notes = notes
)

private fun ProjectFormValues.toUiState() = ProjectFormUiState(
    name = name,
    craft = craft,
    projectType = projectType,
    status = status,
    notes = notes
)

private fun ProjectFormValues.normalized() = copy(
    name = name.trim(),
    notes = notes.trim()
)
