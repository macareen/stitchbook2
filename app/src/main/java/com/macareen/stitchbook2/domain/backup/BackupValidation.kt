package com.macareen.stitchbook2.domain.backup

const val CURRENT_BACKUP_FORMAT_VERSION = 3

/**
 * Checks a parsed backup *before* anything is written (PRODUCT_SPEC.md
 * 6.11: "validate backups before reporting success"). References are
 * resolved against what the library will contain after the restore: the
 * file's own records for types it carries (plus local ones in MERGE), and
 * the untouched local records for types it doesn't.
 */
fun validateBackup(incoming: BackupSnapshot, existing: BackupSnapshot, mode: RestoreMode): List<BackupIssue> {
    val issues = mutableListOf<BackupIssue>()
    if (incoming.formatVersion > CURRENT_BACKUP_FORMAT_VERSION) {
        issues += BackupIssue(
            BackupIssueKind.NEWER_FORMAT,
            null,
            null,
            "Format ${incoming.formatVersion} is newer than this app supports ($CURRENT_BACKUP_FORMAT_VERSION)."
        )
        return issues
    }

    BackupRecordType.entries.forEach { type ->
        incoming.keysOf(type)?.groupingBy { it }?.eachCount()?.filterValues { it > 1 }?.keys?.forEach { key ->
            issues += BackupIssue(BackupIssueKind.DUPLICATE_ID, type, key, "Appears more than once.")
        }
    }

    fun ids(type: BackupRecordType): Set<String> {
        val fromFile = incoming.keysOf(type)?.toSet()
        val local = existing.keysOf(type).orEmpty().toSet()
        return when {
            fromFile == null -> local
            mode == RestoreMode.MERGE -> fromFile + local
            else -> fromFile
        }
    }

    val projects by lazy { ids(BackupRecordType.PROJECTS) }
    val library by lazy { ids(BackupRecordType.LIBRARY_ITEMS) }
    val stash by lazy { ids(BackupRecordType.STASH_ITEMS) }
    val toolSets by lazy { ids(BackupRecordType.TOOL_SETS) }
    val toolItems by lazy { ids(BackupRecordType.TOOL_ITEMS) }
    val counters by lazy { ids(BackupRecordType.COUNTERS) }
    val milestones by lazy { ids(BackupRecordType.MILESTONES) }

    fun requireRef(type: BackupRecordType, key: String, target: String, reference: String?, valid: Set<String>) {
        if (reference != null && reference !in valid) {
            issues += BackupIssue(BackupIssueKind.MISSING_REFERENCE, type, key, "Refers to $target \"$reference\", which isn't in the library or the file.")
        }
    }

    fun invalid(type: BackupRecordType, key: String, detail: String) {
        issues += BackupIssue(BackupIssueKind.INVALID_VALUE, type, key, detail)
    }

    incoming.stashItems?.forEach { if (it.quantity < 0) invalid(BackupRecordType.STASH_ITEMS, it.id, "Quantity is negative.") }
    incoming.toolItems?.forEach {
        requireRef(BackupRecordType.TOOL_ITEMS, it.id, "tool set", it.setId, toolSets)
        if (it.quantity < 0) invalid(BackupRecordType.TOOL_ITEMS, it.id, "Quantity is negative.")
    }
    incoming.toolAssignments?.forEach {
        val key = "${it.projectId}/${it.toolItemId}"
        requireRef(BackupRecordType.TOOL_ASSIGNMENTS, key, "project", it.projectId, projects)
        requireRef(BackupRecordType.TOOL_ASSIGNMENTS, key, "tool", it.toolItemId, toolItems)
    }
    incoming.counters?.forEach { requireRef(BackupRecordType.COUNTERS, it.id, "project", it.projectId, projects) }
    incoming.counterNotes?.forEach { requireRef(BackupRecordType.COUNTER_NOTES, it.id, "counter", it.counterId, counters) }
    incoming.yarnAllocations?.forEach {
        requireRef(BackupRecordType.YARN_ALLOCATIONS, it.id, "project", it.projectId, projects)
        requireRef(BackupRecordType.YARN_ALLOCATIONS, it.id, "stash item", it.stashItemId, stash)
        if (it.quantityReserved < 0 || it.quantityUsed < 0) {
            invalid(BackupRecordType.YARN_ALLOCATIONS, it.id, "Quantities can't be negative.")
        }
    }
    incoming.patternLinks?.forEach {
        val key = "${it.projectId}/${it.libraryItemId}"
        requireRef(BackupRecordType.PATTERN_LINKS, key, "project", it.projectId, projects)
        requireRef(BackupRecordType.PATTERN_LINKS, key, "pattern", it.libraryItemId, library)
    }
    incoming.milestones?.forEach { requireRef(BackupRecordType.MILESTONES, it.id, "project", it.projectId, projects) }
    incoming.photos?.forEach {
        requireRef(BackupRecordType.PHOTOS, it.id, "project", it.projectId, projects)
        requireRef(BackupRecordType.PHOTOS, it.id, "stash item", it.stashItemId, stash)
        requireRef(BackupRecordType.PHOTOS, it.id, "milestone", it.milestoneId, milestones)
    }
    incoming.journalEntries?.forEach { requireRef(BackupRecordType.JOURNAL_ENTRIES, it.id, "project", it.projectId, projects) }
    incoming.sessions?.forEach {
        requireRef(BackupRecordType.SESSIONS, it.id, "project", it.projectId, projects)
        if (it.endedAt != null && it.endedAt < it.startedAt) invalid(BackupRecordType.SESSIONS, it.id, "Ends before it starts.")
    }
    issues += validateGuides(incoming, existing, mode, projects, library)
    return issues
}

