package com.macareen.stitchbook2.feature.library

import com.macareen.stitchbook2.domain.model.normalizedLibraryItemTags
import com.macareen.stitchbook2.domain.model.normalizedLibraryItemTitle
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.PatternPdfChangeResult
import com.macareen.stitchbook2.domain.model.PickedDocument
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
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.PdfSourceRegion
import com.macareen.stitchbook2.domain.parsing.PdfSourceSelection
import com.macareen.stitchbook2.domain.parsing.ReviewedPdfSource
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

enum class PatternEditProblem { FAILED, CHANGED, MISSING }
data class PatternEditReview(
    val expected: LibraryItem,
    val requestId: String = UUID.randomUUID().toString(),
    val isSaving: Boolean = false,
    val problem: PatternEditProblem? = null
)

enum class PdfChangeProblem { FAILED, CHANGED, MISSING }

data class PatternPdfReview(
    val expected: LibraryItem,
    val document: PickedDocument?,
    val requestId: String = UUID.randomUUID().toString(),
    val isSaving: Boolean = false,
    val problem: PdfChangeProblem? = null
)

enum class PatternPdfProblem { UNREADABLE, NO_TEXT, ATTACH_FAILED }

data class PdfSourceReview(
    val expected: LibraryItem,
    val size: String,
    val bytes: ByteArray,
    val original: ExtractedDocument,
    val regions: List<PdfSourceRegion>,
    val preview: ReviewedPdfSource? = null,
    val busy: Boolean = false,
    val problem: String? = null,
    val changed: Boolean = false
)

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
        val completedProjects: Int = 0,
        val guideRemovalFailed: Boolean = false,
        val guideArchiveFailed: Boolean = false,
        val archivedGuides: List<PatternGuideEntry> = emptyList(),
        val isReadingDetails: Boolean = false,
        val detailsReadFailed: Boolean = false
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
    private val editReview = MutableStateFlow<PatternEditReview?>(null)
    val patternEditReview: StateFlow<PatternEditReview?> = editReview.asStateFlow()
    private val pdfReview = MutableStateFlow<PatternPdfReview?>(null)
    val pdfChangeReview: StateFlow<PatternPdfReview?> = pdfReview.asStateFlow()
    private val sourceReview = MutableStateFlow<PdfSourceReview?>(null)
    val pdfSourceReview: StateFlow<PdfSourceReview?> = sourceReview.asStateFlow()

    fun dismissSourceReview() {
        if (sourceReview.value?.busy != true) sourceReview.value = null
    }

    fun changeSourceRegions(regions: List<PdfSourceRegion>) {
        val review = sourceReview.value ?: return
        if (!review.busy) sourceReview.value = review.copy(regions = regions, preview = null, problem = null)
    }

    fun previewSource() {
        val review = sourceReview.value ?: return
        val useCase = createGuideFromPdf ?: return
        if (review.busy || review.changed) return
        sourceReview.value = review.copy(busy = true, problem = null)
        scope.launch {
            try {
                val selection = PdfSourceSelection(review.regions)
                val preview = ByteArrayInputStream(review.bytes).use { useCase.reviewSource(it, review.original, selection) }
                sourceReview.value = review.copy(preview = preview,
                    problem = if (preview.selected.hasNoExtractableText) "No readable text in this selection. Check the original for charts or missing instructions." else null)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                sourceReview.value = review.copy(problem = error.message ?: "Unable to preview selected regions.")
            }
        }
    }

    fun confirmSourceReview() {
        createDraftFromSourceReview(requireTextReview = true)
    }

    /** The fast path creates only an unpublished draft; the Draft editor remains the review step. */
    fun createDraftFromSelection() {
        createDraftFromSourceReview(requireTextReview = false)
    }

    private fun createDraftFromSourceReview(requireTextReview: Boolean) {
        val review = sourceReview.value ?: return
        val useCase = createGuideFromPdf ?: return
        val currentPreview = review.preview?.takeIf { it.selection.regions == review.regions }
        if (requireTextReview && (currentPreview == null || review.problem != null)) return
        if (review.busy || review.changed || review.problem != null) return
        sourceReview.value = review.copy(busy = true)
        creation.value = CreationState(isCreating = true)
        scope.launch {
            try {
                if (libraryRepository.observeLibraryItem(libraryItemId).first() != review.expected) {
                    sourceReview.value = review.copy(changed = true)
                    creation.value = CreationState()
                    return@launch
                }
                val selectedSource = currentPreview ?: ByteArrayInputStream(review.bytes).use {
                    useCase.reviewSource(it, review.original, PdfSourceSelection(review.regions))
                }
                if (selectedSource.selected.hasNoExtractableText) {
                    sourceReview.value = review.copy(preview = selectedSource,
                        problem = "No readable text in this selection. Check the original for charts or missing instructions.")
                    creation.value = CreationState()
                    return@launch
                }
                val name = PatternNames.guide(review.expected.title, review.size, review.expected.pdfFileName)
                when (val result = ByteArrayInputStream(review.bytes).use {
                    useCase.forPattern(libraryItemId, review.size, review.expected.sizes, name, it, selectedSource)
                }) {
                    is CreateGuideFromPdfUseCase.Result.Success -> {
                        sourceReview.value = null
                        created.value = result.guideId.value
                    }
                    else -> sourceReview.value = review.copy(problem = "Unable to create a draft from this selection.")
                }
                creation.value = CreationState()
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                sourceReview.value = review.copy(problem = "Unable to create a draft. Your selection is retained.")
                creation.value = CreationState()
            }
        }
    }

    /** The new guide's id once created; the screen opens it in the Draft editor. */
    val createdGuideId: StateFlow<String?> = created.asStateFlow()

    private val startedProject = MutableStateFlow<String?>(null)
    private val ravelryPdf = MutableStateFlow(RavelryPdfState())

    private data class DiscoveredSizes(val uri: String, val labels: List<String>, val reading: Boolean = false, val failed: Boolean = false)
    private val detailsRetry = MutableStateFlow(0L)
    private val discoveredSizes = MutableStateFlow<DiscoveredSizes?>(null)

    init {
        // Reading choices is independent of creating a guide. A replaced PDF
        // cancels its predecessor; metadata updates do not re-extract the PDF.
        scope.launch {
            combine(libraryRepository.observeLibraryItem(libraryItemId).map { it?.pdfUri }.distinctUntilChanged(), detailsRetry) { uri, _ -> uri }
                .collectLatest { uri ->
                    discoveredSizes.value = null
                    val useCase = createGuideFromPdf
                    if (uri != null && useCase != null) {
                        discoveredSizes.value = DiscoveredSizes(uri, emptyList(), reading = true)
                        try {
                            val bytes = readPatternFile(uri)
                            val info = bytes?.let { ByteArrayInputStream(it).use { input -> useCase.setupPattern(libraryItemId, input, uri) } }
                            discoveredSizes.value = DiscoveredSizes(uri, info?.sizes.orEmpty(), failed = info == null)
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { discoveredSizes.value = DiscoveredSizes(uri, emptyList(), failed = true) }
                    }
                }
        }
    }

    fun readPatternDetails() {
        if (discoveredSizes.value?.reading == true || creation.value.isCreating || pdfReview.value != null || editReview.value != null) return
        detailsRetry.value += 1
    }

    /** Saves the pattern's Ravelry PDF into the pattern folder and links it here. */
    fun getRavelryPdf() {
        val download = downloadRavelryPdf ?: return
        if (ravelryPdf.value.isDownloading || creation.value.isCreating || pdfReview.value != null || editReview.value != null) return
        ravelryPdf.value = RavelryPdfState(isDownloading = true)
        scope.launch {
            ravelryPdf.value = RavelryPdfState(result = download(libraryItemId))
        }
    }

    fun editPattern() {
        if (creation.value.isCreating || ravelryPdf.value.isDownloading || pdfReview.value != null || editReview.value != null) return
        val pattern = (uiState.value as? PatternGuidesUiState.Content)?.pattern ?: return
        editReview.value = PatternEditReview(pattern)
    }

    fun dismissPatternEdit() {
        if (editReview.value?.isSaving != true) editReview.value = null
    }

    /** Explicitly discards this form only after the user chooses Reload saved details. */
    fun reloadPatternEdit(review: PatternEditReview) {
        if (editReview.value != review || review.isSaving) return
        editReview.value = review.copy(isSaving = true)
        scope.launch {
            try {
                val latest = libraryRepository.observeLibraryItem(libraryItemId).first()
                editReview.value = if (latest == null) review.copy(problem = PatternEditProblem.MISSING)
                    else PatternEditReview(latest)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { editReview.value = review.copy(problem = PatternEditProblem.FAILED) }
        }
    }

    fun savePatternEdit(review: PatternEditReview, title: String, craft: Craft,
        author: String, sourceUrl: String, tags: List<String>, notes: String, metadata: PatternMetadataInput) {
        if (editReview.value != review || review.isSaving || review.problem == PatternEditProblem.CHANGED ||
            review.problem == PatternEditProblem.MISSING) return
        val normalizedTitle = normalizedLibraryItemTitle(title) ?: return
        val yardageText = metadata.yardageRequiredText.trim().replace(',', '.')
        val yardage = yardageText.toDoubleOrNull()
        if (yardageText.isNotEmpty() && (yardage == null || !yardage.isFinite() || yardage <= 0.0)) return
        val replacement = review.expected.copy(title = normalizedTitle, craft = craft,
            author = author.trim().ifEmpty { null }, sourceUrl = sourceUrl.trim().ifEmpty { null },
            tags = normalizedLibraryItemTags(tags), notes = notes.trim().ifEmpty { null },
            gauge = metadata.gauge.trim().ifEmpty { null }, sizes = metadata.sizes.trim().ifEmpty { null },
            yardageRequired = yardage, recommendedTools = metadata.recommendedTools.trim().ifEmpty { null },
            ravelryPatternId = metadata.ravelryPatternId.trim().ifEmpty { null }, updatedAt = clock())
        editReview.value = review.copy(isSaving = true)
        scope.launch {
            try {
                val saved = libraryRepository.saveIfUnchanged(review.expected, replacement)
                editReview.value = if (saved) null else review.copy(problem = PatternEditProblem.CHANGED)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { editReview.value = review.copy(problem = PatternEditProblem.FAILED) }
        }
    }

    /** Prepare a review after the user picks an original; no database write yet. */
    fun attachPdf(document: PickedDocument) = requestPdfChange(document)

    fun requestPdfChange(document: PickedDocument?) {
        if (creation.value.isCreating || ravelryPdf.value.isDownloading || pdfReview.value != null || editReview.value != null) return
        val pattern = (uiState.value as? PatternGuidesUiState.Content)?.pattern ?: return
        if (document == null && pattern.pdfUri == null) return
        pdfReview.value = PatternPdfReview(pattern, document)
    }

    fun dismissPdfChange() {
        if (pdfReview.value?.isSaving != true) pdfReview.value = null
    }

    fun confirmPdfChange(review: PatternPdfReview) {
        if (pdfReview.value != review || review.isSaving || review.problem == PdfChangeProblem.MISSING ||
            creation.value.isCreating || ravelryPdf.value.isDownloading) return
        pdfReview.value = review.copy(isSaving = true)
        scope.launch {
            try {
                when (val result = libraryRepository.changePdfReviewed(review.expected, review.document, clock())) {
                    PatternPdfChangeResult.Applied -> {
                        pdfReview.value = null
                        creation.value = CreationState()
                        ravelryPdf.value = RavelryPdfState()
                    }
                    PatternPdfChangeResult.Missing ->
                        pdfReview.value = review.copy(problem = PdfChangeProblem.MISSING)
                    is PatternPdfChangeResult.Changed ->
                        pdfReview.value = review.copy(expected = result.current, problem = PdfChangeProblem.CHANGED)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { pdfReview.value = review.copy(problem = PdfChangeProblem.FAILED) }
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
        val pdfProblem: PatternPdfProblem? = null,
        val guideRemovalFailed: Boolean = false,
        val guideArchiveFailed: Boolean = false
    )

    private val completedProjects = combine(
        projectRepository?.observeProjects() ?: flowOf(emptyList()),
        materialsRepository?.observePatternLinks() ?: flowOf(emptyList())
    ) { projects, links -> completedProjectCount(libraryItemId, projects, links) }

    val uiState: StateFlow<PatternGuidesUiState> = combine(
        libraryRepository.observeLibraryItem(libraryItemId),
        guideRepository.observePatternGuidesIncludingArchived(libraryItemId).map { guides ->
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
                guides.filterNot { it.guide.isArchived },
                creationState.isCreating,
                creationState.failed,
                creationState.pdfProblem,
                canGetRavelryPdf = downloadRavelryPdf?.canDownload(pattern) == true,
                ravelryPdf = ravelryState,
                pdfSizeChoices = sizes?.takeIf { it.uri == pattern.pdfUri }?.labels.orEmpty(),
                guideRemovalFailed = creationState.guideRemovalFailed,
                guideArchiveFailed = creationState.guideArchiveFailed,
                isReadingDetails = sizes?.takeIf { it.uri == pattern.pdfUri }?.reading == true,
                detailsReadFailed = sizes?.takeIf { it.uri == pattern.pdfUri }?.failed == true,
                archivedGuides = guides.filter { it.guide.isArchived }
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
        if (content.isCreating || sourceReview.value != null || pdfReview.value != null || editReview.value != null || sizeLabel.isBlank()) return
        creation.value = CreationState(isCreating = true)
        scope.launch {
            try {
                val guideName = PatternNames.guide(content.pattern.title, sizeLabel, content.pattern.pdfFileName)
                val pdfUri = content.pattern.pdfUri
                val useCase = createGuideFromPdf
                if (fromPattern && pdfUri != null && useCase != null) {
                    val bytes = readPatternFile(pdfUri)
                    if (bytes == null) {
                        creation.value = CreationState(pdfProblem = PatternPdfProblem.UNREADABLE)
                        return@launch
                    }
                    val original = ByteArrayInputStream(bytes).use { useCase.readSource(it) }
                    if (original.hasNoExtractableText) {
                        creation.value = CreationState(pdfProblem = PatternPdfProblem.NO_TEXT)
                        return@launch
                    }
                    val latest = libraryRepository.observeLibraryItem(libraryItemId).first()
                    if (latest?.pdfUri != pdfUri) {
                        creation.value = CreationState(failed = true)
                        return@launch
                    }
                    sourceReview.value = PdfSourceReview(latest, sizeLabel.trim(), bytes, original,
                        (1..original.pageCount).map { PdfSourceRegion(it) })
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

    fun setGuideArchived(guideId: String, archived: Boolean) {
        if (creation.value.isCreating || pdfReview.value != null || editReview.value != null) return
        creation.value = CreationState(isCreating = true)
        scope.launch {
            try {
                val changed = guideRepository.setPatternGuideArchived(
                    com.macareen.stitchbook2.domain.execution.GuideId(guideId), libraryItemId, archived)
                creation.value = CreationState(guideArchiveFailed = !changed)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { creation.value = CreationState(guideArchiveFailed = true) }
        }
    }

    fun removeUnusedGuide(guideId: String) {
        if (creation.value.isCreating || pdfReview.value != null || editReview.value != null) return
        creation.value = CreationState(isCreating = true)
        scope.launch {
            try {
                val removed = guideRepository.deleteUnusedPatternGuide(
                    com.macareen.stitchbook2.domain.execution.GuideId(guideId), libraryItemId)
                creation.value = CreationState(guideRemovalFailed = !removed)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { creation.value = CreationState(guideRemovalFailed = true) }
        }
    }

    fun copyGuideToSize(sourceGuideId: String, targetSize: String) {
        val content = uiState.value as? PatternGuidesUiState.Content ?: return
        if (creation.value.isCreating || pdfReview.value != null || editReview.value != null || targetSize.isBlank()) return
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
