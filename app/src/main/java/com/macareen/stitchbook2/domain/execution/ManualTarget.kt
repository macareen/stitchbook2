package com.macareen.stitchbook2.domain.execution

import org.json.JSONArray
import org.json.JSONObject

/** One authored instruction describes a complete cycle; its number of cycles is unknown. */
enum class TargetUnit(val label: String) { STITCHES("stitches"), CENTIMETRES("cm"), INCHES("in") }

enum class TargetComparison(val label: String) { AT_LEAST("At least"), AT_MOST("At most") }

data class ManualTarget(val unit: TargetUnit, val value: Double, val comparison: TargetComparison = TargetComparison.AT_LEAST) {
    init {
        require(value.isFinite() && value > 0)
        require(unit != TargetUnit.STITCHES || value == value.toLong().toDouble())
    }
    fun accepts(value: Double) = value.isFinite() && value >= 0 &&
        (unit != TargetUnit.STITCHES || value == value.toLong().toDouble())
    fun isReachedBy(observed: Double) = accepts(observed) && when (comparison) {
        TargetComparison.AT_LEAST -> observed >= value
        TargetComparison.AT_MOST -> observed <= value
    }
    fun description() = "${comparison.label.lowercase()} ${formatTargetValue(value)} ${unit.label}"
}

fun formatTargetValue(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

data class TargetObservation(val cycle: Int, val value: Double)

/** Observations survive navigation/undo. The cursor says which cycle is being worked. */
data class TargetWork(
    val cycle: Int = 1,
    val observations: List<TargetObservation> = emptyList(),
    val reached: Boolean = false
)

object ManualTargets {
    fun encode(target: ManualTarget): String = JSONObject().put("unit", target.unit.name).put("value", target.value).put("comparison", target.comparison.name).toString()
    fun decode(json: String): ManualTarget = JSONObject(json).let {
        ManualTarget(TargetUnit.valueOf(it.getString("unit")), number(it, "value"), TargetComparison.valueOf(it.optString("comparison", "AT_LEAST")))
    }
    internal fun number(json: JSONObject, key: String): Double {
        val number = json.get(key)
        require(number is Number && number.toDouble().isFinite())
        return number.toDouble()
    }
    internal fun integer(json: JSONObject, key: String): Int {
        val number = number(json, key)
        require(number == number.toInt().toDouble())
        return number.toInt()
    }
}

/** Revision is owned by the execution; the full occurrence address includes finite ancestors. */
object TargetWorkCodec {
    fun encode(work: Map<ExecutionAddress, TargetWork>): String = JSONArray().apply {
        work.entries.sortedBy { it.key.toString() }.forEach { (address, value) ->
            put(JSONObject().put("address", JSONArray(CastOnCounts.encode(mapOf(address to 0))).getJSONObject(0))
                .put("cycle", value.cycle).put("reached", value.reached)
                .put("observations", JSONArray().apply {
                    value.observations.forEach { put(JSONObject().put("cycle", it.cycle).put("value", it.value)) }
                }))
        }
    }.toString()

    fun decode(json: String, revision: DefinitionRevisionId): Map<ExecutionAddress, TargetWork> {
        val array = JSONArray(json)
        val entries = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val address = CastOnCounts.decode(JSONArray().put(item.getJSONObject("address")).toString(), revision).keys.single()
            val observations = item.getJSONArray("observations")
            val work = TargetWork(ManualTargets.integer(item, "cycle"), (0 until observations.length()).map {
                val observation = observations.getJSONObject(it)
                TargetObservation(ManualTargets.integer(observation, "cycle"), ManualTargets.number(observation, "value"))
            }, item.get("reached").also { require(it is Boolean) } as Boolean)
            require(work.cycle > 0 && work.observations.all { it.cycle > 0 && it.value >= 0 })
            address to work
        }
        require(entries.map { it.first }.distinct().size == entries.size)
        return entries.toMap()
    }
}
