package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.BackupImportResult
import com.macareen.stitchbook2.domain.backup.BackupService
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.CounterNote
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.wouldCreateCycle
import com.macareen.stitchbook2.domain.repository.CounterNoteRepository
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
import com.macareen.stitchbook2.domain.repository.ToolRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val FORMAT_VERSION = 1
private const val KEY_VERSION = "version"
private const val KEY_EXPORTED_AT = "exportedAt"
private const val KEY_PROJECTS = "projects"
private const val KEY_LIBRARY_ITEMS = "libraryItems"
private const val KEY_STASH_ITEMS = "stashItems"
private const val KEY_TOOL_SETS = "toolSets"
private const val KEY_TOOL_ITEMS = "toolItems"
private const val KEY_COUNTERS = "counters"
private const val KEY_COUNTER_NOTES = "counterNotes"

class LocalBackupService(
    private val projectRepository: ProjectRepository,
    private val libraryRepository: LibraryRepository,
    private val stashRepository: StashRepository,
    private val toolRepository: ToolRepository,
    private val counterRepository: CounterRepository,
    private val counterNoteRepository: CounterNoteRepository
) : BackupService {

    override suspend fun exportJson(): String {
        val root = JSONObject()
        root.put(KEY_VERSION, FORMAT_VERSION)
        root.put(KEY_EXPORTED_AT, System.currentTimeMillis())
        root.put(KEY_PROJECTS, JSONArray(projectRepository.observeProjects().first().map { it.toJson() }))
        root.put(KEY_LIBRARY_ITEMS, JSONArray(libraryRepository.observeLibraryItems().first().map { it.toJson() }))
        root.put(KEY_STASH_ITEMS, JSONArray(stashRepository.observeStashItems().first().map { it.toJson() }))
        // Sets before items: a set's own row must exist in the export for a
        // component's setId to resolve on import, mirroring the FK direction.
        root.put(KEY_TOOL_SETS, JSONArray(toolRepository.observeToolSets().first().map { it.toJson() }))
        root.put(KEY_TOOL_ITEMS, JSONArray(toolRepository.observeToolItems().first().map { it.toJson() }))
        root.put(KEY_COUNTERS, JSONArray(counterRepository.observeCounters().first().map { it.toJson() }))
        root.put(
            KEY_COUNTER_NOTES,
            JSONArray(counterNoteRepository.observeNotes().first().map { it.toJson() })
        )
        return root.toString(2)
    }

    override suspend fun importJson(json: String): BackupImportResult {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return BackupImportResult.InvalidFormat
        }

        return try {
            val projectCount = if (root.has(KEY_PROJECTS)) {
                val projects = root.getJSONArray(KEY_PROJECTS).toObjectList().map { it.toProject() }
                replaceAll(
                    current = projectRepository.observeProjects().first(),
                    incoming = projects,
                    delete = projectRepository::deleteProject,
                    save = projectRepository::saveProject
                )
                projects.size
            } else {
                null
            }

            val libraryItemCount = if (root.has(KEY_LIBRARY_ITEMS)) {
                val items = root.getJSONArray(KEY_LIBRARY_ITEMS).toObjectList().map { it.toLibraryItem() }
                replaceAll(
                    current = libraryRepository.observeLibraryItems().first(),
                    incoming = items,
                    delete = libraryRepository::deleteLibraryItem,
                    save = libraryRepository::saveLibraryItem
                )
                items.size
            } else {
                null
            }

            val stashItemCount = if (root.has(KEY_STASH_ITEMS)) {
                val items = root.getJSONArray(KEY_STASH_ITEMS).toObjectList().map { it.toStashItem() }
                replaceAll(
                    current = stashRepository.observeStashItems().first(),
                    incoming = items,
                    delete = stashRepository::deleteStashItem,
                    save = stashRepository::saveStashItem
                )
                items.size
            } else {
                null
            }

            // Sets are replaced before items: an incoming item's setId must
            // already exist (or be null) before that item's row is inserted,
            // and a set being deleted here only clears set_id on any item
            // rows left behind, never fails or deletes them (ON DELETE
            // SET_NULL).
            val toolSetCount = if (root.has(KEY_TOOL_SETS)) {
                val sets = root.getJSONArray(KEY_TOOL_SETS).toObjectList().map { it.toToolSet() }
                replaceAll(
                    current = toolRepository.observeToolSets().first(),
                    incoming = sets,
                    delete = toolRepository::deleteToolSet,
                    save = toolRepository::saveToolSet
                )
                sets.size
            } else {
                null
            }

            val toolItemCount = if (root.has(KEY_TOOL_ITEMS)) {
                val items = root.getJSONArray(KEY_TOOL_ITEMS).toObjectList().map { it.toToolItem() }
                replaceAll(
                    current = toolRepository.observeToolItems().first(),
                    incoming = items,
                    delete = toolRepository::deleteToolItem,
                    save = toolRepository::saveToolItem
                )
                items.size
            } else {
                null
            }

            // Counters last: a counter's optional projectId must resolve
            // against the projects already replaced above.
            val counterCount = if (root.has(KEY_COUNTERS)) {
                // A counter's optional link points at another counter's id.
                // A backup is untrusted input (hand-edited, or from a buggy
                // future exporter), so it can claim a link that would form a
                // cycle or point at an id absent from this same backup --
                // sanitizeLinks() drops any link like that before it's ever
                // written, the same invariant CountersViewModel.saveCounter
                // enforces for user-entered links.
                val counters = sanitizeLinks(root.getJSONArray(KEY_COUNTERS).toObjectList().map { it.toCounter() })
                // Incoming rows can reference each other in either order.
                // Inserting a counter with its real link before its target
                // row exists would violate the self-referencing FK, so
                // every row is first saved with its link stripped, then
                // saved again with the real link once all rows exist.
                replaceAll(
                    current = counterRepository.observeCounters().first(),
                    incoming = counters.map {
                        it.copy(linkedCounterId = null, linkIncrementInterval = null, linkIncrementAmount = null)
                    },
                    delete = counterRepository::deleteCounter,
                    save = counterRepository::saveCounter
                )
                counters.filter { it.linkedCounterId != null }.forEach { counterRepository.saveCounter(it) }
                counters.size
            } else {
                null
            }

            // Notes last: a note's counterId must resolve against the
            // counters already replaced immediately above.
            val counterNoteCount = if (root.has(KEY_COUNTER_NOTES)) {
                val notes = root.getJSONArray(KEY_COUNTER_NOTES).toObjectList().map { it.toCounterNote() }
                replaceAll(
                    current = counterNoteRepository.observeNotes().first(),
                    incoming = notes,
                    delete = counterNoteRepository::deleteNote,
                    save = counterNoteRepository::saveNote
                )
                notes.size
            } else {
                null
            }

            BackupImportResult.Success(
                projectCount,
                libraryItemCount,
                stashItemCount,
                toolSetCount,
                toolItemCount,
                counterCount,
                counterNoteCount
            )
        } catch (_: JSONException) {
            BackupImportResult.InvalidFormat
        } catch (_: IllegalArgumentException) {
            BackupImportResult.InvalidFormat
        }
    }

    override suspend fun resetAllData() {
        projectRepository.observeProjects().first().forEach { projectRepository.deleteProject(it) }
        libraryRepository.observeLibraryItems().first().forEach { libraryRepository.deleteLibraryItem(it) }
        stashRepository.observeStashItems().first().forEach { stashRepository.deleteStashItem(it) }
        // Items before sets: no functional requirement (set_id is ON DELETE
        // SET_NULL either way), but it reads as "clear the members, then the
        // now-empty grouping" rather than the reverse.
        toolRepository.observeToolItems().first().forEach { toolRepository.deleteToolItem(it) }
        toolRepository.observeToolSets().first().forEach { toolRepository.deleteToolSet(it) }
        // Notes before their owning counter: no functional requirement
        // (counter_id is ON DELETE CASCADE either way), but it reads as
        // "clear the notes, then the counter they were about" rather than
        // the reverse.
        counterNoteRepository.observeNotes().first().forEach { counterNoteRepository.deleteNote(it) }
        counterRepository.observeCounters().first().forEach { counterRepository.deleteCounter(it) }
    }

    private suspend fun <T> replaceAll(
        current: List<T>,
        incoming: List<T>,
        delete: suspend (T) -> Unit,
        save: suspend (T) -> Unit
    ) {
        current.forEach { delete(it) }
        incoming.forEach { save(it) }
    }

    /**
     * Drops a counter's link if it points at an id absent from this same
     * backup, or if [wouldCreateCycle] rejects it -- a backup is untrusted
     * input, so this import path enforces the same no-cycle/valid-target
     * invariant normal counter-editing enforces, rather than trusting the
     * file. Checking every counter against the full, unmodified [counters]
     * list (not a partially-sanitized one) means every counter that takes
     * part in a cycle gets its link cleared, breaking the cycle completely
     * rather than leaving one arbitrary link still cyclic.
     */
    private fun sanitizeLinks(counters: List<Counter>): List<Counter> {
        val idsInBackup = counters.map { it.id }.toSet()
        return counters.map { counter ->
            val targetId = counter.linkedCounterId
            val isValid = targetId != null &&
                targetId in idsInBackup &&
                !wouldCreateCycle(counters, counter.id, targetId)
            if (isValid) {
                counter
            } else {
                counter.copy(linkedCounterId = null, linkIncrementInterval = null, linkIncrementAmount = null)
            }
        }
    }
}

