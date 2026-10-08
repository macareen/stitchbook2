package com.macareen.stitchbook2.domain.backup

/**
 * The versioned, portable library format (ROADMAP.md Phase 10; format
 * documented in ARCHITECTURE.md "Backup format"). Covers Projects,
 * Library, Stash, Tools (sets, items, templates, project assignments),
 * Counters and notes, yarn allocations, pattern links, milestones, photo
 * references, journal entries, and crafting sessions.
 *
 * Guides/Drafts/Revisions/Executions are intentionally out of scope --
 * round-tripping that relational graph safely (immutable revisions, pinned
 * executions, optimistic-concurrency versions) needs its own design.
 */
interface BackupService {
    suspend fun exportJson(): String

    /** One project and everything that hangs off it, plus the stash items and patterns it references. */
    suspend fun exportProjectJson(projectId: String): String?

    /** A human-readable Markdown record of one project (details, milestones, journal, time, materials). */
    suspend fun exportProjectMarkdown(projectId: String): String?

    /** Parses, validates, and compares a file with the library without writing anything. */
    suspend fun previewImport(json: String): BackupPreview

    /**
     * Validates first and writes nothing unless the whole file is valid.
     * [RestoreMode.MERGE] only adds records that don't exist yet.
     */
    suspend fun importJson(json: String, mode: RestoreMode = RestoreMode.REPLACE): BackupImportResult

    suspend fun resetAllData()
}

sealed interface BackupImportResult {
    data class Success(
        val projectCount: Int?,
        val libraryItemCount: Int?,
        val stashItemCount: Int?,
        val toolSetCount: Int?,
        val toolItemCount: Int?,
        val counterCount: Int?,
        val counterNoteCount: Int?,
        /** Records actually written per type (MERGE skips identical and conflicting ones). */
        val written: Map<BackupRecordType, Int> = emptyMap(),
        /** Records left untouched because the local copy differs (MERGE only). */
        val conflictsKept: Int = 0,
        /** Display names (or URIs) of referenced PDFs/photos this device can't open -- relink these. */
        val missingFiles: List<String> = emptyList()
    ) : BackupImportResult

    data object InvalidFormat : BackupImportResult

    data class ValidationFailed(val issues: List<BackupIssue>) : BackupImportResult
}
