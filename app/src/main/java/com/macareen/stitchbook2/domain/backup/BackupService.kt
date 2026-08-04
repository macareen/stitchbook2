package com.macareen.stitchbook2.domain.backup

/**
 * Portable JSON export/import over the app's flat, already-domain-modeled
 * data: Projects, Library items, Stash items, and Tool sets/items.
 *
 * Guides/Drafts/Revisions/Executions are intentionally out of scope for this
 * first version -- round-tripping that relational graph safely (immutable
 * revisions, pinned executions, optimistic-concurrency versions) needs its
 * own dedicated design rather than being folded into a generic backup
 * format. See ARCHITECTURE.md's data-ownership section.
 */
interface BackupService {
    suspend fun exportJson(): String

    suspend fun importJson(json: String): BackupImportResult

    suspend fun resetAllData()
}

sealed interface BackupImportResult {
    data class Success(
        val projectCount: Int?,
        val libraryItemCount: Int?,
        val stashItemCount: Int?,
        val toolSetCount: Int?,
        val toolItemCount: Int?
    ) : BackupImportResult

    data object InvalidFormat : BackupImportResult
}