private fun JSONArray.toObjectList(): List<JSONObject> = buildList {
    for (i in 0 until length()) add(getJSONObject(i))
}

private fun Project.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("craft", craft.storageValue)
    put("projectType", projectType.storageValue)
    put("status", status.storageValue)
    put("notes", notes ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun JSONObject.toProject(): Project = Project(
    id = getString("id"),
    name = getString("name"),
    craft = Craft.fromStorageValue(getString("craft"))
        ?: throw IllegalArgumentException("Unknown craft value"),
    projectType = ProjectType.fromStorageValue(getString("projectType"))
        ?: throw IllegalArgumentException("Unknown project type value"),
    status = ProjectStatus.fromStorageValue(getString("status"))
        ?: throw IllegalArgumentException("Unknown project status value"),
    notes = optNullableString("notes"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)

private fun LibraryItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("craft", craft.storageValue)
    put("author", author ?: JSONObject.NULL)
    put("sourceUrl", sourceUrl ?: JSONObject.NULL)
    put("tags", JSONArray(tags))
    put("notes", notes ?: JSONObject.NULL)
    put("bookmarked", bookmarked)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("pdfUri", pdfUri ?: JSONObject.NULL)
    put("pdfFileName", pdfFileName ?: JSONObject.NULL)
    put("pdfLastViewedPage", pdfLastViewedPage ?: JSONObject.NULL)
}

private fun JSONObject.toLibraryItem(): LibraryItem = LibraryItem(
    id = getString("id"),
    title = getString("title"),
    craft = Craft.fromStorageValue(getString("craft"))
        ?: throw IllegalArgumentException("Unknown craft value"),
    author = optNullableString("author"),
    sourceUrl = optNullableString("sourceUrl"),
    tags = getJSONArray("tags").let { array -> List(array.length()) { array.getString(it) } },
    notes = optNullableString("notes"),
    bookmarked = getBoolean("bookmarked"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
    // Absent in backups written before this field existed -- isNull() treats
    // a missing key the same as an explicit null, so older backups restore
    // cleanly with no PDF attachment rather than failing to parse.
    pdfUri = optNullableString("pdfUri"),
    pdfFileName = optNullableString("pdfFileName"),
    pdfLastViewedPage = if (isNull("pdfLastViewedPage")) null else getInt("pdfLastViewedPage")
)

private fun StashItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("category", category.storageValue)
    put("brand", brand ?: JSONObject.NULL)
    put("colorway", colorway ?: JSONObject.NULL)
    put("dyeLot", dyeLot ?: JSONObject.NULL)
    put("weightCategory", weightCategory ?: JSONObject.NULL)
    put("fiberContent", fiberContent ?: JSONObject.NULL)
    put("quantity", quantity)
    put("unitLabel", unitLabel)
    put("yardagePerUnit", yardagePerUnit ?: JSONObject.NULL)
    put("notes", notes ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun JSONObject.toStashItem(): StashItem = StashItem(
    id = getString("id"),
    name = getString("name"),
    category = StashCategory.fromStorageValue(getString("category"))
        ?: throw IllegalArgumentException("Unknown stash category value"),
    brand = optNullableString("brand"),
    colorway = optNullableString("colorway"),
    dyeLot = optNullableString("dyeLot"),
    weightCategory = optNullableString("weightCategory"),
    fiberContent = optNullableString("fiberContent"),
    quantity = getDouble("quantity"),
    unitLabel = getString("unitLabel"),
    yardagePerUnit = if (isNull("yardagePerUnit")) null else getDouble("yardagePerUnit"),
    notes = optNullableString("notes"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)

private fun ToolSet.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("brand", brand ?: JSONObject.NULL)
    put("notes", notes ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun JSONObject.toToolSet(): ToolSet = ToolSet(
    id = getString("id"),
    name = getString("name"),
    brand = optNullableString("brand"),
    notes = optNullableString("notes"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)

private fun ToolItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("category", category.storageValue)
    put("brand", brand ?: JSONObject.NULL)
    put("material", material ?: JSONObject.NULL)
    put("sizeMetricMm", sizeMetricMm ?: JSONObject.NULL)
    put("sizeLabel", sizeLabel ?: JSONObject.NULL)
    put("lengthMm", lengthMm ?: JSONObject.NULL)
    put("statedCableLengthMm", statedCableLengthMm ?: JSONObject.NULL)
    put("cableLengthDefinition", cableLengthDefinition ?: JSONObject.NULL)
    put("approximateAssembledLengthMm", approximateAssembledLengthMm ?: JSONObject.NULL)
    put("connectorFamily", connectorFamily ?: JSONObject.NULL)
    put("compatibilityNotes", compatibilityNotes ?: JSONObject.NULL)
    put("quantity", quantity)
    put("storageLocation", storageLocation ?: JSONObject.NULL)
    put("notes", notes ?: JSONObject.NULL)
    put("setId", setId ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun JSONObject.toToolItem(): ToolItem = ToolItem(
    id = getString("id"),
    name = getString("name"),
    category = ToolCategory.fromStorageValue(getString("category"))
        ?: throw IllegalArgumentException("Unknown tool category value"),
    brand = optNullableString("brand"),
    material = optNullableString("material"),
    sizeMetricMm = optNullableDouble("sizeMetricMm"),
    sizeLabel = optNullableString("sizeLabel"),
    lengthMm = optNullableDouble("lengthMm"),
    statedCableLengthMm = optNullableDouble("statedCableLengthMm"),
    cableLengthDefinition = optNullableString("cableLengthDefinition"),
    approximateAssembledLengthMm = optNullableDouble("approximateAssembledLengthMm"),
    connectorFamily = optNullableString("connectorFamily"),
    compatibilityNotes = optNullableString("compatibilityNotes"),
    quantity = getInt("quantity"),
    storageLocation = optNullableString("storageLocation"),
    notes = optNullableString("notes"),
    setId = optNullableString("setId"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)

private fun Counter.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("projectId", projectId ?: JSONObject.NULL)
    put("name", name)
    put("unitLabel", unitLabel)
    put("currentValue", currentValue)
    put("goal", goal ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("linkedCounterId", linkedCounterId ?: JSONObject.NULL)
    put("linkIncrementInterval", linkIncrementInterval ?: JSONObject.NULL)
    put("linkIncrementAmount", linkIncrementAmount ?: JSONObject.NULL)
    put("autoResetOnGoal", autoResetOnGoal)
}

private fun JSONObject.toCounter(): Counter = Counter(
    id = getString("id"),
    projectId = optNullableString("projectId"),
    name = getString("name"),
    unitLabel = getString("unitLabel"),
    currentValue = getInt("currentValue"),
    goal = if (isNull("goal")) null else getInt("goal"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
    // Absent in backups written before this field existed -- isNull()
    // treats a missing key the same as an explicit null, so older backups
    // restore cleanly with no link rather than failing to parse.
    linkedCounterId = optNullableString("linkedCounterId"),
    linkIncrementInterval = if (isNull("linkIncrementInterval")) null else getInt("linkIncrementInterval"),
    linkIncrementAmount = if (isNull("linkIncrementAmount")) null else getInt("linkIncrementAmount"),
    autoResetOnGoal = optBoolean("autoResetOnGoal", false)
)

private fun CounterNote.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("counterId", counterId)
    put("value", value)
    put("note", note)
    put("createdAt", createdAt)
}

private fun JSONObject.toCounterNote(): CounterNote = CounterNote(
    id = getString("id"),
    counterId = getString("counterId"),
    value = getInt("value"),
    note = getString("note"),
    createdAt = getLong("createdAt")
)

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else getString(name)

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (isNull(name)) null else getDouble(name)
