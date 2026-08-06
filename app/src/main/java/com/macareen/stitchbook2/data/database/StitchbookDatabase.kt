package com.macareen.stitchbook2.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        GuideEntity::class,
        GuideDraftEntity::class,
        DraftNodeEntity::class,
        DefinitionRevisionEntity::class,
        RevisionNodeEntity::class,
        ExecutionEntity::class,
        ExecutionCurrentAddressFrameEntity::class,
        ExecutionCompletedOccurrenceEntity::class,
        ExecutionCompletedOccurrenceFrameEntity::class,
        ActiveExecutionEntity::class,
        LibraryItemEntity::class,
        StashItemEntity::class,
        ToolSetEntity::class,
        ToolItemEntity::class,
        ToolTemplateEntity::class,
        ProjectToolAssignmentEntity::class,
        CounterEntity::class,
        CounterNoteEntity::class
    ],
    version = 14,
    exportSchema = true
)
abstract class StitchbookDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun guideDao(): GuideDao
    abstract fun executionDao(): ExecutionDao
    abstract fun libraryDao(): LibraryDao
    abstract fun stashDao(): StashDao
    abstract fun toolDao(): ToolDao
    abstract fun counterDao(): CounterDao
    abstract fun counterNoteDao(): CounterNoteDao

    companion object {
        private const val DATABASE_NAME = "stitchbook.db"

        @Volatile
        private var instance: StitchbookDatabase? = null

        fun getInstance(context: Context): StitchbookDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
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
                    MIGRATION_13_14
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `guides` (
                `id` TEXT NOT NULL,
                `project_id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `notes` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_guides_project_id` " +
                "ON `guides` (`project_id`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `definition_revisions` (
                `id` TEXT NOT NULL,
                `guide_id` TEXT NOT NULL,
                `revision_number` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`guide_id`) REFERENCES `guides`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_definition_revisions_guide_id` " +
                "ON `definition_revisions` (`guide_id`)"
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                `index_definition_revisions_guide_id_revision_number`
            ON `definition_revisions` (`guide_id`, `revision_number`)
            """.trimIndent()
        )

        // base_revision_id uses ON DELETE SET NULL, not CASCADE, because
        // definition_revisions rows are never deleted on their own — only as
        // part of the same draft's guide-level cascade delete. See the KDoc
        // on DefinitionRevisionEntity for the immutability/no-standalone-
        // delete invariant this depends on.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `guide_drafts` (
                `id` TEXT NOT NULL,
                `guide_id` TEXT NOT NULL,
                `base_revision_id` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `version` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`guide_id`) REFERENCES `guides`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`base_revision_id`) REFERENCES
                    `definition_revisions`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        // Unique index on guide_id: "at most one editable Draft per Guide"
        // is a database-level constraint here, not just a repository-layer
        // convention.
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_guide_drafts_guide_id`
            ON `guide_drafts` (`guide_id`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_guide_drafts_base_revision_id`
            ON `guide_drafts` (`base_revision_id`)
            """.trimIndent()
        )

        // The self-referencing parent_node_id foreign key is DEFERRABLE
        // INITIALLY DEFERRED so an entire node tree can be inserted in one
        // batch without requiring parent rows to be inserted before their
        // children. The constraint is still enforced, just checked at
        // transaction commit instead of per-statement.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `draft_nodes` (
                `draft_id` TEXT NOT NULL,
                `node_id` TEXT NOT NULL,
                `parent_node_id` TEXT,
                `child_order` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT,
                `instruction_text` TEXT,
                `range_unit_label` TEXT,
                `range_start_inclusive` INTEGER,
                `range_end_inclusive` INTEGER,
                `repeat_count` INTEGER,
                `repeat_label` TEXT,
                PRIMARY KEY(`draft_id`, `node_id`),
                FOREIGN KEY(`draft_id`) REFERENCES `guide_drafts`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`draft_id`, `parent_node_id`) REFERENCES
                    `draft_nodes`(`draft_id`, `node_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
                    DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
                `index_draft_nodes_draft_id_parent_node_id`
            ON `draft_nodes` (`draft_id`, `parent_node_id`)
            """.trimIndent()
        )

        // Same rationale as draft_nodes above: deferred so a whole revision
        // tree can be inserted atomically without parent-before-child
        // ordering.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `revision_nodes` (
                `revision_id` TEXT NOT NULL,
                `node_id` TEXT NOT NULL,
                `parent_node_id` TEXT,
                `child_order` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT,
                `instruction_text` TEXT,
                `range_unit_label` TEXT,
                `range_start_inclusive` INTEGER,
                `range_end_inclusive` INTEGER,
                `repeat_count` INTEGER,
                `repeat_label` TEXT,
                PRIMARY KEY(`revision_id`, `node_id`),
                FOREIGN KEY(`revision_id`) REFERENCES
                    `definition_revisions`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`revision_id`, `parent_node_id`) REFERENCES
                    `revision_nodes`(`revision_id`, `node_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
                    DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
                `index_revision_nodes_revision_id_parent_node_id`
            ON `revision_nodes` (`revision_id`, `parent_node_id`)
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // definition_revision_id intentionally has no ON DELETE action: a
        // Definition Revision must never be deleted while an Execution
        // still references it. Deleting the owning Guide still removes
        // this row because guide_id cascades directly, in the same
        // statement that cascades definition_revisions away.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `executions` (
                `id` TEXT NOT NULL,
                `guide_id` TEXT NOT NULL,
                `definition_revision_id` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `current_instruction_node_id` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `completed_at` INTEGER,
                `version` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`guide_id`) REFERENCES `guides`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`definition_revision_id`) REFERENCES
                    `definition_revisions`(`id`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_executions_guide_id` " +
                "ON `executions` (`guide_id`)"
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
                `index_executions_definition_revision_id`
            ON `executions` (`definition_revision_id`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `execution_current_address_frames` (
                `execution_id` TEXT NOT NULL,
                `frame_order` INTEGER NOT NULL,
                `container_node_id` TEXT NOT NULL,
                `frame_type` TEXT NOT NULL,
                `frame_value` INTEGER NOT NULL,
                PRIMARY KEY(`execution_id`, `frame_order`),
                FOREIGN KEY(`execution_id`) REFERENCES `executions`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // address_signature is a derived, injective (collision-free)
        // encoding of the Instruction Node identity and ordered ancestry
        // frames of one executable occurrence (see toSignature() in
        // ExecutionEntityMapping.kt); the normalized frame rows remain the
        // canonical representation. The primary key below is what keeps
        // completed occurrence addresses unique as a set at the database
        // level.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `execution_completed_occurrences` (
                `execution_id` TEXT NOT NULL,
                `address_signature` TEXT NOT NULL,
                `instruction_node_id` TEXT NOT NULL,
                PRIMARY KEY(`execution_id`, `address_signature`),
                FOREIGN KEY(`execution_id`) REFERENCES `executions`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS
                `execution_completed_occurrence_frames` (
                `execution_id` TEXT NOT NULL,
                `address_signature` TEXT NOT NULL,
                `frame_order` INTEGER NOT NULL,
                `container_node_id` TEXT NOT NULL,
                `frame_type` TEXT NOT NULL,
                `frame_value` INTEGER NOT NULL,
                PRIMARY KEY(`execution_id`, `address_signature`, `frame_order`),
                FOREIGN KEY(`execution_id`, `address_signature`) REFERENCES
                    `execution_completed_occurrences`
                    (`execution_id`, `address_signature`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // One row per Guide, present if and only if that Guide currently
        // has an ACTIVE Execution. guide_id as the primary key is the
        // database-level enforcement of "at most one ACTIVE Execution per
        // Guide": a second attempt to activate an Execution for the same
        // Guide violates this primary key and rolls back the transaction.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `active_executions` (
                `guide_id` TEXT NOT NULL,
                `execution_id` TEXT NOT NULL,
                PRIMARY KEY(`guide_id`),
                FOREIGN KEY(`guide_id`) REFERENCES `guides`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`execution_id`) REFERENCES `executions`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                `index_active_executions_execution_id`
            ON `active_executions` (`execution_id`)
            """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `library_items` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `craft` TEXT NOT NULL,
                `author` TEXT,
                `source_url` TEXT,
                `tags` TEXT NOT NULL,
                `notes` TEXT,
                `bookmarked` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stash_items` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `brand` TEXT,
                `colorway` TEXT,
                `dye_lot` TEXT,
                `weight_category` TEXT,
                `fiber_content` TEXT,
                `quantity` REAL NOT NULL,
                `unit_label` TEXT NOT NULL,
                `yardage_per_unit` REAL,
                `notes` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

/**
 * Adds an optional PDF attachment to a Library item -- a persisted-permission
 * `content://` URI plus its display name, never a blob and never a copy of
 * the original file (see PRODUCT_SPEC.md 6.5). All three columns are
 * nullable with no default-value backfill needed: every existing row simply
 * has no PDF attached yet.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `library_items` ADD COLUMN `pdf_uri` TEXT")
        db.execSQL("ALTER TABLE `library_items` ADD COLUMN `pdf_file_name` TEXT")
        db.execSQL("ALTER TABLE `library_items` ADD COLUMN `pdf_last_viewed_page` INTEGER")
    }
}

