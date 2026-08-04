package com.macareen.stitchbook2.data.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.repository.LocalGuideRepository
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.Instruction
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.execution.Range
import com.macareen.stitchbook2.domain.execution.Repeat
import com.macareen.stitchbook2.domain.execution.Section
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import java.util.ArrayDeque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuideRepositoryTest {

    private lateinit var database: StitchbookDatabase
    private lateinit var repository: LocalGuideRepository
    private lateinit var ids: ArrayDeque<String>
    private var now = 100L

    @Before
    fun createDatabase() {
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            StitchbookDatabase::class.java
        ).build()
        ids = ArrayDeque()
        repository = LocalGuideRepository(
            guideDao = database.guideDao(),
            newId = { ids.removeFirst() },
            currentTimeMillis = { now++ }
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun guideBelongsToProjectAndStartsWithOneEmptyDraft() = runBlocking {
        insertProject("project")
        enqueueIds("guide", "draft")

        val guide = repository.createGuide(
            projectId = "project",
            name = "  Cardigan guide  ",
            notes = "  private notes  "
        )

        assertEquals(GuideId("guide"), guide.id)
        assertEquals("project", guide.projectId)
        assertEquals("Cardigan guide", guide.name)
        assertEquals("private notes", guide.notes)
        assertEquals(listOf(guide), repository.observeGuides("project").first())

        val draft = repository.loadDraft(guide.id)
        assertNotNull(draft)
        assertEquals("draft", draft?.id?.value)
        assertEquals(emptyList<NodeId>(), draft?.rootNodeIds)
        assertEquals(emptyList<DraftNode>(), draft?.nodes)

        // createDraftFromLatestRevision() always generates a draft ID before
        // calling the DAO, even though the DAO discards it here because a
        // draft already exists. Enqueue a placeholder for that unused call.
        enqueueIds("unused-draft-id")
        assertEquals(draft, repository.createDraftFromLatestRevision(guide.id))
    }

    @Test
    fun databaseRejectsGuideForMissingProject() {
        enqueueIds("guide", "draft")

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                repository.createGuide("missing", "Guide")
            }
        }
    }

    @Test
    fun separateProjectsOwnSeparateGuideLists() = runBlocking {
        insertProject("one")
        insertProject("two")
        enqueueIds("guide-one", "draft-one", "guide-two", "draft-two")

        repository.createGuide("one", "First")
        repository.createGuide("two", "Second")

        assertEquals(
            listOf(GuideId("guide-one")),
            repository.observeGuides("one").first().map { it.id }
        )
        assertEquals(
            listOf(GuideId("guide-two")),
            repository.observeGuides("two").first().map { it.id }
        )
    }

    @Test
    fun projectCanOwnMultipleGuides() = runBlocking {
        insertProject("project")
        enqueueIds("guide-one", "draft-one", "guide-two", "draft-two")

        repository.createGuide("project", "First")
        repository.createGuide("project", "Second")

        assertEquals(
            setOf(GuideId("guide-one"), GuideId("guide-two")),
            repository.observeGuides("project").first().map { it.id }.toSet()
        )
    }

    @Test
    fun incompleteDraftCanBeSavedButCannotBePublished() = runBlocking {
        val guideId = createGuide()
        val original = checkNotNull(repository.loadDraft(guideId))
        val incomplete = original.copy(
            rootNodeIds = listOf(nodeId("range")),
            nodes = listOf(
                DraftNode(
                    id = nodeId("range"),
                    type = DraftNodeType.RANGE,
                    rangeUnitLabel = null,
                    rangeStartInclusive = 1,
                    rangeEndInclusive = null
                )
            )
        )

        val saved = repository.saveDraft(incomplete)

        assertEquals(incomplete.nodes, saved.nodes)
        enqueueIds("revision")
        assertSuspendThrows<InvalidDraftForPublicationException> {
            repository.publishDraft(guideId)
        }
        assertEquals(emptyList<Any>(), repository.listRevisions(guideId))
        val unchanged = checkNotNull(repository.loadDraft(guideId))
        assertNull(unchanged.baseRevisionId)
        assertEquals(saved.version, unchanged.version)
    }

    @Test
    fun publicationCreatesImmutableMonotonicRevisionsAndKeepsEditableDraft() =
        runBlocking {
            val guideId = createGuide()
            val initial = checkNotNull(repository.loadDraft(guideId))
            repository.saveDraft(initial.withValidTree("Knit"))

            enqueueIds("revision-1")
            val first = repository.publishDraft(guideId)
            assertEquals(1, first.revisionNumber)
            assertEquals(DefinitionRevisionId("revision-1"), first.id)

            val afterFirst = checkNotNull(repository.loadDraft(guideId))
            assertEquals(first.id, afterFirst.baseRevisionId)
            assertEquals("Knit", instructionText(afterFirst.nodes))

            repository.saveDraft(afterFirst.withInstruction("Purl"))
            enqueueIds("revision-2")
            val second = repository.publishDraft(guideId)

            assertEquals(2, second.revisionNumber)
            assertEquals(
                listOf(1, 2),
                repository.listRevisions(guideId).map { it.revisionNumber }
            )
            assertEquals(
                "Knit",
                instructionText(
                    checkNotNull(repository.loadRevision(first.id))
                        .definition.nodes
                )
            )
            assertEquals(
                "Purl",
                instructionText(
                    checkNotNull(repository.loadRevision(second.id))
                        .definition.nodes
                )
            )
            assertEquals(
                second.id,
                checkNotNull(repository.loadDraft(guideId)).baseRevisionId
            )
        }

    @Test
    fun revisionNumbersAreScopedToGuide() = runBlocking {
        insertProject("project")
        enqueueIds("guide-one", "draft-one", "guide-two", "draft-two")
        val firstGuide = repository.createGuide("project", "First").id
        val secondGuide = repository.createGuide("project", "Second").id
        repository.saveDraft(
            checkNotNull(repository.loadDraft(firstGuide)).withValidTree("First")
        )
        repository.saveDraft(
            checkNotNull(repository.loadDraft(secondGuide)).withValidTree("Second")
        )
        enqueueIds("first-revision", "second-revision")

        val firstRevision = repository.publishDraft(firstGuide)
        val secondRevision = repository.publishDraft(secondGuide)

        assertEquals(1, firstRevision.revisionNumber)
        assertEquals(1, secondRevision.revisionNumber)
        assertEquals(DefinitionRevisionId("first-revision"), firstRevision.id)
        assertEquals(DefinitionRevisionId("second-revision"), secondRevision.id)
    }

    @Test
    fun publishedTreePreservesStableIdsOrderAndNodeConfiguration() = runBlocking {
        val guideId = createGuide()
        val draft = checkNotNull(repository.loadDraft(guideId))
        repository.saveDraft(draft.withValidTree("Work even"))
        enqueueIds("revision")

        val definition = repository.publishDraft(guideId).definition

        assertEquals(listOf(nodeId("section")), definition.rootNodeIds)
        val nodes = definition.nodes.associateBy { it.id }
        assertEquals(
            listOf(nodeId("repeat")),
            (nodes.getValue(nodeId("section")) as Section).children
        )
        val repeat = nodes.getValue(nodeId("repeat")) as Repeat
        assertEquals(2, repeat.count)
        assertEquals("Lace", repeat.label)
        assertEquals(listOf(nodeId("range")), repeat.children)
        val range = nodes.getValue(nodeId("range")) as Range
        assertEquals("row", range.unitLabel)
        assertEquals(1, range.startInclusive)
        assertEquals(3, range.endInclusive)
        assertEquals(listOf(nodeId("instruction")), range.children)
        assertEquals(
            "Work even",
            (nodes.getValue(nodeId("instruction")) as Instruction).text
        )
    }

    @Test
    fun staleDraftSaveIsRejectedWithoutReplacingNewerContent() = runBlocking {
        val guideId = createGuide()
        val firstCopy = checkNotNull(repository.loadDraft(guideId))
        val staleCopy = checkNotNull(repository.loadDraft(guideId))

        repository.saveDraft(firstCopy.withValidTree("Current"))

        assertSuspendThrows<DraftConflictException> {
            repository.saveDraft(staleCopy.withValidTree("Stale"))
        }
        assertEquals(
            "Current",
            instructionText(checkNotNull(repository.loadDraft(guideId)).nodes)
        )
    }

    @Test
    fun deletingGuideCascadesDraftRevisionsAndNodes() = runBlocking {
        val guideId = createGuide()
        val draft = checkNotNull(repository.loadDraft(guideId))
        repository.saveDraft(draft.withValidTree("Knit"))
        enqueueIds("revision")
        val revision = repository.publishDraft(guideId)

        repository.deleteGuide(guideId)

        assertNull(repository.getGuide(guideId))
        assertNull(repository.loadDraft(guideId))
        assertNull(repository.loadRevision(revision.id))
    }

    @Test
    fun deletingProjectCascadesItsGuideData() = runBlocking {
        val project = projectEntity("project")
        database.projectDao().upsert(project)
        enqueueIds("guide", "draft")
        val guide = repository.createGuide("project", "Guide")
        val draft = checkNotNull(repository.loadDraft(guide.id))
        repository.saveDraft(draft.withValidTree("Knit"))
        enqueueIds("revision")
        val revision = repository.publishDraft(guide.id)

        database.projectDao().delete(project)

        assertNull(repository.getGuide(guide.id))
        assertNull(repository.loadRevision(revision.id))
    }

    private suspend fun createGuide(): GuideId {
        insertProject("project")
        enqueueIds("guide", "draft")
        return repository.createGuide("project", "Guide").id
    }

    private suspend fun insertProject(id: String) {
        database.projectDao().upsert(projectEntity(id))
    }

    private fun enqueueIds(vararg values: String) {
        values.forEach(ids::addLast)
    }

    private fun com.macareen.stitchbook2.domain.guide.GuideDraft.withValidTree(
        instruction: String
    ) = copy(
        rootNodeIds = listOf(nodeId("section")),
        nodes = listOf(
            DraftNode(
                id = nodeId("section"),
                type = DraftNodeType.SECTION,
                title = "Body",
                children = listOf(nodeId("repeat"))
            ),
            DraftNode(
                id = nodeId("repeat"),
                type = DraftNodeType.REPEAT,
                repeatCount = 2,
                repeatLabel = "Lace",
                children = listOf(nodeId("range"))
            ),
            DraftNode(
                id = nodeId("range"),
                type = DraftNodeType.RANGE,
                rangeUnitLabel = "row",
                rangeStartInclusive = 1,
                rangeEndInclusive = 3,
                children = listOf(nodeId("instruction"))
            ),
            DraftNode(
                id = nodeId("instruction"),
                type = DraftNodeType.INSTRUCTION,
                instructionText = instruction
            )
        )
    )

    private fun com.macareen.stitchbook2.domain.guide.GuideDraft.withInstruction(
        instruction: String
    ) = copy(
        nodes = nodes.map { node ->
            if (node.type == DraftNodeType.INSTRUCTION) {
                node.copy(instructionText = instruction)
            } else {
                node
            }
        }
    )

    private fun instructionText(nodes: List<Any>): String {
        return nodes.mapNotNull { node ->
            when (node) {
                is DraftNode -> node.instructionText
                is Instruction -> node.text
                else -> null
            }
        }.single()
    }

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit
    ): T {
        return try {
            block()
            throw AssertionError("Expected ${T::class.java.simpleName}")
        } catch (throwable: Throwable) {
            if (throwable is T) throwable else throw throwable
        }
    }

    private fun nodeId(value: String) = NodeId(value)
}

private fun projectEntity(id: String) = ProjectEntity(
    id = id,
    name = "Project",
    craft = "KNITTING",
    projectType = "OTHER",
    status = "PLANNED",
    notes = null,
    createdAt = 1,
    updatedAt = 1
)
