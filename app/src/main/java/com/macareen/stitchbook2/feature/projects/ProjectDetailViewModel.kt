package com.macareen.stitchbook2.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractionException
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.ToolRepository
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What a Guide's own entry point offers, derived purely from existing
 * persisted state -- never a business rule this layer invents:
 * - [CONTINUE]: an ACTIVE Execution already exists; Focus Mode resumes it.
 * - [START]: no ACTIVE Execution, but a published (executable) Revision
 *   exists; Focus Mode creates one pinned to [GuideRepository.getLatestRevision].
 * - [NOT_EXECUTABLE]: the Guide has never published a Revision (Draft-only).
 *   Drafts are never executable, so no Start/Continue action is offered.
 */
enum class GuideEntryAction {
    CONTINUE,
    START,
    NOT_EXECUTABLE
}

/** Why importing a PDF as a Guide didn't produce a reviewable Draft. */
enum class PdfImportError {
    NO_EXTRACTABLE_TEXT,
    EXTRACTION_FAILED
}

data class GuideListEntry(
    val guide: Guide,
    val action: GuideEntryAction
)

sealed interface ProjectDetailUiState {
    data object Loading : ProjectDetailUiState
    data object NotFound : ProjectDetailUiState
    data object LoadError : ProjectDetailUiState
    data class Content(
        val project: Project,
        val guideEntries: List<GuideListEntry> = emptyList(),
        /** Tools assigned to this project via the many-to-many join (ARCHITECTURE.md §9) -- assigning happens from the Tools screen; this list is read-only-with-unassign here. */
        val assignedTools: List<ToolItem> = emptyList(),
        val isDeleting: Boolean = false,
        val deleteFailed: Boolean = false,
        val isCreatingGuide: Boolean = false,
        val createGuideFailed: Boolean = false,
        val isImportingPdf: Boolean = false,
        val pdfImportError: PdfImportError? = null
    ) : ProjectDetailUiState
}

private data class CreateGuideState(
    val isCreating: Boolean = false,
    val failed: Boolean = false
)

private data class PdfImportState(
    val isImporting: Boolean = false,
    val error: PdfImportError? = null
)

