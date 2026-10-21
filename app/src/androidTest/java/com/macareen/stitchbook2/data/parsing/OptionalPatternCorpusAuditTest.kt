package com.macareen.stitchbook2.data.parsing

import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.parsing.*
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in read-only corpus audit. Default metrics exclude filenames and extracted prose. */
class OptionalPatternCorpusAuditTest {
    @Test fun auditOnePermissionedPdf() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("auditPattern") == "true")
        assertTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val result = JSONObject()
        try {
            val bytes = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("cat /data/local/tmp/stitchbook-private-pattern.pdf")).use { it.readBytes() }
            val document = PdfBoxTextExtractor(instrumentation.targetContext, MlKitPdfPageOcr()).extract(ByteArrayInputStream(bytes))
            // Explicit diagnostic only: capture privately, never in repository test reports.
            InstrumentationRegistry.getArguments().getString("auditPage")?.toIntOrNull()?.let { page ->
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("auditPageLines", JSONArray(document.lines.filter { it.source.pageNumber == page }
                        .map { it.text }).toString())
                })
            }
            // Private diagnostic only; never enable in ordinary CI or repository test reports.
            if (InstrumentationRegistry.getArguments().getString("auditPieceReferences") == "true") {
                val selected = document.lines.withIndex().filter { (_, line) -> Regex(
                    """\b(?:second|2nd|other|another|opposite)\s+(?:sleeve|sock|mitten|glove|bootie|cuff|panel|leg|arm|ear)|\bmake\s+(?:2|two)\b""",
                    RegexOption.IGNORE_CASE).containsMatchIn(line.text) }.flatMap { (index, _) ->
                        document.lines.subList((index - 1).coerceAtLeast(0), (index + 3).coerceAtMost(document.lines.size))
                    }.distinct()
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("auditPieceReferenceLines", JSONArray(selected.map { line -> JSONObject()
                        .put("page", line.source.pageNumber).put("line", line.source.lineNumber).put("text", line.text) }).toString())
                })
            }
            result.put("pages", document.pageCount).put("lines", document.lines.size)
                .put("unreadablePages", JSONArray(document.pagesWithoutText()))
            val lowText = (1..document.pageCount).filter { page -> document.lines.filter { it.source.pageNumber == page }.sumOf { it.text.length } < 50 }
            result.put("lowTextPages", JSONArray(lowText))
            result.put("chartMentionPages", JSONArray(document.lines.filter {
                Regex("""\b(?:charts?|diagrams?|schematics?|symbol key|legend|esquema|grafico|gráfico)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.text)
            }.map { it.source.pageNumber }.distinct()))
            val found = PatternSizes.findInDocument(document)
            val labels = found?.first.orEmpty()
            result.put("sizeCount", labels.size)
            val title = PatternDocumentTitle.find(document)
            val input = document.copy(lines = document.lines.filterNot { it.source == found?.second?.source })
            fun leaves(nodes: List<ParsedNode>): List<ParsedInstruction> = nodes.flatMap {
                when (it) {
                    is ParsedInstruction -> listOf(it)
                    is ParsedSection -> leaves(it.children)
                    is ParsedRange -> leaves(it.children)
                    is ParsedRepeat -> leaves(it.children)
                }
            }
            fun allNodes(nodes: List<ParsedNode>): List<ParsedNode> = nodes.flatMap { node -> listOf(node) + when (node) {
                is ParsedSection -> allNodes(node.children)
                is ParsedRange -> allNodes(node.children)
                is ParsedRepeat -> allNodes(node.children)
                is ParsedInstruction -> emptyList()
            } }
            val sizes = JSONArray()
            val indices = if (labels.size >= 2) labels.indices.take(50) else listOf(-1)
            indices.forEach { index ->
                val selection = if (index < 0) null else SizeSpecificInstructions.select(input, labels, index)
                val (parsed, details) = PatternTextParser.parseWithDetails(selection?.document ?: document, title)
                val steps = leaves(parsed.rootNodes)
                val metric = JSONObject().put("sizeIndex", index).put("instructions", steps.size)
                    .put("pieceReplaySections", allNodes(parsed.rootNodes).filterIsInstance<ParsedSection>().count { Regex("""\b(?:repeat|work|make)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.title) && Regex("""\b(?:second|other)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.title) })
                    .put("pieceQuantityRepeats", allNodes(parsed.rootNodes).filterIsInstance<ParsedRepeat>().count { it.label == "Piece" })
                    .put("pieceReferenceReviews", parsed.issues.count { it.message.startsWith("This piece refers") })
                    .put("reviewIssues", parsed.issues.size + selection?.issues.orEmpty().size + (if (index >= 0) PatternSizes.unresolvedIssues(parsed, labels.size).size else 0))
                    .put("reference", !details.isEmpty).put("gauge", !details.gauge.isNullOrBlank())
                    .put("tools", !details.tools.isNullOrBlank()).put("yarn", !details.yarn.isNullOrBlank())
                    .put("techniques", !details.techniques.isNullOrBlank()).put("glossary", !details.abbreviations.isNullOrBlank())
                    .put("possibleReferenceSteps", steps.count { Regex("""^(?:abbreviations|glossary|size guide|kfb:|skp:|rs right side)""", RegexOption.IGNORE_CASE).containsMatchIn(it.text) })
                    .put("remainingSizeRuns", if (index < 0) 0 else steps.count { Regex("""\d+(?:st|nd|rd|th)?\s*\(\d+(?:st|nd|rd|th)?\)\s+\d+(?:st|nd|rd|th)?\s*\(\d+(?:st|nd|rd|th)?\)""", RegexOption.IGNORE_CASE).containsMatchIn(it.text) })
                sizes.put(metric)
            }
            result.put("sizes", sizes).put("status", "parsed")
        } catch (error: Exception) { result.put("status", "failed").put("errorType", error.javaClass.simpleName) }
        instrumentation.sendStatus(0, Bundle().apply { putString("auditMetrics", result.toString()) })
    }
}