/** Compares a file with the current library, type by type, without changing anything. */
fun compareBackup(incoming: BackupSnapshot, existing: BackupSnapshot): Pair<Map<BackupRecordType, TypeComparison>, List<BackupConflict>> {
    val conflicts = mutableListOf<BackupConflict>()
    val comparison = buildMap {
        BackupRecordType.entries.forEach { type ->
            val fileRecords = incoming.recordsOf(type) ?: return@forEach
            val localRecords = existing.recordsOf(type).orEmpty()
            var new = 0
            var identical = 0
            fileRecords.forEach { (key, record) ->
                when (localRecords[key]) {
                    null -> new++
                    record -> identical++
                    else -> conflicts += BackupConflict(type, key)
                }
            }
            put(
                type,
                TypeComparison(
                    incoming = fileRecords.size,
                    new = new,
                    identical = identical,
                    conflicting = conflicts.count { it.type == type },
                    onlyLocal = localRecords.keys.count { it !in fileRecords }
                )
            )
        }
    }
    return comparison to conflicts
}

/**
 * The subset of [incoming] a MERGE writes: only records whose identity
 * doesn't exist locally. Identical records need no write and conflicting
 * ones are left exactly as they are locally ("never silently overwrite").
 * The guide graph merges a whole guide at a time; see [planGuideMerge].
 */
fun mergeAdditions(incoming: BackupSnapshot, existing: BackupSnapshot): BackupSnapshot {
    fun <T> List<T>?.onlyNew(type: BackupRecordType, key: (T) -> String): List<T>? {
        val local = existing.keysOf(type).orEmpty().toSet()
        return this?.filter { key(it) !in local }
    }
    return BackupSnapshot(
        formatVersion = incoming.formatVersion,
        projects = incoming.projects.onlyNew(BackupRecordType.PROJECTS) { it.id },
        libraryItems = incoming.libraryItems.onlyNew(BackupRecordType.LIBRARY_ITEMS) { it.id },
        stashItems = incoming.stashItems.onlyNew(BackupRecordType.STASH_ITEMS) { it.id },
        toolSets = incoming.toolSets.onlyNew(BackupRecordType.TOOL_SETS) { it.id },
        toolItems = incoming.toolItems.onlyNew(BackupRecordType.TOOL_ITEMS) { it.id },
        toolTemplates = incoming.toolTemplates.onlyNew(BackupRecordType.TOOL_TEMPLATES) { it.id },
        toolAssignments = incoming.toolAssignments.onlyNew(BackupRecordType.TOOL_ASSIGNMENTS) { "${it.projectId}/${it.toolItemId}" },
        counters = incoming.counters.onlyNew(BackupRecordType.COUNTERS) { it.id },
        counterNotes = incoming.counterNotes.onlyNew(BackupRecordType.COUNTER_NOTES) { it.id },
        yarnAllocations = incoming.yarnAllocations.onlyNew(BackupRecordType.YARN_ALLOCATIONS) { it.id },
        patternLinks = incoming.patternLinks.onlyNew(BackupRecordType.PATTERN_LINKS) { "${it.projectId}/${it.libraryItemId}" },
        milestones = incoming.milestones.onlyNew(BackupRecordType.MILESTONES) { it.id },
        photos = incoming.photos.onlyNew(BackupRecordType.PHOTOS) { it.id },
        journalEntries = incoming.journalEntries.onlyNew(BackupRecordType.JOURNAL_ENTRIES) { it.id },
        sessions = incoming.sessions.onlyNew(BackupRecordType.SESSIONS) { it.id }
    ).withGuideGraph(planGuideMerge(incoming, existing)?.graph)
}
