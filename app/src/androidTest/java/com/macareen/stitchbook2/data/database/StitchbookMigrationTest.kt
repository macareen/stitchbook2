package com.macareen.stitchbook2.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.repository.LocalGuideRepository
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import java.util.ArrayDeque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Bump alongside `StitchbookDatabase.version`; every migration chain below must reach it. */
private const val CURRENT_SCHEMA_VERSION = 18

@RunWith(AndroidJUnit4::class)
class StitchbookMigrationTest {

    private lateinit var context: Context
    private lateinit var database: StitchbookDatabase

    @Before
    fun prepareVersionOneDatabase() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        val sqlite = SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(DATABASE_NAME),
            null
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `projects` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `craft` TEXT NOT NULL,
                `project_type` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `notes` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS room_master_table (
                id INTEGER PRIMARY KEY,
                identity_hash TEXT
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            INSERT OR REPLACE INTO room_master_table (id, identity_hash)
            VALUES(42, 'ad3dcdfbbbd3585f575d95fbaf72e924')
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            INSERT INTO projects (
                id, name, craft, project_type, status, notes, created_at, updated_at
            ) VALUES (
                'existing-project', 'Existing', 'CROCHET', 'OTHER', 'ACTIVE',
                'Survives migration', 10, 20
            )
            """.trimIndent()
        )
        sqlite.version = 1
        sqlite.close()
    }

    @After
    fun cleanUp() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(DATABASE_NAME)
        context.deleteDatabase(HELPER_DATABASE_NAME)
    }

    @Test
    fun migrationFromOneToTwoPreservesProjectsAndCreatesGuideSchema() =
        runBlocking {
            // MIGRATION_3_4/MIGRATION_4_5/MIGRATION_5_6/MIGRATION_6_7/MIGRATION_7_8/MIGRATION_8_9/MIGRATION_9_10/MIGRATION_10_11/MIGRATION_11_12/MIGRATION_12_13/MIGRATION_13_14 must be included even
            // though this test is only about the 1->2 step: Room always
            // migrates up to the version declared on @Database (now 14), so
            // every migration chain built here has to reach that version or
            // Room rejects it outright.
            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            )
                .build()

            val existing = database.projectDao()
                .observeById("existing-project")
                .first()
            assertEquals("Existing", existing?.name)
            assertEquals("CROCHET", existing?.craft)
            assertEquals("Survives migration", existing?.notes)

            val expectedTables = setOf(
                "projects",
                "guides",
                "guide_drafts",
                "draft_nodes",
                "definition_revisions",
                "revision_nodes"
            )
            assertTrue(readTableNames().containsAll(expectedTables))
            assertTrue(
                readIndexNames("guide_drafts")
                    .contains("index_guide_drafts_guide_id")
            )
            assertTrue(
                readIndexNames("definition_revisions").contains(
                    "index_definition_revisions_guide_id_revision_number"
                )
            )
            assertEquals(
                setOf("projects"),
                readForeignKeyParents("guides")
            )
            assertEquals(
                setOf("guides", "definition_revisions"),
                readForeignKeyParents("guide_drafts")
            )
            assertEquals(
                setOf("guide_drafts", "draft_nodes"),
                readForeignKeyParents("draft_nodes")
            )
            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromTwoToThreePreservesGuideDataAndCreatesExecutionSchema() =
        runBlocking {
            // Build a real, tested version-2 database by running the same
            // 1->2 migration the app uses, then seed it through the real
            // repository (not hand-typed SQL) so the "existing" data is
            // guaranteed schema-correct.
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2).build()

            val ids = ArrayDeque(listOf("existing-guide", "existing-draft"))
            val seedGuideRepository = LocalGuideRepository(
                guideDao = seedDatabase.guideDao(),
                newId = { ids.removeFirst() }
            )
            seedGuideRepository.createGuide(
                projectId = "existing-project",
                name = "Existing guide"
            )
            val draft = checkNotNull(
                seedGuideRepository.loadDraft(GuideId("existing-guide"))
            )
            seedGuideRepository.saveDraft(
                draft.copy(
                    rootNodeIds = listOf(NodeId("instruction")),
                    nodes = listOf(
                        DraftNode(
                            id = NodeId("instruction"),
                            type = DraftNodeType.INSTRUCTION,
                            instructionText = "Knit"
                        )
                    )
                )
            )
            ids.addLast("existing-revision")
            seedGuideRepository.publishDraft(GuideId("existing-guide"))
            seedDatabase.close()

            // MIGRATION_3_4/MIGRATION_4_5/MIGRATION_5_6/MIGRATION_6_7/MIGRATION_7_8/MIGRATION_8_9/MIGRATION_9_10/MIGRATION_10_11/MIGRATION_11_12/MIGRATION_12_13/MIGRATION_13_14 must be included even
            // though this test is only about the 2->3 step: Room always
            // migrates up to the version declared on @Database (now 14), so
            // every migration chain built here has to reach that version or
            // Room rejects it outright.
            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val existingProject = database.projectDao()
                .observeById("existing-project")
                .first()
            assertEquals("Existing", existingProject?.name)

            val existingGuide = database.guideDao().getGuide("existing-guide")
            assertEquals("Existing guide", existingGuide?.name)

            val existingDraft = database.guideDao().getDraft("existing-guide")
            assertNotNull(existingDraft)
            assertEquals(1, existingDraft?.nodes?.size)

            val existingRevision = database.guideDao().getRevision("existing-revision")
            assertNotNull(existingRevision)
            assertEquals(1, existingRevision?.revision?.revisionNumber)

            val expectedTables = setOf(
                "projects", "guides", "guide_drafts", "draft_nodes",
                "definition_revisions", "revision_nodes",
                "executions", "execution_current_address_frames",
                "execution_completed_occurrences",
                "execution_completed_occurrence_frames", "active_executions",
                "library_items", "stash_items", "tool_sets", "tool_items", "tool_templates",
                "project_tool_assignments", "counters", "counter_notes"
            )
            assertEquals(expectedTables, readTableNames())

            assertEquals(
                setOf("guides", "definition_revisions"),
                readForeignKeyParents("executions")
            )
            assertEquals(
                setOf("executions"),
                readForeignKeyParents("execution_current_address_frames")
            )
            assertEquals(
                setOf("executions"),
                readForeignKeyParents("execution_completed_occurrences")
            )
            assertEquals(
                setOf("execution_completed_occurrences"),
                readForeignKeyParents("execution_completed_occurrence_frames")
            )
            assertEquals(
                setOf("guides", "executions"),
                readForeignKeyParents("active_executions")
            )
            assertTrue(
                readIndexNames("active_executions").isNotEmpty()
            )

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromThreeToFourAddsLibraryAndStashSchemaWithoutTouchingExistingData() =
        runBlocking {
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
            seedDatabase.close()

            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val existingProject = database.projectDao()
                .observeById("existing-project")
                .first()
            assertEquals("Existing", existingProject?.name)

            assertTrue(readTableNames().containsAll(setOf("library_items", "stash_items")))
            assertEquals(emptyList<LibraryItemEntity>(), database.libraryDao().observeAll().first())
            assertEquals(emptyList<StashItemEntity>(), database.stashDao().observeAll().first())

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromFourToFiveAddsPdfColumnsWithoutTouchingExistingLibraryData() =
        runBlocking {
            // Build a real, tested version-4 database (the full chain up to
            // the version library_items was introduced in) and seed a
            // library item through the real DAO, then migrate it forward
            // through MIGRATION_4_5 and confirm the row survives with the
            // three new PDF columns defaulting to null.
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()
            seedDatabase.libraryDao().upsert(
                LibraryItemEntity(
                    id = "existing-pattern",
                    title = "Existing Pattern",
                    craft = "KNITTING",
                    author = null,
                    sourceUrl = null,
                    tags = "",
                    notes = null,
                    bookmarked = false,
                    createdAt = 10,
                    updatedAt = 20
                )
            )
            seedDatabase.close()

            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val migrated = database.libraryDao().observeById("existing-pattern").first()
            assertEquals("Existing Pattern", migrated?.title)
            assertEquals(null, migrated?.pdfUri)
            assertEquals(null, migrated?.pdfFileName)
            assertEquals(null, migrated?.pdfLastViewedPage)

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromFiveToSixAddsToolSchemaWithoutTouchingExistingData() =
        runBlocking {
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
            seedDatabase.close()

            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val existingProject = database.projectDao()
                .observeById("existing-project")
                .first()
            assertEquals("Existing", existingProject?.name)

            assertTrue(readTableNames().containsAll(setOf("tool_sets", "tool_items")))
            assertEquals(emptyList<ToolSetEntity>(), database.toolDao().observeAllSets().first())
            assertEquals(emptyList<ToolItemEntity>(), database.toolDao().observeAllItems().first())

            assertEquals(setOf("tool_sets"), readForeignKeyParents("tool_items"))
            assertTrue(readIndexNames("tool_items").contains("index_tool_items_set_id"))

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromSixToSevenAddsCounterSchemaWithoutTouchingExistingData() =
        runBlocking {
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6
            ).build()
            seedDatabase.close()

            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val existingProject = database.projectDao()
                .observeById("existing-project")
                .first()
            assertEquals("Existing", existingProject?.name)

            assertTrue(readTableNames().containsAll(setOf("counters")))
            assertEquals(emptyList<CounterEntity>(), database.counterDao().observeAll().first())

            assertEquals(setOf("projects"), readForeignKeyParents("counters"))
            assertTrue(readIndexNames("counters").contains("index_counters_project_id"))

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromSevenToEightAddsCounterNoteSchemaWithoutTouchingExistingData() =
        runBlocking {
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7
            ).build()
            seedDatabase.counterDao().upsert(
                CounterEntity(
                    id = "existing-counter",
                    projectId = null,
                    name = "Right Sleeve",
                    unitLabel = "rows",
                    currentValue = 12,
                    goal = null,
                    createdAt = 10,
                    updatedAt = 20,
                    linkedCounterId = null,
                    linkIncrementInterval = null,
                    linkIncrementAmount = null,
                    autoResetOnGoal = false,
                    repeatIntervalDays = null,
                    lastRepeatResetAt = null
                )
            )
            seedDatabase.close()

            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val existingCounter = database.counterDao().observeById("existing-counter").first()
            assertEquals("Right Sleeve", existingCounter?.name)
            assertEquals(12, existingCounter?.currentValue)

            assertTrue(readTableNames().containsAll(setOf("counter_notes")))
            assertEquals(emptyList<CounterNoteEntity>(), database.counterNoteDao().observeAll().first())

            assertEquals(setOf("counters"), readForeignKeyParents("counter_notes"))
            assertTrue(readIndexNames("counter_notes").contains("index_counter_notes_counter_id"))

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromEightToNineAddsCounterLinkColumnsWithoutTouchingExistingData() =
        runBlocking {
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8
            ).build()
            seedDatabase.counterDao().upsert(
                CounterEntity(
                    id = "existing-counter",
                    projectId = null,
                    name = "Right Sleeve",
                    unitLabel = "rows",
                    currentValue = 12,
                    goal = null,
                    createdAt = 10,
                    updatedAt = 20,
                    linkedCounterId = null,
                    linkIncrementInterval = null,
                    linkIncrementAmount = null,
                    autoResetOnGoal = false,
                    repeatIntervalDays = null,
                    lastRepeatResetAt = null
                )
            )
            // Seeded specifically to prove MIGRATION_8_9's counters-table
            // recreation doesn't cascade-delete counter_notes rows: SQLite's
            // DROP TABLE performs an implicit DELETE on the dropped table
            // first, which *does* invoke ON DELETE CASCADE on children if
            // done naively (see MIGRATION_8_9's KDoc).
            seedDatabase.counterNoteDao().upsert(
                CounterNoteEntity(
                    id = "existing-note",
                    counterId = "existing-counter",
                    value = 12,
                    note = "Switched to smaller needles.",
                    createdAt = 15
                )
            )
            seedDatabase.close()

            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val existingCounter = database.counterDao().observeById("existing-counter").first()
            assertEquals("Right Sleeve", existingCounter?.name)
            assertEquals(12, existingCounter?.currentValue)
            assertEquals(null, existingCounter?.linkedCounterId)
            assertEquals(null, existingCounter?.linkIncrementInterval)
            assertEquals(null, existingCounter?.linkIncrementAmount)

            val existingNote = database.counterNoteDao().observeByCounterId("existing-counter").first().single()
            assertEquals("existing-note", existingNote.id)
            assertEquals("Switched to smaller needles.", existingNote.note)

            assertEquals(setOf("projects", "counters"), readForeignKeyParents("counters"))
            assertTrue(readIndexNames("counters").contains("index_counters_linked_counter_id"))
            assertEquals(setOf("counters"), readForeignKeyParents("counter_notes"))
            assertTrue(readIndexNames("counter_notes").contains("index_counter_notes_counter_id"))

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromNineToTenAddsAutoResetOnGoalWithoutTouchingExistingData() =
        runBlocking {
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9
            ).build()
            seedDatabase.counterDao().upsert(
                CounterEntity(
                    id = "existing-counter",
                    projectId = null,
                    name = "Right Sleeve",
                    unitLabel = "rows",
                    currentValue = 12,
                    goal = 60,
                    createdAt = 10,
                    updatedAt = 20,
                    linkedCounterId = null,
                    linkIncrementInterval = null,
                    linkIncrementAmount = null,
                    autoResetOnGoal = false,
                    repeatIntervalDays = null,
                    lastRepeatResetAt = null
                )
            )
            seedDatabase.close()

            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val existingCounter = database.counterDao().observeById("existing-counter").first()
            assertEquals("Right Sleeve", existingCounter?.name)
            assertEquals(12, existingCounter?.currentValue)
            assertEquals(60, existingCounter?.goal)
            assertEquals(false, existingCounter?.autoResetOnGoal)

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromTenToElevenAddsRepeatingScheduleColumnsWithoutTouchingExistingData() =
        runBlocking {
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10
            ).build()
            seedDatabase.counterDao().upsert(
                CounterEntity(
                    id = "existing-counter",
                    projectId = null,
                    name = "Right Sleeve",
                    unitLabel = "rows",
                    currentValue = 12,
                    goal = null,
                    createdAt = 10,
                    updatedAt = 20,
                    linkedCounterId = null,
                    linkIncrementInterval = null,
                    linkIncrementAmount = null,
                    autoResetOnGoal = false,
                    repeatIntervalDays = null,
                    lastRepeatResetAt = null
                )
            )
            seedDatabase.close()

            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val existingCounter = database.counterDao().observeById("existing-counter").first()
            assertEquals("Right Sleeve", existingCounter?.name)
            assertEquals(12, existingCounter?.currentValue)
            assertEquals(null, existingCounter?.repeatIntervalDays)
            assertEquals(null, existingCounter?.lastRepeatResetAt)

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromElevenToTwelveAddsToolTemplateSchemaWithoutTouchingExistingData() =
        runBlocking {
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11
            ).build()
            seedDatabase.close()

            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val existingProject = database.projectDao()
                .observeById("existing-project")
                .first()
            assertEquals("Existing", existingProject?.name)

            assertTrue(readTableNames().containsAll(setOf("tool_templates")))
            assertEquals(emptyList<ToolTemplateEntity>(), database.toolDao().observeAllTemplates().first())

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromTwelveToThirteenAddsProjectToolAssignmentSchemaWithoutTouchingExistingData() =
        runBlocking {
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12
            ).build()
            seedDatabase.close()

            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val existingProject = database.projectDao()
                .observeById("existing-project")
                .first()
            assertEquals("Existing", existingProject?.name)

            assertTrue(readTableNames().containsAll(setOf("project_tool_assignments")))
            assertEquals(
                emptyList<ToolItemEntity>(),
                database.toolDao().observeItemsForProject("existing-project").first()
            )
            assertEquals(setOf("projects", "tool_items"), readForeignKeyParents("project_tool_assignments"))
            assertTrue(
                readIndexNames("project_tool_assignments")
                    .contains("index_project_tool_assignments_tool_item_id")
            )

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromThirteenToFourteenAddsStashPurchaseAndCareColumnsWithoutTouchingExistingData() =
        runBlocking {
            val seedDatabase = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4
            ).build()
            seedDatabase.stashDao().upsert(
                StashItemEntity(
                    id = "existing-stash-item",
                    name = "Cascade 220",
                    category = "YARN",
                    brand = "Cascade Yarns",
                    colorway = "Ivory",
                    dyeLot = "12345",
                    weightCategory = "Worsted",
                    fiberContent = "100% Wool",
                    quantity = 6.0,
                    unitLabel = "skeins",
                    yardagePerUnit = 220.0,
                    notes = null,
                    storageLocation = null,
                    careInstructions = null,
                    ravelryYarnId = null,
                    purchaseSource = null,
                    purchasePrice = null,
                    purchaseDate = null,
                    createdAt = 10,
                    updatedAt = 20
                )
            )
            seedDatabase.close()

            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            ).build()

            val migrated = database.stashDao().observeById("existing-stash-item").first()
            assertEquals("Cascade 220", migrated?.name)
            assertEquals(6.0, migrated?.quantity)
            assertEquals(null, migrated?.storageLocation)
            assertEquals(null, migrated?.careInstructions)
            assertEquals(null, migrated?.ravelryYarnId)
            assertEquals(null, migrated?.purchaseSource)
            assertEquals(null, migrated?.purchasePrice)
            assertEquals(null, migrated?.purchaseDate)

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromFourteenToFifteenAddsProjectDetailColumnsWithoutTouchingExistingData() =
        runBlocking {
            database = Room.databaseBuilder(
                context,
                StitchbookDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(*ALL_MIGRATIONS).build()
            database.projectDao().upsert(
                ProjectEntity(
                    id = "existing-project",
                    name = "Loom hat",
                    craft = "LOOM_KNITTING",
                    projectType = "HAT",
                    status = "ACTIVE",
                    notes = "Keep",
                    createdAt = 1,
                    updatedAt = 2
                )
            )

            val migrated = database.projectDao().observeById("existing-project").first()
            assertEquals("Loom hat", migrated?.name)
            assertEquals("Keep", migrated?.notes)
            assertEquals(null, migrated?.description)
            assertEquals(null, migrated?.constructionMethod)
            assertEquals(null, migrated?.customTypeLabel)
            assertEquals(null, migrated?.startDate)
            assertEquals(null, migrated?.targetDate)
            assertEquals(null, migrated?.completedDate)
            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    /**
     * Starts from a real version-14 database built from the exported 14.json
     * schema, then checks that 14 -> 18 keeps existing rows, adds only
     * nullable columns, and ends at exactly the schema in 18.json
     * (runMigrationsAndValidate fails on any column, index, or FK mismatch).
     */
    @Test
    fun migrationFromFourteenToEighteenPreservesDataAndMatchesTheExportedSchema() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            StitchbookDatabase::class.java
        )
        helper.createDatabase(HELPER_DATABASE_NAME, 14).use { db ->
            db.execSQL(
                """
                INSERT INTO projects (id, name, craft, project_type, status, notes, created_at, updated_at)
                VALUES ('existing-project', 'Tunisian scarf', 'TUNISIAN_CROCHET', 'SCARF', 'ACTIVE', 'Keep', 1, 2)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO stash_items (
                    id, name, category, brand, colorway, dye_lot, weight_category, fiber_content,
                    quantity, unit_label, yardage_per_unit, notes, storage_location, care_instructions,
                    ravelry_yarn_id, purchase_source, purchase_price, purchase_date, created_at, updated_at
                ) VALUES (
                    'existing-stash-item', 'Merino DK', 'YARN', NULL, 'Teal', NULL, 'DK', NULL,
                    2.5, 'skeins', 230.0, NULL, 'Bin 1', NULL, NULL, NULL, NULL, NULL, 1, 2
                )
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            HELPER_DATABASE_NAME,
            CURRENT_SCHEMA_VERSION,
            true,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18
        ).use { db ->
            db.query(
                "SELECT name, notes, description, construction_method, custom_type_label, start_date, target_date, completed_date FROM projects WHERE id = 'existing-project'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Tunisian scarf", cursor.getString(0))
                assertEquals("Keep", cursor.getString(1))
                (2..7).forEach { column -> assertTrue(cursor.isNull(column)) }
            }
            db.query(
                "SELECT quantity, storage_location, weight_per_unit_grams, remaining_weight_grams FROM stash_items WHERE id = 'existing-stash-item'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2.5, cursor.getDouble(0), 0.0)
                assertEquals("Bin 1", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
            }
            listOf("yarn_allocations", "project_pattern_links", "milestones", "photos", "journal_entries", "crafting_sessions")
                .forEach { table ->
                    db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("$table starts empty", 0, cursor.getInt(0))
                    }
                }
        }
    }

    private fun readTableNames(): Set<String> {
        return database.openHelper.readableDatabase.query(
            """
            SELECT name FROM sqlite_master
            WHERE type = 'table'
              AND name NOT LIKE 'android_%'
              AND name != 'room_master_table'
            """.trimIndent()
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun readIndexNames(table: String): Set<String> {
        return database.openHelper.readableDatabase.query(
            "PRAGMA index_list(`$table`)"
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
        }
    }

    private fun readForeignKeyParents(table: String): Set<String> {
        return database.openHelper.readableDatabase.query(
            "PRAGMA foreign_key_list(`$table`)"
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(2))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "stitchbook-migration-test.db"
        const val HELPER_DATABASE_NAME = "stitchbook-migration-helper-test.db"
    }
}
