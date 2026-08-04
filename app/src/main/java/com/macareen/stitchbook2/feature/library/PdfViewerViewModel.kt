package com.macareen.stitchbook2.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PdfViewerUiState {
    data object Loading : PdfViewerUiState
    data object NotFound : PdfViewerUiState
    data class Content(val item: LibraryItem) : PdfViewerUiState
}

/**
 * Owns only the Library item's persisted state (in particular
 * [LibraryItem.pdfLastViewedPage]); actually opening the PDF and rendering
 * pages is platform I/O the screen itself performs against the item's
 * `pdfUri` via [android.graphics.pdf.PdfRenderer], mirroring how Settings'
 * SAF import/export I/O already lives in its Composable rather than here.
 */
class PdfViewerViewModel(
    libraryItemId: String,
    private val repository: LibraryRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    val uiState = repository.observeLibraryItem(libraryItemId)
        .map<LibraryItem?, PdfViewerUiState> { item ->
            if (item == null) PdfViewerUiState.NotFound else PdfViewerUiState.Content(item)
        }
        .catch { emit(PdfViewerUiState.NotFound) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PdfViewerUiState.Loading
        )

    fun updateLastViewedPage(item: LibraryItem, page: Int) {
        if (item.pdfLastViewedPage == page) return
        scope.launch {
            try {
                repository.saveLibraryItem(item.copy(pdfLastViewedPage = page))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort: losing the remembered page on a transient
                // write failure isn't worth surfacing to the reader.
            }
        }
    }

    companion object {
        fun factory(
            libraryItemId: String,
            repository: LibraryRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PdfViewerViewModel(libraryItemId, repository)
            }
        }
    }
}
