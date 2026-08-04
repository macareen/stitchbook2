package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.BackupImportResult
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBackupServiceTest {

    private val project = Project(
        id = "project-1",
        name = "Everyday cardigan",
        craft = Craft.KNITTING,
        projectType = ProjectType.CARDIGAN,
        status = ProjectStatus.ACTIVE,
        notes = "Use size 7 needles.",
        createdAt = 100,
        updatedAt = 200
    )

    private val libraryItem = LibraryItem(
        id = "library-1",
        title = "Raglan Guide",
        craft = Craft.KNITTING,
        author = "EZ",
        sourceUrl = "https://example.com",
        tags = listOf("raglan", "construction"),
        notes = null,
        bookmarked = true,
        createdAt = 100,
        updatedAt = 200,
        pdfUri = "content://com.example.provider/document/42",
        pdfFileName = "Raglan Guide.pdf",
        pdfLastViewedPage = 2
    )

    private val stashItem = StashItem(
        id = "stash-1",
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
        notes = null,
        createdAt = 100,
        updatedAt = 200
    )

    @Test
    fun exportedJsonRoundTripsThroughImportIntoAFreshRepositorySet() = runBlocking {
        val sourceProjects = FakeProjectRepository(listOf(project))
        val sourceLibrary = FakeLibraryRepository(listOf(libraryItem))
        val sourceStash = FakeStashRepository(listOf(stashItem))
        val exportingService = LocalBackupService(sourceProjects, sourceLibrary, sourceStash)

        val json = exportingService.exportJson()

        val destinationProjects = FakeProjectRepository(emptyList())
        val destinationLibrary = FakeLibraryRepository(emptyList())
        val destinationStash = FakeStashRepository(emptyList())
        val importingService = LocalBackupService(destinationProjects, destinationLibrary, destinationStash)

        val result = importingService.importJson(json) as BackupImportResult.Success
        assertEquals(1, result.projectCount)
        assertEquals(1, result.libraryItemCount)
        assertEquals(1, result.stashItemCount)

        assertEquals(project, destinationProjects.items.value.single())
        assertEquals(libraryItem, destinationLibrary.items.value.single())
        assertEquals(stashItem, destinationStash.items.value.single())
    }

    @Test
    fun importReplacesExistingDataForKeysPresentInTheBackup() = runBlocking {
        val projects = FakeProjectRepository(listOf(project))
        val library = FakeLibraryRepository(listOf(libraryItem))
        val stash = FakeStashRepository(listOf(stashItem))
        val service = LocalBackupService(projects, library, stash)

        val replacement = project.copy(id = "project-2", name = "Replacement project")
        val json = """{"version":1,"exportedAt":0,"projects":[${projectJson(replacement)}]}"""

        val result = service.importJson(json) as BackupImportResult.Success
        assertEquals(1, result.projectCount)
        assertEquals(null, result.libraryItemCount)
        assertEquals(null, result.stashItemCount)

        assertEquals(listOf(replacement), projects.items.value)
        // Library/Stash were absent from the backup, so they are untouched.
        assertEquals(listOf(libraryItem), library.items.value)
        assertEquals(listOf(stashItem), stash.items.value)
    }

    @Test
    fun resetAllDataClearsEveryRepository() = runBlocking {
        val projects = FakeProjectRepository(listOf(project))
        val library = FakeLibraryRepository(listOf(libraryItem))
        val stash = FakeStashRepository(listOf(stashItem))
        val service = LocalBackupService(projects, library, stash)

        service.resetAllData()

        assertTrue(projects.items.value.isEmpty())
        assertTrue(library.items.value.isEmpty())
        assertTrue(stash.items.value.isEmpty())
    }

    @Test
    fun importingMalformedJsonReturnsInvalidFormatWithoutTouchingAnyRepository() = runBlocking {
        val projects = FakeProjectRepository(listOf(project))
        val library = FakeLibraryRepository(listOf(libraryItem))
        val stash = FakeStashRepository(listOf(stashItem))
        val service = LocalBackupService(projects, library, stash)

        val result = service.importJson("not json")

        assertEquals(BackupImportResult.InvalidFormat, result)
        assertEquals(listOf(project), projects.items.value)
    }

    @Test
    fun importingAnUnknownEnumValueReturnsInvalidFormat() = runBlocking {
        val projects = FakeProjectRepository(emptyList())
        val library = FakeLibraryRepository(emptyList())
        val stash = FakeStashRepository(emptyList())
        val service = LocalBackupService(projects, library, stash)

        val json = """
            {"version":1,"exportedAt":0,"projects":[
                {"id":"p","name":"n","craft":"NOT_A_REAL_CRAFT","projectType":"OTHER",
                 "status":"ACTIVE","notes":null,"createdAt":0,"updatedAt":0}
            ]}
        """.trimIndent()

        val result = service.importJson(json)

        assertEquals(BackupImportResult.InvalidFormat, result)
    }

    private fun projectJson(project: Project): String {
        val notesJson = project.notes?.let { "\"$it\"" } ?: "null"
        return """
            {"id":"${project.id}","name":"${project.name}","craft":"${project.craft.storageValue}",
             "projectType":"${project.projectType.storageValue}","status":"${project.status.storageValue}",
             "notes":$notesJson,"createdAt":${project.createdAt},"updatedAt":${project.updatedAt}}
        """.trimIndent()
    }
}

private class FakeProjectRepository(initial: List<Project>) : ProjectRepository {
    val items = MutableStateFlow(initial)
    override fun observeProjects(): Flow<List<Project>> = items
    override fun observeProject(id: String): Flow<Project?> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun saveProject(project: Project) {
        items.value = items.value.filterNot { it.id == project.id } + project
    }
    override suspend fun deleteProject(project: Project) {
        items.value = items.value.filterNot { it.id == project.id }
    }
}

private class FakeLibraryRepository(initial: List<LibraryItem>) : LibraryRepository {
    val items = MutableStateFlow(initial)
    override fun observeLibraryItems(): Flow<List<LibraryItem>> = items
    override fun observeLibraryItem(id: String): Flow<LibraryItem?> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun saveLibraryItem(item: LibraryItem) {
        items.value = items.value.filterNot { it.id == item.id } + item
    }
    override suspend fun deleteLibraryItem(item: LibraryItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }
}

private class FakeStashRepository(initial: List<StashItem>) : StashRepository {
    val items = MutableStateFlow(initial)
    override fun observeStashItems(): Flow<List<StashItem>> = items
    override fun observeStashItem(id: String): Flow<StashItem?> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun saveStashItem(item: StashItem) {
        items.value = items.value.filterNot { it.id == item.id } + item
    }
    override suspend fun deleteStashItem(item: StashItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }
}
