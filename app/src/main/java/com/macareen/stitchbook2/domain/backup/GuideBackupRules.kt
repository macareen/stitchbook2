package com.macareen.stitchbook2.domain.backup

/*
 * Validation and MERGE selection for the guide graph. The graph restores as
 * a unit per guide: a guide's draft, revisions, and progress only make sense
 * together (a draft builds on a revision, progress is pinned to a revision,
 * an active pointer names one execution), so MERGE never mixes a file's
 * children into a guide that already exists on the device.
 */

/**
 * Problems in the file's guide graph that would break a foreign key or the
 * guide and execution models. [projects] and [libraryItems] are the IDs
 * that will exist after the restore. In MERGE a guide, execution, or link
 * whose project or pattern is missing is not an error: [planGuideMerge]
 * clears or skips it and reports a notice instead.
 */
internal fun validateGuides(
    incoming: BackupSnapshot,
    existing: BackupSnapshot,
    mode: RestoreMode,
    projects: Set<String>,
    libraryItems: Set<String>
): List<BackupIssue> {
    val issues = mutableListOf<BackupIssue>()
    val carried = GUIDE_GRAPH_TYPES.filter { incoming.keysOf(it) != null }
    if (carried.isEmpty()) return issues
    if (carried.size != GUIDE_GRAPH_TYPES.size) {
        // Partial graphs can't be restored safely: replacing revisions without
        // their executions would orphan progress pinned to them.
        issues += BackupIssue(
            BackupIssueKind.INVALID_VALUE,
            null,
            null,
            "Guides, drafts, revisions, progress, and project guide links must all be in the file, or none of them."
        )
        return issues
    }
    val graph = checkNotNull(incoming.guideGraph())
    val strict = mode == RestoreMode.REPLACE

    fun invalid(type: BackupRecordType, key: String, detail: String) {
        issues += BackupIssue(BackupIssueKind.INVALID_VALUE, type, key, detail)
    }

    fun missing(type: BackupRecordType, key: String, target: String, reference: String) {
        issues += BackupIssue(BackupIssueKind.MISSING_REFERENCE, type, key, "Refers to $target \"$reference\", which isn't in the library or the file.")
    }

    val guideIds = graph.guides.map { it.id }.toSet()
    graph.guides.forEach { guide ->
        if (strict) {
            guide.projectId?.takeIf { it !in projects }?.let { missing(BackupRecordType.GUIDES, guide.id, "project", it) }
            guide.libraryItemId?.takeIf { it !in libraryItems }?.let { missing(BackupRecordType.GUIDES, guide.id, "pattern", it) }
        }
    }

    // Child rows must belong to a guide in the file: they are restored with it.
    fun requireGuide(type: BackupRecordType, key: String, guideId: String) {
        if (guideId !in guideIds) missing(type, key, "guide", guideId)
    }

    // An ID already used on this device by another guide's row would collide on insert.
    val localGuideOf = buildMap {
        existing.guideDrafts.orEmpty().forEach { put(BackupRecordType.GUIDE_DRAFTS to it.id, it.guideId) }
        existing.guideRevisions.orEmpty().forEach { put(BackupRecordType.GUIDE_REVISIONS to it.id, it.guideId) }
        existing.executions.orEmpty().forEach { put(BackupRecordType.EXECUTIONS to it.id, it.guideId) }
    }
    fun requireSameGuideLocally(type: BackupRecordType, id: String, guideId: String) {
        val localGuide = localGuideOf[type to id]
        if (localGuide != null && localGuide != guideId) {
            invalid(type, id, "Belongs to a different guide (\"$localGuide\") on this device.")
        }
    }

    val revisionsById = graph.revisions.associateBy { it.id }
    graph.revisions.forEach { revision ->
        requireGuide(BackupRecordType.GUIDE_REVISIONS, revision.id, revision.guideId)
        requireSameGuideLocally(BackupRecordType.GUIDE_REVISIONS, revision.id, revision.guideId)
        if (revision.revisionNumber < 1) invalid(BackupRecordType.GUIDE_REVISIONS, revision.id, "Revision number must be 1 or more.")
        nodeTreeProblems(revision.nodes).forEach { invalid(BackupRecordType.GUIDE_REVISIONS, revision.id, it) }
    }
    graph.revisions.groupBy { it.guideId to it.revisionNumber }.filterValues { it.size > 1 }.forEach { (key, duplicates) ->
        duplicates.forEach { invalid(BackupRecordType.GUIDE_REVISIONS, it.id, "Guide \"${key.first}\" has more than one revision ${key.second}.") }
    }

    graph.drafts.forEach { draft ->
        requireGuide(BackupRecordType.GUIDE_DRAFTS, draft.id, draft.guideId)
        requireSameGuideLocally(BackupRecordType.GUIDE_DRAFTS, draft.id, draft.guideId)
        draft.baseRevisionId?.let { baseId ->
            if (revisionsById[baseId]?.guideId != draft.guideId) {
                invalid(BackupRecordType.GUIDE_DRAFTS, draft.id, "Builds on revision \"$baseId\", which isn't a revision of the same guide in the file.")
            }
        }
        nodeTreeProblems(draft.nodes).forEach { invalid(BackupRecordType.GUIDE_DRAFTS, draft.id, it) }
    }
    graph.drafts.groupBy { it.guideId }.filterValues { it.size > 1 }.forEach { (guideId, duplicates) ->
        duplicates.forEach { invalid(BackupRecordType.GUIDE_DRAFTS, it.id, "Guide \"$guideId\" has more than one draft.") }
    }

    val executionsById = graph.executions.associateBy { it.id }
    graph.executions.forEach { execution ->
        val key = execution.id
        requireGuide(BackupRecordType.EXECUTIONS, key, execution.guideId)
        requireSameGuideLocally(BackupRecordType.EXECUTIONS, key, execution.guideId)
        if (revisionsById[execution.definitionRevisionId]?.guideId != execution.guideId) {
            invalid(BackupRecordType.EXECUTIONS, key, "Follows revision \"${execution.definitionRevisionId}\", which isn't a revision of the same guide in the file.")
        }
        if (strict) execution.projectId?.takeIf { it !in projects }?.let { missing(BackupRecordType.EXECUTIONS, key, "project", it) }
        if (execution.status !in EXECUTION_STATUSES) {
            invalid(BackupRecordType.EXECUTIONS, key, "Unknown status \"${execution.status}\".")
        } else if ((execution.status == "COMPLETED") != (execution.currentInstructionNodeId == null)) {
            invalid(BackupRecordType.EXECUTIONS, key, "Only finished progress may have no current step.")
        }
        val frames = execution.currentFrames + execution.completedOccurrences.flatMap { it.frames }
        frames.map { it.frameType }.filter { it !in ADDRESS_FRAME_TYPES }.toSet().forEach {
            invalid(BackupRecordType.EXECUTIONS, key, "Unknown position frame \"$it\".")
        }
        if (execution.completedOccurrences.map { it.addressSignature }.toSet().size != execution.completedOccurrences.size) {
            invalid(BackupRecordType.EXECUTIONS, key, "Lists the same finished step more than once.")
        }
    }

    graph.activeExecutions.forEach { pointer ->
        val key = activeExecutionKey(pointer.guideId, pointer.projectKey)
        requireGuide(BackupRecordType.ACTIVE_EXECUTIONS, key, pointer.guideId)
        val execution = executionsById[pointer.executionId]
        when {
            execution == null -> missing(BackupRecordType.ACTIVE_EXECUTIONS, key, "progress", pointer.executionId)
            execution.guideId != pointer.guideId || execution.projectId.orEmpty() != pointer.projectKey ->
                invalid(BackupRecordType.ACTIVE_EXECUTIONS, key, "Points to progress for a different guide or project.")
            execution.status != "ACTIVE" -> invalid(BackupRecordType.ACTIVE_EXECUTIONS, key, "Points to finished progress.")
        }
    }
    graph.activeExecutions.groupBy { it.executionId }.filterValues { it.size > 1 }.forEach { (executionId, pointers) ->
        pointers.forEach {
            invalid(BackupRecordType.ACTIVE_EXECUTIONS, activeExecutionKey(it.guideId, it.projectKey), "Progress \"$executionId\" is resumed from more than one place.")
        }
    }

    graph.projectGuides.forEach { link ->
        val key = "${link.projectId}/${link.guideId}"
        if (strict) {
            if (link.projectId !in projects) missing(BackupRecordType.PROJECT_GUIDES, key, "project", link.projectId)
            if (link.guideId !in guideIds) missing(BackupRecordType.PROJECT_GUIDES, key, "guide", link.guideId)
        }
    }
    return issues
}