class ProjectDetailViewModel(
    private val projectId: String,
    private val repository: ProjectRepository,
    private val guideRepository: GuideRepository,
    private val executionRepository: ExecutionRepository,
    private val toolRepository: ToolRepository,
    private val createGuideFromPdfUseCase: CreateGuideFromPdfUseCase,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val deleteState = MutableStateFlow(DeleteState())
    private val createGuideState = MutableStateFlow(CreateGuideState())
    private val pdfImportState = MutableStateFlow(PdfImportState())

    val uiState: StateFlow<ProjectDetailUiState> = combine(
        combine(
            repository.observeProject(projectId)
                .map<Project?, ProjectLoadState> { ProjectLoadState.Loaded(it) }
                .catch { emit(ProjectLoadState.Failed) },
            guideRepository.observeGuides(projectId)
                .map { guides -> guides.map { guide -> GuideListEntry(guide, resolveEntryAction(guide)) } }
                .catch { emit(emptyList()) },
            deleteState,
            createGuideState,
            pdfImportState
        ) { loadState, guideEntries, deletion, creation, pdfImport ->
            BaseState(loadState, guideEntries, deletion, creation, pdfImport)
        },
        toolRepository.observeToolItemsForProject(projectId).catch { emit(emptyList()) }
    ) { base, assignedTools ->
        when (base.loadState) {
            ProjectLoadState.Failed -> ProjectDetailUiState.LoadError
            is ProjectLoadState.Loaded -> {
                val project = base.loadState.project
                if (project == null) {
                    ProjectDetailUiState.NotFound
                } else {
                    ProjectDetailUiState.Content(
                        project = project,
                        guideEntries = base.guideEntries,
                        assignedTools = assignedTools,
                        isDeleting = base.deletion.isDeleting,
                        deleteFailed = base.deletion.failed,
                        isCreatingGuide = base.creation.isCreating,
                        createGuideFailed = base.creation.failed,
                        isImportingPdf = base.pdfImport.isImporting,
                        pdfImportError = base.pdfImport.error
                    )
                }
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectDetailUiState.Loading
    )

    /**
     * Reads existing persisted state only -- never decides completion,
     * traversal, or revision selection itself. An ACTIVE Execution always
     * wins over a published Revision: only one can ever be true at once
     * for a real Guide, and Continue must never be offered alongside Start.
     */
    private suspend fun resolveEntryAction(guide: Guide): GuideEntryAction {
        return try {
            if (executionRepository.getActiveExecution(guide.id) != null) {
                GuideEntryAction.CONTINUE
            } else if (guideRepository.getLatestRevision(guide.id) != null) {
                GuideEntryAction.START
            } else {
                GuideEntryAction.NOT_EXECUTABLE
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            GuideEntryAction.NOT_EXECUTABLE
        }
    }

    private val deletedChannel = Channel<Unit>(Channel.BUFFERED)
    val deletedEvents = deletedChannel.receiveAsFlow()

    private val guideCreatedChannel = Channel<String>(Channel.BUFFERED)
    val guideCreatedEvents = guideCreatedChannel.receiveAsFlow()

    /**
     * Creates a new Guide with an empty Draft and emits its ID via
     * [guideCreatedEvents] so the caller can navigate straight into the
     * Draft editor -- there is no in-app way to author a Guide's content
     * otherwise.
     */
    fun createGuide(name: String) {
        val current = uiState.value as? ProjectDetailUiState.Content ?: return
        if (current.isCreatingGuide) return

        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return

        createGuideState.value = CreateGuideState(isCreating = true)

        scope.launch {
            try {
                val guide = guideRepository.createGuide(current.project.id, normalizedName)
                createGuideState.value = CreateGuideState()
                guideCreatedChannel.send(guide.id.value)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                createGuideState.value = CreateGuideState(failed = true)
            }
        }
    }

    /**
     * Extracts, parses, and creates a Guide from [pdfBytes] the same way
     * [createGuide] creates one manually -- the resulting Draft is
     * unpublished and ordinary, reviewed in the same Draft editor, never
     * auto-published (ROADMAP.md's "Parser foundation" item). [pdfBytes] is
     * a plain in-memory copy rather than a live `content://` stream so this
     * call's lifetime does not depend on the caller keeping a file
     * descriptor open.
     */
    fun createGuideFromPdf(name: String, pdfBytes: ByteArray) {
        val current = uiState.value as? ProjectDetailUiState.Content ?: return
        if (current.isImportingPdf) return

        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return

        pdfImportState.value = PdfImportState(isImporting = true)

        scope.launch {
            try {
                when (
                    val result = createGuideFromPdfUseCase(
                        current.project.id,
                        normalizedName,
                        ByteArrayInputStream(pdfBytes)
                    )
                ) {
                    is CreateGuideFromPdfUseCase.Result.Success -> {
                        pdfImportState.value = PdfImportState()
                        guideCreatedChannel.send(result.guideId.value)
                    }

                    CreateGuideFromPdfUseCase.Result.NoExtractableText -> {
                        pdfImportState.value = PdfImportState(error = PdfImportError.NO_EXTRACTABLE_TEXT)
                    }

                    is CreateGuideFromPdfUseCase.Result.ExtractionFailed -> {
                        pdfImportState.value = PdfImportState(error = PdfImportError.EXTRACTION_FAILED)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                pdfImportState.value = PdfImportState(error = PdfImportError.EXTRACTION_FAILED)
            }
        }
    }

    /** Removes just this one project<->tool membership row -- never touches the ToolItem itself or its other project assignments. */
    fun unassignTool(toolItem: ToolItem) {
        scope.launch {
            try {
                toolRepository.unassignToolFromProject(toolItem.id, projectId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Nothing to reconcile locally: the list reflects whatever is
                // actually persisted on the next emission.
            }
        }
    }

    fun deleteProject() {
        val current = uiState.value as? ProjectDetailUiState.Content ?: return
        if (current.isDeleting) return

        deleteState.value = DeleteState(isDeleting = true)

        scope.launch {
            try {
                repository.deleteProject(current.project)
                deletedChannel.send(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                deleteState.value = DeleteState(failed = true)
            }
        }
    }

    companion object {
        fun factory(
            projectId: String,
            repository: ProjectRepository,
            guideRepository: GuideRepository,
            executionRepository: ExecutionRepository,
            toolRepository: ToolRepository,
            createGuideFromPdfUseCase: CreateGuideFromPdfUseCase
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProjectDetailViewModel(
                    projectId,
                    repository,
                    guideRepository,
                    executionRepository,
                    toolRepository,
                    createGuideFromPdfUseCase
                )
            }
        }
    }
}

private data class BaseState(
    val loadState: ProjectLoadState,
    val guideEntries: List<GuideListEntry>,
    val deletion: DeleteState,
    val creation: CreateGuideState,
    val pdfImport: PdfImportState
)

private sealed interface ProjectLoadState {
    data class Loaded(val project: Project?) : ProjectLoadState
    data object Failed : ProjectLoadState
}

private data class DeleteState(
    val isDeleting: Boolean = false,
    val failed: Boolean = false
)
