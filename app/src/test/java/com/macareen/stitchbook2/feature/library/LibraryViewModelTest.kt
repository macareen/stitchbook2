package com.macareen.stitchbook2.feature.library

import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.PatternRemovalPreview
import com.macareen.stitchbook2.domain.model.PatternRemovalProject
import com.macareen.stitchbook2.domain.model.PatternRemovalResult
import com.macareen.stitchbook2.domain.model.ProjectStatus
import kotlinx.coroutines.CompletableDeferred
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
            notes = "  ",
            pdfUri = null,
            pdfFileName = null
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
            notes = "",
            pdfUri = null,
            pdfFileName = null
        )

        assertTrue(repository.items.value.isEmpty())
    }

    @Test
    fun deletingAnItemRemovesItFromTheRepository() {
        val repository = FakeLibraryRepository(listOf(item("to-delete")))
        val viewModel = viewModel(repository)

        viewModel.requestRemoval(repository.items.value.single())
        assertEquals(1, repository.items.value.size)
        viewModel.confirmRemoval()

        assertTrue(repository.items.value.isEmpty())
    }

    @Test fun changedDependenciesRequireAnotherConfirmation() {
        val repository = FakeLibraryRepository(listOf(item("pattern")))
        val viewModel = viewModel(repository)
        viewModel.requestRemoval(repository.items.value.single())
        val original = viewModel.removalReview.value?.preview
        repository.projects = listOf(PatternRemovalProject("new", "New project", ProjectStatus.ACTIVE))
        viewModel.confirmRemoval()
        assertEquals(PatternRemovalProblem.CHANGED, viewModel.removalReview.value?.problem)
        assertEquals(repository.projects, viewModel.removalReview.value?.preview?.projects)
        assertEquals(1, repository.items.value.size)
        viewModel.confirmRemoval(original)
        assertEquals(1, repository.removalCalls)
        assertEquals(1, repository.items.value.size)
        viewModel.confirmRemoval()
        assertTrue(repository.items.value.isEmpty())
        assertEquals(null, viewModel.removalReview.value)
    }

    @Test fun failedRemovalKeepsTheReviewedSelectionForRetry() {
        val repository = FakeLibraryRepository(listOf(item("pattern")))
        val viewModel = viewModel(repository)
        viewModel.requestRemoval(repository.items.value.single())
        val preview = viewModel.removalReview.value?.preview
        repository.failRemoval = true
        viewModel.confirmRemoval()
        assertEquals(PatternRemovalProblem.FAILED, viewModel.removalReview.value?.problem)
        assertEquals(preview, viewModel.removalReview.value?.preview)
        repository.failRemoval = false
        viewModel.confirmRemoval()
        assertTrue(repository.items.value.isEmpty())
    }

    @Test fun removingCannotBeDismissedOrSubmittedTwice() {
        val repository = FakeLibraryRepository(listOf(item("pattern")))
        val viewModel = viewModel(repository)
        viewModel.requestRemoval(repository.items.value.single())
        val result = CompletableDeferred<PatternRemovalResult>()
        repository.pendingRemoval = result
        viewModel.confirmRemoval()
        viewModel.confirmRemoval()
        viewModel.dismissRemoval()
        assertEquals(1, repository.removalCalls)
        assertEquals(true, viewModel.removalReview.value?.isRemoving)
        result.complete(PatternRemovalResult.Removed)
        assertEquals(null, viewModel.removalReview.value)
    }

    @Test fun dismissedLoadingReviewDoesNotReopenWhenItsReadCompletes() {
        val repository = FakeLibraryRepository(listOf(item("pattern")))
        val viewModel = viewModel(repository)
        val result = CompletableDeferred<PatternRemovalPreview?>()
        repository.pendingPreview = result
        val item = repository.items.value.single()
        viewModel.requestRemoval(item)
        viewModel.dismissRemoval()
        result.complete(PatternRemovalPreview(item, emptyList(), emptyList()))
        assertEquals(null, viewModel.removalReview.value)
    }

    @Test fun failedPreviewCanBeRetriedWithoutDeletingTheItem() {
        val repository = FakeLibraryRepository(listOf(item("pattern")))
        val viewModel = viewModel(repository)
        val item = repository.items.value.single()
        repository.failPreview = true
        viewModel.requestRemoval(item)
        assertEquals(PatternRemovalProblem.FAILED, viewModel.removalReview.value?.problem)
        assertEquals(false, viewModel.removalReview.value?.isLoading)
        assertEquals(null, viewModel.removalReview.value?.preview)
        repository.failPreview = false
        viewModel.requestRemoval(item)
        assertEquals(item, viewModel.removalReview.value?.preview?.item)
        assertEquals(null, viewModel.removalReview.value?.problem)
        assertEquals(1, repository.items.value.size)
        assertEquals(0, repository.removalCalls)
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
    var projects = emptyList<PatternRemovalProject>()
    var failRemoval = false
    var failPreview = false
    var removalCalls = 0
    var pendingRemoval: CompletableDeferred<PatternRemovalResult>? = null
    var pendingPreview: CompletableDeferred<PatternRemovalPreview?>? = null

    override fun observeLibraryItems(): Flow<List<LibraryItem>> = items

    override fun observeLibraryItem(id: String): Flow<LibraryItem?> =
        throw UnsupportedOperationException("Not used by LibraryViewModel")

    override suspend fun saveLibraryItem(item: LibraryItem) {
        items.value = items.value.filterNot { it.id == item.id } + item
    }

    override suspend fun deleteLibraryItem(item: LibraryItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }

    override suspend fun previewRemoval(id: String): PatternRemovalPreview? {
        check(!failPreview)
        pendingPreview?.let { return it.await() }
        return items.value.find { it.id == id }?.let { PatternRemovalPreview(it, emptyList(), projects) }
    }

    override suspend fun removeReviewed(preview: PatternRemovalPreview): PatternRemovalResult {
        removalCalls++
        pendingRemoval?.let { return it.await() }
        check(!failRemoval)
        val current = previewRemoval(preview.item.id) ?: return PatternRemovalResult.Missing
        if (current != preview) return PatternRemovalResult.Changed(current)
        deleteLibraryItem(preview.item)
        return PatternRemovalResult.Removed
    }
}
