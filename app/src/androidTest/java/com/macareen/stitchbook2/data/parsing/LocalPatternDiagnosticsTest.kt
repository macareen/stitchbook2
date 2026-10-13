package com.macareen.stitchbook2.data.parsing

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.parsing.*
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in diagnostics for a user-provided PDF. No pattern text or files belong in source control. */
class LocalPatternDiagnosticsTest {
    @Test fun inspectProvidedDigitalPattern() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("localPatternDiagnostics") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pipe = instrumentation.uiAutomation.executeShellCommand("cat /sdcard/Download/stitchbook-comparison.pdf")
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(pipe).use { it.readBytes() }
        assertTrue("Provide a PDF smaller than 20 MiB in emulator Downloads", bytes.size in 1..20_971_520)
        val noNetworkOcr = object : PdfPageOcr {
            override suspend fun recognizeText(page: Bitmap): List<String> = emptyList()
        }
        val document = PdfBoxTextExtractor(instrumentation.targetContext, noNetworkOcr)
            .extract(ByteArrayInputStream(bytes))
        assertTrue("The diagnostic expects a digital text layer", document.lines.isNotEmpty())
        val title = PatternDocumentTitle.find(document)
        args.getString("expectedTitle")?.let {
            org.junit.Assert.assertEquals(it, title)
            org.junit.Assert.assertEquals(it, PdfBoxPatternTitleReader(instrumentation.targetContext).read(ByteArrayInputStream(bytes)))
        }
        val found = PatternSizes.findInDocument(document)
        val labels = found?.first.orEmpty()
        val report = JSONObject().put("pages", document.pageCount).put("lines", document.lines.size)
            .put("sizes", JSONArray(labels)).put("sizeSourcePage", found?.second?.source?.pageNumber)
        val results = JSONArray()
        labels.indices.forEach { index ->
            val sized = SizeSpecificInstructions.select(document.copy(lines = document.lines.filterNot { it == found?.second }), labels, index)
            val (parsed, details) = PatternTextParser.parseWithDetails(sized.document, args.getString("patternTitle") ?: title)
            fun count(nodes: List<ParsedNode>): Int = nodes.sumOf { node -> when (node) {
                is ParsedSection -> count(node.children)
                is ParsedRange -> count(node.children)
                is ParsedRepeat -> count(node.children)
                is ParsedInstruction -> 1
            } }
            fun repeats(nodes: List<ParsedNode>): List<ParsedRepeat> = nodes.flatMap { node -> when (node) {
                is ParsedSection -> repeats(node.children)
                is ParsedRange -> repeats(node.children)
                is ParsedRepeat -> listOf(node) + repeats(node.children)
                is ParsedInstruction -> emptyList()
            } }
            val repeatNodes = repeats(parsed.rootNodes)
            if (labels[index] == args.getString("expectedRepeatSize")) {
                val structural = JSONObject().put("size", labels[index]).put("repeats", JSONArray(repeatNodes.map {
                    JSONObject().put("count", it.count).put("page", it.source.pageNumber).put("children", it.children.size)
                })).put("issues", JSONArray((parsed.issues + sized.issues).map {
                    JSONObject().put("page", it.source.pageNumber).put("line", it.source.lineNumber)
                        .put("kind", if (it.message.startsWith("Could not find")) "repeat_target" else if (it.message.contains("size-only")) "size_scope" else "other")
                }))
                Log.i("LocalPatternDiagnostics", structural.toString())
                if (args.getString("privateTextDump") == "true" && android.os.Build.VERSION.SDK_INT >= 29) {
                    val name = "stitchbook-private-native-${System.currentTimeMillis()}.txt"
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = instrumentation.targetContext.contentResolver
                    val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
                    val text = "ORIGINAL\n" + document.lines.joinToString("\n") { "${it.source.pageNumber}:${it.source.lineNumber} ${it.text}" } +
                        "\nSELECTED\n" + sized.document.lines.joinToString("\n") { "${it.source.pageNumber}:${it.source.lineNumber} ${it.text}" }
                    resolver.openOutputStream(uri)!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    Log.i("LocalPatternDiagnostics", "Private emulator-only text dump created")
                }
                val expectedCount = requireNotNull(args.getString("expectedRepeatCount")).toInt()
                val expectedPage = requireNotNull(args.getString("expectedRepeatPage")).toInt()
                assertTrue("Expected a two-row repeat at the selected count and source page",
                    repeatNodes.any { it.count == expectedCount && it.source.pageNumber == expectedPage && it.children.size == 2 })
                assertTrue("Expected no unresolved parser/size issues", parsed.issues.isEmpty() && sized.issues.isEmpty())
            }
            results.put(JSONObject().put("size", labels[index]).put("instructions", count(parsed.rootNodes))
                .put("repeatCounts", JSONArray(repeatNodes.map { it.count }))
                .put("issues", parsed.issues.size + sized.issues.size).put("hasGauge", details.gauge != null))
        }
        report.put("results", results)
        report.put("diagnosticLabel", args.getString("diagnosticLabel") ?: "private")
        Log.i("LocalPatternDiagnostics", report.toString())
        args.getString("expectedSizes")?.let { expected ->
            org.junit.Assert.assertEquals(expected.split(","), labels)
        }
    }
}
