package com.macareen.stitchbook2.data.photos

import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.DefaultAppContainer
import com.macareen.stitchbook2.domain.backup.BackupImportResult
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.PhotoRole
import com.macareen.stitchbook2.domain.ravelry.*
import java.io.OutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Uses synthetic responses and an emulator database. No live API calls or real credentials. */
class RavelrySyncPhotoTest {
    @Test fun reviewedPhotoChangesPreserveLocalPhotosAndRoundTripThroughBackup() = runBlocking {
        val container = DefaultAppContainer(InstrumentationRegistry.getInstrumentation().targetContext)
        val api = FakeApi()
        val cached = mutableListOf<String>()
        val credentials = object : RavelryCredentialStore {
            override fun load() = RavelryCredentials("synthetic-access", "synthetic-personal")
            override fun save(credentials: RavelryCredentials) = Unit
            override fun clear() = Unit
        }
        val sync = RavelrySync(api, credentials, container.stashRepository, container.toolRepository,
            container.projectRepository, container.libraryRepository, journalRepository = container.journalRepository,
            cacheThumbnail = { cached += it })
        val first = sync.preview()
        assertEquals(1, sync.apply(first, includeChanges = false))
        val id = first.newStash.single().id
        val local = Photo("local-cover", null, id, "content://local/original", "My photo",
            "Keep my caption", null, null, PhotoRole.NONE, 0, 0)
        container.journalRepository.savePhoto(local)
        cached.clear()
        assertTrue(sync.preview().hasNothingToDo)
        assertEquals(listOf(api.entry.photoUrl), cached)
        api.entry = api.entry.copy(photoUrl = "https://images.example.test/new.jpg")
        val changed = sync.preview()
        assertEquals(1, changed.changedStash.size)
        cached.clear()
        assertEquals(0, sync.apply(changed, includeChanges = false))
        assertTrue(cached.isEmpty())
        assertEquals("https://images.example.test/old.jpg",
            container.journalRepository.observePhotos().first().single { it.id == "ravelry-stash-photo-9123" }.uri)
        assertEquals(1, sync.apply(changed, includeChanges = true))
        assertEquals(local, container.journalRepository.observePhotos().first().single { it.id == local.id })
        val json = container.backupService.exportJson()
        assertTrue(json.contains("new.jpg"))
        val result = container.backupService.importJson(json)
        assertTrue(result is BackupImportResult.Success)
        assertFalse((result as BackupImportResult.Success).missingFiles.contains("Ravelry stash photo"))
        assertEquals(api.entry.photoUrl,
            container.journalRepository.observePhotos().first().single { it.id == "ravelry-stash-photo-9123" }.uri)
    }

    private class FakeApi : RavelryApi {
        var entry = RavelryStashEntry(9123, null, 1, "Test yarn", null, "Worsted", null, null, null,
            2.0, 220.0, 100.0, "https://images.example.test/old.jpg")
        override suspend fun currentUsername(credentials: RavelryCredentials) = "synthetic-user"
        override suspend fun stash(credentials: RavelryCredentials, username: String) = listOf(entry)
        override suspend fun needles(credentials: RavelryCredentials, username: String) = emptyList<RavelryNeedle>()
        override suspend fun projects(credentials: RavelryCredentials, username: String) = emptyList<RavelryProject>()
        override suspend fun library(credentials: RavelryCredentials, username: String) = emptyList<RavelryVolume>()
        override suspend fun volumeAttachments(credentials: RavelryCredentials, volumeId: Long) = emptyList<RavelryAttachment>()
        override suspend fun downloadLink(credentials: RavelryCredentials, attachmentId: Long): String = error("Unused")
        override suspend fun downloadFile(url: String, into: OutputStream) = error("Unused")
    }
}