/** Structural problems in one draft or revision node tree. */
private fun nodeTreeProblems(nodes: List<GuideNodeRecord>): List<String> {
    val problems = mutableListOf<String>()
    val ids = nodes.map { it.nodeId }
    ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { problems += "Step \"$it\" appears more than once." }
    val idSet = ids.toSet()
    nodes.forEach { node ->
        if (node.type !in GUIDE_NODE_TYPES) problems += "Step \"${node.nodeId}\" has unknown type \"${node.type}\"."
        if (node.parentNodeId != null && node.parentNodeId !in idSet) {
            problems += "Step \"${node.nodeId}\" sits under \"${node.parentNodeId}\", which isn't in the same guide."
        }
    }
    return problems
}

/** What a MERGE writes for the guide graph, and what it changes or leaves out. */
data class GuideMergePlan(
    val graph: GuideBackupGraph,
    val notices: List<BackupNotice>
)

/**
 * Selects the guide rows a MERGE writes. Only guides that don't exist on
 * the device are added, each with its draft, revisions, and progress.
 *
 * - A new guide whose project or pattern exists in neither the file nor
 *   the device keeps the guide with that link cleared (both columns are
 *   nullable), and says so.
 * - Progress for a missing project moves to the guide on its own (project
 *   key ""). If that leaves two active pointers on the same key, the most
 *   recently updated progress stays resumable and the other is kept but
 *   reported.
 * - Rows of a guide that already exists on the device are left out; those
 *   the device doesn't already hold are reported (differing ones are
 *   already counted as conflicts).
 * - Project-guide links whose project or guide won't exist are reported
 *   and skipped: a link has nothing to keep once one side is missing.
 *
 * Returns null when the file carries no guides.
 */
