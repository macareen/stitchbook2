package com.macareen.stitchbook2.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.normalizedToolSetName
import com.macareen.stitchbook2.domain.repository.ToolRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One [ToolSet] plus how many [com.macareen.stitchbook2.domain.model.ToolItem]s currently point at it. */
data class ToolSetSummary(
    val set: ToolSet,
    val itemCount: Int
)

sealed interface ToolSetsUiState {
    data object Loading : ToolSetsUiState
    data object Error : ToolSetsUiState
    data class Content(val summaries: List<ToolSetSummary>) : ToolSetsUiState
}

/**
 * Fills the gap ROADMAP.md's Tools status called out: `ToolSet` CRUD already
 * existed at the repository layer (bulk creation could create one, CSV
 * import could resolve/create one by name), but there was no UI to browse,
 * rename, or delete an existing set directly. Reassigning an already-created
 * item to a different (or no) set is handled by `ToolsScreen`'s own add/edit
 * dialog instead of here, since that's a per-item action.
 */
class ToolSetsViewModel(
    private val repository: ToolRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    val uiState: StateFlow<ToolSetsUiState> = combine(
        repository.observeToolSets(),
        repository.observeToolItems()
    ) { sets, items ->
        val countsBySetId = items.mapNotNull { it.setId }.groupingBy { it }.eachCount()
        ToolSetsUiState.Content(
            sets.map { set -> ToolSetSummary(set, countsBySetId[set.id] ?: 0) }
        ) as ToolSetsUiState
    }
        .catch { emit(ToolSetsUiState.Error) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ToolSetsUiState.Loading
        )

    fun renameSet(set: ToolSet, name: String, brand: String, notes: String) {
        val normalizedName = normalizedToolSetName(name) ?: return
        scope.launch {
            try {
                repository.saveToolSet(
                    set.copy(
                        name = normalizedName,
                        brand = brand.trim().ifEmpty { null },
                        notes = notes.trim().ifEmpty { null },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort, same rationale as ToolsViewModel.saveItem: the
                // list reflects whatever is actually persisted on the next
                // emission either way.
            }
        }
    }

    fun deleteSet(set: ToolSet) {
        scope.launch {
            try {
                repository.deleteToolSet(set)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort, same rationale as renameSet above.
            }
        }
    }

    companion object {
        fun factory(repository: ToolRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ToolSetsViewModel(repository)
            }
        }
    }
}
