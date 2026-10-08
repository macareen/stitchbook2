package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.BackupSnapshot
import com.macareen.stitchbook2.domain.backup.CURRENT_BACKUP_FORMAT_VERSION
import com.macareen.stitchbook2.domain.backup.ToolAssignment
import com.macareen.stitchbook2.domain.model.BulkSizeInputMode
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.CounterNote
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.model.JournalEntry
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.Milestone
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.PhotoRole
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectPatternLink
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.ToolTemplate
import com.macareen.stitchbook2.domain.model.YarnAllocation
import org.json.JSONArray
import org.json.JSONObject

/*
 * Stitchbook backup JSON (documented in ARCHITECTURE.md "Backup format").
 *
 * Version 2 is a superset of version 1: the same top-level record keys
 * plus new ones, a "format" marker, and a "manifest" (record counts and
 * the external files the records reference). Readers treat any absent key
 * as "this file doesn't carry that type" and any absent field as null, so
 * version 1 files still restore.
 *
 * Files (PDFs, photos) are *referenced*, never embedded: the user's
 * originals stay in their own storage, and the manifest lists each
 * reference with its display name so it can be relinked on a new device.
 */

internal const val FORMAT_MARKER = "stitchbook-backup"

private const val KEY_FORMAT = "format"
private const val KEY_VERSION = "version"
private const val KEY_EXPORTED_AT = "exportedAt"
private const val KEY_MANIFEST = "manifest"
private const val KEY_PROJECTS = "projects"
private const val KEY_LIBRARY_ITEMS = "libraryItems"
private const val KEY_STASH_ITEMS = "stashItems"
private const val KEY_TOOL_SETS = "toolSets"
private const val KEY_TOOL_ITEMS = "toolItems"
private const val KEY_TOOL_TEMPLATES = "toolTemplates"
private const val KEY_TOOL_ASSIGNMENTS = "toolAssignments"
private const val KEY_COUNTERS = "counters"
private const val KEY_COUNTER_NOTES = "counterNotes"
private const val KEY_YARN_ALLOCATIONS = "yarnAllocations"
private const val KEY_PATTERN_LINKS = "patternLinks"
private const val KEY_MILESTONES = "milestones"
private const val KEY_PHOTOS = "photos"
private const val KEY_JOURNAL_ENTRIES = "journalEntries"
private const val KEY_SESSIONS = "sessions"

internal fun encodeBackup(snapshot: BackupSnapshot, exportedAt: Long): String {
    val root = JSONObject()
    root.put(KEY_FORMAT, FORMAT_MARKER)
    root.put(KEY_VERSION, CURRENT_BACKUP_FORMAT_VERSION)
    root.put(KEY_EXPORTED_AT, exportedAt)
    root.put(KEY_MANIFEST, manifest(snapshot))
    fun <T> put(key: String, records: List<T>?, encode: (T) -> JSONObject) {
        if (records != null) root.put(key, JSONArray(records.map(encode)))
    }
    put(KEY_PROJECTS, snapshot.projects) { it.toJson() }
    put(KEY_LIBRARY_ITEMS, snapshot.libraryItems) { it.toJson() }
    put(KEY_STASH_ITEMS, snapshot.stashItems) { it.toJson() }
    put(KEY_TOOL_SETS, snapshot.toolSets) { it.toJson() }
    put(KEY_TOOL_ITEMS, snapshot.toolItems) { it.toJson() }
    put(KEY_TOOL_TEMPLATES, snapshot.toolTemplates) { it.toJson() }
    put(KEY_TOOL_ASSIGNMENTS, snapshot.toolAssignments) {
        JSONObject().put("projectId", it.projectId).put("toolItemId", it.toolItemId)
    }
    put(KEY_COUNTERS, snapshot.counters) { it.toJson() }
    put(KEY_COUNTER_NOTES, snapshot.counterNotes) { it.toJson() }
    put(KEY_YARN_ALLOCATIONS, snapshot.yarnAllocations) { it.toJson() }
    put(KEY_PATTERN_LINKS, snapshot.patternLinks) {
        JSONObject().put("projectId", it.projectId).put("libraryItemId", it.libraryItemId)
    }
    put(KEY_MILESTONES, snapshot.milestones) { it.toJson() }
    put(KEY_PHOTOS, snapshot.photos) { it.toJson() }
    put(KEY_JOURNAL_ENTRIES, snapshot.journalEntries) { it.toJson() }
    put(KEY_SESSIONS, snapshot.sessions) { it.toJson() }
    return root.toString(2)
}

