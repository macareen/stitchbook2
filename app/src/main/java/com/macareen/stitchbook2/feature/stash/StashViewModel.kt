package com.macareen.stitchbook2.feature.stash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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

class StashViewModel(
    private val repository: StashRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val filterState = MutableStateFlow(StashFilterState())

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
     * [colorway]/[dyeLot]/[weightCategory]/[fiberContent]/[yardagePerUnit] are
     * only meaningful for [StashCategory.YARN]; a non-yarn category clears
     * them regardless of what the form happened to hold, so switching a
     * form's category away from Yarn can never leave stale yarn-only data
     * behind.
     */
    fun saveItem(
        original: StashItem?,
        name: String,
        category: StashCategory,
        brand: String,
        colorway: String,
        dyeLot: String,
        weightCategory: String,
        fiberContent: String,
        quantity: Double,
        unitLabel: String,
        yardagePerUnit: Double?,
        notes: String
    ) {
        val normalizedName = normalizedStashItemName(name) ?: return
        val isYarn = category == StashCategory.YARN
        scope.launch {
            val now = System.currentTimeMillis()
            val item = StashItem(
                id = original?.id ?: UUID.randomUUID().toString(),
                name = normalizedName,
                category = category,
                brand = brand.trim().ifEmpty { null },
                colorway = if (isYarn) colorway.trim().ifEmpty { null } else null,
                dyeLot = if (isYarn) dyeLot.trim().ifEmpty { null } else null,
                weightCategory = if (isYarn) weightCategory.trim().ifEmpty { null } else null,
                fiberContent = if (isYarn) fiberContent.trim().ifEmpty { null } else null,
                quantity = quantity,
                unitLabel = unitLabel.trim().ifEmpty { "units" },
                yardagePerUnit = if (isYarn) yardagePerUnit else null,
                notes = notes.trim().ifEmpty { null },
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
