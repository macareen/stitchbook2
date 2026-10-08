package com.macareen.stitchbook2.data.parsing

import com.macareen.stitchbook2.domain.parsing.Abbreviation
import com.macareen.stitchbook2.domain.parsing.StructuredGuide
import com.macareen.stitchbook2.domain.parsing.StructuredGuideDecodeResult
import com.macareen.stitchbook2.domain.parsing.StructuredGuideDecoder
import com.macareen.stitchbook2.domain.parsing.StructuredGuideLimits
import com.macareen.stitchbook2.domain.parsing.StructuredGuideProblem
import com.macareen.stitchbook2.domain.parsing.StructuredStep
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Decodes a pasted assistant reply into a [StructuredGuide].
 *
 * A reply is untrusted input: it may wrap the JSON in prose or a Markdown
 * code fence, use slightly different spellings ("rounds", "rnd"), or be
 * wrong. Wrapping and spelling are tolerated; anything that would change
 * what the person knits or crochets is reported as a problem with its path,
 * and nothing is guessed or silently dropped.
 */
class StructuredGuideJsonDecoder : StructuredGuideDecoder {

    override fun decode(reply: String): StructuredGuideDecodeResult {
        val json = extractJsonObject(reply)
            ?: return invalid("", "No JSON object was found in the reply.")
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return invalid("", "The reply is not valid JSON: ${e.message.orEmpty().take(120)}")
        }
        return Reader().read(root)
    }

    private class Reader {
        val problems = mutableListOf<StructuredGuideProblem>()
        var stepTotal = 0

        fun read(root: JSONObject): StructuredGuideDecodeResult {
            root.opt("format")?.let { format ->
                if (format != FORMAT) problem("format", "Expected \"$FORMAT\" but found \"$format\".")
            }
            (root.opt("version") as? Number)?.toInt()?.let { version ->
                if (version > SUPPORTED_VERSION) {
                    problem("version", "Version $version is newer than this app understands ($SUPPORTED_VERSION).")
                }
            }

            val stepsArray = root.opt("steps") as? JSONArray
            if (stepsArray == null || stepsArray.length() == 0) problem("steps", "The guide has no steps.")
            val steps = stepsArray?.let { readSteps(it, "steps", depth = 1) }.orEmpty()

            val guide = StructuredGuide(
                name = (root.opt("name") as? String)?.trim()?.takeIf { it.isNotEmpty() },
                materials = readStrings(root, "materials"),
                abbreviations = readAbbreviations(root),
                notes = readStrings(root, "notes"),
                steps = steps,
                review = readStrings(root, "review")
            )
            return if (problems.isEmpty()) {
                StructuredGuideDecodeResult.Valid(guide)
            } else {
                StructuredGuideDecodeResult.Invalid(problems.toList())
            }
        }

        private fun readSteps(array: JSONArray, path: String, depth: Int): List<StructuredStep> {
            if (depth > StructuredGuideLimits.MAX_DEPTH) {
                problem(path, "Steps are nested more than ${StructuredGuideLimits.MAX_DEPTH} levels deep.")
                return emptyList()
            }
            val steps = mutableListOf<StructuredStep>()
            for (index in 0 until array.length()) {
                val itemPath = "$path[$index]"
                val item = array.opt(index)
                val step = when (item) {
                    is JSONObject -> readStep(item, itemPath, depth)
                    // A bare string is an instruction; assistants often shorten it that way.
                    is String -> text(item, itemPath)?.let(StructuredStep::Instruction)
                    else -> null.also { problem(itemPath, "A step must be an object.") }
                }
                if (step != null) {
                    stepTotal++
                    if (stepTotal == StructuredGuideLimits.MAX_STEPS + 1) {
                        problem(itemPath, "The guide has more than ${StructuredGuideLimits.MAX_STEPS} steps.")
                    }
                    steps += step
                }
            }
            return steps
        }

        private fun readStep(item: JSONObject, path: String, depth: Int): StructuredStep? {
            val type = (item.opt("type") as? String)?.trim()?.lowercase()
            return when (type) {
                "section" -> {
                    val title = text(item.opt("title"), "$path.title") ?: return null
                    StructuredStep.Section(title, children(item, path, depth, required = true))
                }

                "rows", "row", "round", "rounds" -> readRows(item, path, depth, type)
                "repeat" -> {
                    val times = whole(item.opt("times"), "$path.times", 1, StructuredGuideLimits.MAX_REPEAT_TIMES)
                    val label = (item.opt("label") as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    val steps = children(item, path, depth, required = true)
                    times?.let { StructuredStep.Repeat(it, label, steps) }
                }

                "instruction", "step", "note" -> text(item.opt("text"), "$path.text")?.let(StructuredStep::Instruction)
                null -> null.also { problem("$path.type", "The step has no type.") }
                else -> null.also { problem("$path.type", "Unknown step type \"$type\".") }
            }
        }

        private fun readRows(item: JSONObject, path: String, depth: Int, type: String): StructuredStep? {
            val unit = normaliseUnit((item.opt("unit") as? String) ?: if (type.startsWith("round")) "round" else "row")
            val from = whole(item.opt("from") ?: item.opt("number"), "$path.from", 1, StructuredGuideLimits.MAX_ROW_NUMBER)
            val to = if (item.has("to")) {
                whole(item.opt("to"), "$path.to", 1, StructuredGuideLimits.MAX_ROW_NUMBER)
            } else {
                from
            }
            if (from != null && to != null && to < from) {
                problem("$path.to", "Ends at $unit $to, before it starts at $from.")
                return null
            }
            val steps = buildList {
                (item.opt("text") as? String)?.let { raw -> text(raw, "$path.text")?.let { add(StructuredStep.Instruction(it)) } }
                addAll(children(item, path, depth, required = false))
            }
            if (steps.isEmpty()) {
                problem(path, "The $unit has no text or steps.")
                return null
            }
            return if (from != null && to != null) StructuredStep.Rows(unit, from, to, steps) else null
        }

        private fun children(item: JSONObject, path: String, depth: Int, required: Boolean): List<StructuredStep> {
            val array = item.opt("steps") as? JSONArray
            if (array == null || array.length() == 0) {
                if (required) problem("$path.steps", "Needs at least one step inside.")
                return emptyList()
            }
            return readSteps(array, "$path.steps", depth + 1)
        }

        private fun readStrings(root: JSONObject, key: String): List<String> {
            val value = root.opt(key) ?: return emptyList()
            if (value is String) return listOfNotNull(text(value, key))
            val array = value as? JSONArray ?: return emptyList<String>().also { problem(key, "Expected a list.") }
            return (0 until array.length()).mapNotNull { index ->
                when (val item = array.opt(index)) {
                    is String -> text(item, "$key[$index]")
                    else -> null.also { problem("$key[$index]", "Expected text.") }
                }
            }
        }

        private fun readAbbreviations(root: JSONObject): List<Abbreviation> {
            return when (val value = root.opt("abbreviations")) {
                null -> emptyList()
                // {"sc": "single crochet"} is accepted as well as the list form.
                is JSONObject -> value.keys().asSequence().mapNotNull { term ->
                    text(value.opt(term), "abbreviations.$term")?.let { Abbreviation(term.trim(), it) }
                }.toList()

                is JSONArray -> (0 until value.length()).mapNotNull { index ->
                    val item = value.opt(index) as? JSONObject
                    val path = "abbreviations[$index]"
                    if (item == null) {
                        null.also { problem(path, "Expected {\"term\", \"meaning\"}.") }
                    } else {
                        val term = text(item.opt("term"), "$path.term")
                        val meaning = text(item.opt("meaning"), "$path.meaning")
                        if (term != null && meaning != null) Abbreviation(term, meaning) else null
                    }
                }

                else -> emptyList<Abbreviation>().also { problem("abbreviations", "Expected a list.") }
            }
        }

        private fun text(value: Any?, path: String): String? {
            val text = (value as? String)?.trim()
            return when {
                text.isNullOrEmpty() -> null.also { problem(path, "Text is missing.") }
                text.length > StructuredGuideLimits.MAX_TEXT_LENGTH ->
                    null.also { problem(path, "Text is longer than ${StructuredGuideLimits.MAX_TEXT_LENGTH} characters.") }

                else -> text
            }
        }

        private fun whole(value: Any?, path: String, min: Int, max: Int): Int? {
            val number = when (value) {
                is Int -> value
                is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
                is Double -> value.takeIf { it % 1.0 == 0.0 && it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }?.toInt()
                is String -> value.trim().toIntOrNull()
                else -> null
            }
            return when {
                number == null -> null.also { problem(path, "Expected a whole number.") }
                number < min || number > max -> null.also { problem(path, "Expected a number from $min to $max, found $number.") }
                else -> number
            }
        }

        private fun problem(path: String, message: String) {
            if (problems.size < MAX_REPORTED_PROBLEMS) problems += StructuredGuideProblem(path, message)
        }
    }

    companion object {
        const val FORMAT = "stitchbook-guide"
        const val SUPPORTED_VERSION = 1
        private const val MAX_REPORTED_PROBLEMS = 25

        private fun invalid(path: String, message: String) =
            StructuredGuideDecodeResult.Invalid(listOf(StructuredGuideProblem(path, message)))

        /** The outermost `{...}` in [reply], ignoring code fences and surrounding prose. */
        internal fun extractJsonObject(reply: String): String? {
            val start = reply.indexOf('{')
            val end = reply.lastIndexOf('}')
            return if (start >= 0 && end > start) reply.substring(start, end + 1) else null
        }

        internal fun normaliseUnit(raw: String): String = when (val unit = raw.trim().lowercase()) {
            "rows", "r" -> "row"
            "rounds", "rnd", "rnds", "rd", "rds" -> "round"
            "" -> "row"
            else -> unit
        }
    }
}
