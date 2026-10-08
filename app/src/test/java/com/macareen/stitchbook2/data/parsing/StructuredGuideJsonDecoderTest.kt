package com.macareen.stitchbook2.data.parsing

import com.macareen.stitchbook2.domain.parsing.Abbreviation
import com.macareen.stitchbook2.domain.parsing.StructuredGuide
import com.macareen.stitchbook2.domain.parsing.StructuredGuideDecodeResult
import com.macareen.stitchbook2.domain.parsing.StructuredGuideLimits
import com.macareen.stitchbook2.domain.parsing.StructuredGuidePrompt
import com.macareen.stitchbook2.domain.parsing.StructuredStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StructuredGuideJsonDecoderTest {

    private val decoder = StructuredGuideJsonDecoder()

    @Test
    fun thePromptsOwnExampleDecodes() {
        val guide = valid(StructuredGuidePrompt.SCHEMA_EXAMPLE)

        assertEquals("Guide name", guide.name)
        assertEquals(listOf("Worsted yarn, 200 m", "5 mm hook"), guide.materials)
        assertEquals(listOf(Abbreviation("sc", "single crochet")), guide.abbreviations)
        val body = guide.steps.single() as StructuredStep.Section
        assertEquals("Body", body.title)
        val repeat = body.steps[2] as StructuredStep.Repeat
        assertEquals(3, repeat.times)
        assertEquals("Rounds 3-4", repeat.label)
        assertEquals(listOf("Round 7 refers to a chart that is not in the text."), guide.review)
        assertEquals(5, guide.stepCount)
    }

    @Test
    fun proseAndCodeFencesAroundTheJsonAreIgnored() {
        val reply = """
            Here is your guide:
            ```json
            {"steps": [{"type": "instruction", "text": "Cast on 40."}]}
            ```
            Let me know if you need changes!
        """.trimIndent()

        assertEquals(listOf(StructuredStep.Instruction("Cast on 40.")), valid(reply).steps)
    }

    @Test
    fun rowSpellingsAndShortFormsAreNormalised() {
        val guide = valid(
            """{"steps": [
                {"type": "round", "number": 1, "text": "Ch 4, join."},
                {"type": "rows", "unit": "Rnds", "from": "2", "to": 5, "text": "Sc around."},
                "Fasten off."
            ]}"""
        )

        assertEquals(StructuredStep.Rows("round", 1, 1, listOf(StructuredStep.Instruction("Ch 4, join."))), guide.steps[0])
        assertEquals(StructuredStep.Rows("round", 2, 5, listOf(StructuredStep.Instruction("Sc around."))), guide.steps[1])
        assertEquals(StructuredStep.Instruction("Fasten off."), guide.steps[2])
    }

    @Test
    fun abbreviationsAcceptAnObjectMap() {
        val guide = valid("""{"abbreviations": {"k2tog": "knit two together"}, "steps": ["Knit."]}""")

        assertEquals(listOf(Abbreviation("k2tog", "knit two together")), guide.abbreviations)
    }

    @Test
    fun problemsNameWhereTheyAre() {
        val problems = invalid(
            """{"steps": [
                {"type": "rows", "from": 5, "to": 2, "text": "Knit."},
                {"type": "repeat", "times": 0, "steps": ["Purl."]},
                {"type": "section", "title": "Edging"},
                {"type": "chart", "text": "See chart A"},
                {"type": "instruction", "text": "   "}
            ]}"""
        )

        assertEquals(
            listOf("steps[0].to", "steps[1].times", "steps[2].steps", "steps[3].type", "steps[4].text"),
            problems
        )
    }

    @Test
    fun aReplyWithoutStepsOrJsonIsRejected() {
        assertEquals(listOf("steps"), invalid("""{"name": "Hat"}"""))
        assertEquals(listOf(""), invalid("Sorry, I can't read that file."))
        assertEquals(listOf(""), invalid("{ not json }"))
    }

    @Test
    fun aDifferentFormatOrNewerVersionIsRejected() {
        assertEquals(listOf("format"), invalid("""{"format": "other-app", "steps": ["Knit."]}"""))
        assertEquals(listOf("version"), invalid("""{"version": 2, "steps": ["Knit."]}"""))
    }

    @Test
    fun deepNestingIsRejected() {
        var steps = """["Knit."]"""
        repeat(StructuredGuideLimits.MAX_DEPTH) { steps = """[{"type": "repeat", "times": 2, "steps": $steps}]""" }

        assertTrue(invalid("""{"steps": $steps}""").single().endsWith(".steps"))
    }

    @Test
    fun unitsNormaliseToTheSingularTheFocusScreenShows() {
        assertEquals("row", StructuredGuideJsonDecoder.normaliseUnit(" Rows "))
        assertEquals("round", StructuredGuideJsonDecoder.normaliseUnit("rnd"))
        assertEquals("pass", StructuredGuideJsonDecoder.normaliseUnit("Pass"))
    }

    private fun valid(reply: String): StructuredGuide = when (val result = decoder.decode(reply)) {
        is StructuredGuideDecodeResult.Valid -> result.guide
        is StructuredGuideDecodeResult.Invalid -> fail("Expected valid but got ${result.problems}") as Nothing
    }

    private fun invalid(reply: String): List<String> = when (val result = decoder.decode(reply)) {
        is StructuredGuideDecodeResult.Valid -> fail("Expected problems but got ${result.guide}") as Nothing
        is StructuredGuideDecodeResult.Invalid -> result.problems.map { it.path }
    }
}
