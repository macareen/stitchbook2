package com.macareen.stitchbook2.domain.ravelry

import com.macareen.stitchbook2.domain.library.FolderPdf
import com.macareen.stitchbook2.domain.library.PatternFileExistsException
import com.macareen.stitchbook2.domain.library.PatternFolder
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRavelryPdfTest {

    private val credentials = RavelryCredentials("access", "personal")
    private val api = FakeApi()
    private val folder = FakeFolder()
    private val store = FakeStore(credentials)
    private val library = FakeLibrary(listOf(item(RavelryImportPlanner.VOLUME_ID_PREFIX + "7", "Seaside Cardigan", patternId = "900")))
    private val download = DownloadRavelryPdf(api, store, folder, library, clock = { 99 })

    @Test
    fun thePdfIsSavedIntoTheFolderAndLinkedToTheEntry() = runBlocking {
        api.attachments[7] = listOf(RavelryAttachment(31, "Seaside_Cardigan.pdf"), RavelryAttachment(32, "Charts.zip"))

        val result = download("ravelry-volume-7")

        assertEquals(DownloadRavelryPdf.Result.Saved("Seaside_Cardigan.pdf", alreadyInFolder = false, otherPdfs = 0), result)
        assertEquals("%PDF-1.7 pattern", folder.saved["Seaside_Cardigan.pdf"]!!.toString(Charsets.US_ASCII.name()))
        val linked = library.items.value.single()
        assertEquals("content://tree/Seaside_Cardigan.pdf", linked.pdfUri)
        assertEquals("Seaside_Cardigan.pdf", linked.pdfFileName)
        assertEquals(99L, linked.updatedAt)
        assertEquals(listOf(31L), api.linksAskedFor)
    }

    @Test
    fun aFileAlreadyInTheFolderIsLinkedAndNeverReplaced() = runBlocking {
        api.attachments[7] = listOf(RavelryAttachment(31, "Seaside_Cardigan.pdf"))
        folder.existing += FolderPdf("content://tree/sub/seaside_cardigan.pdf", "seaside_cardigan.pdf")

        val result = download("ravelry-volume-7")

        assertEquals(DownloadRavelryPdf.Result.Saved("seaside_cardigan.pdf", alreadyInFolder = true, otherPdfs = 0), result)
        assertTrue(folder.saved.isEmpty())
        assertTrue(api.linksAskedFor.isEmpty())
        assertEquals("content://tree/sub/seaside_cardigan.pdf", library.items.value.single().pdfUri)
    }

    @Test
    fun somethingThatIsNotAPdfIsNotKeptOrLinked() = runBlocking {
        api.attachments[7] = listOf(RavelryAttachment(31, "Seaside_Cardigan.pdf"))
        api.body = "<html>Sign in</html>"

        val result = download("ravelry-volume-7")

        assertEquals(DownloadRavelryPdf.Result.Failed(DownloadRavelryPdf.Problem.NOT_A_PDF), result)
        assertTrue(folder.saved.isEmpty())
        assertNull(library.items.value.single().pdfUri)
    }

    @Test
    fun missingPrerequisitesAreToldBeforeAnythingIsFetched() = runBlocking {
        folder.writable = false
        assertEquals(DownloadRavelryPdf.Result.Failed(DownloadRavelryPdf.Problem.FOLDER_READ_ONLY), download("ravelry-volume-7"))
        folder.uri = null
        assertEquals(DownloadRavelryPdf.Result.Failed(DownloadRavelryPdf.Problem.NO_FOLDER), download("ravelry-volume-7"))
        store.credentials = null
        assertEquals(DownloadRavelryPdf.Result.Failed(DownloadRavelryPdf.Problem.NO_KEY), download("ravelry-volume-7"))
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun ravelryFailuresBecomePlainProblems() = runBlocking {
        api.failure = RavelryAuthException("HTTP 403")
        assertEquals(DownloadRavelryPdf.Result.Failed(DownloadRavelryPdf.Problem.KEY_REFUSED), download("ravelry-volume-7"))
        api.failure = RavelryUnavailableException("Could not reach Ravelry.", IOException("offline"))
        assertEquals(DownloadRavelryPdf.Result.Failed(DownloadRavelryPdf.Problem.OFFLINE), download("ravelry-volume-7"))
        api.failure = RavelryUnavailableException("HTTP 500")
        assertEquals(DownloadRavelryPdf.Result.Failed(DownloadRavelryPdf.Problem.UNREADABLE_ANSWER), download("ravelry-volume-7"))
        api.failure = null
        api.attachments[7] = listOf(RavelryAttachment(32, "Charts.zip"))
        assertEquals(DownloadRavelryPdf.Result.Failed(DownloadRavelryPdf.Problem.NO_PDF), download("ravelry-volume-7"))
    }

    @Test
    fun anEntryWithOnlyAPatternIdIsFoundInTheRavelryLibrary() = runBlocking {
        library.items.value = listOf(item("own-entry", "Hat", patternId = "900"))
        api.volumes = listOf(RavelryVolume(id = 8, title = "Hat", authorName = null, patternId = 900))
        api.attachments[8] = listOf(RavelryAttachment(41, null), RavelryAttachment(40, "Hat.pdf"), RavelryAttachment(42, "Hat (es).pdf"))

        val result = download("own-entry")

        assertEquals(DownloadRavelryPdf.Result.Saved("Hat.pdf", alreadyInFolder = false, otherPdfs = 2), result)
        assertEquals(listOf(40L), api.linksAskedFor)
    }

    @Test
    fun onlyRavelryEntriesWithoutAFileOfferADownload() {
        assertTrue(download.canDownload(item("ravelry-volume-7", "A")))
        assertTrue(download.canDownload(item("mine", "A", patternId = "5")))
        assertFalse(download.canDownload(item("mine", "A")))
        assertFalse(download.canDownload(item("ravelry-volume-7", "A").copy(pdfUri = "content://x")))
    }

    @Test
    fun fileNamesAreSafeAndEndInPdf() {
        assertEquals("Seaside_Cardigan.pdf", DownloadRavelryPdf.fileNameFor("Seaside_Cardigan.pdf", "x"))
        assertEquals("Lace Shawl.pdf", DownloadRavelryPdf.fileNameFor(null, "Lace: Shawl?"))
        assertEquals("a b.pdf", DownloadRavelryPdf.fileNameFor("a/b.PDF", "x"))
        assertEquals("Ravelry pattern.pdf", DownloadRavelryPdf.fileNameFor("  ", "..."))
    }

    @Test
    fun theSignatureCheckPassesPdfsThroughWhole() {
        val sink = ByteArrayOutputStream()
        PdfSignatureCheck(sink).use { check ->
            check.write("%PD".toByteArray())
            check.write("F-1.4 rest".toByteArray(), 0, 10)
        }
        assertEquals("%PDF-1.4 rest", sink.toString(Charsets.US_ASCII.name()))
        assertTrue(runCatching { PdfSignatureCheck(ByteArrayOutputStream()).use { it.write("%PD".toByteArray()) } }.exceptionOrNull() is NotAPdfException)
    }

    private fun item(id: String, title: String, patternId: String? = null) = LibraryItem(
        id = id, title = title, craft = Craft.KNITTING, author = null, sourceUrl = null, tags = emptyList(),
        notes = null, bookmarked = false, createdAt = 1, updatedAt = 1, ravelryPatternId = patternId
    )

    private class FakeApi : RavelryApi {
        val attachments = mutableMapOf<Long, List<RavelryAttachment>>()
        var volumes = emptyList<RavelryVolume>()
        var body = "%PDF-1.7 pattern"
        var failure: IOException? = null
        val calls = mutableListOf<String>()
        val linksAskedFor = mutableListOf<Long>()

        private fun call(name: String) {
            calls += name
            failure?.let { throw it }
        }

        override suspend fun currentUsername(credentials: RavelryCredentials) = "ana".also { call("user") }
        override suspend fun stash(credentials: RavelryCredentials, username: String) = emptyList<RavelryStashEntry>()
        override suspend fun needles(credentials: RavelryCredentials, username: String) = emptyList<RavelryNeedle>()
        override suspend fun projects(credentials: RavelryCredentials, username: String) = emptyList<RavelryProject>()
        override suspend fun library(credentials: RavelryCredentials, username: String) = volumes.also { call("library") }
        override suspend fun volumeAttachments(credentials: RavelryCredentials, volumeId: Long) =
            attachments[volumeId].orEmpty().also { call("volume") }

        override suspend fun downloadLink(credentials: RavelryCredentials, attachmentId: Long): String {
            call("link")
            linksAskedFor += attachmentId
            return "https://downloads.test/$attachmentId"
        }

        override suspend fun downloadFile(url: String, into: OutputStream) {
            call("download")
            into.write(body.toByteArray())
        }
    }

    private class FakeFolder : PatternFolder {
        var uri: String? = "content://tree"
        var writable = true
        val existing = mutableListOf<FolderPdf>()
        val saved = mutableMapOf<String, ByteArrayOutputStream>()

        override fun folderUri() = uri
        override suspend fun folderName() = "Patterns"
        override suspend fun choose(uri: String) = true
        override suspend fun forget() = Unit
        override suspend fun listPdfs() = existing.toList()
        override fun canSave() = writable

        override suspend fun saveNewPdf(displayName: String, write: suspend (OutputStream) -> Unit): FolderPdf {
            if (existing.any { it.displayName.equals(displayName, ignoreCase = true) }) throw PatternFileExistsException(displayName)
            val out = ByteArrayOutputStream()
            write(out)
            saved[displayName] = out
            return FolderPdf("$uri/$displayName", displayName)
        }
    }

    private class FakeStore(var credentials: RavelryCredentials?) : RavelryCredentialStore {
        override fun load() = credentials
        override fun save(credentials: RavelryCredentials) = Unit
        override fun clear() = Unit
    }

    private class FakeLibrary(initial: List<LibraryItem>) : LibraryRepository {
        val items = MutableStateFlow(initial)
        override fun observeLibraryItems(): Flow<List<LibraryItem>> = items
        override fun observeLibraryItem(id: String): Flow<LibraryItem?> = items.map { list -> list.firstOrNull { it.id == id } }
        override suspend fun saveLibraryItem(item: LibraryItem) {
            items.value = items.value.filterNot { it.id == item.id } + item
        }
        override suspend fun deleteLibraryItem(item: LibraryItem) = Unit
    }
}
