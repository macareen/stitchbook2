package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.BackupImportResult
import com.macareen.stitchbook2.domain.backup.BackupService
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
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

class LocalBackupService(
    private val projectRepository: ProjectRepository,
    private val libraryRepository: LibraryRepository,
    private val stashRepository: StashRepository
) : BackupService {

    override suspend fun exportJson(): String {
        val root = JSONObject()
        root.put(KEY_VERSION, FORMAT_VERSION)
        root.put(KEY_EXPORTED_AT, System.currentTimeMillis())
        root.put(KEY_PROJECTS, JSONArray(projectRepository.observeProjects().first().map { it.toJson() }))
        root.put(KEY_LIBRARY_ITEMS, JSONArray(libraryRepository.observeLibraryItems().first().map { it.toJson() }))
        root.put(KEY_STASH_ITEMS, JSONArray(stashRepository.observeStashItems().first().map { it.toJson() }))
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

            BackupImportResult.Success(projectCount, libraryItemCount, stashItemCount)
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

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else getString(name)
