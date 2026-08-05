package com.macareen.stitchbook2.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.data.csv.LibraryCsvImportReport
import com.macareen.stitchbook2.data.csv.LibraryCsvRowError
import com.macareen.stitchbook2.data.csv.libraryItemsToCsv
import com.macareen.stitchbook2.data.csv.parseLibraryCsv
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.normalizedLibraryItemTags
import com.macareen.stitchbook2.domain.model.normalizedLibraryItemTitle
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryFilterState(
    val searchQuery: String = "",
    val craftFilter: Craft? = null,
    val bookmarksOnly: Boolean = false
)

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Error : LibraryUiState
    data class Content(
        val items: List<LibraryItem>,
        val filter: LibraryFilterState,
        val hasAnyItems: Boolean
    ) : LibraryUiState
}

class LibraryViewModel(
    private val repository: LibraryRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val filterState = MutableStateFlow(LibraryFilterState())
    private val _importReport = MutableStateFlow<LibraryCsvImportReport?>(null)
    val importReport: StateFlow<LibraryCsvImportReport?> = _importReport

    val uiState = combine(
        repository.observeLibraryItems(),
        filterState
    ) { items, filter ->
        LibraryUiState.Content(
            items = items.filter { matchesFilter(it, filter) },
            filter = filter,
            hasAnyItems = items.isNotEmpty()
        ) as LibraryUiState
    }
        .catch { emit(LibraryUiState.Error) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState.Loading
        )

    fun updateSearchQuery(value: String) {
        filterState.value = filterState.value.copy(searchQuery = value)
    }

    fun updateCraftFilter(value: Craft?) {
        filterState.value = filterState.value.copy(craftFilter = value)
    }

    fun updateBookmarksOnly(value: Boolean) {
        filterState.value = filterState.value.copy(bookmarksOnly = value)
    }

    fun toggleBookmark(item: LibraryItem) {
        scope.launch {
            try {
                repository.saveLibraryItem(
                    item.copy(bookmarked = !item.bookmarked, updatedAt = System.currentTimeMillis())
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort: the list re-renders from persisted state on the
                // next emission either way, so there is nothing else to do here.
            }
        }
    }

    fun saveItem(
        original: LibraryItem?,
        title: String,
        craft: Craft,
        author: String,
        sourceUrl: String,
        tags: List<String>,
        notes: String,
        pdfUri: String?,
        pdfFileName: String?
    ) {
        val normalizedTitle = normalizedLibraryItemTitle(title) ?: return
        scope.launch {
            val now = System.currentTimeMillis()
            val item = LibraryItem(
                id = original?.id ?: UUID.randomUUID().toString(),
                title = normalizedTitle,
                craft = craft,
                author = author.trim().ifEmpty { null },
                sourceUrl = sourceUrl.trim().ifEmpty { null },
                tags = normalizedLibraryItemTags(tags),
                notes = notes.trim().ifEmpty { null },
                bookmarked = original?.bookmarked ?: false,
                createdAt = original?.createdAt ?: now,
                updatedAt = now,
                pdfUri = pdfUri,
                pdfFileName = pdfFileName,
                // A newly attached/changed PDF has no remembered page yet;
                // only carry the prior page forward when the attachment
                // itself didn't change.
                pdfLastViewedPage = if (pdfUri == original?.pdfUri) original?.pdfLastViewedPage else null
            )
            try {
                repository.saveLibraryItem(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The dialog already closed optimistically; a failed save
                // simply leaves the previous persisted state in place.
            }
        }
    }

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

    fun deleteItem(item: LibraryItem) {
        scope.launch {
            try {
                repository.deleteLibraryItem(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Nothing to reconcile locally: the list reflects whatever is
                // actually persisted on the next emission.
            }
        }
    }

    /** Snapshots every persisted item (ignoring the current search/filter -- export is always complete) as CSV text. */
    fun exportCsv(onReady: suspend (String) -> Unit) {
        scope.launch {
            val csv = libraryItemsToCsv(repository.observeLibraryItems().first())
            onReady(csv)
        }
    }

    /**
     * Parses [csvText] against every currently persisted item (by id) and
     * persists every structurally valid row immediately -- row-level
     * errors are reported via [importReport] but never block the valid
     * rows in the same file from being saved.
     */
    fun importCsv(csvText: String) {
        scope.launch {
            try {
                val existingItemsById = repository.observeLibraryItems().first().associateBy { it.id }
                val report = parseLibraryCsv(csvText, existingItemsById = existingItemsById)
                report.validItems.forEach { repository.saveLibraryItem(it) }
                _importReport.value = report
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _importReport.value = LibraryCsvImportReport(
                    validItems = emptyList(),
                    rowErrors = listOf(
                        LibraryCsvRowError(
                            rowNumber = 0,
                            message = "This file could not be read as CSV."
                        )
                    )
                )
            }
        }
    }

    fun dismissImportReport() {
        _importReport.value = null
    }

    private fun matchesFilter(item: LibraryItem, filter: LibraryFilterState): Boolean {
        if (filter.craftFilter != null && item.craft != filter.craftFilter) return false
        if (filter.bookmarksOnly && !item.bookmarked) return false
        val query = filter.searchQuery.trim()
        if (query.isEmpty()) return true
        return item.title.contains(query, ignoreCase = true) ||
            item.author?.contains(query, ignoreCase = true) == true ||
            item.tags.any { it.contains(query, ignoreCase = true) }
    }

    companion object {
        fun factory(repository: LibraryRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibraryViewModel(repository)
            }
        }
    }
}
