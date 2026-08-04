package com.macareen.stitchbook2.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
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
    }

    @Test
    fun migrationFromOneToTwoPreservesProjectsAndCreatesGuideSchema() =
        runBlocking {
            // MIGRATION_3_4/MIGRATION_4_5/MIGRATION_5_6 must be included even
            // though this test is only about the 1->2 step: Room always
            // migrates up to the version declared on @Database (now 6), so
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
                MIGRATION_5_6
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
            assertEquals(6, database.openHelper.readableDatabase.version)
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

            // MIGRATION_3_4/MIGRATION_4_5/MIGRATION_5_6 must be included even
            // though this test is only about the 2->3 step: Room always
            // migrates up to the version declared on @Database (now 6), so
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
                MIGRATION_5_6
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
                "library_items", "stash_items", "tool_sets", "tool_items"
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

            assertEquals(6, database.openHelper.readableDatabase.version)
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
                MIGRATION_5_6
            ).build()

            val existingProject = database.projectDao()
                .observeById("existing-project")
                .first()
            assertEquals("Existing", existingProject?.name)

            assertTrue(readTableNames().containsAll(setOf("library_items", "stash_items")))
            assertEquals(emptyList<LibraryItemEntity>(), database.libraryDao().observeAll().first())
            assertEquals(emptyList<StashItemEntity>(), database.stashDao().observeAll().first())

            assertEquals(6, database.openHelper.readableDatabase.version)
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
                MIGRATION_5_6
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
                MIGRATION_5_6
            ).build()

            val migrated = database.libraryDao().observeById("existing-pattern").first()
            assertEquals("Existing Pattern", migrated?.title)
            assertEquals(null, migrated?.pdfUri)
            assertEquals(null, migrated?.pdfFileName)
            assertEquals(null, migrated?.pdfLastViewedPage)

            assertEquals(6, database.openHelper.readableDatabase.version)
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
                MIGRATION_5_6
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

            assertEquals(6, database.openHelper.readableDatabase.version)
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
    }
}
