package com.macareen.stitchbook2.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.repository.ToolRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
        val hasAnyItems: Boolean
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
    val notes: String
)

class ToolsViewModel(
    private val repository: ToolRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val filterState = MutableStateFlow(ToolFilterState())

    val uiState = combine(
        repository.observeToolItems(),
        filterState
    ) { items, filter ->
        ToolsUiState.Content(
            items = items.filter { matchesFilter(it, filter) },
            filter = filter,
            hasAnyItems = items.isNotEmpty()
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
        val normalizedName = form.name.trim().takeIf { it.isNotEmpty() } ?: return
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
                setId = original?.setId,
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
