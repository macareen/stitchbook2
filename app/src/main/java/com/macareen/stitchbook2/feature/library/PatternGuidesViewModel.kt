package com.macareen.stitchbook2.feature.library

import com.macareen.stitchbook2.domain.library.completedProjectCount
import kotlinx.coroutines.flow.flowOf
import com.macareen.stitchbook2.domain.model.PatternNames
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
import com.macareen.stitchbook2.domain.ravelry.DownloadRavelryPdf
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PatternPdfProblem { UNREADABLE, NO_TEXT, ATTACH_FAILED }

/** Fetching the pattern's purchased PDF from Ravelry into the pattern folder. */
data class RavelryPdfState(
    val isDownloading: Boolean = false,
    val result: DownloadRavelryPdf.Result? = null
)

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
        val pdfProblem: PatternPdfProblem? = null,
        /** Whether "Get the PDF" from Ravelry is offered. */
        val canGetRavelryPdf: Boolean = false,
        val ravelryPdf: RavelryPdfState = RavelryPdfState(),
        val pdfSizeChoices: List<String> = emptyList(),
        val completedProjects: Int = 0
    ) : PatternGuidesUiState {
        /** PDF sizes take precedence; saved Library sizes remain a fallback. */
        val sizeChoices: List<String> get() = pdfSizeChoices.ifEmpty { PatternSizes.labelsFrom(pattern.sizes.orEmpty()) }
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
    private val clock: () -> Long = System::currentTimeMillis,
    private val downloadRavelryPdf: DownloadRavelryPdf? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val creation = MutableStateFlow(CreationState())
    private val created = MutableStateFlow<String?>(null)

    /** The new guide's id once created; the screen opens it in the Draft editor. */
    val createdGuideId: StateFlow<String?> = created.asStateFlow()

    private val startedProject = MutableStateFlow<String?>(null)
    private val ravelryPdf = MutableStateFlow(RavelryPdfState())

    private data class DiscoveredSizes(val uri: String, val labels: List<String>)
    private val discoveredSizes = MutableStateFlow<DiscoveredSizes?>(null)

    init {
        // Reading choices is independent of creating a guide. A replaced PDF
        // cancels its predecessor; metadata updates do not re-extract the PDF.
        scope.launch {
            libraryRepository.observeLibraryItem(libraryItemId)
                .map { it?.pdfUri }
                .distinctUntilChanged()
                .collectLatest { uri ->
                    discoveredSizes.value = null
                    val useCase = createGuideFromPdf
                    if (uri != null && useCase != null) {
                        val labels = try {
                            val bytes = readPatternFile(uri)
                            if (bytes == null) emptyList() else ByteArrayInputStream(bytes).use { useCase.sizeChoices(it) }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            emptyList()
                        }
                        discoveredSizes.value = DiscoveredSizes(uri, labels)
                    }
                }
        }
    }

    /** Saves the pattern's Ravelry PDF into the pattern folder and links it here. */
    fun getRavelryPdf() {
        val download = downloadRavelryPdf ?: return
        if (ravelryPdf.value.isDownloading) return
        ravelryPdf.value = RavelryPdfState(isDownloading = true)
        scope.launch {
            ravelryPdf.value = RavelryPdfState(result = download(libraryItemId))
        }
    }

    /** Link a user-selected original document without copying or changing its contents. */
    fun attachPdf(document: com.macareen.stitchbook2.domain.model.PickedDocument) {
        if (creation.value.isCreating || ravelryPdf.value.isDownloading) return
        creation.value = CreationState(isCreating = true)
        scope.launch {
            try {
                val latest = libraryRepository.observeLibraryItem(libraryItemId).first() ?: return@launch
                libraryRepository.saveLibraryItem(latest.copy(pdfUri = document.uri, pdfFileName = document.displayName,
                    pdfLastViewedPage = if (latest.pdfUri == document.uri) latest.pdfLastViewedPage else null, updatedAt = clock()))
                ravelryPdf.value = RavelryPdfState()
                creation.value = CreationState()
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { creation.value = CreationState(pdfProblem = PatternPdfProblem.ATTACH_FAILED) }
            finally { if (creation.value.isCreating) creation.value = CreationState() }
        }
    }

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

    private val completedProjects = combine(
        projectRepository?.observeProjects() ?: flowOf(emptyList()),
        materialsRepository?.observePatternLinks() ?: flowOf(emptyList())
    ) { projects, links -> completedProjectCount(libraryItemId, projects, links) }

    val uiState: StateFlow<PatternGuidesUiState> = combine(
        libraryRepository.observeLibraryItem(libraryItemId),
        guideRepository.observePatternGuides(libraryItemId).map { guides ->
            guides.map { guide -> PatternGuideEntry(guide, isPublished = guideRepository.getLatestRevision(guide.id) != null) }
        },
        creation,
        ravelryPdf,
        discoveredSizes
    ) { pattern, guides, creationState, ravelryState, sizes ->
        if (pattern == null) {
            PatternGuidesUiState.Missing
        } else {
            PatternGuidesUiState.Content(
                pattern,
                guides,
                creationState.isCreating,
                creationState.failed,
                creationState.pdfProblem,
                canGetRavelryPdf = downloadRavelryPdf?.canDownload(pattern) == true,
                ravelryPdf = ravelryState,
                pdfSizeChoices = sizes?.takeIf { it.uri == pattern.pdfUri }?.labels.orEmpty()
            )
        }
    }
        .combine(completedProjects) { state, count ->
            if (state is PatternGuidesUiState.Content) state.copy(completedProjects = count) else state
        }
        .catch { emit(PatternGuidesUiState.Missing) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), PatternGuidesUiState.Loading)

    /** Creates the guide for [sizeLabel]; with [fromPattern], filled from the pattern's PDF for that size. */
    fun createGuide(sizeLabel: String, fromPattern: Boolean = false) {
        val content = uiState.value as? PatternGuidesUiState.Content ?: return
        if (content.isCreating || sizeLabel.isBlank()) return
        creation.value = CreationState(isCreating = true)
        scope.launch {
            try {
                val guideName = PatternNames.guide(content.pattern.title, sizeLabel, content.pattern.pdfFileName)
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

    fun copyGuideToSize(sourceGuideId: String, targetSize: String) {
        val content = uiState.value as? PatternGuidesUiState.Content ?: return
        if (creation.value.isCreating || targetSize.isBlank()) return
        val source = content.guides.firstOrNull { it.guide.id.value == sourceGuideId && it.isPublished } ?: return
        creation.value = CreationState(isCreating = true)
        scope.launch {
            try {
                val name = PatternNames.guide(content.pattern.title, targetSize, content.pattern.pdfFileName)
                created.value = guideRepository.copyPatternGuideToSize(source.guide.id, targetSize, name).id.value
                creation.value = CreationState()
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { creation.value = CreationState(failed = true) }
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
            materialsRepository: MaterialsRepository? = null,
            downloadRavelryPdf: DownloadRavelryPdf? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PatternGuidesViewModel(
                    libraryItemId,
                    libraryRepository,
                    guideRepository,
                    createGuideFromPdf = createGuideFromPdf,
                    readPatternFile = readPatternFile,
                    projectRepository = projectRepository,
                    materialsRepository = materialsRepository,
                    downloadRavelryPdf = downloadRavelryPdf
                )
            }
        }
    }
}
