package com.macareen.stitchbook2.data.parsing

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.parsing.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in emulator-only regression input. No personal pattern fixture or extracted text belongs in Git. */
class OptionalLocalPatternDiagnosticTest {
    @Test fun localPdfResolvesEverySizeAndKeepsNumberedRepeatChildren() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("checkLocalPattern") == "true")
        assertTrue("Private fixture checks require an isolated emulator", Build.HARDWARE in listOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        PDFBoxResourceLoader.init(context)
        // Fixed path: never interpolate a shell command from instrumentation arguments.
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
            .executeShellCommand("cat /data/local/tmp/stitchbook-private-pattern.pdf")).use { it.readBytes() }
        val document = PdfBoxTextExtractor(context, MlKitPdfPageOcr()).extract(ByteArrayInputStream(bytes))
        val found = PatternSizes.findInDocument(document)
        assertNotNull("The provided PDF must expose its size list", found)
        val (labels, sizeLine) = requireNotNull(found)
        assertEquals(args.getString("expectedSizeCount")?.toIntOrNull() ?: labels.size, labels.size)
        val input = document.copy(lines = document.lines.filterNot { it.source == sizeLine.source })
        val title = PatternDocumentTitle.find(document)
        val alternating = Regex("""\d+\s*\(\d+\)\s+\d+\s*\(\d+\)""")
        fun descendants(nodes: List<ParsedNode>): List<ParsedNode> = nodes.flatMap { node ->
            listOf(node) + when (node) {
                is ParsedSection -> descendants(node.children)
                is ParsedRange -> descendants(node.children)
                is ParsedRepeat -> descendants(node.children)
                is ParsedInstruction -> emptyList()
            }
        }
        labels.indices.forEach { index ->
            val selected = SizeSpecificInstructions.select(input, labels, index)
            val (parsed, details) = PatternTextParser.parseWithDetails(selected.document, title)
            assertNotNull("Pattern reference material must remain available", com.macareen.stitchbook2.domain.guide.GuideReference.notes(details))
            val nodes = descendants(parsed.rootNodes)
            val instructions = nodes.filterIsInstance<ParsedInstruction>()
            assertFalse("Glossary definitions must not become executable steps", instructions.any {
                Regex("""^(?:kfb|skp|ssk|k2tog|rs|ws)\s*[:=]""", RegexOption.IGNORE_CASE).containsMatchIn(it.text)
            })
            assertFalse("Per-size ordinal runs remain unresolved", instructions.any {
                Regex("""\d+(?:st|nd|rd|th)\s*\(\d+(?:st|nd|rd|th)\)\s+\d+(?:st|nd|rd|th)""", RegexOption.IGNORE_CASE).containsMatchIn(it.text)
            })
            assertTrue("Each selected size must retain instructions", instructions.isNotEmpty())
            assertFalse("An alternating size run remains at size index $index",
                instructions.any { alternating.containsMatchIn(it.text) })
            val numberedRepeats = nodes.filterIsInstance<ParsedRepeat>().filter { repeat ->
                repeat.children.size >= 2 && repeat.children.all { it is ParsedInstruction &&
                    Regex("""^Row\s+\d+""", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
            }
            numberedRepeats.forEach { repeat ->
                val children = repeat.children.mapIndexed { childIndex, node ->
                    com.macareen.stitchbook2.domain.execution.Instruction(
                        com.macareen.stitchbook2.domain.execution.NodeId("row-$childIndex"), (node as ParsedInstruction).text)
                }
                val container = com.macareen.stitchbook2.domain.execution.Repeat(
                    com.macareen.stitchbook2.domain.execution.NodeId("repeat"), repeat.count, children = children.map { it.id })
                val definition = com.macareen.stitchbook2.domain.execution.GuideDefinition(
                    com.macareen.stitchbook2.domain.execution.GuideId("private-check"),
                    com.macareen.stitchbook2.domain.execution.DefinitionRevisionId("private-revision"),
                    listOf(container.id), listOf(container) + children)
                val validated = com.macareen.stitchbook2.domain.execution.GuideDefinitionValidator.validate(definition)
                val address = com.macareen.stitchbook2.domain.execution.GuideTraversal(validated).occurrences().first().address
                assertNotNull("Imported numbered rows must expose their repeat context at size index $index",
                    com.macareen.stitchbook2.domain.execution.RepeatedRowContext.of(validated, address))
            }
            val rowPairs = numberedRepeats.size
            assertTrue("Expected numbered row instructions inside repeats at size index $index", rowPairs > 0)
            android.util.Log.i("StitchbookPatternCheck", "sizeIndex=$index sizeCount=${labels.size} instructionCount=${instructions.size} numberedRepeatCount=$rowPairs remainingAlternatingRuns=0")
        }
    }
}