/**
 * Adds the Tools inventory schema (PRODUCT_SPEC.md 6.8): individual tool
 * components (`tool_items`) and the grouped sets they may optionally belong
 * to (`tool_sets`). `set_id` uses ON DELETE SET NULL, not CASCADE -- a set is
 * a label over existing inventory, so deleting the set must return its
 * components to standalone items rather than deleting or orphan-failing them
 * (see ARCHITECTURE.md 9).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tool_sets` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `brand` TEXT,
                `notes` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tool_items` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `brand` TEXT,
                `material` TEXT,
                `size_metric_mm` REAL,
                `size_label` TEXT,
                `length_mm` REAL,
                `stated_cable_length_mm` REAL,
                `cable_length_definition` TEXT,
                `approximate_assembled_length_mm` REAL,
                `connector_family` TEXT,
                `compatibility_notes` TEXT,
                `quantity` INTEGER NOT NULL,
                `storage_location` TEXT,
                `notes` TEXT,
                `set_id` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`set_id`) REFERENCES `tool_sets`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tool_items_set_id` " +
                "ON `tool_items` (`set_id`)"
        )
    }
}

/**
 * Adds the Counters schema (PRODUCT_SPEC.md 6.3): this first increment is
 * persistence only -- a counter's current value, optional goal, and the
 * project it may belong to. `project_id` uses ON DELETE CASCADE, the same
 * relationship a Guide already has to its Project (see [GuideEntity]):
 * a project's counters are part of that project's own record and go with
 * it, while a null `project_id` (a standalone counter) is never subject to
 * this cascade. Increment/decrement actions, automatic reset rules, linked
 * behavior between counters, repeating schedules, and notifications are
 * later increments of this same phase (ROADMAP.md Phase 3), not part of
 * this migration.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `counters` (
                `id` TEXT NOT NULL,
                `project_id` TEXT,
                `name` TEXT NOT NULL,
                `unit_label` TEXT NOT NULL,
                `current_value` INTEGER NOT NULL,
                `goal` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_counters_project_id` " +
                "ON `counters` (`project_id`)"
        )
    }
}

/**
 * Adds value-specific notes for Counters (PRODUCT_SPEC.md 6.3, "Notes
 * attached to particular values"). `counter_id` is required (unlike a
 * Counter's own optional `project_id`) and uses ON DELETE CASCADE: a note
 * only means something relative to the counter it was written against, so
 * deleting that counter takes its notes with it.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `counter_notes` (
                `id` TEXT NOT NULL,
                `counter_id` TEXT NOT NULL,
                `value` INTEGER NOT NULL,
                `note` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`counter_id`) REFERENCES `counters`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_counter_notes_counter_id` " +
                "ON `counter_notes` (`counter_id`)"
        )
    }
}

/**
 * Adds a single optional outgoing link between Counters (PRODUCT_SPEC.md
 * 6.3, "Linked behavior between counters"): every `link_increment_interval`
 * increments of a counter, the counter at `linked_counter_id` is bumped by
 * `link_increment_amount`. See [wouldCreateCycle] for the invariant the
 * application layer enforces before ever writing a link.
 *
 * SQLite can't add a foreign-key column via `ALTER TABLE ADD COLUMN`, so
 * `counters` has to be recreated to add `linked_counter_id`. A naive
 * recreate (create a new table, copy rows across, `DROP TABLE counters`,
 * rename the new table into place) is unsafe here: `counter_notes.counter_id`
 * has `ON DELETE CASCADE` pointing at `counters`, and per SQLite's own
 * documentation, `DROP TABLE` performs an implicit `DELETE` of every row in
 * the dropped table first when foreign keys are enabled -- and that
 * implicit delete *does* invoke `ON DELETE CASCADE` on children
 * (sqlite.org/foreignkeys.html section 5: "the implicit DELETE ... may
 * invoke foreign key actions"). A naive recreate would therefore silently
 * wipe every `counter_notes` row the moment the old `counters` table is
 * dropped, before it's even recreated.
 *
 * To avoid this, nothing that still has a live foreign key pointing at
 * `counters` is ever dropped: `counters` is renamed out of the way first
 * (SQLite automatically rewrites `counter_notes`' foreign key definition to
 * follow the rename), then `counter_notes` itself is recreated pointing at
 * the *new* `counters` table, and only then are the two renamed-away old
 * tables dropped -- by that point nothing references them, so their
 * implicit deletes have no cascade action left to invoke.
 *
 * `linked_counter_id` uses ON DELETE SET NULL, the same "pointer, not
 * ownership" relationship tool_items already has to tool_sets: deleting the
 * linked-to counter clears this counter's link rather than deleting it.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Move the old counters table out of the way. SQLite rewrites
        // counter_notes' FK definition to follow this rename automatically.
        db.execSQL("ALTER TABLE `counters` RENAME TO `counters_old`")

        // 2. Create the real, final counters table and copy every row
        // across. The three new columns are nullable and omitted from the
        // column list, so every existing row gets NULL for them -- no
        // counter had a link before this migration.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `counters` (
                `id` TEXT NOT NULL,
                `project_id` TEXT,
                `name` TEXT NOT NULL,
                `unit_label` TEXT NOT NULL,
                `current_value` INTEGER NOT NULL,
                `goal` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `linked_counter_id` TEXT,
                `link_increment_interval` INTEGER,
                `link_increment_amount` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`linked_counter_id`) REFERENCES `counters`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `counters` (
                `id`, `project_id`, `name`, `unit_label`, `current_value`,
                `goal`, `created_at`, `updated_at`
            )
            SELECT `id`, `project_id`, `name`, `unit_label`, `current_value`,
                `goal`, `created_at`, `updated_at`
            FROM `counters_old`
            """.trimIndent()
        )

        // 3. Recreate counter_notes pointing at the new counters table --
        // every counter_id value already exists there from step 2, so this
        // insert satisfies the foreign key -- BEFORE counters_old is ever
        // dropped, so nothing still references it by step 4.
        db.execSQL("ALTER TABLE `counter_notes` RENAME TO `counter_notes_old`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `counter_notes` (
                `id` TEXT NOT NULL,
                `counter_id` TEXT NOT NULL,
                `value` INTEGER NOT NULL,
                `note` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`counter_id`) REFERENCES `counters`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `counter_notes` (`id`, `counter_id`, `value`, `note`, `created_at`)
            SELECT `id`, `counter_id`, `value`, `note`, `created_at`
            FROM `counter_notes_old`
            """.trimIndent()
        )

        // 4. Now safe to drop both temporary copies: nothing references
        // counters_old or counter_notes_old anymore.
        db.execSQL("DROP TABLE `counter_notes_old`")
        db.execSQL("DROP TABLE `counters_old`")

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_counters_project_id` " +
                "ON `counters` (`project_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_counters_linked_counter_id` " +
                "ON `counters` (`linked_counter_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_counter_notes_counter_id` " +
                "ON `counter_notes` (`counter_id`)"
        )
    }
}

/**
 * Adds automatic-reset-on-goal to Counters (PRODUCT_SPEC.md 6.3,
 * "Automatic reset rules"): the common "row counter resets each repeat"
 * pattern, where reaching a goal via increment resets a counter back to 0
 * in the same step. Unlike the previous migration, this is a plain
 * non-foreign-key column, so a simple `ALTER TABLE ADD COLUMN` suffices --
 * no table recreation needed. `NOT NULL DEFAULT 0` backfills every
 * existing row to "off", since auto-reset didn't exist before this
 * migration.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `counters` ADD COLUMN `auto_reset_on_goal` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * Adds a repeating reset schedule to Counters (PRODUCT_SPEC.md 6.3,
 * "Repeating schedules"): a counter can reset itself every N days. Both
 * new columns are plain nullable columns, so -- like MIGRATION_9_10, and
 * unlike the link column's migration -- a simple `ALTER TABLE ADD COLUMN`
 * suffices; no table recreation needed.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `counters` ADD COLUMN `repeat_interval_days` INTEGER")
        db.execSQL("ALTER TABLE `counters` ADD COLUMN `last_repeat_reset_at` INTEGER")
    }
}

/**
 * A brand-new, standalone table -- no foreign key to any existing table, so
 * this is the simplest kind of migration (see MIGRATION_8_9's KDoc for the
 * contrasting case where an FK column addition forced a table recreation).
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tool_templates` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `brand` TEXT,
                `material` TEXT,
                `size_input_mode` TEXT NOT NULL,
                `range_start` REAL,
                `range_end` REAL,
                `range_increment` REAL,
                `custom_sizes` TEXT,
                `quantity_per_size` INTEGER NOT NULL,
                `storage_location` TEXT,
                `notes` TEXT,
                `create_as_set` INTEGER NOT NULL,
                `set_name` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

/**
 * The first genuine many-to-many junction table in this schema
 * (ARCHITECTURE.md §9's "explicit join entities" for projects-tools): a
 * pure membership record with a composite primary key, both FKs cascading
 * since a join row means nothing once either side it links is gone --
 * unlike `tool_items.set_id`'s SET_NULL (a set is a label over inventory,
 * not ownership). No table recreation needed since both parent tables
 * already exist unchanged; this is purely additive, like MIGRATION_11_12.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `project_tool_assignments` (
                `project_id` TEXT NOT NULL,
                `tool_item_id` TEXT NOT NULL,
                PRIMARY KEY(`project_id`, `tool_item_id`),
                FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`tool_item_id`) REFERENCES `tool_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_project_tool_assignments_tool_item_id`
            ON `project_tool_assignments` (`tool_item_id`)
            """.trimIndent()
        )
    }
}

/**
 * Six plain nullable columns, none of them foreign keys, so a simple
 * `ALTER TABLE ADD COLUMN` per field suffices -- `stash_items` has never
 * been touched by a migration since MIGRATION_3_4 created it, so this is
 * its first schema change (PRODUCT_SPEC.md 6.7: storage location, care
 * instructions, Ravelry yarn ID, and purchase information).
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `stash_items` ADD COLUMN `storage_location` TEXT")
        db.execSQL("ALTER TABLE `stash_items` ADD COLUMN `care_instructions` TEXT")
        db.execSQL("ALTER TABLE `stash_items` ADD COLUMN `ravelry_yarn_id` TEXT")
        db.execSQL("ALTER TABLE `stash_items` ADD COLUMN `purchase_source` TEXT")
        db.execSQL("ALTER TABLE `stash_items` ADD COLUMN `purchase_price` REAL")
        db.execSQL("ALTER TABLE `stash_items` ADD COLUMN `purchase_date` TEXT")
    }
}
