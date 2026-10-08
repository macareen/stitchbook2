package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.BackupImportResult
import com.macareen.stitchbook2.domain.backup.BackupPreview
import com.macareen.stitchbook2.domain.backup.BackupRecordType
import com.macareen.stitchbook2.domain.backup.BackupService
import com.macareen.stitchbook2.domain.backup.BackupSnapshot
import com.macareen.stitchbook2.domain.backup.CURRENT_BACKUP_FORMAT_VERSION
import com.macareen.stitchbook2.domain.backup.GuideBackupGraph
import com.macareen.stitchbook2.domain.backup.GuideBackupStore
import com.macareen.stitchbook2.domain.backup.RestoreMode
import com.macareen.stitchbook2.domain.backup.planGuideMerge
import com.macareen.stitchbook2.domain.backup.ToolAssignment
import com.macareen.stitchbook2.domain.backup.compareBackup
import com.macareen.stitchbook2.domain.backup.mergeAdditions
import com.macareen.stitchbook2.domain.backup.validateBackup
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.wouldCreateCycle
import com.macareen.stitchbook2.domain.repository.CounterNoteRepository
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.JournalRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.repository.MaterialsRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.SessionRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
import com.macareen.stitchbook2.domain.repository.ToolRepository
import kotlinx.coroutines.flow.first
import org.json.JSONException

/**
 * Orchestrates export, validation, comparison, and restore over the
 * repositories. JSON shape lives in BackupJsonCodec.kt; the rules
 * (validation, merge selection) are pure functions in domain/backup.
 *
 * The newer repositories and the guide store are optional so a partial
 * service (as in tests) simply doesn't carry those record types.
 */
