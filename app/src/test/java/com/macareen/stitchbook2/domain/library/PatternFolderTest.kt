package com.macareen.stitchbook2.domain.library

import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatternFolderTest {

    private val tree = "content://provider/tree/Patterns"
    private fun pdf(name: String) = FolderPdf("$tree/document/Patterns%2F$name", name)

    @Test
    fun newPdfsBecomeLibraryEntriesThatPointAtTheOriginalFile() {
        var next = 0
        val items = PatternFolderPlanner.newItems(listOf(pdf("Top-down_Raglan.pdf")), emptyList(), now = 5) { "id-${next++}" }

        val item = items.single()
        assertEquals("Top-down Raglan", item.title)
        assertEquals("$tree/document/Patterns%2FTop-down_Raglan.pdf", item.pdfUri)
        assertEquals("Top-down_Raglan.pdf", item.pdfFileName)
        assertEquals(Craft.OTHER, item.craft)
    }

    @Test
    fun filesAlreadyInTheLibraryAreNotAddedTwice() {
        val existing = listOf(
            entry("a", pdfUri = pdf("Hat.pdf").documentUri, fileName = "Hat.pdf"),
            // Attached by hand earlier, from a different address.
            entry("b", pdfUri = "content://downloads/42", fileName = "Sock.PDF")
        )

        val items = PatternFolderPlanner.newItems(
            listOf(pdf("Hat.pdf"), pdf("Sock.pdf"), pdf("Shawl.pdf"), pdf("Shawl.pdf")),
            existing,
            now = 5
        ) { "new" }

        assertEquals(listOf("Shawl.pdf"), items.map { it.pdfFileName })
    }

    @Test
    fun missingFilesAreCountedButEntriesAreKept() = runBlocking {
        val library = FakeLibrary(listOf(entry("a", pdf("Gone.pdf").documentUri, "Gone.pdf"), entry("b", "content://elsewhere/1", "Other.pdf")))
        val sync = SyncPatternFolder(FakeFolder(tree, listOf(pdf("New.pdf"))), library, newId = { "n" }, clock = { 1 })

        val result = sync()!!

        assertEquals(1, result.added)
        assertEquals(1, result.missing)
        assertEquals(3, library.items.value.size)
    }

    @Test
    fun noFolderMeansNothingHappens() = runBlocking {
        val library = FakeLibrary(emptyList())

        assertNull(SyncPatternFolder(FakeFolder(null, emptyList()), library, newId = { "n" })())
        assertEquals(0, library.items.value.size)
    }

    @Test(expected = IOException::class)
    fun anUnreadableFolderIsReportedNotTreatedAsEmpty() = runBlocking<Unit> {
        val folder = object : PatternFolder by FakeFolder(tree, emptyList()) {
            override suspend fun listPdfs(): List<FolderPdf> = throw IOException("revoked")
        }
        SyncPatternFolder(folder, FakeLibrary(emptyList()), newId = { "n" })()
    }

    private fun entry(id: String, pdfUri: String, fileName: String) = LibraryItem(
        id = id,
        title = id,
        craft = Craft.KNITTING,
        author = null,
        sourceUrl = null,
        tags = emptyList(),
        notes = null,
        bookmarked = false,
        createdAt = 0,
        updatedAt = 0,
        pdfUri = pdfUri,
        pdfFileName = fileName
    )
}

private class FakeFolder(private val uri: String?, private val pdfs: List<FolderPdf>) : PatternFolder {
    override fun folderUri(): String? = uri
    override suspend fun folderName(): String? = "Patterns"
    override suspend fun choose(uri: String): Boolean = true
    override suspend fun forget() = Unit
    override suspend fun listPdfs(): List<FolderPdf> = pdfs
}

private class FakeLibrary(initial: List<LibraryItem>) : LibraryRepository {
    val items = MutableStateFlow(initial)
    override fun observeLibraryItems(): Flow<List<LibraryItem>> = items
    override fun observeLibraryItem(id: String): Flow<LibraryItem?> = items.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun saveLibraryItem(item: LibraryItem) {
        items.value = items.value.filterNot { it.id == item.id } + item
    }
    override suspend fun deleteLibraryItem(item: LibraryItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }
}
