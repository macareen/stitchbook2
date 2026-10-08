package com.macareen.stitchbook2.feature.library

import com.macareen.stitchbook2.domain.model.ProjectFromPattern
import com.macareen.stitchbook2.domain.model.ProjectPatternLink
import com.macareen.stitchbook2.domain.repository.MaterialsRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import java.util.UUID
import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.parsing.PatternSizes
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PatternPdfProblem { UNREADABLE, NO_TEXT }

/** One size's guide on a pattern, and whether it can be knitted yet. */
data class PatternGuideEntry(val guide: Guide, val isPublished: Boolean)

sealed interface PatternGuidesUiState {
    data object Loading : PatternGuidesUiState
    data object Missing : PatternGuidesUiState
    data class Content(
        val pattern: LibraryItem,
        val guides: List<PatternGuideEntry>,
        val isCreating: Boolean,
        val createFailed: Boolean,
        /** Why filling a guide from the pattern's PDF failed, if it did. */
        val pdfProblem: PatternPdfProblem? = null
    ) : PatternGuidesUiState {
        /** The sizes the Library entry names, offered as choices for a new guide. */
        val sizeChoices: List<String> get() = PatternSizes.labelsFrom(pattern.sizes.orEmpty())
    }
}

/**
 * A pattern's own screen: its original file and its guides, one per size.
 * A new size guide starts as a draft, empty or filled from the pattern's PDF
 * with only that size's numbers, and opens in the Draft editor.
 */
class PatternGuidesViewModel(
    private val libraryItemId: String,
    private val libraryRepository: LibraryRepository,
    private val guideRepository: GuideRepository,
    externalScope: CoroutineScope? = null,
    private val createGuideFromPdf: CreateGuideFromPdfUseCase? = null,
    /** Reads the pattern's file by its stored address; null when it can't be opened. */
    private val readPatternFile: suspend (String) -> ByteArray? = { null },
    private val projectRepository: ProjectRepository? = null,
    private val materialsRepository: MaterialsRepository? = null,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val creation = MutableStateFlow(CreationState())
    private val created = MutableStateFlow<String?>(null)

    /** The new guide's id once created; the screen opens it in the Draft editor. */
    val createdGuideId: StateFlow<String?> = created.asStateFlow()

    private val startedProject = MutableStateFlow<String?>(null)

    /** The project just started from this pattern; the screen opens it. */
    val startedProjectId: StateFlow<String?> = startedProject.asStateFlow()

    /**
     * Starts a Planned project filled in from this pattern (title, craft, a
     * type read from the title, description) and links the pattern to it.
     */
    fun startProject() {
        val projects = projectRepository ?: return
        scope.launch {
            val pattern = libraryRepository.observeLibraryItem(libraryItemId).first() ?: return@launch
            val project = ProjectFromPattern.project(pattern, newId(), clock())
            projects.saveProject(project)
            materialsRepository?.linkPattern(ProjectPatternLink(project.id, pattern.id))
            startedProject.value = project.id
        }
    }

    fun consumeStartedProject() {
        startedProject.value = null
    }

    private data class CreationState(
        val isCreating: Boolean = false,
        val failed: Boolean = false,
        val pdfProblem: PatternPdfProblem? = null
    )

    val uiState: StateFlow<PatternGuidesUiState> = combine(
        libraryRepository.observeLibraryItem(libraryItemId),
        guideRepository.observePatternGuides(libraryItemId).map { guides ->
            guides.map { guide -> PatternGuideEntry(guide, isPublished = guideRepository.getLatestRevision(guide.id) != null) }
        },
        creation
    ) { pattern, guides, creationState ->
        if (pattern == null) {
            PatternGuidesUiState.Missing
        } else {
            PatternGuidesUiState.Content(pattern, guides, creationState.isCreating, creationState.failed, creationState.pdfProblem)
        }
    }
        .catch { emit(PatternGuidesUiState.Missing) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), PatternGuidesUiState.Loading)

    /** Creates the guide for [sizeLabel]; with [fromPattern], filled from the pattern's PDF for that size. */
    fun createGuide(sizeLabel: String, name: String, fromPattern: Boolean = false) {
        val content = uiState.value as? PatternGuidesUiState.Content ?: return
        if (content.isCreating || sizeLabel.isBlank()) return
        creation.value = CreationState(isCreating = true)
        scope.launch {
            try {
                val guideName = name.trim().ifEmpty { "${content.pattern.title} · ${sizeLabel.trim()}" }
                val pdfUri = content.pattern.pdfUri
                val useCase = createGuideFromPdf
                if (fromPattern && pdfUri != null && useCase != null) {
                    val problem = fillFromPattern(useCase, pdfUri, sizeLabel.trim(), content.pattern.sizes, guideName)
                    if (problem != null) {
                        creation.value = CreationState(pdfProblem = problem)
                        return@launch
                    }
                } else {
                    created.value = guideRepository.createPatternGuide(libraryItemId, sizeLabel, guideName).id.value
                }
                creation.value = CreationState()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                creation.value = CreationState(failed = true)
            }
        }
    }

    private suspend fun fillFromPattern(
        useCase: CreateGuideFromPdfUseCase,
        pdfUri: String,
        sizeLabel: String,
        knownSizes: String?,
        guideName: String
    ): PatternPdfProblem? {
        val bytes = readPatternFile(pdfUri) ?: return PatternPdfProblem.UNREADABLE
        return when (val result = useCase.forPattern(libraryItemId, sizeLabel, knownSizes, guideName, ByteArrayInputStream(bytes))) {
            is CreateGuideFromPdfUseCase.Result.Success -> {
                created.value = result.guideId.value
                null
            }
            CreateGuideFromPdfUseCase.Result.NoExtractableText -> PatternPdfProblem.NO_TEXT
            is CreateGuideFromPdfUseCase.Result.ExtractionFailed -> PatternPdfProblem.UNREADABLE
        }
    }

    fun consumeCreatedGuide() {
        created.value = null
    }

    companion object {
        fun factory(
            libraryItemId: String,
            libraryRepository: LibraryRepository,
            guideRepository: GuideRepository,
            createGuideFromPdf: CreateGuideFromPdfUseCase,
            readPatternFile: suspend (String) -> ByteArray?,
            projectRepository: ProjectRepository? = null,
            materialsRepository: MaterialsRepository? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PatternGuidesViewModel(
                    libraryItemId,
                    libraryRepository,
                    guideRepository,
                    createGuideFromPdf = createGuideFromPdf,
                    readPatternFile = readPatternFile,
                    projectRepository = projectRepository,
                    materialsRepository = materialsRepository
                )
            }
        }
    }
}
