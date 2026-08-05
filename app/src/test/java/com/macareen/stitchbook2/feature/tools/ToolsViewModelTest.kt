package com.macareen.stitchbook2.feature.tools

import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.ToolTemplate
import com.macareen.stitchbook2.domain.repository.ToolRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers only the filtering behavior [ToolsViewModel] adds on top of
 * persisted repository state, and that saving parses/clears fields the
 * selected category doesn't use.
 */
class ToolsViewModelTest {

    // Dispatchers.Unconfined runs launched coroutines synchronously up to
    // their first real suspension point. The fake repository below has no
    // real suspension, so uiState settles before the constructor call
    // returns -- no manual idling needed.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun searchQueryMatchesNameBrandOrMaterial() {
        val repository = FakeToolRepository(
            listOf(
                item("hook", name = "5mm crochet hook", brand = "Clover", material = "Bamboo"),
                item("tip", name = "US 7 tip", brand = "ChiaoGoo", material = "Steel")
            )
        )
        val viewModel = viewModel(repository)

        viewModel.updateSearchQuery("clover")

        assertEquals(listOf("hook"), contentState(viewModel).items.map { it.id })
    }

    @Test
    fun categoryFilterOnlyShowsMatchingCategory() {
        val repository = FakeToolRepository(
            listOf(
                item("hook", category = ToolCategory.CROCHET_HOOK),
                item("marker", category = ToolCategory.STITCH_MARKER)
            )
        )
        val viewModel = viewModel(repository)

        viewModel.updateCategoryFilter(ToolCategory.STITCH_MARKER)

        assertEquals(listOf("marker"), contentState(viewModel).items.map { it.id })
    }

    @Test
    fun savingAnInterchangeableCableKeepsCableFieldsAndClearsSizeFields() {
        val repository = FakeToolRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveItem(original = null, form = formFor(ToolCategory.INTERCHANGEABLE_CABLE))

        val saved = repository.items.value.single()
        assertEquals(610.0, saved.statedCableLengthMm)
        assertEquals("Tip-to-tip including 5cm tips", saved.cableLengthDefinition)
        assertEquals(620.0, saved.approximateAssembledLengthMm)
        assertEquals("ChiaoGoo Twist", saved.connectorFamily)
        assertNull(saved.sizeMetricMm)
        assertNull(saved.sizeLabel)
        assertNull(saved.lengthMm)
    }

    @Test
    fun savingACrochetHookKeepsSizeFieldsAndClearsCableAndConnectorFields() {
        val repository = FakeToolRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveItem(original = null, form = formFor(ToolCategory.CROCHET_HOOK))

        val saved = repository.items.value.single()
        assertEquals(4.5, saved.sizeMetricMm)
        assertEquals("US 7", saved.sizeLabel)
        assertNull(saved.lengthMm)
        assertNull(saved.statedCableLengthMm)
        assertNull(saved.cableLengthDefinition)
        assertNull(saved.approximateAssembledLengthMm)
        assertNull(saved.connectorFamily)
        assertNull(saved.compatibilityNotes)
    }

    @Test
    fun savingAStitchMarkerClearsEverySizeLengthAndConnectorField() {
        val repository = FakeToolRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveItem(original = null, form = formFor(ToolCategory.STITCH_MARKER))

        val saved = repository.items.value.single()
        assertNull(saved.sizeMetricMm)
        assertNull(saved.sizeLabel)
        assertNull(saved.lengthMm)
        assertNull(saved.statedCableLengthMm)
        assertNull(saved.connectorFamily)
    }

    @Test
    fun blankQuantityDefaultsToOne() {
        val repository = FakeToolRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveItem(
            original = null,
            form = formFor(ToolCategory.STITCH_MARKER).copy(quantityText = "not a number")
        )

        assertEquals(1, repository.items.value.single().quantity)
    }

    @Test
    fun editingAnItemPreservesItsExistingSetAssignmentWhenTheFormCarriesItForward() {
        val repository = FakeToolRepository(
            listOf(item("member", category = ToolCategory.INTERCHANGEABLE_TIP, setId = "set-1"))
        )
        val viewModel = viewModel(repository)
        val original = repository.items.value.single()

        viewModel.saveItem(
            original = original,
            form = formFor(ToolCategory.INTERCHANGEABLE_TIP).copy(name = "Renamed tip", setId = "set-1")
        )

        val saved = repository.items.value.single()
        assertEquals("Renamed tip", saved.name)
        assertEquals("set-1", saved.setId)
    }

    @Test
    fun savingCanReassignAnItemToADifferentSet() {
        val repository = FakeToolRepository(
            listOf(item("member", category = ToolCategory.INTERCHANGEABLE_TIP, setId = "set-1"))
        )
        val viewModel = viewModel(repository)
        val original = repository.items.value.single()

        viewModel.saveItem(
            original = original,
            form = formFor(ToolCategory.INTERCHANGEABLE_TIP).copy(setId = "set-2")
        )

        assertEquals("set-2", repository.items.value.single().setId)
    }

    @Test
    fun savingWithNoSetSelectedRemovesTheItemFromItsSet() {
        val repository = FakeToolRepository(
            listOf(item("member", category = ToolCategory.INTERCHANGEABLE_TIP, setId = "set-1"))
        )
        val viewModel = viewModel(repository)
        val original = repository.items.value.single()

        viewModel.saveItem(
            original = original,
            form = formFor(ToolCategory.INTERCHANGEABLE_TIP).copy(setId = null)
        )

        assertNull(repository.items.value.single().setId)
    }

    @Test
    fun savingWithABlankNameIsANoOp() {
        val repository = FakeToolRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveItem(original = null, form = formFor(ToolCategory.CROCHET_HOOK).copy(name = "   "))

        assertTrue(repository.items.value.isEmpty())
    }

    @Test
    fun deletingAnItemRemovesItFromTheRepository() {
        val repository = FakeToolRepository(listOf(item("to-delete")))
        val viewModel = viewModel(repository)

        viewModel.deleteItem(repository.items.value.single())

        assertTrue(repository.items.value.isEmpty())
    }

    private fun contentState(viewModel: ToolsViewModel): ToolsUiState.Content {
        return viewModel.uiState.value as ToolsUiState.Content
    }

    private fun formFor(category: ToolCategory) = ToolItemFormInput(
        name = "Test tool",
        category = category,
        brand = "Test brand",
        material = "Test material",
        sizeMetricMmText = "4.5",
        sizeLabel = "US 7",
        lengthMmText = "127",
        statedCableLengthMmText = "610",
        cableLengthDefinition = "Tip-to-tip including 5cm tips",
        approximateAssembledLengthMmText = "620",
        connectorFamily = "ChiaoGoo Twist",
        compatibilityNotes = "Twist-compatible only",
        quantityText = "1",
        storageLocation = "Drawer 1",
        notes = "",
        setId = null
    )

    private fun item(
        id: String,
        name: String = id,
        category: ToolCategory = ToolCategory.CROCHET_HOOK,
        brand: String? = null,
        material: String? = null,
        setId: String? = null
    ) = ToolItem(
        id = id,
        name = name,
        category = category,
        brand = brand,
        material = material,
        sizeMetricMm = null,
        sizeLabel = null,
        lengthMm = null,
        statedCableLengthMm = null,
        cableLengthDefinition = null,
        approximateAssembledLengthMm = null,
        connectorFamily = null,
        compatibilityNotes = null,
        quantity = 1,
        storageLocation = null,
        notes = null,
        setId = setId,
        createdAt = 0,
        updatedAt = 0
    )

    private fun viewModel(repository: FakeToolRepository): ToolsViewModel {
        val viewModel = ToolsViewModel(repository, externalScope = scope)
        // uiState is built with SharingStarted.WhileSubscribed, so it only
        // starts (and its value only advances past the initial Loading
        // state) once it has an active collector. Under Dispatchers.Unconfined
        // this collection runs synchronously through the fake's
        // non-suspending emissions before this call returns.
        scope.launch { viewModel.uiState.collect {} }
        return viewModel
    }
}

