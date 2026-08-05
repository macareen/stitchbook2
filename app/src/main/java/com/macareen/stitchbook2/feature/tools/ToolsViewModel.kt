package com.macareen.stitchbook2.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.data.csv.ToolsCsvImportReport
import com.macareen.stitchbook2.data.csv.ToolsCsvRowError
import com.macareen.stitchbook2.data.csv.parseToolsCsv
import com.macareen.stitchbook2.data.csv.toolItemsToCsv
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.normalizedToolItemName
import com.macareen.stitchbook2.domain.repository.ToolRepository
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

data class ToolFilterState(
    val searchQuery: String = "",
    val categoryFilter: ToolCategory? = null
)

sealed interface ToolsUiState {
    data object Loading : ToolsUiState
    data object Error : ToolsUiState
    data class Content(
        val items: List<ToolItem>,
        val filter: ToolFilterState,
        val hasAnyItems: Boolean,
        /** Every persisted set, for the add/edit dialog's set picker -- ignores the current search/category filter. */
        val sets: List<ToolSet>
    ) : ToolsUiState
}

/**
 * Raw, unvalidated form field values from the add/edit dialog. [ToolsViewModel.saveItem]
 * owns parsing numeric text and clearing fields that don't apply to [category] --
 * the dialog itself makes no judgment about what is valid for a given category.
 */
data class ToolItemFormInput(
    val name: String,
    val category: ToolCategory,
    val brand: String,
    val material: String,
    val sizeMetricMmText: String,
    val sizeLabel: String,
    val lengthMmText: String,
    val statedCableLengthMmText: String,
    val cableLengthDefinition: String,
    val approximateAssembledLengthMmText: String,
    val connectorFamily: String,
    val compatibilityNotes: String,
    val quantityText: String,
    val storageLocation: String,
    val notes: String,
    /** Null for a standalone item; otherwise the ToolSet this item is a member of. */
    val setId: String?
)

class ToolsViewModel(
    private val repository: ToolRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val filterState = MutableStateFlow(ToolFilterState())

    private val _importReport = MutableStateFlow<ToolsCsvImportReport?>(null)
    val importReport: StateFlow<ToolsCsvImportReport?> = _importReport

    val uiState = combine(
        repository.observeToolItems(),
        filterState,
        repository.observeToolSets()
    ) { items, filter, sets ->
        ToolsUiState.Content(
            items = items.filter { matchesFilter(it, filter) },
            filter = filter,
            hasAnyItems = items.isNotEmpty(),
            sets = sets
        ) as ToolsUiState
    }
        .catch { emit(ToolsUiState.Error) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ToolsUiState.Loading
        )

    fun updateSearchQuery(value: String) {
        filterState.value = filterState.value.copy(searchQuery = value)
    }

    fun updateCategoryFilter(value: ToolCategory?) {
        filterState.value = filterState.value.copy(categoryFilter = value)
    }

    fun saveItem(original: ToolItem?, form: ToolItemFormInput) {
        val normalizedName = normalizedToolItemName(form.name) ?: return
        val category = form.category
        scope.launch {
            val now = System.currentTimeMillis()
            val item = ToolItem(
                id = original?.id ?: UUID.randomUUID().toString(),
                name = normalizedName,
                category = category,
                brand = form.brand.trim().ifEmpty { null },
                material = form.material.trim().ifEmpty { null },
                sizeMetricMm = if (category.usesSizeFields()) form.sizeMetricMmText.toDoubleOrNull() else null,
                sizeLabel = if (category.usesSizeFields()) form.sizeLabel.trim().ifEmpty { null } else null,
                lengthMm = if (category.usesLengthField()) form.lengthMmText.toDoubleOrNull() else null,
                statedCableLengthMm = if (category.usesCableFields()) {
                    form.statedCableLengthMmText.toDoubleOrNull()
                } else {
                    null
                },
                cableLengthDefinition = if (category.usesCableFields()) {
                    form.cableLengthDefinition.trim().ifEmpty { null }
                } else {
                    null
                },
                approximateAssembledLengthMm = if (category.usesCableFields()) {
                    form.approximateAssembledLengthMmText.toDoubleOrNull()
                } else {
                    null
                },
                connectorFamily = if (category.usesConnectorFields()) {
                    form.connectorFamily.trim().ifEmpty { null }
                } else {
                    null
                },
                compatibilityNotes = if (category.usesConnectorFields()) {
                    form.compatibilityNotes.trim().ifEmpty { null }
                } else {
                    null
                },
                quantity = form.quantityText.toIntOrNull()?.coerceAtLeast(0) ?: 1,
                storageLocation = form.storageLocation.trim().ifEmpty { null },
                notes = form.notes.trim().ifEmpty { null },
                setId = form.setId,
                createdAt = original?.createdAt ?: now,
                updatedAt = now
            )
            try {
                repository.saveToolItem(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The dialog already closed optimistically; a failed save
                // simply leaves the previous persisted state in place.
            }
        }
    }

    fun deleteItem(item: ToolItem) {
        scope.launch {
            try {
                repository.deleteToolItem(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Nothing to reconcile locally: the list reflects whatever is
                // actually persisted on the next emission.
            }
        }
    }

    /** Snapshots every persisted item and set (ignoring the current search/category filter -- export is always complete) as CSV text. */
    fun exportCsv(onReady: suspend (String) -> Unit) {
        scope.launch {
            val setsById = repository.observeToolSets().first().associateBy { it.id }
            val csv = toolItemsToCsv(repository.observeToolItems().first(), setsById)
            onReady(csv)
        }
    }

    /**
     * Parses [csvText] against every currently persisted item (by id) and set
     * (by name), and persists every structurally valid row immediately --
     * row-level errors are reported via [importReport] but never block the
     * valid rows in the same file from being saved. Any set a row names by
     * `setName` but not by `setId` is created once and shared by every row
     * naming it, before the items that reference it are saved, so a
     * component's `setId` always resolves.
     */
    fun importCsv(csvText: String) {
        scope.launch {
            try {
                val existingItemsById = repository.observeToolItems().first().associateBy { it.id }
                val existingSetsById = repository.observeToolSets().first().associateBy { it.id }
                val report = parseToolsCsv(
                    csvText,
                    existingItemsById = existingItemsById,
                    existingSetsById = existingSetsById
                )
                report.newSets.forEach { repository.saveToolSet(it) }
                report.validItems.forEach { repository.saveToolItem(it) }
                _importReport.value = report
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _importReport.value = ToolsCsvImportReport(
                    validItems = emptyList(),
                    newSets = emptyList(),
                    rowErrors = listOf(
                        ToolsCsvRowError(
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

    private fun matchesFilter(item: ToolItem, filter: ToolFilterState): Boolean {
        if (filter.categoryFilter != null && item.category != filter.categoryFilter) return false
        val query = filter.searchQuery.trim()
        if (query.isEmpty()) return true
        return item.name.contains(query, ignoreCase = true) ||
            item.brand?.contains(query, ignoreCase = true) == true ||
            item.material?.contains(query, ignoreCase = true) == true
    }

    companion object {
        fun factory(repository: ToolRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ToolsViewModel(repository)
            }
        }
    }
}
