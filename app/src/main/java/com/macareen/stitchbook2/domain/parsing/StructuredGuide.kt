package com.macareen.stitchbook2.domain.parsing

/**
 * A guide described as structured data rather than free text: the shared
 * exchange format between Stitchbook and any helper that reads a pattern
 * for the user (an assistant the user copies a prompt into, an on-device
 * model, or a future API integration). Every helper produces this one shape
 * and Stitchbook validates it the same way, so no helper is trusted to write
 * into the database directly.
 *
 * The JSON form is documented in [StructuredGuidePrompt] and decoded by a
 * [StructuredGuideDecoder]. It only describes steps; it carries no ids,
 * timestamps, or execution state.
 */
data class StructuredGuide(
    val name: String?,
    val materials: List<String> = emptyList(),
    val abbreviations: List<Abbreviation> = emptyList(),
    val notes: List<String> = emptyList(),
    val steps: List<StructuredStep>,
    /** Anything the helper could not read confidently. Shown to the user, never dropped. */
    val review: List<String> = emptyList()
) {
    /** Number of steps a person would work through, counting nested steps. */
    val stepCount: Int get() = steps.sumOf { it.stepCount }
}

data class Abbreviation(val term: String, val meaning: String)

sealed interface StructuredStep {
    val stepCount: Int

    data class Section(val title: String, val steps: List<StructuredStep>) : StructuredStep {
        override val stepCount: Int get() = steps.sumOf { it.stepCount }
    }

    /** One row/round, or a span worked the same way ("Rounds 4-10: sc around"). */
    data class Rows(
        val unit: String,
        val from: Int,
        val to: Int,
        val steps: List<StructuredStep>
    ) : StructuredStep {
        override val stepCount: Int get() = maxOf(1, steps.sumOf { it.stepCount })
    }

    data class Repeat(val times: Int, val label: String?, val steps: List<StructuredStep>) : StructuredStep {
        override val stepCount: Int get() = steps.sumOf { it.stepCount }
    }

    data class Instruction(val text: String) : StructuredStep {
        override val stepCount: Int get() = 1
    }
}

/** One reason a reply could not be turned into a guide, with where it was found ("steps[2].from"). */
data class StructuredGuideProblem(val path: String, val message: String)

sealed interface StructuredGuideDecodeResult {
    data class Valid(val guide: StructuredGuide) : StructuredGuideDecodeResult
    data class Invalid(val problems: List<StructuredGuideProblem>) : StructuredGuideDecodeResult
}

/**
 * Reads a helper's reply (which may wrap the JSON in prose or a code fence)
 * into a validated [StructuredGuide]. Implemented in the data layer so the
 * domain stays free of a JSON library.
 */
fun interface StructuredGuideDecoder {
    fun decode(reply: String): StructuredGuideDecodeResult
}

/** Limits that keep a pasted reply from producing an unusable or enormous draft. */
object StructuredGuideLimits {
    const val MAX_DEPTH = 6
    const val MAX_STEPS = 3_000
    const val MAX_TEXT_LENGTH = 2_000
    const val MAX_ROW_NUMBER = 100_000
    const val MAX_REPEAT_TIMES = 10_000
}
