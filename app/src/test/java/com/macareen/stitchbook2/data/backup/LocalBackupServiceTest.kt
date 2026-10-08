package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.BackupImportResult
import com.macareen.stitchbook2.domain.backup.BackupPreview
import com.macareen.stitchbook2.domain.backup.BackupRecordType
import com.macareen.stitchbook2.domain.backup.BackupSnapshot
import com.macareen.stitchbook2.domain.backup.CURRENT_BACKUP_FORMAT_VERSION
import com.macareen.stitchbook2.domain.backup.GuideBackupGraph
import com.macareen.stitchbook2.domain.backup.RestoreMode
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.CounterNote
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.JournalEntry
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.Milestone
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.ToolTemplate
import com.macareen.stitchbook2.domain.repository.CounterNoteRepository
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.JournalRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
import com.macareen.stitchbook2.domain.repository.ToolRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
        storageLocation = "Bin 3",
        careInstructions = "Hand wash cold",
        ravelryYarnId = "12345",
        purchaseSource = "Local yarn shop",
        purchasePrice = 8.5,
        purchaseDate = "2024-03-15",
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
        updatedAt = 200,
        linkedCounterId = null,
        linkIncrementInterval = null,
        linkIncrementAmount = null,
        autoResetOnGoal = true
    )

    private val counterNote = CounterNote(
        id = "note-1",
        counterId = "counter-1",
        value = 12,
        note = "Switched to smaller needles.",
        createdAt = 150
    )

    @Test
    fun exportedJsonRoundTripsThroughImportIntoAFreshRepositorySet() = runBlocking {
        val sourceProjects = FakeProjectRepository(listOf(project))
        val sourceLibrary = FakeLibraryRepository(listOf(libraryItem))
        val sourceStash = FakeStashRepository(listOf(stashItem))
        val sourceTools = FakeToolRepository(listOf(toolSet), listOf(toolItem))
        val sourceCounters = FakeCounterRepository(listOf(counter))
        val sourceCounterNotes = FakeCounterNoteRepository(listOf(counterNote))
        val exportingService = LocalBackupService(
            sourceProjects,
            sourceLibrary,
            sourceStash,
            sourceTools,
            sourceCounters,
            sourceCounterNotes
        )

        val json = exportingService.exportJson()

        val destinationProjects = FakeProjectRepository(emptyList())
        val destinationLibrary = FakeLibraryRepository(emptyList())
        val destinationStash = FakeStashRepository(emptyList())
        val destinationTools = FakeToolRepository(emptyList(), emptyList())
        val destinationCounters = FakeCounterRepository(emptyList())
        val destinationCounterNotes = FakeCounterNoteRepository(emptyList())
        val importingService = LocalBackupService(
            destinationProjects,
            destinationLibrary,
            destinationStash,
            destinationTools,
            destinationCounters,
            destinationCounterNotes
        )

        val result = importingService.importJson(json) as BackupImportResult.Success
        assertEquals(1, result.projectCount)
        assertEquals(1, result.libraryItemCount)
        assertEquals(1, result.stashItemCount)
        assertEquals(1, result.toolSetCount)
        assertEquals(1, result.toolItemCount)
        assertEquals(1, result.counterCount)
        assertEquals(1, result.counterNoteCount)

        assertEquals(project, destinationProjects.items.value.single())
        assertEquals(libraryItem, destinationLibrary.items.value.single())
        assertEquals(stashItem, destinationStash.items.value.single())
        assertEquals(toolSet, destinationTools.sets.value.single())
        assertEquals(toolItem, destinationTools.items.value.single())
        assertEquals(counter, destinationCounters.counters.value.single())
        assertEquals(counterNote, destinationCounterNotes.notes.value.single())
    }

    @Test
    fun exportedJsonRoundTripsALinkedCounterPairRegardlessOfListOrder() = runBlocking {
        val target = counter
        // The linking counter is listed BEFORE its target here on purpose:
        // a real (non-fake) repository enforces the self-referencing FK, so
        // inserting this row's real link before `target` exists would fail
        // without the two-pass strip-then-relink import handles.
        val linking = Counter(
            id = "counter-2",
            projectId = null,
            name = "Round",
            unitLabel = "rounds",
            currentValue = 3,
            goal = null,
            createdAt = 100,
            updatedAt = 200,
            linkedCounterId = target.id,
            linkIncrementInterval = 4,
            linkIncrementAmount = 1
        )
        val sourceCounters = FakeCounterRepository(listOf(linking, target))
        // The counters' project travels with them: validation rejects a
        // counter whose project is in neither the file nor the library.
        val exportingService = LocalBackupService(
            FakeProjectRepository(listOf(project)),
            FakeLibraryRepository(emptyList()),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            sourceCounters,
            FakeCounterNoteRepository(emptyList())
        )

        val json = exportingService.exportJson()

        val destinationCounters = FakeCounterRepository(emptyList())
        val importingService = LocalBackupService(
            FakeProjectRepository(emptyList()),
            FakeLibraryRepository(emptyList()),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            destinationCounters,
            FakeCounterNoteRepository(emptyList())
        )

        val result = importingService.importJson(json)

        assertTrue(result is BackupImportResult.Success)

        val restored = destinationCounters.counters.value.associateBy { it.id }
        assertEquals(target, restored.getValue(target.id))
        assertEquals(linking, restored.getValue(linking.id))
    }

    @Test
    fun importingABackupWithACyclicCounterLinkPairDropsBothLinks() = runBlocking {
        // A backup is untrusted input -- this pair could never be produced
        // by CountersScreen's own cycle validation, but a hand-edited or
        // buggy-exporter file could still contain one.
        val a = Counter(
            id = "a", projectId = null, name = "A", unitLabel = "rows",
            currentValue = 0, goal = null, createdAt = 0, updatedAt = 0,
            linkedCounterId = "b", linkIncrementInterval = 1, linkIncrementAmount = 1
        )
        val b = Counter(
            id = "b", projectId = null, name = "B", unitLabel = "rows",
            currentValue = 0, goal = null, createdAt = 0, updatedAt = 0,
            linkedCounterId = "a", linkIncrementInterval = 1, linkIncrementAmount = 1
        )
        val json = LocalBackupService(
            FakeProjectRepository(emptyList()),
            FakeLibraryRepository(emptyList()),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            FakeCounterRepository(listOf(a, b)),
            FakeCounterNoteRepository(emptyList())
        ).exportJson()

        val destinationCounters = FakeCounterRepository(emptyList())
        LocalBackupService(
            FakeProjectRepository(emptyList()),
            FakeLibraryRepository(emptyList()),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            destinationCounters,
            FakeCounterNoteRepository(emptyList())
        ).importJson(json)

        assertTrue(destinationCounters.counters.value.all { it.linkedCounterId == null })
    }

    @Test
    fun importingABackupWithADanglingCounterLinkDropsIt() = runBlocking {
        val json = """
            {"version":1,"exportedAt":0,"counters":[
                {"id":"a","projectId":null,"name":"A","unitLabel":"rows","currentValue":0,
                 "goal":null,"createdAt":0,"updatedAt":0,"linkedCounterId":"does-not-exist",
                 "linkIncrementInterval":1,"linkIncrementAmount":1}
            ]}
        """.trimIndent()
        val destinationCounters = FakeCounterRepository(emptyList())
        val service = LocalBackupService(
            FakeProjectRepository(emptyList()),
            FakeLibraryRepository(emptyList()),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            destinationCounters,
            FakeCounterNoteRepository(emptyList())
        )

        val result = service.importJson(json) as BackupImportResult.Success

        assertEquals(1, result.counterCount)
        val imported = destinationCounters.counters.value.single()
        assertEquals(null, imported.linkedCounterId)
    }

    @Test
    fun importReplacesExistingDataForKeysPresentInTheBackup() = runBlocking {
        val projects = FakeProjectRepository(listOf(project))
        val library = FakeLibraryRepository(listOf(libraryItem))
        val stash = FakeStashRepository(listOf(stashItem))
        val tools = FakeToolRepository(listOf(toolSet), listOf(toolItem))
        val counters = FakeCounterRepository(listOf(counter))
        val counterNotes = FakeCounterNoteRepository(listOf(counterNote))
        val service = LocalBackupService(projects, library, stash, tools, counters, counterNotes)

        val replacement = project.copy(id = "project-2", name = "Replacement project")
        val json = """{"version":1,"exportedAt":0,"projects":[${projectJson(replacement)}]}"""

        val result = service.importJson(json) as BackupImportResult.Success
        assertEquals(1, result.projectCount)
        assertEquals(null, result.libraryItemCount)
        assertEquals(null, result.stashItemCount)
        assertEquals(null, result.toolSetCount)
        assertEquals(null, result.toolItemCount)
        assertEquals(null, result.counterCount)
        assertEquals(null, result.counterNoteCount)

        assertEquals(listOf(replacement), projects.items.value)
        // Library/Stash/Tools/Counters/CounterNotes were absent from the backup, so they are untouched.
        assertEquals(listOf(libraryItem), library.items.value)
        assertEquals(listOf(stashItem), stash.items.value)
        assertEquals(listOf(toolSet), tools.sets.value)
        assertEquals(listOf(toolItem), tools.items.value)
        assertEquals(listOf(counter), counters.counters.value)
        assertEquals(listOf(counterNote), counterNotes.notes.value)
    }

    @Test
    fun resetAllDataClearsEveryRepository() = runBlocking {
        val projects = FakeProjectRepository(listOf(project))
        val library = FakeLibraryRepository(listOf(libraryItem))
        val stash = FakeStashRepository(listOf(stashItem))
        val tools = FakeToolRepository(listOf(toolSet), listOf(toolItem))
        val counters = FakeCounterRepository(listOf(counter))
        val counterNotes = FakeCounterNoteRepository(listOf(counterNote))
        val service = LocalBackupService(projects, library, stash, tools, counters, counterNotes)

        service.resetAllData()

        assertTrue(projects.items.value.isEmpty())
        assertTrue(library.items.value.isEmpty())
        assertTrue(stash.items.value.isEmpty())
        assertTrue(tools.sets.value.isEmpty())
        assertTrue(tools.items.value.isEmpty())
        assertTrue(counters.counters.value.isEmpty())
        assertTrue(counterNotes.notes.value.isEmpty())
    }

    @Test
    fun resetAllDataAlsoClearsGuidesThatBelongToNoProject() = runBlocking {
        val store = FakeGuideBackupStore(GuideBackupFixtures.graph)

        guideService(store).resetAllData()

        assertEquals(GuideBackupGraph(), store.graph)
    }

    @Test
    fun importingMalformedJsonReturnsInvalidFormatWithoutTouchingAnyRepository() = runBlocking {
        val projects = FakeProjectRepository(listOf(project))
        val library = FakeLibraryRepository(listOf(libraryItem))
        val stash = FakeStashRepository(listOf(stashItem))
        val tools = FakeToolRepository(listOf(toolSet), listOf(toolItem))
        val counters = FakeCounterRepository(listOf(counter))
        val counterNotes = FakeCounterNoteRepository(listOf(counterNote))
        val service = LocalBackupService(projects, library, stash, tools, counters, counterNotes)

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
        val counterNotes = FakeCounterNoteRepository(emptyList())
        val service = LocalBackupService(projects, library, stash, tools, counters, counterNotes)

        val json = """
            {"version":1,"exportedAt":0,"projects":[
                {"id":"p","name":"n","craft":"NOT_A_REAL_CRAFT","projectType":"OTHER",
                 "status":"ACTIVE","notes":null,"createdAt":0,"updatedAt":0}
            ]}
        """.trimIndent()

        val result = service.importJson(json)

        assertEquals(BackupImportResult.InvalidFormat, result)
    }

    @Test
    fun aVersion1FileStillPreviewsAndRestoresWithNewerFieldsDefaulted() = runBlocking {
        val projects = FakeProjectRepository(emptyList())
        val journal = FakeJournalRepository()
        val service = LocalBackupService(
            projects,
            FakeLibraryRepository(emptyList()),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            FakeCounterRepository(emptyList()),
            FakeCounterNoteRepository(emptyList()),
            journalRepository = journal
        )
        // Shape written by format-1 exports: no "format" marker, no manifest, original project fields only.
        val json = """{"version":1,"exportedAt":0,"projects":[${projectJson(project)}]}"""

        val preview = service.previewImport(json) as BackupPreview.Ready
        assertEquals(1, preview.formatVersion)
        assertEquals(setOf(BackupRecordType.PROJECTS), preview.comparison.keys)

        val result = service.importJson(json, RestoreMode.REPLACE)

        assertTrue(result is BackupImportResult.Success)
        val restored = projects.items.value.single()
        assertEquals(project, restored)
        assertEquals(null, restored.description)
        assertEquals(null, restored.startDate)
        assertTrue(journal.entries.value.isEmpty())
    }

    @Test
    fun mergeAddsOnlyNewRecordsAndNeverOverwritesAConflict() = runBlocking {
        val localVersion = project.copy(name = "Local edits", updatedAt = 900)
        val projects = FakeProjectRepository(listOf(localVersion))
        val service = LocalBackupService(
            projects,
            FakeLibraryRepository(emptyList()),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            FakeCounterRepository(emptyList()),
            FakeCounterNoteRepository(emptyList())
        )
        val added = project.copy(id = "project-2", name = "From the file")
        val json = """{"version":2,"exportedAt":0,"projects":[${projectJson(project)},${projectJson(added)}]}"""

        val result = service.importJson(json, RestoreMode.MERGE) as BackupImportResult.Success

        assertEquals(setOf(localVersion, added), projects.items.value.toSet())
        assertEquals(1, result.conflictsKept)
        assertEquals(mapOf(BackupRecordType.PROJECTS to 1), result.written)
    }

    @Test
    fun replacingTheProjectsTypeUpdatesAKeptProjectWithoutDroppingItsChildren() = runBlocking {
        val journal = FakeJournalRepository(
            entries = listOf(journalEntry("entry-1", "project-1"), journalEntry("entry-2", "project-2")),
            milestones = listOf(milestone("milestone-1", "project-1"))
        )
        // Mirrors Room's ON DELETE CASCADE, so a delete-and-reinsert would visibly lose the children.
        val projects = FakeProjectRepository(listOf(project, project.copy(id = "project-2"))) { deleted ->
            journal.entries.value = journal.entries.value.filterNot { it.projectId == deleted.id }
            journal.milestones.value = journal.milestones.value.filterNot { it.projectId == deleted.id }
        }
        val service = LocalBackupService(
            projects,
            FakeLibraryRepository(emptyList()),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            FakeCounterRepository(emptyList()),
            FakeCounterNoteRepository(emptyList()),
            journalRepository = journal
        )
        val renamed = project.copy(name = "Renamed in the backup", updatedAt = 999)
        val json = """{"version":2,"exportedAt":0,"projects":[${projectJson(renamed)}]}"""

        val result = service.importJson(json, RestoreMode.REPLACE)

        assertTrue(result is BackupImportResult.Success)
        assertEquals(listOf(renamed), projects.items.value)
        assertEquals(listOf("project-2"), projects.deletedIds)
        // The kept project's children survive; the removed project's children cascade away.
        assertEquals(listOf("entry-1"), journal.entries.value.map { it.id })
        assertEquals(listOf("milestone-1"), journal.milestones.value.map { it.id })
    }

    @Test
    fun anInvalidFileWritesNothingAndReportsEachIssue() = runBlocking {
        val projects = FakeProjectRepository(listOf(project))
        val journal = FakeJournalRepository()
        val service = LocalBackupService(
            projects,
            FakeLibraryRepository(emptyList()),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            FakeCounterRepository(emptyList()),
            FakeCounterNoteRepository(emptyList()),
            journalRepository = journal
        )
        val json = """
            {"version":2,"exportedAt":0,"projects":[],
             "journalEntries":[{"id":"j1","projectId":"ghost","entryDate":"2026-01-01","title":null,
                                "body":"x","createdAt":0,"updatedAt":0}]}
        """.trimIndent()

        assertTrue(service.previewImport(json) is BackupPreview.Invalid)
        val result = service.importJson(json, RestoreMode.REPLACE) as BackupImportResult.ValidationFailed

        assertEquals(listOf(BackupRecordType.JOURNAL_ENTRIES), result.issues.map { it.type })
        assertEquals(listOf(project), projects.items.value)
        assertTrue(journal.entries.value.isEmpty())
    }

    @Test
    fun referencedFilesThatCannotBeOpenedAreReportedByDisplayName() = runBlocking {
        val exporting = LocalBackupService(
            FakeProjectRepository(emptyList()),
            FakeLibraryRepository(listOf(libraryItem)),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            FakeCounterRepository(emptyList()),
            FakeCounterNoteRepository(emptyList())
        )
        val json = exporting.exportJson()
        val importing = LocalBackupService(
            FakeProjectRepository(emptyList()),
            FakeLibraryRepository(emptyList()),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            FakeCounterRepository(emptyList()),
            FakeCounterNoteRepository(emptyList()),
            isFileAccessible = { false }
        )

        val result = importing.importJson(json, RestoreMode.MERGE) as BackupImportResult.Success

        assertEquals(listOf("Raglan Guide.pdf"), result.missingFiles)
    }

    private fun guideService(
        store: FakeGuideBackupStore,
        projects: List<Project> = listOf(project),
        library: List<LibraryItem> = listOf(libraryItem)
    ) = LocalBackupService(
        FakeProjectRepository(projects),
        FakeLibraryRepository(library),
        FakeStashRepository(emptyList()),
        FakeToolRepository(emptyList(), emptyList()),
        FakeCounterRepository(emptyList()),
        FakeCounterNoteRepository(emptyList()),
        guideBackupStore = store
    )

    /** A file carrying only the guide graph, so its projects and patterns resolve against the device. */
    private fun guideOnlyJson(graph: GuideBackupGraph = GuideBackupFixtures.graph): String =
        encodeBackup(BackupSnapshot(formatVersion = CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(graph), exportedAt = 0)

    @Test
    fun guidesDraftsRevisionsAndProgressRoundTripThroughExportAndReplace() = runBlocking {
        val json = guideService(FakeGuideBackupStore(GuideBackupFixtures.graph)).exportJson()
        val destination = FakeGuideBackupStore()
        val restoring = LocalBackupService(
            FakeProjectRepository(emptyList()),
            FakeLibraryRepository(emptyList()),
            FakeStashRepository(emptyList()),
            FakeToolRepository(emptyList(), emptyList()),
            FakeCounterRepository(emptyList()),
            FakeCounterNoteRepository(emptyList()),
            guideBackupStore = destination
        )

        val result = restoring.importJson(json, RestoreMode.REPLACE) as BackupImportResult.Success

        assertEquals(GuideBackupFixtures.graph, destination.graph)
        assertEquals(2, result.written[BackupRecordType.GUIDES])
        assertEquals(1, result.written[BackupRecordType.EXECUTIONS])
        assertTrue(result.notices.isEmpty())
    }

    @Test
    fun aFormat2FileRestoresWithoutTouchingGuides() = runBlocking {
        val store = FakeGuideBackupStore(GuideBackupFixtures.graph)
        val service = guideService(store)
        val json = """{"format":"stitchbook-backup","version":2,"exportedAt":0,"projects":[${projectJson(project)}]}"""

        val preview = service.previewImport(json) as BackupPreview.Ready
        val result = service.importJson(json, RestoreMode.REPLACE)

        assertEquals(setOf(BackupRecordType.PROJECTS), preview.comparison.keys)
        assertTrue(result is BackupImportResult.Success)
        assertEquals(0, store.replaceCalls)
        assertEquals(GuideBackupFixtures.graph, store.graph)
    }

    @Test
    fun mergeKeepsAGuideWhoseProjectAndPatternAreMissingAndReportsTheClearedLinks() = runBlocking {
        val store = FakeGuideBackupStore()
        val service = guideService(store, projects = emptyList(), library = emptyList())
        val json = guideOnlyJson()

        val preview = service.previewImport(json) as BackupPreview.Ready
        val result = service.importJson(json, RestoreMode.MERGE) as BackupImportResult.Success

        val guide = store.graph.guides.single { it.id == "guide-1" }
        assertEquals(null, guide.projectId)
        assertEquals(null, guide.libraryItemId)
        assertEquals(null, store.graph.guides.single { it.id == "guide-2" }.libraryItemId)
        // Progress moves to the guide on its own, and its resume point follows it.
        assertEquals(null, store.graph.executions.single().projectId)
        assertEquals(listOf(GuideBackupFixtures.active.copy(projectKey = "")), store.graph.activeExecutions)
        assertTrue(store.graph.projectGuides.isEmpty())
        assertEquals(GuideBackupFixtures.graph.drafts, store.graph.drafts)
        assertEquals(GuideBackupFixtures.graph.revisions, store.graph.revisions)

        val noticeTypes = result.notices.map { it.type }.toSet()
        assertEquals(
            setOf(BackupRecordType.GUIDES, BackupRecordType.EXECUTIONS, BackupRecordType.PROJECT_GUIDES),
            noticeTypes
        )
        assertEquals(result.notices, preview.mergeNotices)
    }

    @Test
    fun replaceRejectsAGuideWhoseProjectIsMissingInsteadOfClearingIt() = runBlocking {
        val store = FakeGuideBackupStore()
        val service = guideService(store, projects = emptyList(), library = emptyList())

        val result = service.importJson(guideOnlyJson(), RestoreMode.REPLACE) as BackupImportResult.ValidationFailed

        assertTrue(result.issues.any { it.type == BackupRecordType.GUIDES && it.recordKey == "guide-1" })
        assertEquals(0, store.replaceCalls)
    }

    @Test
    fun mergeLeavesAnExistingGuideAndItsProgressAsTheyAreAndReportsWhatItSkipped() = runBlocking {
        val localGuide = GuideBackupFixtures.projectGuide.copy(name = "Edited on this device")
        val store = FakeGuideBackupStore(GuideBackupGraph(guides = listOf(localGuide)))
        val service = guideService(store)

        val result = service.importJson(guideOnlyJson(), RestoreMode.MERGE) as BackupImportResult.Success

        assertEquals(listOf(localGuide, GuideBackupFixtures.patternGuide), store.graph.guides)
        assertTrue(store.graph.drafts.isEmpty())
        assertTrue(store.graph.revisions.isEmpty())
        assertTrue(store.graph.executions.isEmpty())
        assertEquals(listOf(GuideBackupFixtures.link), store.graph.projectGuides)
        assertEquals(1, result.conflictsKept)
        assertEquals(
            setOf("draft-1", "revision-1", "execution-1", "guide-1/project-1"),
            result.notices.map { it.recordKey }.toSet()
        )
    }

    @Test
    fun aFileWithOnlyPartOfTheGuideGraphIsRejected() = runBlocking {
        val store = FakeGuideBackupStore(GuideBackupFixtures.graph)
        val json = """{"format":"stitchbook-backup","version":3,"exportedAt":0,"guides":[]}"""

        val result = guideService(store).importJson(json, RestoreMode.REPLACE)

        assertTrue(result is BackupImportResult.ValidationFailed)
        assertEquals(GuideBackupFixtures.graph, store.graph)
    }

    @Test
    fun progressPinnedToAnotherGuidesRevisionIsRejected() = runBlocking {
        val store = FakeGuideBackupStore()
        val otherRevision = GuideBackupFixtures.revision.copy(id = "revision-2", guideId = "guide-2")
        val graph = GuideBackupFixtures.graph.copy(
            revisions = GuideBackupFixtures.graph.revisions + otherRevision,
            executions = listOf(GuideBackupFixtures.execution.copy(definitionRevisionId = "revision-2"))
        )

        val result = guideService(store).importJson(guideOnlyJson(graph), RestoreMode.MERGE) as BackupImportResult.ValidationFailed

        assertEquals(listOf(BackupRecordType.EXECUTIONS to "execution-1"), result.issues.map { it.type to it.recordKey })
        assertEquals(0, store.addCalls)
    }

    private fun journalEntry(id: String, projectId: String) = JournalEntry(
        id = id,
        projectId = projectId,
        entryDate = "2026-02-01",
        title = null,
        body = "Progress note",
        createdAt = 100,
        updatedAt = 100
    )

    private fun milestone(id: String, projectId: String) = Milestone(
        id = id,
        projectId = projectId,
        title = "Cast on",
        reachedDate = "2026-01-15",
        notes = null,
        position = 0,
        createdAt = 100,
        updatedAt = 100
    )

    private fun projectJson(project: Project): String {
        val notesJson = project.notes?.let { "\"$it\"" } ?: "null"
        return """
            {"id":"${project.id}","name":"${project.name}","craft":"${project.craft.storageValue}",
             "projectType":"${project.projectType.storageValue}","status":"${project.status.storageValue}",
             "notes":$notesJson,"createdAt":${project.createdAt},"updatedAt":${project.updatedAt}}
        """.trimIndent()
    }
}

