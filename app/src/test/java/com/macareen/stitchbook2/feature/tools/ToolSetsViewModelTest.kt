package com.macareen.stitchbook2.feature.tools

import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.ToolTemplate
import com.macareen.stitchbook2.domain.repository.ToolRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSetsViewModelTest {

    // See ToolsViewModelTest's own comment on why Dispatchers.Unconfined
    // needs no manual idling here: the fake repository never really
    // suspends, so uiState settles before the constructor call returns.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun eachSetsItemCountReflectsOnlyItsOwnMembers() {
        val repository = FakeToolSetsRepository(
            sets = listOf(set("set-1"), set("set-2")),
            items = listOf(
                item("a", setId = "set-1"),
                item("b", setId = "set-1"),
                item("c", setId = "set-2"),
                item("standalone", setId = null)
            )
        )
        val viewModel = viewModel(repository)

        val counts = contentState(viewModel).summaries.associate { it.set.id to it.itemCount }

        assertEquals(2, counts["set-1"])
        assertEquals(1, counts["set-2"])
    }

    @Test
    fun renamingASetUpdatesItsNameBrandAndNotes() {
        val repository = FakeToolSetsRepository(sets = listOf(set("set-1", name = "Old name")), items = emptyList())
        val viewModel = viewModel(repository)
        val original = repository.sets.value.single()

        viewModel.renameSet(original, name = "New name", brand = "New brand", notes = "New notes")

        val saved = repository.sets.value.single()
        assertEquals("New name", saved.name)
        assertEquals("New brand", saved.brand)
        assertEquals("New notes", saved.notes)
    }

    @Test
    fun renamingWithABlankNameIsANoOp() {
        val repository = FakeToolSetsRepository(sets = listOf(set("set-1", name = "Old name")), items = emptyList())
        val viewModel = viewModel(repository)
        val original = repository.sets.value.single()

        viewModel.renameSet(original, name = "   ", brand = "", notes = "")

        assertEquals("Old name", repository.sets.value.single().name)
    }

    @Test
    fun deletingASetRemovesItFromTheRepository() {
        val repository = FakeToolSetsRepository(sets = listOf(set("set-1")), items = emptyList())
        val viewModel = viewModel(repository)

        viewModel.deleteSet(repository.sets.value.single())

        assertTrue(repository.sets.value.isEmpty())
    }

    private fun contentState(viewModel: ToolSetsViewModel): ToolSetsUiState.Content {
        return viewModel.uiState.value as ToolSetsUiState.Content
    }

    private fun set(id: String, name: String = id) = ToolSet(
        id = id,
        name = name,
        brand = null,
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    private fun item(id: String, setId: String?) = ToolItem(
        id = id,
        name = id,
        category = ToolCategory.CROCHET_HOOK,
        brand = null,
        material = null,
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

    private fun viewModel(repository: FakeToolSetsRepository): ToolSetsViewModel {
        val viewModel = ToolSetsViewModel(repository, externalScope = scope)
        scope.launch { viewModel.uiState.collect {} }
        return viewModel
    }
}

private class FakeToolSetsRepository(
    sets: List<ToolSet>,
    items: List<ToolItem>
) : ToolRepository {
    val sets = MutableStateFlow(sets)
    val items = MutableStateFlow(items)

    override fun observeToolItems(): Flow<List<ToolItem>> = items

    override fun observeToolItem(id: String): Flow<ToolItem?> =
        throw UnsupportedOperationException("Not used by ToolSetsViewModel")

    override fun observeToolItemsBySet(setId: String): Flow<List<ToolItem>> =
        throw UnsupportedOperationException("Not used by ToolSetsViewModel")

    override suspend fun saveToolItem(item: ToolItem) {
        throw UnsupportedOperationException("Not used by ToolSetsViewModel")
    }

    override suspend fun deleteToolItem(item: ToolItem) {
        throw UnsupportedOperationException("Not used by ToolSetsViewModel")
    }

    override fun observeToolSets(): Flow<List<ToolSet>> = sets

    override fun observeToolSet(id: String): Flow<ToolSet?> =
        throw UnsupportedOperationException("Not used by ToolSetsViewModel")

    override suspend fun saveToolSet(set: ToolSet) {
        sets.value = sets.value.filterNot { it.id == set.id } + set
    }

    override suspend fun deleteToolSet(set: ToolSet) {
        sets.value = sets.value.filterNot { it.id == set.id }
    }

    override fun observeToolTemplates(): Flow<List<ToolTemplate>> =
        throw UnsupportedOperationException("Not used by ToolSetsViewModel")
    override suspend fun saveToolTemplate(template: ToolTemplate) =
        throw UnsupportedOperationException("Not used by ToolSetsViewModel")
    override suspend fun deleteToolTemplate(template: ToolTemplate) =
        throw UnsupportedOperationException("Not used by ToolSetsViewModel")
}
