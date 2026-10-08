package com.macareen.stitchbook2.data.ravelry

import com.macareen.stitchbook2.domain.ravelry.RavelryNeedle
import com.macareen.stitchbook2.domain.ravelry.RavelryStashEntry
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

    data class StashPage(val entries: List<RavelryStashEntry>, val isLastPage: Boolean)

    fun stashPage(body: String): StashPage {
        val root = JSONObject(body)
        val array = root.optJSONArray("stash")
        val entries = (0 until (array?.length() ?: 0)).mapNotNull { index ->
            array?.optJSONObject(index)?.let(::stashEntry)
        }
        val paginator = root.optJSONObject("paginator")
        val page = paginator?.number("page")
        val lastPage = paginator?.number("last_page") ?: paginator?.number("page_count")
        return StashPage(entries, isLastPage = page == null || lastPage == null || page >= lastPage)
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