private class FakeProjectRepository(
    initial: List<Project>,
    /** Called after a delete, so a test can emulate Room's cascades. */
    private val onDelete: (Project) -> Unit = {}
) : ProjectRepository {
    val items = MutableStateFlow(initial)
    val deletedIds = mutableListOf<String>()
    override fun observeProjects(): Flow<List<Project>> = items
    override fun observeProject(id: String): Flow<Project?> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun saveProject(project: Project) {
        items.value = items.value.filterNot { it.id == project.id } + project
    }
    override suspend fun deleteProject(project: Project) {
        items.value = items.value.filterNot { it.id == project.id }
        deletedIds += project.id
        onDelete(project)
    }
}

private class FakeJournalRepository(
    entries: List<JournalEntry> = emptyList(),
    milestones: List<Milestone> = emptyList(),
    photos: List<Photo> = emptyList()
) : JournalRepository {
    val entries = MutableStateFlow(entries)
    val milestones = MutableStateFlow(milestones)
    val photos = MutableStateFlow(photos)

    override fun observePhotos(): Flow<List<Photo>> = photos
    override fun observePhotosForProject(projectId: String): Flow<List<Photo>> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override fun observePhotosForStashItem(stashItemId: String): Flow<List<Photo>> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun savePhoto(photo: Photo) {
        photos.value = photos.value.filterNot { it.id == photo.id } + photo
    }
    override suspend fun deletePhoto(photo: Photo) {
        photos.value = photos.value.filterNot { it.id == photo.id }
    }
    override fun observeEntries(): Flow<List<JournalEntry>> = entries
    override fun observeEntriesForProject(projectId: String): Flow<List<JournalEntry>> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun saveEntry(entry: JournalEntry) {
        entries.value = entries.value.filterNot { it.id == entry.id } + entry
    }
    override suspend fun deleteEntry(entry: JournalEntry) {
        entries.value = entries.value.filterNot { it.id == entry.id }
    }
    override fun observeMilestones(): Flow<List<Milestone>> = milestones
    override fun observeMilestonesForProject(projectId: String): Flow<List<Milestone>> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun saveMilestones(milestones: List<Milestone>) {
        val ids = milestones.map { it.id }.toSet()
        this.milestones.value = this.milestones.value.filterNot { it.id in ids } + milestones
    }
    override suspend fun deleteMilestone(milestone: Milestone) {
        milestones.value = milestones.value.filterNot { it.id == milestone.id }
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

    val templates = MutableStateFlow<List<ToolTemplate>>(emptyList())
    val assignments = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    override fun observeToolTemplates(): Flow<List<ToolTemplate>> = templates
    override suspend fun saveToolTemplate(template: ToolTemplate) {
        templates.value = templates.value.filterNot { it.id == template.id } + template
    }
    override suspend fun deleteToolTemplate(template: ToolTemplate) {
        templates.value = templates.value.filterNot { it.id == template.id }
    }

    override fun observeToolItemsForProject(projectId: String): Flow<List<ToolItem>> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override fun observeProjectIdsForToolItem(toolItemId: String): Flow<List<String>> =
        flowOf(assignments.value[toolItemId].orEmpty().toList())
    override suspend fun setProjectAssignments(toolItemId: String, projectIds: Set<String>) {
        assignments.value = assignments.value + (toolItemId to projectIds)
    }
    override suspend fun unassignToolFromProject(toolItemId: String, projectId: String) =
        throw UnsupportedOperationException("Not used by LocalBackupService")
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
    override suspend fun incrementCounterValue(id: String, amount: Int, updatedAt: Long): Unit =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun deleteCounter(counter: Counter) {
        counters.value = counters.value.filterNot { it.id == counter.id }
    }
}

private class FakeCounterNoteRepository(initial: List<CounterNote>) : CounterNoteRepository {
    val notes = MutableStateFlow(initial)
    override fun observeNotes(): Flow<List<CounterNote>> = notes
    override fun observeNotesByCounter(counterId: String): Flow<List<CounterNote>> =
        throw UnsupportedOperationException("Not used by LocalBackupService")
    override suspend fun saveNote(note: CounterNote) {
        notes.value = notes.value.filterNot { it.id == note.id } + note
    }
    override suspend fun deleteNote(note: CounterNote) {
        notes.value = notes.value.filterNot { it.id == note.id }
    }
}
