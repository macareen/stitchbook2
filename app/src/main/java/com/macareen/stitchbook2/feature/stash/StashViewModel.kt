package com.macareen.stitchbook2.feature.stash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.data.csv.StashCsvImportReport
import com.macareen.stitchbook2.data.csv.StashCsvRowError
import com.macareen.stitchbook2.data.csv.parseStashCsv
import com.macareen.stitchbook2.data.csv.stashItemsToCsv
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.normalizedStashItemName
import com.macareen.stitchbook2.domain.repository.StashRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StashFilterState(
    val searchQuery: String = "",
    val categoryFilter: StashCategory? = null
)

sealed interface StashUiState {
    data object Loading : StashUiState
    data object Error : StashUiState
    data class Content(
        val items: List<StashItem>,
        val filter: StashFilterState,
        val hasAnyItems: Boolean
    ) : StashUiState
}

/**
 * Raw, unvalidated form field values from the add/edit dialog. [StashViewModel.saveItem]
 * owns parsing numeric text and clearing yarn-only fields the selected
 * category doesn't use -- the dialog itself makes no judgment about what
 * is valid for a given category.
 */
data class StashItemFormInput(
    val name: String,
    val category: StashCategory,
    val brand: String,
    val colorway: String,
    val dyeLot: String,
    val weightCategory: String,
    val fiberContent: String,
    val quantityText: String,
    val unitLabel: String,
    val yardagePerUnitText: String,
    val notes: String,
    val storageLocation: String,
    val careInstructions: String,
    val ravelryYarnId: String,
    val purchaseSource: String,
    val purchasePriceText: String,
    val purchaseDate: String
)

class StashViewModel(
    private val repository: StashRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val filterState = MutableStateFlow(StashFilterState())

    private val _importReport = MutableStateFlow<StashCsvImportReport?>(null)
    val importReport: StateFlow<StashCsvImportReport?> = _importReport

    val uiState = combine(
        repository.observeStashItems(),
        filterState
    ) { items, filter ->
        StashUiState.Content(
            items = items.filter { matchesFilter(it, filter) },
            filter = filter,
            hasAnyItems = items.isNotEmpty()
        ) as StashUiState
    }
        .catch { emit(StashUiState.Error) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StashUiState.Loading
        )

    fun updateSearchQuery(value: String) {
        filterState.value = filterState.value.copy(searchQuery = value)
    }

    fun updateCategoryFilter(value: StashCategory?) {
        filterState.value = filterState.value.copy(categoryFilter = value)
    }

    /**
     * [colorway]/[dyeLot]/[weightCategory]/[fiberContent]/[yardagePerUnit]/[ravelryYarnId]
     * are only meaningful for [StashCategory.YARN]; a non-yarn category
     * clears them regardless of what the form happened to hold, so switching
     * a form's category away from Yarn can never leave stale yarn-only data
     * behind. [storageLocation]/[careInstructions]/[purchaseSource]/
     * [purchasePrice]/[purchaseDate] are meaningful for every category (you
     * buy and store needles and notions too, not just yarn) and are never
     * cleared.
     */
    fun saveItem(original: StashItem?, form: StashItemFormInput) {
        val normalizedName = normalizedStashItemName(form.name) ?: return
        val isYarn = form.category == StashCategory.YARN
        scope.launch {
            val now = System.currentTimeMillis()
            val item = StashItem(
                id = original?.id ?: UUID.randomUUID().toString(),
                name = normalizedName,
                category = form.category,
                brand = form.brand.trim().ifEmpty { null },
                colorway = if (isYarn) form.colorway.trim().ifEmpty { null } else null,
                dyeLot = if (isYarn) form.dyeLot.trim().ifEmpty { null } else null,
                weightCategory = if (isYarn) form.weightCategory.trim().ifEmpty { null } else null,
                fiberContent = if (isYarn) form.fiberContent.trim().ifEmpty { null } else null,
                quantity = form.quantityText.toDoubleOrNull() ?: 1.0,
                unitLabel = form.unitLabel.trim().ifEmpty { "units" },
                yardagePerUnit = if (isYarn) form.yardagePerUnitText.toDoubleOrNull() else null,
                notes = form.notes.trim().ifEmpty { null },
                storageLocation = form.storageLocation.trim().ifEmpty { null },
                careInstructions = form.careInstructions.trim().ifEmpty { null },
                ravelryYarnId = if (isYarn) form.ravelryYarnId.trim().ifEmpty { null } else null,
                purchaseSource = form.purchaseSource.trim().ifEmpty { null },
                purchasePrice = form.purchasePriceText.toDoubleOrNull(),
                purchaseDate = form.purchaseDate.trim().ifEmpty { null },
                createdAt = original?.createdAt ?: now,
                updatedAt = now
            )
            try {
                repository.saveStashItem(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The dialog already closed optimistically; a failed save
                // simply leaves the previous persisted state in place.
            }
        }
    }

    /** Snapshots every persisted item (ignoring the current search/category filter -- export is always complete) as CSV text. */
    fun exportCsv(onReady: suspend (String) -> Unit) {
        scope.launch {
            val csv = stashItemsToCsv(repository.observeStashItems().first())
            onReady(csv)
        }
    }

    /**
     * Parses [csvText] against every currently persisted item (by id, so an
     * id already in the stash is updated rather than duplicated) and
     * persists every structurally valid row immediately -- row-level errors
     * are reported via [importReport] but never block the valid rows in the
     * same file from being saved.
     */
    fun importCsv(csvText: String) {
        scope.launch {
            try {
                val existingById = repository.observeStashItems().first().associateBy { it.id }
                val report = parseStashCsv(csvText, existingItemsById = existingById)
                report.validItems.forEach { repository.saveStashItem(it) }
                _importReport.value = report
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _importReport.value = StashCsvImportReport(
                    validItems = emptyList(),
                    rowErrors = listOf(
                        StashCsvRowError(
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

    fun deleteItem(item: StashItem) {
        scope.launch {
            try {
                repository.deleteStashItem(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Nothing to reconcile locally: the list reflects whatever is
                // actually persisted on the next emission.
            }
        }
    }

    private fun matchesFilter(item: StashItem, filter: StashFilterState): Boolean {
        if (filter.categoryFilter != null && item.category != filter.categoryFilter) return false
        val query = filter.searchQuery.trim()
        if (query.isEmpty()) return true
        return item.name.contains(query, ignoreCase = true) ||
            item.brand?.contains(query, ignoreCase = true) == true ||
            item.colorway?.contains(query, ignoreCase = true) == true
    }

    companion object {
        fun factory(repository: StashRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StashViewModel(repository)
            }
        }
    }
}
