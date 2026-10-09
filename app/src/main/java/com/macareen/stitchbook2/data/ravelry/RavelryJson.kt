package com.macareen.stitchbook2.data.ravelry

import com.macareen.stitchbook2.domain.ravelry.RavelryAttachment
import com.macareen.stitchbook2.domain.ravelry.RavelryNeedle
import com.macareen.stitchbook2.domain.ravelry.RavelryProject
import com.macareen.stitchbook2.domain.ravelry.RavelryStashEntry
import com.macareen.stitchbook2.domain.ravelry.RavelryVolume
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads Ravelry API responses (see the "Stash (small)", "Pack (stash)",
 * "Yarn (stash_list)", and "NeedleRecord (full)" result objects in
 * Ravelry's API documentation). Ravelry's documentation leaves many types
 * unstated, so numbers are accepted as numbers or numeric text, and missing
 * or null fields simply become null.
 */
internal object RavelryJson {

    fun username(body: String): String =
        JSONObject(body).getJSONObject("user").getString("username")

    data class Page<T>(val entries: List<T>, val isLastPage: Boolean)

    fun stashPage(body: String): Page<RavelryStashEntry> = page(body, "stash", ::stashEntry)

    fun projectsPage(body: String): Page<RavelryProject> = page(body, "projects") { project ->
        project.number("id")?.toLong()?.let { id ->
            RavelryProject(
                id = id,
                name = project.text("name"),
                craftName = project.text("craft_name"),
                statusName = project.text("status_name"),
                patternName = project.text("pattern_name") ?: project.text("personal_source_name"),
                started = project.text("started"),
                completed = project.text("completed"),
                finishBy = project.text("finish_by")
            )
        }
    }

    fun volumesPage(body: String): Page<RavelryVolume> = page(body, "volumes") { volume ->
        volume.number("id")?.toLong()?.let { id ->
            RavelryVolume(
                id = id,
                title = volume.text("title"),
                authorName = volume.text("author_name"),
                patternId = volume.number("pattern_id")?.toLong()
            )
        }
    }

    /**
     * The files on one volume ("Volume (full)"). Ravelry's documentation is
     * behind a sign-in and the attachment list's exact key is not confirmed,
     * so the known spellings are all accepted, and an attachment's id is its
     * product attachment id when one is given.
     */
    fun volumeAttachments(body: String): List<RavelryAttachment> {
        val root = JSONObject(body)
        val volume = root.optJSONObject("volume") ?: root
        val array = ATTACHMENT_KEYS.firstNotNullOfOrNull { volume.optJSONArray(it) } ?: return emptyList()
        return array.objects().mapNotNull { attachment ->
            val id = (attachment.number("product_attachment_id") ?: attachment.number("id"))?.toLong() ?: return@mapNotNull null
            RavelryAttachment(id = id, fileName = attachment.text("filename") ?: attachment.text("file_name") ?: attachment.text("name"))
        }
    }

    /** The address in a "generate download link" answer, or null when it holds none. */
    fun downloadUrl(body: String): String? {
        val root = JSONObject(body)
        val link = root.optJSONObject("download_link")
        return link?.text("url") ?: root.text("download_link") ?: root.text("url")
    }

    private val ATTACHMENT_KEYS = listOf("volume_attachments", "product_attachments", "attachments")

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun <T> page(body: String, key: String, read: (JSONObject) -> T?): Page<T> {
        val root = JSONObject(body)
        val array = root.optJSONArray(key)
        val entries = (0 until (array?.length() ?: 0)).mapNotNull { index -> array?.optJSONObject(index)?.let(read) }
        val paginator = root.optJSONObject("paginator")
        val page = paginator?.number("page")
        val lastPage = paginator?.number("last_page") ?: paginator?.number("page_count")
        return Page(entries, isLastPage = page == null || lastPage == null || page >= lastPage)
    }

    private fun stashEntry(entry: JSONObject): RavelryStashEntry? {
        val id = entry.number("id")?.toLong() ?: return null
        val yarn = entry.optJSONObject("yarn")
        val pack = entry.optJSONObject("primary_pack")
        return RavelryStashEntry(
            id = id,
            name = entry.text("name"),
            yarnId = yarn?.number("id")?.toLong(),
            yarnName = yarn?.text("name"),
            companyName = yarn?.text("yarn_company_name"),
            weightName = yarn?.optJSONObject("yarn_weight")?.text("name") ?: entry.text("yarn_weight_name"),
            colorway = entry.text("colorway_name") ?: pack?.text("colorway"),
            dyeLot = entry.text("dye_lot") ?: pack?.text("dye_lot"),
            location = entry.text("location"),
            skeins = pack?.number("skeins"),
            yardsPerSkein = pack?.number("yards_per_skein") ?: yarn?.number("yardage"),
            gramsPerSkein = pack?.number("grams_per_skein") ?: yarn?.number("grams")
        )
    }

    fun needles(body: String): List<RavelryNeedle> {
        val array = JSONObject(body).optJSONArray("needle_records") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val record = array.optJSONObject(index) ?: return@mapNotNull null
            val id = record.number("id")?.toLong() ?: return@mapNotNull null
            val type = record.optJSONObject("needle_type")
            RavelryNeedle(
                id = id,
                name = type?.text("name"),
                typeName = type?.text("type_name"),
                metricName = type?.text("metric_name"),
                length = type?.text("length"),
                comment = record.text("comment")
            )
        }
    }

    private fun JSONObject.text(key: String): String? {
        if (isNull(key)) return null
        return when (val value = opt(key)) {
            is String -> value.trim().takeIf { it.isNotEmpty() }
            is Number -> value.toString()
            else -> null
        }
    }

    private fun JSONObject.number(key: String): Double? {
        if (isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
    }
}
