package com.macareen.stitchbook2.feature.tools

import com.macareen.stitchbook2.domain.model.BulkSizeInputMode
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkToolCreationViewModelTest {

    // Dispatchers.Unconfined runs launched coroutines synchronously up to
    // their first real suspension point, so assertions can run immediately
    // after each call without manual idling.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun rangeModeGeneratesInclusiveStepsAndPreviewNames() {
        val repository = FakeBulkToolRepository()
        val viewModel = viewModel(repository)

        viewModel.updateRangeStart("4")
        viewModel.updateRangeEnd("5")
        viewModel.updateRangeIncrement("0.5")

        val preview = viewModel.uiState.value.preview
        assertEquals(listOf(4.0, 4.5, 5.0), preview.map { it.sizeMetricMm })
        assertEquals(listOf("4 mm", "4.5 mm", "5 mm"), preview.map { it.name })
    }

    @Test
    fun rangeModeWithNonPositiveIncrementProducesNoPreview() {
        val repository = FakeBulkToolRepository()
        val viewModel = viewModel(repository)

        viewModel.updateRangeStart("4")
        viewModel.updateRangeEnd("5")
        viewModel.updateRangeIncrement("0")

        assertTrue(viewModel.uiState.value.preview.isEmpty())
    }

    @Test
    fun customListModeParsesDeduplicatesAndSortsSizes() {
        val repository = FakeBulkToolRepository()
        val viewModel = viewModel(repository)

        viewModel.updateSizeInputMode(BulkSizeInputMode.CUSTOM_LIST)
        viewModel.updateCustomSizes("5, 3.5, not-a-number, 3.5, 4")

        assertEquals(listOf(3.5, 4.0, 5.0), viewModel.uiState.value.preview.map { it.sizeMetricMm })
    }

    @Test
    fun createAllPersistsOneItemPerPreviewSizeWithSharedFields() {
        val repository = FakeBulkToolRepository()
        val viewModel = viewModel(repository)
        viewModel.updateCategory(ToolCategory.CROCHET_HOOK)
        viewModel.updateBrand("Clover")
        viewModel.updateMaterial("Bamboo")
        viewModel.updateStorageLocation("Drawer 1")
        viewModel.updateQuantityPerSize("3")
        viewModel.updateRangeStart("4")
        viewModel.updateRangeEnd("5")
        viewModel.updateRangeIncrement("1")

        viewModel.createAll()

        val saved = repository.items.value.sortedBy { it.sizeMetricMm }
        assertEquals(2, saved.size)
        assertEquals(listOf(4.0, 5.0), saved.map { it.sizeMetricMm })
        saved.forEach { item ->
            assertEquals(ToolCategory.CROCHET_HOOK, item.category)
            assertEquals("Clover", item.brand)
            assertEquals("Bamboo", item.material)
            assertEquals("Drawer 1", item.storageLocation)
            assertEquals(3, item.quantity)
            assertNull(item.setId)
        }
        assertTrue(viewModel.uiState.value.didCreate)
    }

    @Test
    fun createAllAsASetCreatesTheSetAndAssignsEveryGeneratedItemToIt() {
        val repository = FakeBulkToolRepository()
        val viewModel = viewModel(repository)
        viewModel.updateRangeStart("4")
        viewModel.updateRangeEnd("5")
        viewModel.updateRangeIncrement("1")
        viewModel.updateCreateAsSet(true)
        viewModel.updateSetName("ChiaoGoo Twist Set")

        viewModel.createAll()

        val createdSet = repository.sets.value.single()
        assertEquals("ChiaoGoo Twist Set", createdSet.name)
        val saved = repository.items.value
        assertEquals(2, saved.size)
        saved.forEach { item -> assertEquals(createdSet.id, item.setId) }
    }

    @Test
    fun creatingAsASetWithABlankNameFailsWithoutPersistingAnything() {
        val repository = FakeBulkToolRepository()
        val viewModel = viewModel(repository)
        viewModel.updateRangeStart("4")
        viewModel.updateRangeEnd("5")
        viewModel.updateRangeIncrement("1")
        viewModel.updateCreateAsSet(true)
        viewModel.updateSetName("   ")

        viewModel.createAll()

        assertTrue(repository.items.value.isEmpty())
        assertTrue(repository.sets.value.isEmpty())
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun creatingWithNoValidSizesFailsWithoutPersistingAnything() {
        val repository = FakeBulkToolRepository()
        val viewModel = viewModel(repository)

        viewModel.createAll()

        assertTrue(repository.items.value.isEmpty())
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun savingTheCurrentFormAsATemplatePersistsItsFieldsButCreatesNoToolItem() {
        val repository = FakeBulkToolRepository()
        val viewModel = viewModel(repository)
        viewModel.updateCategory(ToolCategory.CROCHET_HOOK)
        viewModel.updateBrand("Clover")
        viewModel.updateRangeStart("4")
        viewModel.updateRangeEnd("5")
        viewModel.updateRangeIncrement("0.5")
        viewModel.updateQuantityPerSize("2")

        viewModel.saveCurrentAsTemplate("My Crochet Hooks")

        val saved = repository.templates.value.single()
        assertEquals("My Crochet Hooks", saved.name)
        assertEquals(ToolCategory.CROCHET_HOOK, saved.category)
        assertEquals("Clover", saved.brand)
        assertEquals(4.0, saved.rangeStart)
        assertEquals(5.0, saved.rangeEnd)
        assertEquals(0.5, saved.rangeIncrement)
        assertEquals(2, saved.quantityPerSize)
        assertTrue(repository.items.value.isEmpty())
    }

    @Test
    fun savingATemplateWithABlankNameIsANoOp() {
        val repository = FakeBulkToolRepository()
        val viewModel = viewModel(repository)

        viewModel.saveCurrentAsTemplate("   ")

        assertTrue(repository.templates.value.isEmpty())
    }

    @Test
    fun applyingATemplateOverwritesTheFormAndUpdatesThePreview() {
        val repository = FakeBulkToolRepository()
        val viewModel = viewModel(repository)
        val template = ToolTemplate(
            id = "template-1",
            name = "My Crochet Hooks",
            category = ToolCategory.CROCHET_HOOK,
            brand = "Clover",
            material = "Bamboo",
            sizeInputMode = BulkSizeInputMode.RANGE,
            rangeStart = 4.0,
            rangeEnd = 5.0,
            rangeIncrement = 0.5,
            customSizes = null,
            quantityPerSize = 2,
            storageLocation = "Drawer 1",
            notes = "From a template",
            createAsSet = false,
            setName = null,
            createdAt = 0,
            updatedAt = 0
        )

        viewModel.applyTemplate(template)

        val form = viewModel.uiState.value.form
        assertEquals(ToolCategory.CROCHET_HOOK, form.category)
        assertEquals("Clover", form.brand)
        assertEquals("Bamboo", form.material)
        assertEquals("4.0", form.rangeStartText)
        assertEquals("5.0", form.rangeEndText)
        assertEquals("2", form.quantityPerSizeText)
        assertEquals(listOf(4.0, 4.5, 5.0), viewModel.uiState.value.preview.map { it.sizeMetricMm })
    }

    @Test
    fun deletingATemplateRemovesItFromTheRepository() {
        val repository = FakeBulkToolRepository()
        val viewModel = viewModel(repository)
        viewModel.saveCurrentAsTemplate("Doomed template")
        val saved = repository.templates.value.single()

        viewModel.deleteTemplate(saved)

        assertTrue(repository.templates.value.isEmpty())
    }

    private fun viewModel(repository: FakeBulkToolRepository): BulkToolCreationViewModel {
        val viewModel = BulkToolCreationViewModel(repository, externalScope = scope)
        // templates is a stateIn(WhileSubscribed) flow, same rationale as
        // every other screen's uiState in this codebase: it only starts
        // (and advances past its empty initial value) once it has an
        // active collector.
        scope.launch { viewModel.templates.collect {} }
        return viewModel
    }
}

private class FakeBulkToolRepository : ToolRepository {
    val items = MutableStateFlow(emptyList<ToolItem>())
    val sets = MutableStateFlow(emptyList<ToolSet>())
    val templates = MutableStateFlow(emptyList<ToolTemplate>())

    override fun observeToolItems(): Flow<List<ToolItem>> = items

    override fun observeToolItem(id: String): Flow<ToolItem?> =
        throw UnsupportedOperationException("Not used by BulkToolCreationViewModel")

    override fun observeToolItemsBySet(setId: String): Flow<List<ToolItem>> =
        throw UnsupportedOperationException("Not used by BulkToolCreationViewModel")

    override suspend fun saveToolItem(item: ToolItem) {
        items.value = items.value.filterNot { it.id == item.id } + item
    }

    override suspend fun deleteToolItem(item: ToolItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }

    override fun observeToolSets(): Flow<List<ToolSet>> = sets

    override fun observeToolSet(id: String): Flow<ToolSet?> =
        throw UnsupportedOperationException("Not used by BulkToolCreationViewModel")

    override suspend fun saveToolSet(set: ToolSet) {
        sets.value = sets.value.filterNot { it.id == set.id } + set
    }

    override suspend fun deleteToolSet(set: ToolSet) {
        sets.value = sets.value.filterNot { it.id == set.id }
    }

    override fun observeToolTemplates(): Flow<List<ToolTemplate>> = templates

    override suspend fun saveToolTemplate(template: ToolTemplate) {
        templates.value = templates.value.filterNot { it.id == template.id } + template
    }

    override suspend fun deleteToolTemplate(template: ToolTemplate) {
        templates.value = templates.value.filterNot { it.id == template.id }
    }

    override fun observeToolItemsForProject(projectId: String): Flow<List<ToolItem>> =
        throw UnsupportedOperationException("Not used by BulkToolCreationViewModel")
    override fun observeProjectIdsForToolItem(toolItemId: String): Flow<List<String>> =
        throw UnsupportedOperationException("Not used by BulkToolCreationViewModel")
    override suspend fun setProjectAssignments(toolItemId: String, projectIds: Set<String>) =
        throw UnsupportedOperationException("Not used by BulkToolCreationViewModel")
    override suspend fun unassignToolFromProject(toolItemId: String, projectId: String) =
        throw UnsupportedOperationException("Not used by BulkToolCreationViewModel")
}