fun planGuideMerge(incoming: BackupSnapshot, existing: BackupSnapshot): GuideMergePlan? {
    val graph = incoming.guideGraph() ?: return null
    val notices = mutableListOf<BackupNotice>()
    fun notice(type: BackupRecordType, key: String, detail: String) {
        notices += BackupNotice(type, key, detail)
    }

    val projects = incoming.projects.orEmpty().map { it.id }.toSet() + existing.projects.orEmpty().map { it.id }
    val libraryItems = incoming.libraryItems.orEmpty().map { it.id }.toSet() + existing.libraryItems.orEmpty().map { it.id }
    val localGuides = existing.guides.orEmpty().map { it.id }.toSet()

    val newGuides = graph.guides.filter { it.id !in localGuides }.map { guide ->
        var kept = guide
        if (guide.projectId != null && guide.projectId !in projects) {
            kept = kept.copy(projectId = null)
            notice(BackupRecordType.GUIDES, guide.id, "Its project \"${guide.projectId}\" isn't in the file or on this device, so the guide was restored without it.")
        }
        if (guide.libraryItemId != null && guide.libraryItemId !in libraryItems) {
            kept = kept.copy(libraryItemId = null)
            notice(BackupRecordType.GUIDES, guide.id, "Its pattern \"${guide.libraryItemId}\" isn't in the file or on this device, so the guide was restored without it.")
        }
        kept
    }
    val newGuideIds = newGuides.map { it.id }.toSet()

    fun <T> List<T>.keptOrReported(type: BackupRecordType, localKeys: Set<String>, key: (T) -> String, guideOf: (T) -> String): List<T> {
        val (added, skipped) = partition { guideOf(it) in newGuideIds }
        skipped.filter { key(it) !in localKeys }.forEach {
            notice(type, key(it), "Not restored: its guide \"${guideOf(it)}\" stays as it is on this device.")
        }
        return added
    }

    val drafts = graph.drafts.keptOrReported(
        BackupRecordType.GUIDE_DRAFTS, existing.guideDrafts.orEmpty().map { it.id }.toSet(), { it.id }, { it.guideId }
    )
    val revisions = graph.revisions.keptOrReported(
        BackupRecordType.GUIDE_REVISIONS, existing.guideRevisions.orEmpty().map { it.id }.toSet(), { it.id }, { it.guideId }
    )
    val executions = graph.executions.keptOrReported(
        BackupRecordType.EXECUTIONS, existing.executions.orEmpty().map { it.id }.toSet(), { it.id }, { it.guideId }
    ).map { execution ->
        if (execution.projectId != null && execution.projectId !in projects) {
            notice(BackupRecordType.EXECUTIONS, execution.id, "Its project \"${execution.projectId}\" isn't in the file or on this device, so this progress now belongs to the guide on its own.")
            execution.copy(projectId = null)
        } else {
            execution
        }
    }
    val executionsById = executions.associateBy { it.id }

    val pointers = graph.activeExecutions.keptOrReported(
        BackupRecordType.ACTIVE_EXECUTIONS,
        existing.activeExecutions.orEmpty().map { activeExecutionKey(it.guideId, it.projectKey) }.toSet(),
        { activeExecutionKey(it.guideId, it.projectKey) },
        { it.guideId }
    ).map { pointer ->
        // Follows its execution if the execution's project was cleared above.
        pointer.copy(projectKey = executionsById[pointer.executionId]?.projectId.orEmpty())
    }
    val activeExecutions = pointers.groupBy { it.guideId to it.projectKey }.map { (_, sameKey) ->
        val resumed = sameKey.maxBy { executionsById[it.executionId]?.updatedAt ?: Long.MIN_VALUE }
        sameKey.filter { it != resumed }.forEach {
            notice(
                BackupRecordType.EXECUTIONS,
                it.executionId,
                "Kept, but no longer the progress resumed for guide \"${it.guideId}\": more recent progress now takes that place."
            )
        }
        resumed
    }

    val guidesAfter = localGuides + newGuideIds
    val localLinks = existing.projectGuides.orEmpty().toSet()
    val projectGuides = graph.projectGuides.filter { it !in localLinks }.filter { link ->
        val key = "${link.projectId}/${link.guideId}"
        when {
            link.projectId !in projects -> {
                notice(BackupRecordType.PROJECT_GUIDES, key, "Not restored: project \"${link.projectId}\" isn't in the file or on this device.")
                false
            }
            link.guideId !in guidesAfter -> {
                notice(BackupRecordType.PROJECT_GUIDES, key, "Not restored: guide \"${link.guideId}\" isn't in the file or on this device.")
                false
            }
            else -> true
        }
    }

    return GuideMergePlan(
        graph = GuideBackupGraph(
            guides = newGuides,
            drafts = drafts,
            revisions = revisions,
            executions = executions,
            activeExecutions = activeExecutions,
            projectGuides = projectGuides
        ),
        notices = notices
    )
}
