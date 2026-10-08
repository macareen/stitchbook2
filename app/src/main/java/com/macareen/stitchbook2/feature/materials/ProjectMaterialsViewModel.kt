package com.macareen.stitchbook2.feature.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.AllocationError
import com.macareen.stitchbook2.domain.model.AllocationException
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.ProjectPatternLink
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.YarnAllocation
import com.macareen.stitchbook2.domain.model.consume
import com.macareen.stitchbook2.domain.model.unallocatedQuantity
import com.macareen.stitchbook2.domain.model.validateReservation
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.repository.MaterialsRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
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

data class AllocationRow(
    val allocation: YarnAllocation,
    val item: StashItem
)

data class AvailableStashItem(
    val item: StashItem,
    val unallocated: Double
)

enum class MaterialsFeedback {
    INVALID_AMOUNT,
    EXCEEDS_UNALLOCATED,
    EXCEEDS_AVAILABLE,
    SAVE_FAILED
}

data class ProjectMaterialsUiState(
    val isLoading: Boolean = true,
    val projectName: String = "",
    val allocations: List<AllocationRow> = emptyList(),
    /** Stash items with something left to reserve -- yarn first, since that's what allocation is mostly for. */
    val availableStash: List<AvailableStashItem> = emptyList(),
    val linkedPatterns: List<LibraryItem> = emptyList(),
    val unlinkedPatterns: List<LibraryItem> = emptyList(),
    val feedback: MaterialsFeedback? = null
)

class ProjectMaterialsViewModel(
    private val projectId: String,
    projectRepository: ProjectRepository,
    private val stashRepository: StashRepository,
    libraryRepository: LibraryRepository,
    private val materialsRepository: MaterialsRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val feedback = MutableStateFlow<MaterialsFeedback?>(null)

    val uiState: StateFlow<ProjectMaterialsUiState> = combine(
        projectRepository.observeProject(projectId),
        stashRepository.observeStashItems(),
        materialsRepository.observeAllocations(),
        libraryRepository.observeLibraryItems(),
        materialsRepository.observePatternLinks()
    ) { project, stash, allocations, library, links ->
        val stashById = stash.associateBy { it.id }
        val linkedIds = links.filter { it.projectId == projectId }.map { it.libraryItemId }.toSet()
        ProjectMaterialsUiState(
            isLoading = false,
            projectName = project?.name.orEmpty(),
            allocations = allocations
                .filter { it.projectId == projectId }
                .mapNotNull { allocation -> stashById[allocation.stashItemId]?.let { AllocationRow(allocation, it) } },
            availableStash = stash
                .map { AvailableStashItem(it, unallocatedQuantity(it, allocations)) }
                .filter { it.unallocated > 0.0 }
                .sortedWith(compareBy({ it.item.category != StashCategory.YARN }, { it.item.name.lowercase() })),
            linkedPatterns = library.filter { it.id in linkedIds },
            unlinkedPatterns = library.filter { it.id !in linkedIds }
        )
    }
        .catch { emit(ProjectMaterialsUiState(isLoading = false, feedback = MaterialsFeedback.SAVE_FAILED)) }
        .combine(feedback) { state, message -> state.copy(feedback = message ?: state.feedback) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ProjectMaterialsUiState())

    fun allocate(stashItemId: String, amountText: String, notes: String) {
        val amount = parseAmount(amountText) ?: return
        runSafely {
            val item = stashRepository.observeStashItem(stashItemId).first() ?: return@runSafely
            val allocations = materialsRepository.observeAllocations().first()
            when (validateReservation(item, allocations, amount)) {
                null -> {
                    val now = System.currentTimeMillis()
                    materialsRepository.saveAllocation(
                        YarnAllocation(
                            id = UUID.randomUUID().toString(),
                            projectId = projectId,
                            stashItemId = stashItemId,
                            quantityReserved = amount,
                            quantityUsed = 0.0,
                            notes = notes.trim().ifEmpty { null },
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
                AllocationError.NOT_POSITIVE -> feedback.value = MaterialsFeedback.INVALID_AMOUNT
                else -> feedback.value = MaterialsFeedback.EXCEEDS_UNALLOCATED
            }
        }
    }

    fun recordUse(allocationId: String, amountText: String) {
        val amount = parseAmount(amountText) ?: return
        runSafely {
            val allocations = materialsRepository.observeAllocations().first()
            val allocation = allocations.firstOrNull { it.id == allocationId } ?: return@runSafely
            val item = stashRepository.observeStashItem(allocation.stashItemId).first() ?: return@runSafely
            consume(item, allocation, allocations, amount, System.currentTimeMillis())
                .onSuccess { materialsRepository.recordConsumption(it.item, it.allocation) }
                .onFailure { error ->
                    feedback.value = if ((error as? AllocationException)?.error == AllocationError.NOT_POSITIVE) {
                        MaterialsFeedback.INVALID_AMOUNT
                    } else {
                        MaterialsFeedback.EXCEEDS_AVAILABLE
                    }
                }
        }
    }

    /** Drops the reservation only; yarn already recorded as used stays subtracted from the stash. */
    fun release(allocation: YarnAllocation) = runSafely { materialsRepository.deleteAllocation(allocation) }

    fun linkPattern(libraryItemId: String) =
        runSafely { materialsRepository.linkPattern(ProjectPatternLink(projectId, libraryItemId)) }

    fun unlinkPattern(libraryItemId: String) =
        runSafely { materialsRepository.unlinkPattern(ProjectPatternLink(projectId, libraryItemId)) }

    fun dismissFeedback() {
        feedback.value = null
    }

    private fun parseAmount(text: String): Double? {
        val amount = text.trim().replace(',', '.').toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            feedback.value = MaterialsFeedback.INVALID_AMOUNT
            return null
        }
        feedback.value = null
        return amount
    }

    private fun runSafely(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                feedback.value = MaterialsFeedback.SAVE_FAILED
            }
        }
    }

    companion object {
        fun factory(
            projectId: String,
            projectRepository: ProjectRepository,
            stashRepository: StashRepository,
            libraryRepository: LibraryRepository,
            materialsRepository: MaterialsRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProjectMaterialsViewModel(
                    projectId,
                    projectRepository,
                    stashRepository,
                    libraryRepository,
                    materialsRepository
                )
            }
        }
    }
}