class LocalBackupService(
    private val projectRepository: ProjectRepository,
    private val libraryRepository: LibraryRepository,
    private val stashRepository: StashRepository,
    private val toolRepository: ToolRepository,
    private val counterRepository: CounterRepository,
    private val counterNoteRepository: CounterNoteRepository,
    private val materialsRepository: MaterialsRepository? = null,
    private val journalRepository: JournalRepository? = null,
    private val sessionRepository: SessionRepository? = null,
    private val guideBackupStore: GuideBackupStore? = null,
    /** Whether a referenced `content://` file can currently be opened; injected so tests need no Android. */
    private val isFileAccessible: suspend (String) -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis
) : BackupService {

    override suspend fun exportJson(): String = encodeBackup(loadSnapshot(), clock())

    override suspend fun exportProjectJson(projectId: String): String? {
        val all = loadSnapshot()
        val project = all.projects.orEmpty().firstOrNull { it.id == projectId } ?: return null
        val allocations = all.yarnAllocations?.filter { it.projectId == projectId }
        val links = all.patternLinks?.filter { it.projectId == projectId }
        val assignments = all.toolAssignments?.filter { it.projectId == projectId }
        val stashIds = allocations.orEmpty().map { it.stashItemId }.toSet()
        val libraryIds = links.orEmpty().map { it.libraryItemId }.toSet()
        val toolIds = assignments.orEmpty().map { it.toolItemId }.toSet()
        val toolItems = all.toolItems?.filter { it.id in toolIds }
        val setIds = toolItems.orEmpty().mapNotNull { it.setId }.toSet()
        val counters = all.counters?.filter { it.projectId == projectId }
            ?.map { counter ->
                // A link to another project's counter can't travel with this file.
                if (counters(all).any { it.id == counter.linkedCounterId && it.projectId == projectId }) counter
                else counter.copy(linkedCounterId = null, linkIncrementInterval = null, linkIncrementAmount = null)
            }
        val counterIds = counters.orEmpty().map { it.id }.toSet()
        val subset = BackupSnapshot(
            formatVersion = CURRENT_BACKUP_FORMAT_VERSION,
            projects = listOf(project),
            libraryItems = all.libraryItems?.filter { it.id in libraryIds },
            stashItems = all.stashItems?.filter { it.id in stashIds },
            toolSets = all.toolSets?.filter { it.id in setIds },
            toolItems = toolItems,
            toolAssignments = assignments,
            counters = counters,
            counterNotes = all.counterNotes?.filter { it.counterId in counterIds },
            yarnAllocations = allocations,
            patternLinks = links,
            milestones = all.milestones?.filter { it.projectId == projectId },
            photos = all.photos?.filter { it.projectId == projectId },
            journalEntries = all.journalEntries?.filter { it.projectId == projectId },
            sessions = all.sessions?.filter { it.projectId == projectId }
        )
        return encodeBackup(subset, clock())
    }

    override suspend fun exportProjectMarkdown(projectId: String): String? {
        val all = loadSnapshot()
        val project = all.projects.orEmpty().firstOrNull { it.id == projectId } ?: return null
        return projectMarkdown(project, all, clock())
    }

    override suspend fun previewImport(json: String): BackupPreview {
        val incoming = parse(json) ?: return BackupPreview.Unreadable
        val existing = loadSnapshot()
        // Show issues for the stricter REPLACE view; MERGE additionally accepts references to local records.
        val issues = validateBackup(incoming, existing, RestoreMode.MERGE)
        if (issues.isNotEmpty()) return BackupPreview.Invalid(issues)
        val (comparison, conflicts) = compareBackup(incoming, existing)
        val mergeNotices = planGuideMerge(incoming, existing)?.notices.orEmpty()
        return BackupPreview.Ready(incoming.formatVersion, comparison, conflicts, mergeNotices)
    }

    override suspend fun importJson(json: String, mode: RestoreMode): BackupImportResult {
        val incoming = parse(json) ?: return BackupImportResult.InvalidFormat
        val existing = loadSnapshot()
        val issues = validateBackup(incoming, existing, mode)
        if (issues.isNotEmpty()) return BackupImportResult.ValidationFailed(issues)

        val toWrite = if (mode == RestoreMode.MERGE) mergeAdditions(incoming, existing) else incoming
        val conflictsKept = if (mode == RestoreMode.MERGE) compareBackup(incoming, existing).second.size else 0
        val notices = if (mode == RestoreMode.MERGE) planGuideMerge(incoming, existing)?.notices.orEmpty() else emptyList()
        write(toWrite, existing, mode)

        return BackupImportResult.Success(
            projectCount = incoming.projects?.size,
            libraryItemCount = incoming.libraryItems?.size,
            stashItemCount = incoming.stashItems?.size,
            toolSetCount = incoming.toolSets?.size,
            toolItemCount = incoming.toolItems?.size,
            counterCount = incoming.counters?.size,
            counterNoteCount = incoming.counterNotes?.size,
            written = toWrite.counts(),
            conflictsKept = conflictsKept,
            missingFiles = missingFiles(incoming),
            notices = notices
        )
    }

    override suspend fun resetAllData() {
        // Guides go first and all at once: pattern guides belong to no project, so
        // deleting projects alone would leave them (and their progress) behind.
        guideBackupStore?.replace(GuideBackupGraph())
        projectRepository.observeProjects().first().forEach { projectRepository.deleteProject(it) }
        libraryRepository.observeLibraryItems().first().forEach { libraryRepository.deleteLibraryItem(it) }
        stashRepository.observeStashItems().first().forEach { stashRepository.deleteStashItem(it) }
        toolRepository.observeToolItems().first().forEach { toolRepository.deleteToolItem(it) }
        toolRepository.observeToolSets().first().forEach { toolRepository.deleteToolSet(it) }
        counterNoteRepository.observeNotes().first().forEach { counterNoteRepository.deleteNote(it) }
        counterRepository.observeCounters().first().forEach { counterRepository.deleteCounter(it) }
        // Allocations, links, milestones, photos, and journal entries cascade
        // with their projects/stash items; sessions are SET_NULL, so clear them.
        sessionRepository?.let { sessions -> sessions.observeSessions().first().forEach { sessions.deleteSession(it) } }
        journalRepository?.let { journal -> journal.observePhotos().first().forEach { journal.deletePhoto(it) } }
    }

    private fun parse(json: String): BackupSnapshot? = try {
        decodeBackup(json)
    } catch (_: JSONException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun counters(snapshot: BackupSnapshot): List<Counter> = snapshot.counters.orEmpty()

    private suspend fun loadSnapshot(): BackupSnapshot {
        val toolItems = toolRepository.observeToolItems().first()
        return BackupSnapshot(
            formatVersion = CURRENT_BACKUP_FORMAT_VERSION,
            projects = projectRepository.observeProjects().first(),
            libraryItems = libraryRepository.observeLibraryItems().first(),
            stashItems = stashRepository.observeStashItems().first(),
            toolSets = toolRepository.observeToolSets().first(),
            toolItems = toolItems,
            toolTemplates = toolRepository.observeToolTemplates().first(),
            toolAssignments = toolItems.flatMap { item ->
                toolRepository.observeProjectIdsForToolItem(item.id).first().map { ToolAssignment(it, item.id) }
            },
            counters = counterRepository.observeCounters().first(),
            counterNotes = counterNoteRepository.observeNotes().first(),
            yarnAllocations = materialsRepository?.observeAllocations()?.first(),
            patternLinks = materialsRepository?.observePatternLinks()?.first(),
            milestones = journalRepository?.observeMilestones()?.first(),
            photos = journalRepository?.observePhotos()?.first(),
            journalEntries = journalRepository?.observeEntries()?.first(),
            sessions = sessionRepository?.observeSessions()?.first()
        ).withGuideGraph(guideBackupStore?.load())
    }

    /**
     * Writes parents before children. REPLACE removes local records of a
     * type only when the file carries that type and lacks them, then
     * *upserts* the file's records -- updating in place rather than
     * delete-and-reinsert, so children of a kept parent are never cascaded
     * away. MERGE ([toWrite] already filtered to new records) never deletes.
     */
    private suspend fun write(toWrite: BackupSnapshot, existing: BackupSnapshot, mode: RestoreMode) {
        val replace = mode == RestoreMode.REPLACE

        suspend fun <T> sync(incoming: List<T>?, local: List<T>?, key: (T) -> String, delete: suspend (T) -> Unit, save: suspend (T) -> Unit) {
            if (incoming == null) return
            if (replace) {
                val keep = incoming.map(key).toSet()
                local.orEmpty().filter { key(it) !in keep }.forEach { delete(it) }
            }
            incoming.forEach { save(it) }
        }

        sync(toWrite.projects, existing.projects, { it.id }, projectRepository::deleteProject, projectRepository::saveProject)
        sync(toWrite.libraryItems, existing.libraryItems, { it.id }, libraryRepository::deleteLibraryItem, libraryRepository::saveLibraryItem)
        sync(toWrite.stashItems, existing.stashItems, { it.id }, stashRepository::deleteStashItem, stashRepository::saveStashItem)
        sync(toWrite.toolSets, existing.toolSets, { it.id }, toolRepository::deleteToolSet, toolRepository::saveToolSet)
        sync(toWrite.toolItems, existing.toolItems, { it.id }, toolRepository::deleteToolItem, toolRepository::saveToolItem)
        sync(toWrite.toolTemplates, existing.toolTemplates, { it.id }, toolRepository::deleteToolTemplate, toolRepository::saveToolTemplate)

        toWrite.toolAssignments?.let { incoming ->
            val local = existing.toolAssignments.orEmpty()
            val toolIds = (incoming.map { it.toolItemId } + if (replace) local.map { it.toolItemId } else emptyList()).toSet()
            toolIds.forEach { toolId ->
                val fromFile = incoming.filter { it.toolItemId == toolId }.map { it.projectId }.toSet()
                val kept = if (replace) emptySet() else local.filter { it.toolItemId == toolId }.map { it.projectId }.toSet()
                toolRepository.setProjectAssignments(toolId, fromFile + kept)
            }
        }

        toWrite.counters?.let { incoming ->
            // Links are validated against every counter that will exist afterwards.
            val universe = if (replace) incoming else existing.counters.orEmpty() + incoming
            val counters = sanitizeLinks(incoming, universe)
            // Rows can reference each other in either order, so save link-stripped first, then relink.
            sync(
                counters.map { it.copy(linkedCounterId = null, linkIncrementInterval = null, linkIncrementAmount = null) },
                existing.counters,
                { it.id },
                counterRepository::deleteCounter,
                counterRepository::saveCounter
            )
            counters.filter { it.linkedCounterId != null }.forEach { counterRepository.saveCounter(it) }
        }
        sync(toWrite.counterNotes, existing.counterNotes, { it.id }, counterNoteRepository::deleteNote, counterNoteRepository::saveNote)

        materialsRepository?.let { materials ->
            sync(toWrite.yarnAllocations, existing.yarnAllocations, { it.id }, materials::deleteAllocation, materials::saveAllocation)
            sync(
                toWrite.patternLinks,
                existing.patternLinks,
                { "${it.projectId}/${it.libraryItemId}" },
                materials::unlinkPattern,
                materials::linkPattern
            )
        }
        journalRepository?.let { journal ->
            sync(toWrite.milestones, existing.milestones, { it.id }, journal::deleteMilestone) { journal.saveMilestones(listOf(it)) }
            sync(toWrite.photos, existing.photos, { it.id }, journal::deletePhoto, journal::savePhoto)
            sync(toWrite.journalEntries, existing.journalEntries, { it.id }, journal::deleteEntry, journal::saveEntry)
        }
        sessionRepository?.let { sessions ->
            sync(toWrite.sessions, existing.sessions, { it.id }, sessions::deleteSession, sessions::saveSession)
        }
        // Last, because guides reference projects and library items written above.
        // The store writes the whole graph in one transaction.
        val guideGraph = toWrite.guideGraph()
        if (guideBackupStore != null && guideGraph != null) {
            if (replace) guideBackupStore.replace(guideGraph) else guideBackupStore.add(guideGraph)
        }
    }

    private suspend fun missingFiles(snapshot: BackupSnapshot): List<String> {
        val references = snapshot.libraryItems.orEmpty().mapNotNull { item -> item.pdfUri?.let { it to (item.pdfFileName ?: it) } } +
            snapshot.photos.orEmpty().map { it.uri to (it.displayName ?: it.uri) }
        return references.filterNot { (uri, _) -> isFileAccessible(uri) }.map { it.second }
    }

    /**
     * Drops a counter's link if its target won't exist or the link would
     * close a cycle -- a backup is untrusted input, so import enforces the
     * same invariant counter editing does. Every counter is checked against
     * the full unmodified [universe], so all members of a cycle lose their
     * link rather than leaving one arbitrary link still cyclic.
     */
    private fun sanitizeLinks(counters: List<Counter>, universe: List<Counter>): List<Counter> {
        val ids = universe.map { it.id }.toSet()
        return counters.map { counter ->
            val targetId = counter.linkedCounterId
            val isValid = targetId != null && targetId in ids && !wouldCreateCycle(universe, counter.id, targetId)
            if (isValid) counter else counter.copy(linkedCounterId = null, linkIncrementInterval = null, linkIncrementAmount = null)
        }
    }
}
