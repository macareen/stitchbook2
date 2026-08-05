package com.macareen.stitchbook2.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.BulkSizeInputMode
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.ToolTemplate
import com.macareen.stitchbook2.domain.model.normalizedToolTemplateName
import com.macareen.stitchbook2.domain.repository.ToolRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BulkToolCreationFormState(
    val category: ToolCategory = ToolCategory.CIRCULAR_NEEDLES,
    val brand: String = "",
    val material: String = "",
    val sizeInputMode: BulkSizeInputMode = BulkSizeInputMode.RANGE,
    val rangeStartText: String = "",
    val rangeEndText: String = "",
    val rangeIncrementText: String = "0.5",
    val customSizesText: String = "",
    val quantityPerSizeText: String = "1",
    val storageLocation: String = "",
    val notes: String = "",
    val createAsSet: Boolean = false,
    val setName: String = ""
)

data class BulkToolPreviewItem(val sizeMetricMm: Double, val name: String)

data class BulkToolCreationUiState(
    val form: BulkToolCreationFormState = BulkToolCreationFormState(),
    val preview: List<BulkToolPreviewItem> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val didCreate: Boolean = false
)

private const val MAX_BULK_SIZES = 100

class BulkToolCreationViewModel(
    private val repository: ToolRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val _uiState = MutableStateFlow(BulkToolCreationUiState())
    val uiState: StateFlow<BulkToolCreationUiState> = _uiState.asStateFlow()

    val templates: StateFlow<List<ToolTemplate>> = repository.observeToolTemplates()
        .stateIn(scope = scope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyList())

    fun updateCategory(value: ToolCategory) = updateForm { it.copy(category = value) }
    fun updateBrand(value: String) = updateForm { it.copy(brand = value) }
    fun updateMaterial(value: String) = updateForm { it.copy(material = value) }
    fun updateSizeInputMode(value: BulkSizeInputMode) = updateForm { it.copy(sizeInputMode = value) }
    fun updateRangeStart(value: String) = updateForm { it.copy(rangeStartText = value) }
    fun updateRangeEnd(value: String) = updateForm { it.copy(rangeEndText = value) }
    fun updateRangeIncrement(value: String) = updateForm { it.copy(rangeIncrementText = value) }
    fun updateCustomSizes(value: String) = updateForm { it.copy(customSizesText = value) }
    fun updateQuantityPerSize(value: String) = updateForm { it.copy(quantityPerSizeText = value) }
    fun updateStorageLocation(value: String) = updateForm { it.copy(storageLocation = value) }
    fun updateNotes(value: String) = updateForm { it.copy(notes = value) }
    fun updateCreateAsSet(value: Boolean) = updateForm { it.copy(createAsSet = value) }
    fun updateSetName(value: String) = updateForm { it.copy(setName = value) }

    /** Persists the current form as a named, reusable [ToolTemplate] -- applying it later only pre-fills the form, it never creates [ToolItem]s on its own. */
    fun saveCurrentAsTemplate(name: String) {
        val normalizedName = normalizedToolTemplateName(name) ?: return
        val form = _uiState.value.form
        scope.launch {
            val now = System.currentTimeMillis()
            try {
                repository.saveToolTemplate(
                    ToolTemplate(
                        id = UUID.randomUUID().toString(),
                        name = normalizedName,
                        category = form.category,
                        brand = form.brand.trim().ifEmpty { null },
                        material = form.material.trim().ifEmpty { null },
                        sizeInputMode = form.sizeInputMode,
                        rangeStart = form.rangeStartText.toDoubleOrNull(),
                        rangeEnd = form.rangeEndText.toDoubleOrNull(),
                        rangeIncrement = form.rangeIncrementText.toDoubleOrNull(),
                        customSizes = form.customSizesText.trim().ifEmpty { null },
                        quantityPerSize = form.quantityPerSizeText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        storageLocation = form.storageLocation.trim().ifEmpty { null },
                        notes = form.notes.trim().ifEmpty { null },
                        createAsSet = form.createAsSet,
                        setName = form.setName.trim().ifEmpty { null },
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort, same rationale as ToolsViewModel.saveItem: the
                // template list reflects whatever is actually persisted on
                // the next emission either way.
            }
        }
    }

    /** Overwrites every form field with [template]'s saved values -- this alone never creates a ToolItem, only createAll() does. */
    fun applyTemplate(template: ToolTemplate) {
        updateForm {
            BulkToolCreationFormState(
                category = template.category,
                brand = template.brand.orEmpty(),
                material = template.material.orEmpty(),
                sizeInputMode = template.sizeInputMode,
                rangeStartText = template.rangeStart?.toString().orEmpty(),
                rangeEndText = template.rangeEnd?.toString().orEmpty(),
                rangeIncrementText = template.rangeIncrement?.toString() ?: "0.5",
                customSizesText = template.customSizes.orEmpty(),
                quantityPerSizeText = template.quantityPerSize.toString(),
                storageLocation = template.storageLocation.orEmpty(),
                notes = template.notes.orEmpty(),
                createAsSet = template.createAsSet,
                setName = template.setName.orEmpty()
            )
        }
    }

    fun deleteTemplate(template: ToolTemplate) {
        scope.launch {
            try {
                repository.deleteToolTemplate(template)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort, same rationale as saveCurrentAsTemplate above.
            }
        }
    }

    fun createAll() {
        val state = _uiState.value
        val sizes = generateBulkSizes(state.form)
        if (sizes.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Enter at least one valid size to generate tools.")
            return
        }
        if (state.form.createAsSet && state.form.setName.trim().isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Enter a name for the new set.")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            try {
                val quantity = state.form.quantityPerSizeText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val setId = if (state.form.createAsSet) {
                    val newSetId = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    repository.saveToolSet(
                        ToolSet(
                            id = newSetId,
                            name = state.form.setName.trim(),
                            brand = state.form.brand.trim().ifEmpty { null },
                            notes = null,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    newSetId
                } else {
                    null
                }

                sizes.forEach { size ->
                    val now = System.currentTimeMillis()
                    repository.saveToolItem(
                        ToolItem(
                            id = UUID.randomUUID().toString(),
                            name = formatBulkItemName(size),
                            category = state.form.category,
                            brand = state.form.brand.trim().ifEmpty { null },
                            material = state.form.material.trim().ifEmpty { null },
                            sizeMetricMm = size,
                            sizeLabel = null,
                            lengthMm = null,
                            statedCableLengthMm = null,
                            cableLengthDefinition = null,
                            approximateAssembledLengthMm = null,
                            connectorFamily = null,
                            compatibilityNotes = null,
                            quantity = quantity,
                            storageLocation = state.form.storageLocation.trim().ifEmpty { null },
                            notes = state.form.notes.trim().ifEmpty { null },
                            setId = setId,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }

                _uiState.value = _uiState.value.copy(isSaving = false, didCreate = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Could not create tools. Try again."
                )
            }
        }
    }

    private fun updateForm(transform: (BulkToolCreationFormState) -> BulkToolCreationFormState) {
        _uiState.update { current ->
            val newForm = transform(current.form)
            current.copy(form = newForm, preview = buildBulkPreview(newForm), errorMessage = null)
        }
    }

    companion object {
        fun factory(repository: ToolRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BulkToolCreationViewModel(repository)
            }
        }
    }
}

/**
 * Range mode generates `start, start + increment, ...` up to and including
 * `end` (within floating-point tolerance); custom-list mode parses a
 * comma-separated list of numbers. Either way the result is deduplicated and
 * sorted -- concretely avoiding the unintended-duplicate sizes a mistyped
 * range or repeated list entry would otherwise generate (PRODUCT_SPEC.md
 * 6.8) -- and capped at [MAX_BULK_SIZES] so a zero/negative increment or a
 * huge range can't generate an unbounded number of items.
 */
internal fun generateBulkSizes(form: BulkToolCreationFormState): List<Double> {
    val raw = when (form.sizeInputMode) {
        BulkSizeInputMode.RANGE -> {
            val start = form.rangeStartText.toDoubleOrNull()
            val end = form.rangeEndText.toDoubleOrNull()
            val increment = form.rangeIncrementText.toDoubleOrNull()
            if (start == null || end == null || increment == null || increment <= 0.0 || end < start) {
                emptyList()
            } else {
                generateSequence(start) { it + increment }
                    .takeWhile { it <= end + 1e-9 }
                    .take(MAX_BULK_SIZES + 1)
                    .toList()
            }
        }

        BulkSizeInputMode.CUSTOM_LIST -> {
            form.customSizesText.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { it.toDoubleOrNull() }
        }
    }
    return raw.distinct().sorted().take(MAX_BULK_SIZES)
}

internal fun buildBulkPreview(form: BulkToolCreationFormState): List<BulkToolPreviewItem> {
    return generateBulkSizes(form).map { size ->
        BulkToolPreviewItem(sizeMetricMm = size, name = formatBulkItemName(size))
    }
}

private fun formatBulkItemName(sizeMm: Double): String {
    val formatted = if (sizeMm == sizeMm.toLong().toDouble()) {
        sizeMm.toLong().toString()
    } else {
        sizeMm.toString()
    }
    return "$formatted mm"
}
