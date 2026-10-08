package com.macareen.stitchbook2.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Bump alongside `StitchbookDatabase.version`; every migration chain below must reach it. */
private const val CURRENT_SCHEMA_VERSION = 20

@RunWith(AndroidJUnit4::class)
class StitchbookMigrationTest {

    private lateinit var context: Context
    private lateinit var database: StitchbookDatabase
    private val migrationHelper by lazy {
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), StitchbookDatabase::class.java)
    }

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
            // @Before leaves a real version-1 database in place.
            database = openMigrated()

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
                setOf("projects", "library_items"),
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
            seedAtVersion(
                2,
                EXISTING_PROJECT_SQL,
                """
                INSERT INTO guides (id, project_id, name, notes, created_at, updated_at)
                VALUES ('existing-guide', 'existing-project', 'Existing guide', NULL, 10, 20)
                """,
                """
                INSERT INTO definition_revisions (id, guide_id, revision_number, created_at)
                VALUES ('existing-revision', 'existing-guide', 1, 30)
                """,
                """
                INSERT INTO revision_nodes (revision_id, node_id, parent_node_id, child_order, type, instruction_text)
                VALUES ('existing-revision', 'instruction', NULL, 0, 'INSTRUCTION', 'Knit')
                """,
                """
                INSERT INTO guide_drafts (id, guide_id, base_revision_id, created_at, updated_at, version)
                VALUES ('existing-draft', 'existing-guide', 'existing-revision', 10, 30, 2)
                """,
                """
                INSERT INTO draft_nodes (draft_id, node_id, parent_node_id, child_order, type, instruction_text)
                VALUES ('existing-draft', 'instruction', NULL, 0, 'INSTRUCTION', 'Knit')
                """
            )

            database = openMigrated()

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
                "project_tool_assignments", "counters", "counter_notes",
                "yarn_allocations", "project_pattern_links", "milestones", "photos",
                "journal_entries", "crafting_sessions", "project_guides"
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
            seedAtVersion(3, EXISTING_PROJECT_SQL)

            database = openMigrated()

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
            seedAtVersion(
                4,
                """
                INSERT INTO library_items (id, title, craft, author, source_url, tags, notes, bookmarked, created_at, updated_at)
                VALUES ('existing-pattern', 'Existing Pattern', 'KNITTING', NULL, NULL, '', NULL, 0, 10, 20)
                """
            )

            database = openMigrated()

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
            seedAtVersion(5, EXISTING_PROJECT_SQL)

            database = openMigrated()

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
            seedAtVersion(6, EXISTING_PROJECT_SQL)

            database = openMigrated()

            val existingProject = database.projectDao()
                .observeById("existing-project")
                .first()
            assertEquals("Existing", existingProject?.name)

            assertTrue(readTableNames().containsAll(setOf("counters")))
            assertEquals(emptyList<CounterEntity>(), database.counterDao().observeAll().first())

            // Version 9 added the self-referencing link, so at the current version
            // counters reference both projects and other counters.
            assertEquals(setOf("projects", "counters"), readForeignKeyParents("counters"))
            assertTrue(readIndexNames("counters").contains("index_counters_project_id"))

            assertEquals(CURRENT_SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        }

    @Test
    fun migrationFromSevenToEightAddsCounterNoteSchemaWithoutTouchingExistingData() =
        runBlocking {
            seedAtVersion(
                7,
                """
                INSERT INTO counters (id, project_id, name, unit_label, current_value, goal, created_at, updated_at)
                VALUES ('existing-counter', NULL, 'Right Sleeve', 'rows', 12, NULL, 10, 20)
                """
            )

            database = openMigrated()

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
            seedAtVersion(
                8,
                """
                INSERT INTO counters (id, project_id, name, unit_label, current_value, goal, created_at, updated_at)
                VALUES ('existing-counter', NULL, 'Right Sleeve', 'rows', 12, NULL, 10, 20)
                """,
                // Seeded specifically to prove MIGRATION_8_9's counters-table
                // recreation doesn't cascade-delete counter_notes rows: SQLite's
                // DROP TABLE performs an implicit DELETE on the dropped table
                // first, which *does* invoke ON DELETE CASCADE on children if
                // done naively (see MIGRATION_8_9's KDoc).
                """
                INSERT INTO counter_notes (id, counter_id, value, note, created_at)
                VALUES ('existing-note', 'existing-counter', 12, 'Switched to smaller needles.', 15)
                """
            )

            database = openMigrated()

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
            seedAtVersion(
                9,
                """
                INSERT INTO counters (
                    id, project_id, name, unit_label, current_value, goal, created_at, updated_at,
                    linked_counter_id, link_increment_interval, link_increment_amount
                ) VALUES ('existing-counter', NULL, 'Right Sleeve', 'rows', 12, 60, 10, 20, NULL, NULL, NULL)
                """
            )

            database = openMigrated()

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
            seedAtVersion(
                10,
                """
                INSERT INTO counters (
                    id, project_id, name, unit_label, current_value, goal, created_at, updated_at,
                    linked_counter_id, link_increment_interval, link_increment_amount, auto_reset_on_goal
                ) VALUES ('existing-counter', NULL, 'Right Sleeve', 'rows', 12, NULL, 10, 20, NULL, NULL, NULL, 0)
                """
            )

            database = openMigrated()

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
            seedAtVersion(11, EXISTING_PROJECT_SQL)

            database = openMigrated()

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
            seedAtVersion(12, EXISTING_PROJECT_SQL)

            database = openMigrated()

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
            seedAtVersion(
                13,
                """
                INSERT INTO stash_items (
                    id, name, category, brand, colorway, dye_lot, weight_category, fiber_content,
                    quantity, unit_label, yardage_per_unit, notes, created_at, updated_at
                ) VALUES (
                    'existing-stash-item', 'Cascade 220', 'YARN', 'Cascade Yarns', 'Ivory', '12345',
                    'Worsted', '100% Wool', 6.0, 'skeins', 220.0, NULL, 10, 20
                )
                """
            )

            database = openMigrated()

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
            seedAtVersion(
                14,
                """
                INSERT INTO projects (id, name, craft, project_type, status, notes, created_at, updated_at)
                VALUES ('existing-project', 'Loom hat', 'LOOM_KNITTING', 'HAT', 'ACTIVE', 'Keep', 1, 2)
                """
            )

            database = openMigrated()

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
     * Replaces the test database with a real version-[version] database built
     * from that version's exported schema, then runs [statements] on it. Room's
     * builder can only create the current version, so this is how each test
     * starts from the exact schema its migration step begins at.
     */
    /**
     * Rebuilding `guides` must keep every guide and everything hanging off
     * it: drafts, published revisions, and in-progress knitting. Foreign keys
     * are switched on before migrating, the worst case, to prove the rebuild
     * can't cascade either way. The result must match 19.json exactly.
     */
    @Test
    fun migrationFromEighteenToNineteenKeepsEveryGuideDraftRevisionAndProgressRow() {
        val helper = migrationHelper
        helper.createDatabase(HELPER_DATABASE_NAME, 18).use { db ->
            listOf(
                EXISTING_PROJECT_SQL,
                """
                INSERT INTO guides (id, project_id, name, notes, created_at, updated_at)
                VALUES ('g1', 'existing-project', 'Body', 'size M', 10, 20)
                """,
                """
                INSERT INTO definition_revisions (id, guide_id, revision_number, created_at)
                VALUES ('r1', 'g1', 1, 30)
                """,
                """
                INSERT INTO revision_nodes (revision_id, node_id, parent_node_id, child_order, type, instruction_text)
                VALUES ('r1', 'n1', NULL, 0, 'INSTRUCTION', 'Knit')
                """,
                """
                INSERT INTO guide_drafts (id, guide_id, base_revision_id, created_at, updated_at, version)
                VALUES ('d1', 'g1', 'r1', 10, 30, 2)
                """,
                """
                INSERT INTO draft_nodes (draft_id, node_id, parent_node_id, child_order, type, instruction_text)
                VALUES ('d1', 'n1', NULL, 0, 'INSTRUCTION', 'Knit')
                """,
                """
                INSERT INTO executions (id, guide_id, definition_revision_id, status, current_instruction_node_id, created_at, updated_at, completed_at, version)
                VALUES ('e1', 'g1', 'r1', 'ACTIVE', 'n1', 40, 50, NULL, 3)
                """,
                """
                INSERT INTO execution_current_address_frames (execution_id, frame_order, container_node_id, frame_type, frame_value)
                VALUES ('e1', 0, 'root', 'ROOT', 0)
                """,
                "INSERT INTO active_executions (guide_id, execution_id) VALUES ('g1', 'e1')"
            ).forEach { db.execSQL(it.trimIndent()) }
            db.execSQL("PRAGMA foreign_keys = ON")
        }

        helper.runMigrationsAndValidate(HELPER_DATABASE_NAME, 19, true, MIGRATION_18_19).use { db ->
            db.query("SELECT project_id, library_item_id, size_label, name, notes FROM guides WHERE id = 'g1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("existing-project", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertEquals("Body", cursor.getString(3))
                assertEquals("size M", cursor.getString(4))
            }
            mapOf(
                "definition_revisions" to 1,
                "revision_nodes" to 1,
                "guide_drafts" to 1,
                "draft_nodes" to 1,
                "executions" to 1,
                "execution_current_address_frames" to 1,
                "active_executions" to 1,
                "project_guides" to 0
            ).forEach { (table, expected) ->
                db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("$table rows after migration", expected, cursor.getInt(0))
                }
            }
            db.query("SELECT name FROM sqlite_master WHERE name = 'guides_v18'").use { cursor ->
                assertFalse("the old table is gone", cursor.moveToFirst())
            }
            listOf("guide_drafts", "definition_revisions", "executions", "active_executions").forEach { child ->
                val parents = db.query("PRAGMA foreign_key_list(`$child`)").use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("table"))) }
                }
                assertTrue("$child still points at guides", "guides" in parents)
                assertFalse("$child never points at the old table", "guides_v18" in parents)
            }
        }
    }

    /**
     * Progress becomes per project: every existing progress record takes its
     * guide's project, and the active pointer is keyed by that project ("" for
     * a pattern guide with none), so nobody loses their place. Foreign keys are
     * on before migrating, the worst case for the `active_executions` rebuild.
     */
    @Test
    fun migrationFromNineteenToTwentyKeepsEveryPlaceUnderItsProject() {
        val helper = migrationHelper
        helper.createDatabase(HELPER_DATABASE_NAME, 19).use { db ->
            listOf(
                EXISTING_PROJECT_SQL,
                """
                INSERT INTO guides (id, project_id, library_item_id, size_label, name, notes, created_at, updated_at)
                VALUES ('g1', 'existing-project', NULL, NULL, 'Body', NULL, 10, 20)
                """,
                """
                INSERT INTO guides (id, project_id, library_item_id, size_label, name, notes, created_at, updated_at)
                VALUES ('g2', NULL, NULL, 'M', 'Pattern guide', NULL, 10, 20)
                """,
                """
                INSERT INTO definition_revisions (id, guide_id, revision_number, created_at)
                VALUES ('r1', 'g1', 1, 30), ('r2', 'g2', 1, 30)
                """,
                """
                INSERT INTO revision_nodes (revision_id, node_id, parent_node_id, child_order, type, instruction_text)
                VALUES ('r1', 'n1', NULL, 0, 'INSTRUCTION', 'Knit'), ('r2', 'n1', NULL, 0, 'INSTRUCTION', 'Purl')
                """,
                """
                INSERT INTO executions (id, guide_id, definition_revision_id, status, current_instruction_node_id, created_at, updated_at, completed_at, version)
                VALUES ('e1', 'g1', 'r1', 'ACTIVE', 'n1', 40, 50, NULL, 3),
                       ('e2', 'g2', 'r2', 'ACTIVE', 'n1', 40, 50, NULL, 1),
                       ('e0', 'g1', 'r1', 'COMPLETED', NULL, 1, 2, 2, 5)
                """,
                "INSERT INTO active_executions (guide_id, execution_id) VALUES ('g1', 'e1'), ('g2', 'e2')"
            ).forEach { db.execSQL(it.trimIndent()) }
            db.execSQL("PRAGMA foreign_keys = ON")
        }

        helper.runMigrationsAndValidate(HELPER_DATABASE_NAME, 20, true, MIGRATION_19_20).use { db ->
            db.query("SELECT id, project_id FROM executions ORDER BY id").use { cursor ->
                val projects = buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
                assertEquals(mapOf("e0" to "existing-project", "e1" to "existing-project", "e2" to null), projects)
            }
            db.query("SELECT guide_id, project_key, execution_id FROM active_executions ORDER BY guide_id").use { cursor ->
                val rows = buildList {
                    while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                }
                assertEquals(listOf(Triple("g1", "existing-project", "e1"), Triple("g2", "", "e2")), rows)
            }
            db.query("SELECT name FROM sqlite_master WHERE name = 'active_executions_v20'").use { cursor ->
                assertFalse("the temporary table is gone", cursor.moveToFirst())
            }
        }
    }

    private fun seedAtVersion(version: Int, vararg statements: String) {
        migrationHelper.createDatabase(DATABASE_NAME, version).use { db ->
            statements.forEach { db.execSQL(it.trimIndent()) }
        }
    }

    /** Opens the test database through every production migration, as the app does. */
    private fun openMigrated(): StitchbookDatabase =
        Room.databaseBuilder(context, StitchbookDatabase::class.java, DATABASE_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    /**
     * Starts from a real version-14 database built from the exported 14.json
     * schema, then checks that 14 -> current keeps existing rows, adds only
     * nullable columns, and ends at exactly the current exported schema
     * (runMigrationsAndValidate fails on any column, index, or FK mismatch).
     */
    @Test
    fun migrationFromFourteenToCurrentPreservesDataAndMatchesTheExportedSchema() {
        val helper = migrationHelper
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
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20
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

        /** The same project row in every schema from 1 through 14. */
        const val EXISTING_PROJECT_SQL = """
            INSERT INTO projects (id, name, craft, project_type, status, notes, created_at, updated_at)
            VALUES ('existing-project', 'Existing', 'CROCHET', 'OTHER', 'ACTIVE', 'Survives migration', 10, 20)
            """
        const val HELPER_DATABASE_NAME = "stitchbook-migration-helper-test.db"
    }
}
