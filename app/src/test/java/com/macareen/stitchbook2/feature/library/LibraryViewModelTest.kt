package com.macareen.stitchbook2.feature.library

import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers only the filtering/search behavior [LibraryViewModel] adds on top
 * of persisted repository state, and that save/delete/toggle-bookmark calls
 * reach the repository with the expected, normalized values.
 */
class LibraryViewModelTest {

    // Dispatchers.Unconfined runs launched coroutines synchronously up to
    // their first real suspension point. The fake repository below has no
    // real suspension, so uiState settles before the constructor call
    // returns -- no manual idling needed.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun searchQueryMatchesTitleAuthorOrTag() {
        val repository = FakeLibraryRepository(
            listOf(
                item("raglan", title = "Raglan Guide", author = "EZ", tags = listOf("construction")),
                item("honeycomb", title = "Honeycomb Stitch", author = "Someone", tags = listOf("raglan"))
            )
        )
        val viewModel = viewModel(repository)

        viewModel.updateSearchQuery("raglan")

        val ids = contentState(viewModel).items.map { it.id }
        assertEquals(setOf("raglan", "honeycomb"), ids.toSet())
    }

    @Test
    fun craftFilterOnlyShowsMatchingCraft() {
        val repository = FakeLibraryRepository(
            listOf(
                item("knit-item", craft = Craft.KNITTING),
                item("crochet-item", craft = Craft.CROCHET)
            )
        )
        val viewModel = viewModel(repository)

        viewModel.updateCraftFilter(Craft.CROCHET)

        val ids = contentState(viewModel).items.map { it.id }
        assertEquals(listOf("crochet-item"), ids)
    }

    @Test
    fun bookmarksOnlyFilterExcludesUnbookmarkedItems() {
        val repository = FakeLibraryRepository(
            listOf(
                item("bookmarked", bookmarked = true),
                item("not-bookmarked", bookmarked = false)
            )
        )
        val viewModel = viewModel(repository)

        viewModel.updateBookmarksOnly(true)

        val ids = contentState(viewModel).items.map { it.id }
        assertEquals(listOf("bookmarked"), ids)
    }

    @Test
    fun toggleBookmarkFlipsStoredValue() {
        val repository = FakeLibraryRepository(listOf(item("item", bookmarked = false)))
        val viewModel = viewModel(repository)

        viewModel.toggleBookmark(repository.items.value.single())

        assertTrue(repository.items.value.single().bookmarked)
    }

    @Test
    fun savingANewItemNormalizesTagsAndBlankFields() {
        val repository = FakeLibraryRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveItem(
            original = null,
            title = "  New Reference  ",
            craft = Craft.CROCHET,
            author = "   ",
            sourceUrl = "  ",
            tags = listOf(" tag-one ", "", "tag,two"),
            notes = "  "
        )

        val saved = repository.items.value.single()
        assertEquals("New Reference", saved.title)
        assertEquals(null, saved.author)
        assertEquals(null, saved.sourceUrl)
        assertEquals(null, saved.notes)
        assertEquals(listOf("tag-one", "tagtwo"), saved.tags)
    }

    @Test
    fun savingWithABlankTitleIsANoOp() {
        val repository = FakeLibraryRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveItem(
            original = null,
            title = "   ",
            craft = Craft.KNITTING,
            author = "",
            sourceUrl = "",
            tags = emptyList(),
            notes = ""
        )

        assertTrue(repository.items.value.isEmpty())
    }

    @Test
    fun deletingAnItemRemovesItFromTheRepository() {
        val repository = FakeLibraryRepository(listOf(item("to-delete")))
        val viewModel = viewModel(repository)

        viewModel.deleteItem(repository.items.value.single())

        assertTrue(repository.items.value.isEmpty())
    }

    private fun contentState(viewModel: LibraryViewModel): LibraryUiState.Content {
        return viewModel.uiState.value as LibraryUiState.Content
    }

    private fun item(
        id: String,
        title: String = id,
        craft: Craft = Craft.KNITTING,
        author: String? = null,
        tags: List<String> = emptyList(),
        bookmarked: Boolean = false
    ) = LibraryItem(
        id = id,
        title = title,
        craft = craft,
        author = author,
        sourceUrl = null,
        tags = tags,
        notes = null,
        bookmarked = bookmarked,
        createdAt = 0,
        updatedAt = 0
    )

    private fun viewModel(repository: FakeLibraryRepository): LibraryViewModel {
        val viewModel = LibraryViewModel(repository, externalScope = scope)
        // uiState is built with SharingStarted.WhileSubscribed, so it only
        // starts (and its value only advances past the initial Loading
        // state) once it has an active collector. Under Dispatchers.Unconfined
        // this collection runs synchronously through the fake's
        // non-suspending emissions before this call returns.
        scope.launch { viewModel.uiState.collect {} }
        return viewModel
    }
}

private class FakeLibraryRepository(initial: List<LibraryItem>) : LibraryRepository {
    val items = MutableStateFlow(initial)

    override fun observeLibraryItems(): Flow<List<LibraryItem>> = items

    override fun observeLibraryItem(id: String): Flow<LibraryItem?> =
        throw UnsupportedOperationException("Not used by LibraryViewModel")

    override suspend fun saveLibraryItem(item: LibraryItem) {
        items.value = items.value.filterNot { it.id == item.id } + item
    }

    override suspend fun deleteLibraryItem(item: LibraryItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }
}
