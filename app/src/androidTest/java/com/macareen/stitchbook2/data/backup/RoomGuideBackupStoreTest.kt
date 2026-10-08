package com.macareen.stitchbook2.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.database.ProjectEntity
import com.macareen.stitchbook2.data.database.StitchbookDatabase
import com.macareen.stitchbook2.data.repository.LocalExecutionRepository
import com.macareen.stitchbook2.data.repository.LocalGuideRepository
import com.macareen.stitchbook2.domain.backup.BackupSnapshot
import com.macareen.stitchbook2.domain.backup.CURRENT_BACKUP_FORMAT_VERSION
import com.macareen.stitchbook2.domain.backup.GuideBackupGraph
import com.macareen.stitchbook2.domain.backup.GuideRecord
import com.macareen.stitchbook2.domain.backup.ProjectGuideLink
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomGuideBackupStoreTest {

    private lateinit var source: StitchbookDatabase
    private lateinit var destination: StitchbookDatabase
    private val ids = ArrayDeque<String>()
    private var now = 100L

    @Before
    fun createDatabases() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        source = Room.inMemoryDatabaseBuilder(context, StitchbookDatabase::class.java).build()
        destination = Room.inMemoryDatabaseBuilder(context, StitchbookDatabase::class.java).build()
    }

    @After
    fun closeDatabases() {
        source.close()
        destination.close()
    }

    @Test
    fun realProgressSurvivesAJsonBackupAndReplaceIntoAnEmptyDatabase() = runBlocking {
        source.projectDao().upsert(projectEntity("project-1"))
        val guideId = publishSimpleGuide(source)
        val executions = executionRepository(source)
        ids.addLast("execution")
        val created = executions.createExecution(guideId, checkNotNull(guideRepository(source).getLatestRevision(guideId)).id, "project-1")
        executions.applyComplete(created.state.executionId, created.version)
        val backedUp = checkNotNull(executions.getActiveExecution(guideId, "project-1"))

        val graph = RoomGuideBackupStore(source.guideBackupDao()).load()
        val json = encodeBackup(BackupSnapshot(formatVersion = CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(graph), exportedAt = 0)
        val decoded = checkNotNull(decodeBackup(json).guideGraph())

        destination.projectDao().upsert(projectEntity("project-1"))
        val store = RoomGuideBackupStore(destination.guideBackupDao())
        store.replace(decoded)

        assertEquals(graph, store.load())
        val restored = checkNotNull(executionRepository(destination).getActiveExecution(guideId, "project-1"))
        assertEquals(backedUp.version, restored.version)
        assertEquals(backedUp.state.currentAddress, restored.state.currentAddress)
        assertEquals(backedUp.state.completedAddresses, restored.state.completedAddresses)
        assertNotNull(guideRepository(destination).loadDraft(guideId))

        // Restored progress keeps working with the real engine.
        executionRepository(destination).applyComplete(restored.state.executionId, restored.version)
        assertEquals(restored.version + 1, executionRepository(destination).loadExecution(ExecutionId("execution"))?.version)
    }

    @Test
    fun replaceUpdatesAKeptGuideInPlaceAndRemovesGuidesTheFileLacks() = runBlocking {
        destination.projectDao().upsert(projectEntity("project-1"))
        val store = RoomGuideBackupStore(destination.guideBackupDao())
        val kept = guide("kept")
        val link = ProjectGuideLink("project-1", "kept")
        store.add(GuideBackupGraph(guides = listOf(kept, guide("dropped")), projectGuides = listOf(link)))

        val renamed = kept.copy(name = "Renamed by the backup", updatedAt = 999)
        store.replace(GuideBackupGraph(guides = listOf(renamed), projectGuides = listOf(link)))

        val after = store.load()
        assertEquals(listOf(renamed), after.guides)
        assertEquals(listOf(link), after.projectGuides)
    }

    @Test
    fun aCollisionDuringAddWritesNothing() = runBlocking {
        val store = RoomGuideBackupStore(destination.guideBackupDao())
        store.add(GuideBackupGraph(guides = listOf(guide("existing"))))

        try {
            store.add(GuideBackupGraph(guides = listOf(guide("new"), guide("existing"))))
            fail("Expected the duplicate guide to abort the write")
        } catch (_: Exception) {
            // The transaction rolls back as a whole.
        }

        assertEquals(listOf("existing"), store.load().guides.map { it.id })
    }

    @Test
    fun replacingWithAnEmptyGraphRemovesEveryGuide() = runBlocking {
        source.projectDao().upsert(projectEntity("project-1"))
        publishSimpleGuide(source)
        val store = RoomGuideBackupStore(source.guideBackupDao())

        store.replace(GuideBackupGraph())

        val after = store.load()
        assertTrue(after.guides.isEmpty())
        assertTrue(after.revisions.isEmpty())
        assertTrue(after.drafts.isEmpty())
    }

    private suspend fun publishSimpleGuide(database: StitchbookDatabase): GuideId {
        val guides = guideRepository(database)
        ids.addLast("guide")
        ids.addLast("draft")
        val guide = guides.createGuide("project-1", "Body")
        val draft = checkNotNull(guides.loadDraft(guide.id))
        guides.saveDraft(
            draft.copy(
                rootNodeIds = listOf(NodeId("section")),
                nodes = listOf(
                    DraftNode(id = NodeId("section"), type = DraftNodeType.SECTION, title = "Body", children = listOf(NodeId("range"))),
                    DraftNode(
                        id = NodeId("range"),
                        type = DraftNodeType.RANGE,
                        rangeUnitLabel = "round",
                        rangeStartInclusive = 1,
                        rangeEndInclusive = 3,
                        children = listOf(NodeId("instruction"))
                    ),
                    DraftNode(id = NodeId("instruction"), type = DraftNodeType.INSTRUCTION, instructionText = "Knit")
                )
            )
        )
        ids.addLast("revision")
        guides.publishDraft(guide.id)
        return guide.id
    }

    private fun guideRepository(database: StitchbookDatabase) =
        LocalGuideRepository(guideDao = database.guideDao(), newId = { ids.removeFirst() }, currentTimeMillis = { now++ })

    private fun executionRepository(database: StitchbookDatabase) =
        LocalExecutionRepository(executionDao = database.executionDao(), newId = { ids.removeFirst() }, currentTimeMillis = { now++ })

    private fun guide(id: String) = GuideRecord(
        id = id,
        projectId = "project-1".takeIf { id != "existing" && id != "new" },
        libraryItemId = null,
        sizeLabel = null,
        name = "Guide $id",
        notes = null,
        createdAt = 1,
        updatedAt = 1
    )

    private fun projectEntity(id: String) = ProjectEntity(
        id = id,
        name = "Project",
        craft = "KNITTING",
        projectType = "OTHER",
        status = "ACTIVE",
        notes = null,
        createdAt = 1,
        updatedAt = 1
    )
}
