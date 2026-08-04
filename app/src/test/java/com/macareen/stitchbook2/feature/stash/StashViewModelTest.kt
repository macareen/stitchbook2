package com.macareen.stitchbook2.feature.stash

import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.repository.StashRepository
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
 * Covers only the filtering behavior [StashViewModel] adds on top of
 * persisted repository state, and that saving strips yarn-only fields for
 * non-yarn categories.
 */
class StashViewModelTest {

    // Dispatchers.Unconfined runs launched coroutines synchronously up to
    // their first real suspension point. The fake repository below has no
    // real suspension, so uiState settles before the constructor call
    // returns -- no manual idling needed.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun searchQueryMatchesNameBrandOrColorway() {
        val repository = FakeStashRepository(
            listOf(
                item("cascade", name = "Cascade 220", brand = "Cascade Yarns", colorway = "Ivory"),
                item("hook", name = "5mm Hook", brand = "Clover", colorway = null)
            )
        )
        val viewModel = viewModel(repository)

        viewModel.updateSearchQuery("cascade")

        assertEquals(listOf("cascade"), contentState(viewModel).items.map { it.id })
    }

    @Test
    fun categoryFilterOnlyShowsMatchingCategory() {
        val repository = FakeStashRepository(
            listOf(
                item("yarn-item", category = StashCategory.YARN),
                item("hook-item", category = StashCategory.NEEDLES_HOOKS)
            )
        )
        val viewModel = viewModel(repository)

        viewModel.updateCategoryFilter(StashCategory.NEEDLES_HOOKS)

        assertEquals(listOf("hook-item"), contentState(viewModel).items.map { it.id })
    }

    @Test
    fun savingAYarnItemKeepsYarnOnlyFields() {
        val repository = FakeStashRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveItem(
            original = null,
            name = "Cascade 220",
            category = StashCategory.YARN,
            brand = "Cascade Yarns",
            colorway = "Ivory",
            dyeLot = "12345",
            weightCategory = "Worsted",
            fiberContent = "100% Wool",
            quantity = 6.0,
            unitLabel = "skeins",
            yardagePerUnit = 220.0,
            notes = ""
        )

        val saved = repository.items.value.single()
        assertEquals("Ivory", saved.colorway)
        assertEquals("12345", saved.dyeLot)
        assertEquals("Worsted", saved.weightCategory)
        assertEquals(220.0, saved.yardagePerUnit)
    }

    @Test
    fun savingANonYarnItemClearsYarnOnlyFieldsEvenIfProvided() {
        val repository = FakeStashRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveItem(
            original = null,
            name = "5mm Crochet Hook",
            category = StashCategory.NEEDLES_HOOKS,
            brand = "Clover",
            colorway = "should be dropped",
            dyeLot = "should be dropped",
            weightCategory = "should be dropped",
            fiberContent = "should be dropped",
            quantity = 1.0,
            unitLabel = "hook",
            yardagePerUnit = 999.0,
            notes = ""
        )

        val saved = repository.items.value.single()
        assertNull(saved.colorway)
        assertNull(saved.dyeLot)
        assertNull(saved.weightCategory)
        assertNull(saved.fiberContent)
        assertNull(saved.yardagePerUnit)
    }

    @Test
    fun savingWithABlankNameIsANoOp() {
        val repository = FakeStashRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveItem(
            original = null,
            name = "   ",
            category = StashCategory.YARN,
            brand = "",
            colorway = "",
            dyeLot = "",
            weightCategory = "",
            fiberContent = "",
            quantity = 1.0,
            unitLabel = "skeins",
            yardagePerUnit = null,
            notes = ""
        )

        assertTrue(repository.items.value.isEmpty())
    }

    @Test
    fun deletingAnItemRemovesItFromTheRepository() {
        val repository = FakeStashRepository(listOf(item("to-delete")))
        val viewModel = viewModel(repository)

        viewModel.deleteItem(repository.items.value.single())

        assertTrue(repository.items.value.isEmpty())
    }

    private fun contentState(viewModel: StashViewModel): StashUiState.Content {
        return viewModel.uiState.value as StashUiState.Content
    }

    private fun item(
        id: String,
        name: String = id,
        category: StashCategory = StashCategory.YARN,
        brand: String? = null,
        colorway: String? = null
    ) = StashItem(
        id = id,
        name = name,
        category = category,
        brand = brand,
        colorway = colorway,
        dyeLot = null,
        weightCategory = null,
        fiberContent = null,
        quantity = 1.0,
        unitLabel = "units",
        yardagePerUnit = null,
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    private fun viewModel(repository: FakeStashRepository): StashViewModel {
        val viewModel = StashViewModel(repository, externalScope = scope)
        // uiState is built with SharingStarted.WhileSubscribed, so it only
        // starts (and its value only advances past the initial Loading
        // state) once it has an active collector. Under Dispatchers.Unconfined
        // this collection runs synchronously through the fake's
        // non-suspending emissions before this call returns.
        scope.launch { viewModel.uiState.collect {} }
        return viewModel
    }
}

private class FakeStashRepository(initial: List<StashItem>) : StashRepository {
    val items = MutableStateFlow(initial)

    override fun observeStashItems(): Flow<List<StashItem>> = items

    override fun observeStashItem(id: String): Flow<StashItem?> =
        throw UnsupportedOperationException("Not used by StashViewModel")

    override suspend fun saveStashItem(item: StashItem) {
        items.value = items.value.filterNot { it.id == item.id } + item
    }

    override suspend fun deleteStashItem(item: StashItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }
}
