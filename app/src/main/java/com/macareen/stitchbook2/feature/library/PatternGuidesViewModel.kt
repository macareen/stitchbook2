package com.macareen.stitchbook2.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
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

/** One size's guide on a pattern, and whether it can be knitted yet. */
data class PatternGuideEntry(val guide: Guide, val isPublished: Boolean)

sealed interface PatternGuidesUiState {
    data object Loading : PatternGuidesUiState
    data object Missing : PatternGuidesUiState
    data class Content(
        val pattern: LibraryItem,
        val guides: List<PatternGuideEntry>,
        val isCreating: Boolean,
        val createFailed: Boolean
    ) : PatternGuidesUiState
}

/**
 * A pattern's own screen: its original file and its guides, one per size.
 * A new size guide starts as an empty draft and opens in the Draft editor.
 */
class PatternGuidesViewModel(
    private val libraryItemId: String,
    libraryRepository: LibraryRepository,
    private val guideRepository: GuideRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val creation = MutableStateFlow(CreationState())
    private val created = MutableStateFlow<String?>(null)

    /** The new guide's id once created; the screen opens it in the Draft editor. */
    val createdGuideId: StateFlow<String?> = created.asStateFlow()

    private data class CreationState(val isCreating: Boolean = false, val failed: Boolean = false)

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
            PatternGuidesUiState.Content(pattern, guides, creationState.isCreating, creationState.failed)
        }
    }
        .catch { emit(PatternGuidesUiState.Missing) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), PatternGuidesUiState.Loading)

    fun createGuide(sizeLabel: String, name: String) {
        val content = uiState.value as? PatternGuidesUiState.Content ?: return
        if (content.isCreating || sizeLabel.isBlank()) return
        creation.value = CreationState(isCreating = true)
        scope.launch {
            try {
                val guideName = name.trim().ifEmpty { "${content.pattern.title} · ${sizeLabel.trim()}" }
                created.value = guideRepository.createPatternGuide(libraryItemId, sizeLabel, guideName).id.value
                creation.value = CreationState()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                creation.value = CreationState(failed = true)
            }
        }
    }

    fun consumeCreatedGuide() {
        created.value = null
    }

    companion object {
        fun factory(
            libraryItemId: String,
            libraryRepository: LibraryRepository,
            guideRepository: GuideRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PatternGuidesViewModel(libraryItemId, libraryRepository, guideRepository) }
        }
    }
}