private class FakeToolRepository(initialItems: List<ToolItem>) : ToolRepository {
    val items = MutableStateFlow(initialItems)
    val sets = MutableStateFlow(emptyList<ToolSet>())

    override fun observeToolItems(): Flow<List<ToolItem>> = items

    override fun observeToolItem(id: String): Flow<ToolItem?> =
        throw UnsupportedOperationException("Not used by ToolsViewModel")

    override fun observeToolItemsBySet(setId: String): Flow<List<ToolItem>> =
        throw UnsupportedOperationException("Not used by ToolsViewModel")

    override suspend fun saveToolItem(item: ToolItem) {
        items.value = items.value.filterNot { it.id == item.id } + item
    }

    override suspend fun deleteToolItem(item: ToolItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }

    override fun observeToolSets(): Flow<List<ToolSet>> = sets

    override fun observeToolSet(id: String): Flow<ToolSet?> =
        throw UnsupportedOperationException("Not used by ToolsViewModel")

    override suspend fun saveToolSet(set: ToolSet) {
        sets.value = sets.value.filterNot { it.id == set.id } + set
    }

    override suspend fun deleteToolSet(set: ToolSet) {
        sets.value = sets.value.filterNot { it.id == set.id }
    }

    override fun observeToolTemplates(): Flow<List<ToolTemplate>> =
        throw UnsupportedOperationException("Not used by ToolsViewModel")
    override suspend fun saveToolTemplate(template: ToolTemplate) =
        throw UnsupportedOperationException("Not used by ToolsViewModel")
    override suspend fun deleteToolTemplate(template: ToolTemplate) =
        throw UnsupportedOperationException("Not used by ToolsViewModel")
}