/** Throws [org.json.JSONException] or [IllegalArgumentException] for anything that isn't a readable backup. */
internal fun decodeBackup(json: String): BackupSnapshot {
    val root = JSONObject(json)
    if (root.has(KEY_FORMAT) && root.getString(KEY_FORMAT) != FORMAT_MARKER) {
        throw IllegalArgumentException("Not a Stitchbook backup")
    }
    fun <T> list(key: String, decode: (JSONObject) -> T): List<T>? =
        if (root.has(key)) root.getJSONArray(key).toObjectList().map(decode) else null
    return BackupSnapshot(
        formatVersion = root.optInt(KEY_VERSION, 1),
        projects = list(KEY_PROJECTS) { it.toProject() },
        libraryItems = list(KEY_LIBRARY_ITEMS) { it.toLibraryItem() },
        stashItems = list(KEY_STASH_ITEMS) { it.toStashItem() },
        toolSets = list(KEY_TOOL_SETS) { it.toToolSet() },
        toolItems = list(KEY_TOOL_ITEMS) { it.toToolItem() },
        toolTemplates = list(KEY_TOOL_TEMPLATES) { it.toToolTemplate() },
        toolAssignments = list(KEY_TOOL_ASSIGNMENTS) { ToolAssignment(it.getString("projectId"), it.getString("toolItemId")) },
        counters = list(KEY_COUNTERS) { it.toCounter() },
        counterNotes = list(KEY_COUNTER_NOTES) { it.toCounterNote() },
        yarnAllocations = list(KEY_YARN_ALLOCATIONS) { it.toYarnAllocation() },
        patternLinks = list(KEY_PATTERN_LINKS) { ProjectPatternLink(it.getString("projectId"), it.getString("libraryItemId")) },
        milestones = list(KEY_MILESTONES) { it.toMilestone() },
        photos = list(KEY_PHOTOS) { it.toPhoto() },
        journalEntries = list(KEY_JOURNAL_ENTRIES) { it.toJournalEntry() },
        sessions = list(KEY_SESSIONS) { it.toSession() }
    )
}

/** Counts plus every external file reference, for humans and for recovery on a new device. */
private fun manifest(snapshot: BackupSnapshot): JSONObject {
    val counts = JSONObject()
    snapshot.counts().forEach { (type, count) -> counts.put(type.name, count) }
    val files = JSONArray()
    snapshot.libraryItems.orEmpty().filter { it.pdfUri != null }.forEach {
        files.put(JSONObject().put("kind", "pdf").put("recordId", it.id).put("uri", it.pdfUri).put("displayName", it.pdfFileName ?: JSONObject.NULL))
    }
    snapshot.photos.orEmpty().forEach {
        files.put(JSONObject().put("kind", "photo").put("recordId", it.id).put("uri", it.uri).put("displayName", it.displayName ?: JSONObject.NULL))
    }
    return JSONObject()
        .put("recordCounts", counts)
        .put("externalFiles", files)
        .put("fileRule", "Files are referenced, not embedded. Keep your PDFs and photos alongside this backup; relink them after restoring on another device.")
}

internal fun JSONArray.toObjectList(): List<JSONObject> = buildList {
    for (i in 0 until length()) add(getJSONObject(i))
}

internal fun Project.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("craft", craft.storageValue)
    put("projectType", projectType.storageValue)
    put("status", status.storageValue)
    put("notes", notes ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("description", description ?: JSONObject.NULL)
    put("constructionMethod", constructionMethod ?: JSONObject.NULL)
    put("customTypeLabel", customTypeLabel ?: JSONObject.NULL)
    put("startDate", startDate ?: JSONObject.NULL)
    put("targetDate", targetDate ?: JSONObject.NULL)
    put("completedDate", completedDate ?: JSONObject.NULL)
}

internal fun JSONObject.toProject(): Project = Project(
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
    updatedAt = getLong("updatedAt"),
    // Absent in backups written before schema v15 -- optNullableString
    // treats a missing key as null, so older backups still restore.
    description = optNullableString("description"),
    constructionMethod = optNullableString("constructionMethod"),
    customTypeLabel = optNullableString("customTypeLabel"),
    startDate = optNullableString("startDate"),
    targetDate = optNullableString("targetDate"),
    completedDate = optNullableString("completedDate")
)

