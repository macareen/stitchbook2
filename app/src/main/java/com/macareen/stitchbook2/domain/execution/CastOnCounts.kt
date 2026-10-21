package com.macareen.stitchbook2.domain.execution

import org.json.JSONArray
import org.json.JSONObject

/** Explicit occurrence addresses, never display text or positional list indices. */
object CastOnCounts {
    fun encode(counts: Map<ExecutionAddress, Int>): String = JSONArray().apply {
        counts.entries.sortedBy { it.key.toString() }.forEach { (address, count) ->
            put(JSONObject().put("nodeId", address.instructionNodeId.value).put("count", count)
                .put("frames", JSONArray().apply {
                    address.ancestryFrames.forEach { frame ->
                        put(JSONObject().put("containerId", frame.containerNodeId.value).apply {
                            when (frame) {
                                is AncestryFrame.RangeValue -> put("type", "range").put("value", frame.value)
                                is AncestryFrame.RepeatIteration -> put("type", "repeat").put("value", frame.iteration)
                            }
                        })
                    }
                }))
        }
    }.toString()

    private fun JSONObject.strictInt(key: String): Int {
        val value = get(key)
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toInt().toDouble()) { "Expected integer $key" }
        return value.toInt()
    }

    fun decode(json: String, revisionId: DefinitionRevisionId): Map<ExecutionAddress, Int> {
        val array = JSONArray(json)
        val entries = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val frames = item.getJSONArray("frames")
            val address = ExecutionAddress(revisionId, NodeId(item.getString("nodeId")),
                (0 until frames.length()).map { position ->
                    val frame = frames.getJSONObject(position)
                    val id = NodeId(frame.getString("containerId"))
                    val number = frame.strictInt("value")
                    when (frame.getString("type")) {
                        "range" -> AncestryFrame.RangeValue(id, number)
                        "repeat" -> AncestryFrame.RepeatIteration(id, number)
                        else -> error("Unknown cast-on address frame")
                    }
                })
            val count = item.strictInt("count")
            require(count >= 0) { "Cast-on count must be non-negative" }
            address to count
        }
        require(entries.map { it.first }.toSet().size == entries.size) { "Duplicate cast-on address" }
        return entries.toMap()
    }
}
