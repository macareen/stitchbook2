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
        CounterEntity::class
    ],
    version = 7,
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
                    MIGRATION_6_7
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