internal fun LibraryItem.toJson(): JSONObject = JSONObject().apply {
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
    put("gauge", gauge ?: JSONObject.NULL)
    put("sizes", sizes ?: JSONObject.NULL)
    put("yardageRequired", yardageRequired ?: JSONObject.NULL)
    put("recommendedTools", recommendedTools ?: JSONObject.NULL)
    put("ravelryPatternId", ravelryPatternId ?: JSONObject.NULL)
}

internal fun JSONObject.toLibraryItem(): LibraryItem = LibraryItem(
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
    pdfLastViewedPage = if (isNull("pdfLastViewedPage")) null else getInt("pdfLastViewedPage"),
    gauge = optNullableString("gauge"),
    sizes = optNullableString("sizes"),
    yardageRequired = optNullableDouble("yardageRequired"),
    recommendedTools = optNullableString("recommendedTools"),
    ravelryPatternId = optNullableString("ravelryPatternId")
)

internal fun StashItem.toJson(): JSONObject = JSONObject().apply {
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
    put("storageLocation", storageLocation ?: JSONObject.NULL)
    put("careInstructions", careInstructions ?: JSONObject.NULL)
    put("ravelryYarnId", ravelryYarnId ?: JSONObject.NULL)
    put("purchaseSource", purchaseSource ?: JSONObject.NULL)
    put("purchasePrice", purchasePrice ?: JSONObject.NULL)
    put("purchaseDate", purchaseDate ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("weightPerUnitGrams", weightPerUnitGrams ?: JSONObject.NULL)
    put("remainingWeightGrams", remainingWeightGrams ?: JSONObject.NULL)
}

internal fun JSONObject.toStashItem(): StashItem = StashItem(
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
    storageLocation = optNullableString("storageLocation"),
    careInstructions = optNullableString("careInstructions"),
    ravelryYarnId = optNullableString("ravelryYarnId"),
    purchaseSource = optNullableString("purchaseSource"),
    purchasePrice = optNullableDouble("purchasePrice"),
    purchaseDate = optNullableString("purchaseDate"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
    weightPerUnitGrams = optNullableDouble("weightPerUnitGrams"),
    remainingWeightGrams = optNullableDouble("remainingWeightGrams")
)

internal fun ToolSet.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("brand", brand ?: JSONObject.NULL)
    put("notes", notes ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

internal fun JSONObject.toToolSet(): ToolSet = ToolSet(
    id = getString("id"),
    name = getString("name"),
    brand = optNullableString("brand"),
    notes = optNullableString("notes"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)

internal fun ToolItem.toJson(): JSONObject = JSONObject().apply {
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

internal fun JSONObject.toToolItem(): ToolItem = ToolItem(
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

internal fun Counter.toJson(): JSONObject = JSONObject().apply {
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
    put("repeatIntervalDays", repeatIntervalDays ?: JSONObject.NULL)
    put("lastRepeatResetAt", lastRepeatResetAt ?: JSONObject.NULL)
}

internal fun JSONObject.toCounter(): Counter = Counter(
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
    autoResetOnGoal = optBoolean("autoResetOnGoal", false),
    repeatIntervalDays = if (isNull("repeatIntervalDays")) null else getInt("repeatIntervalDays"),
    lastRepeatResetAt = if (isNull("lastRepeatResetAt")) null else getLong("lastRepeatResetAt")
)

internal fun CounterNote.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("counterId", counterId)
    put("value", value)
    put("note", note)
    put("createdAt", createdAt)
}

internal fun JSONObject.toCounterNote(): CounterNote = CounterNote(
    id = getString("id"),
    counterId = getString("counterId"),
    value = getInt("value"),
    note = getString("note"),
    createdAt = getLong("createdAt")
)

internal fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else getString(name)

internal fun JSONObject.optNullableDouble(name: String): Double? =
    if (isNull(name)) null else getDouble(name)


internal fun ToolTemplate.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("category", category.storageValue)
    put("brand", brand ?: JSONObject.NULL)
    put("material", material ?: JSONObject.NULL)
    put("sizeInputMode", sizeInputMode.name)
    put("rangeStart", rangeStart ?: JSONObject.NULL)
    put("rangeEnd", rangeEnd ?: JSONObject.NULL)
    put("rangeIncrement", rangeIncrement ?: JSONObject.NULL)
    put("customSizes", customSizes ?: JSONObject.NULL)
    put("quantityPerSize", quantityPerSize)
    put("storageLocation", storageLocation ?: JSONObject.NULL)
    put("notes", notes ?: JSONObject.NULL)
    put("createAsSet", createAsSet)
    put("setName", setName ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

internal fun JSONObject.toToolTemplate(): ToolTemplate = ToolTemplate(
    id = getString("id"),
    name = getString("name"),
    category = ToolCategory.fromStorageValue(getString("category"))
        ?: throw IllegalArgumentException("Unknown tool category value"),
    brand = optNullableString("brand"),
    material = optNullableString("material"),
    sizeInputMode = BulkSizeInputMode.valueOf(getString("sizeInputMode")),
    rangeStart = optNullableDouble("rangeStart"),
    rangeEnd = optNullableDouble("rangeEnd"),
    rangeIncrement = optNullableDouble("rangeIncrement"),
    customSizes = optNullableString("customSizes"),
    quantityPerSize = getInt("quantityPerSize"),
    storageLocation = optNullableString("storageLocation"),
    notes = optNullableString("notes"),
    createAsSet = getBoolean("createAsSet"),
    setName = optNullableString("setName"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)

internal fun YarnAllocation.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("projectId", projectId)
    put("stashItemId", stashItemId)
    put("quantityReserved", quantityReserved)
    put("quantityUsed", quantityUsed)
    put("notes", notes ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

internal fun JSONObject.toYarnAllocation(): YarnAllocation = YarnAllocation(
    id = getString("id"),
    projectId = getString("projectId"),
    stashItemId = getString("stashItemId"),
    quantityReserved = getDouble("quantityReserved"),
    quantityUsed = getDouble("quantityUsed"),
    notes = optNullableString("notes"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)

internal fun Milestone.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("projectId", projectId)
    put("title", title)
    put("reachedDate", reachedDate ?: JSONObject.NULL)
    put("notes", notes ?: JSONObject.NULL)
    put("position", position)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

internal fun JSONObject.toMilestone(): Milestone = Milestone(
    id = getString("id"),
    projectId = getString("projectId"),
    title = getString("title"),
    reachedDate = optNullableString("reachedDate"),
    notes = optNullableString("notes"),
    position = getInt("position"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)

internal fun Photo.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("projectId", projectId ?: JSONObject.NULL)
    put("stashItemId", stashItemId ?: JSONObject.NULL)
    put("uri", uri)
    put("displayName", displayName ?: JSONObject.NULL)
    put("caption", caption ?: JSONObject.NULL)
    put("takenDate", takenDate ?: JSONObject.NULL)
    put("milestoneId", milestoneId ?: JSONObject.NULL)
    put("role", role.storageValue)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

internal fun JSONObject.toPhoto(): Photo = Photo(
    id = getString("id"),
    projectId = optNullableString("projectId"),
    stashItemId = optNullableString("stashItemId"),
    uri = getString("uri"),
    displayName = optNullableString("displayName"),
    caption = optNullableString("caption"),
    takenDate = optNullableString("takenDate"),
    milestoneId = optNullableString("milestoneId"),
    role = PhotoRole.fromStorageValue(optNullableString("role")),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)

internal fun JournalEntry.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("projectId", projectId)
    put("entryDate", entryDate)
    put("title", title ?: JSONObject.NULL)
    put("body", body)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

internal fun JSONObject.toJournalEntry(): JournalEntry = JournalEntry(
    id = getString("id"),
    projectId = getString("projectId"),
    entryDate = getString("entryDate"),
    title = optNullableString("title"),
    body = getString("body"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)

internal fun CraftingSession.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("projectId", projectId ?: JSONObject.NULL)
    put("startedAt", startedAt)
    put("endedAt", endedAt ?: JSONObject.NULL)
    put("pausedAt", pausedAt ?: JSONObject.NULL)
    put("pausedTotalMillis", pausedTotalMillis)
    put("zoneId", zoneId)
    put("rowsCompleted", rowsCompleted ?: JSONObject.NULL)
    put("stitchesPerRow", stitchesPerRow ?: JSONObject.NULL)
    put("notes", notes ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

internal fun JSONObject.toSession(): CraftingSession = CraftingSession(
    id = getString("id"),
    projectId = optNullableString("projectId"),
    startedAt = getLong("startedAt"),
    endedAt = if (isNull("endedAt")) null else getLong("endedAt"),
    pausedAt = if (isNull("pausedAt")) null else getLong("pausedAt"),
    pausedTotalMillis = optLong("pausedTotalMillis", 0L),
    zoneId = getString("zoneId"),
    rowsCompleted = if (isNull("rowsCompleted")) null else getInt("rowsCompleted"),
    stitchesPerRow = if (isNull("stitchesPerRow")) null else getInt("stitchesPerRow"),
    notes = optNullableString("notes"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)
