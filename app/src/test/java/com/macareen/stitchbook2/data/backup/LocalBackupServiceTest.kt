package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.BackupImportResult
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
import com.macareen.stitchbook2.domain.repository.ToolRepository
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

    private val toolSet = ToolSet(
        id = "tool-set-1",
        name = "ChiaoGoo TWIST Red Lace 5-inch Set",
        brand = "ChiaoGoo",
        notes = null,
        createdAt = 100,
        updatedAt = 200
    )

    private val toolItem = ToolItem(
        id = "tool-item-1",
        name = "US 7 interchangeable tip",
        category = ToolCategory.INTERCHANGEABLE_TIP,
        brand = "ChiaoGoo",
        material = "Stainless steel",
        sizeMetricMm = 4.5,
        sizeLabel = "US 7",
        lengthMm = 127.0,
        statedCableLengthMm = null,
        cableLengthDefinition = null,
        approximateAssembledLengthMm = null,
        connectorFamily = "ChiaoGoo Twist",
        compatibilityNotes = null,
        quantity = 1,
        storageLocation = "Tip case, slot 7",
        notes = null,
        setId = "tool-set-1",
        createdAt = 100,
        updatedAt = 200
    )

    private val counter = Counter(
        id = "counter-1",
        projectId = "project-1",
        name = "Right Sleeve",
        unitLabel = "rows",
        currentValue = 12,
        goal = 60,
        createdAt = 100,
        updatedAt = 200
    )

    @Test
    fun exportedJsonRoundTripsThroughImportIntoAFreshRepositorySet() = runBlocking {
        val sourceProjects = FakeProjectRepository(listOf(project))
        val sourceLibrary = FakeLibraryRepository(listOf(libraryItem))
        val sourceStash = FakeStashRepository(listOf(stashItem))
        val sourceTools = FakeToolRepository(listOf(toolSet), listOf(toolItem))
        val sourceCounters = FakeCounterRepository(listOf(counter))
        val exportingService = LocalBackupService(
            sourceProjects,
            sourceLibrary,
            sourceStash,
            sourceTools,
            sourceCounters
        )

        val json = exportingService.exportJson()

        val destinationProjects = FakeProjectRepository(emptyList())
        val destinationLibrary = FakeLibraryRepository(emptyList())
        val destinationStash = FakeStashRepository(emptyList())
        val destinationTools = FakeToolRepository(emptyList(), emptyList())
        val destinationCounters = FakeCounterRepository(emptyList())
        val importingService = LocalBackupService(
            destinationProjects,
            destinationLibrary,
            destinationStash,
            destinationTools,
            destinationCounters
        )

        val result = importingService.importJson(json) as BackupImportResult.Success
        assertEquals(1, result.projectCount)
        assertEquals(1, result.libraryItemCount)
        assertEquals(1, result.stashItemCount)
        assertEquals(1, result.toolSetCount)
        assertEquals(1, result.toolItemCount)
        assertEquals(1, result.counterCount)

        assertEquals(project, destinationProjects.items.value.single())
        assertEquals(libraryItem, destinationLibrary.items.value.single())
        assertEquals(stashItem, destinationStash.items.value.single())
        assertEquals(toolSet, destinationTools.sets.value.single())
        assertEquals(toolItem, destinationTools.items.value.single())
        assertEquals(counter, destinationCounters.counters.value.single())
    }

    @Test
    fun importReplacesExistingDataForKeysPresentInTheBackup() = runBlocking {
        val projects = FakeProjectRepository(listOf(project))
        val library = FakeLibraryRepository(listOf(libraryItem))
        val stash = FakeStashRepository(listOf(stashItem))
        val tools = FakeToolRepository(listOf(toolSet), listOf(toolItem))
        val counters = FakeCounterRepository(listOf(counter))
        val service = LocalBackupService(projects, library, stash, tools, counters)

        val replacement = project.copy(id = "project-2", name = "Replacement project")
        val json = """{"version":1,"exportedAt":0,"projects":[${projectJson(replacement)}]}"""

        val result = service.importJson(json) as BackupImportResult.Success
        assertEquals(1, result.projectCount)
        assertEquals(null, result.libraryItemCount)
        assertEquals(null, result.stashItemCount)
        assertEquals(null, result.toolSetCount)
        assertEquals(null, result.toolItemCount)
        assertEquals(null, result.counterCount)

        assertEquals(listOf(replacement), projects.items.value)
        // Library/Stash/Tools/Counters were absent from the backup, so they are untouched.
        assertEquals(listOf(libraryItem), library.items.value)
        assertEquals(listOf(stashItem), stash.items.value)
        assertEquals(listOf(toolSet), tools.sets.value)
        assertEquals(listOf(toolItem), tools.items.value)
        assertEquals(listOf(counter), counters.counters.value)
    }

    @Test
    fun resetAllDataClearsEveryRepository() = runBlocking {
        val projects = FakeProjectRepository(listOf(project))
        val library = FakeLibraryRepository(listOf(libraryItem))
        val stash = FakeStashRepository(listOf(stashItem))
        val tools = FakeToolRepository(listOf(toolSet), listOf(toolItem))
        val counters = FakeCounterRepository(listOf(counter))
        val service = LocalBackupService(projects, library, stash, tools, counters)

        service.resetAllData()

        assertTrue(projects.items.value.isEmpty())
        assertTrue(library.items.value.isEmpty())
        assertTrue(stash.items.value.isEmpty())
        assertTrue(tools.sets.value.isEmpty())
        assertTrue(tools.items.value.isEmpty())
        assertTrue(counters.counters.value.isEmpty())
    }

    @Test
    fun importingMalformedJsonReturnsInvalidFormatWithoutTouchingAnyRepository() = runBlocking {
        val projects = FakeProjectRepository(listOf(project))
        val library = FakeLibraryRepository(listOf(libraryItem))
        val stash = FakeStashRepository(listOf(stashItem))
        val tools = FakeToolRepository(listOf(toolSet), listOf(toolItem))
        val counters = FakeCounterRepository(listOf(counter))
        val service = LocalBackupService(projects, library, stash, tools, counters)

        val result = service.importJson("not json")

        assertEquals(BackupImportResult.InvalidFormat, result)
        assertEquals(listOf(project), projects.items.value)
    }

    @Test
    fun importingAnUnknownEnumValueReturnsInvalidFormat() = runBlocking {
        val projects = FakeProjectRepository(emptyList())
        val library = FakeLibraryRepository(emptyList())
        val stash = FakeStashRepository(emptyList())
        val tools = FakeToolRepository(emptyList(), emptyList())
        val counters = FakeCounterRepository(emptyList())
        val service = LocalBackupService(projects, library, stash, tools, counters)

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

private class FakeToolRepository(
    initialSets: List<ToolSet>,
    initialItems: List<ToolItem>
) : ToolRepository {
    val sets = MutableStateFlow(initialSets)
    val items = MutableStateFlow(initialItems)

    override fun observeToolItems(): Flow<List<ToolItem>> = items
    override fun observeToolItem(id: String): Flow<ToolItem?> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override fun observeToolItemsBySet(setId: String): Flow<List<ToolItem>> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun saveToolItem(item: ToolItem) {
        items.value = items.value.filterNot { it.id == item.id } + item
    }
    override suspend fun deleteToolItem(item: ToolItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }

    override fun observeToolSets(): Flow<List<ToolSet>> = sets
    override fun observeToolSet(id: String): Flow<ToolSet?> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun saveToolSet(set: ToolSet) {
        sets.value = sets.value.filterNot { it.id == set.id } + set
    }
    override suspend fun deleteToolSet(set: ToolSet) {
        sets.value = sets.value.filterNot { it.id == set.id }
    }
}

private class FakeCounterRepository(initial: List<Counter>) : CounterRepository {
    val counters = MutableStateFlow(initial)
    override fun observeCounters(): Flow<List<Counter>> = counters
    override fun observeCountersByProject(projectId: String): Flow<List<Counter>> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override fun observeCounter(id: String): Flow<Counter?> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun saveCounter(counter: Counter) {
        counters.value = counters.value.filterNot { it.id == counter.id } + counter
    }
    override suspend fun deleteCounter(counter: Counter) {
        counters.value = counters.value.filterNot { it.id == counter.id }
    }
}
