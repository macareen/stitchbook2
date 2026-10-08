package com.macareen.stitchbook2.domain.usecase

import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.ParsedPatternMapper
import com.macareen.stitchbook2.domain.parsing.ParsingIssue
import com.macareen.stitchbook2.domain.parsing.PatternSizes
import com.macareen.stitchbook2.domain.parsing.PatternTextParser
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractionException
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.repository.GuideRepository
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ties together PDF text extraction, deterministic parsing, and Draft-node
 * mapping into one operation: create a new Guide, then save its Draft
 * populated from [pdf]'s content, exactly the way a manually-authored Draft
 * would be saved. Never publishes -- the result is an ordinary editable
 * Draft the user reviews in the existing Draft editor, per ROADMAP.md's
 * "Parser foundation" item.
 *
 * This spans two repository-shaped dependencies plus real branching rules
 * (no extractable text, extraction failure), so it is a use case per
 * ARCHITECTURE.md §4 rather than logic folded into a ViewModel.
 */
class CreateGuideFromPdfUseCase(
    private val textExtractor: PdfTextExtractor,
    private val guideRepository: GuideRepository,
    private val newNodeId: () -> String
) {
    sealed interface Result {
        data class Success(val guideId: GuideId, val issueCount: Int) : Result
        /** No text was found even after OCR fallback -- see [PdfTextExtractor]. */
        data object NoExtractableText : Result
        data class ExtractionFailed(val cause: Throwable) : Result
    }

    suspend operator fun invoke(projectId: String, guideName: String, pdf: InputStream): Result {
        val document = extract(pdf) ?: return Result.NoExtractableText
        if (document is Extraction.Failed) return Result.ExtractionFailed(document.cause)
        return createDraft((document as Extraction.Read).document, emptyList()) {
            guideRepository.createGuide(projectId, guideName)
        }
    }

    /**
     * Creates a pattern's guide for [sizeLabel] from the pattern's own PDF,
     * keeping only that size's numbers. The size list comes from the PDF's
     * "Sizes:" line, else from [knownSizes] (the Library entry's sizes). When
     * the size isn't in that list, every size's numbers are kept and the
     * draft says so, so nothing is guessed.
     */
    suspend fun forPattern(
        libraryItemId: String,
        sizeLabel: String,
        knownSizes: String?,
        guideName: String,
        pdf: InputStream
    ): Result {
        val extraction = extract(pdf) ?: return Result.NoExtractableText
        if (extraction is Extraction.Failed) return Result.ExtractionFailed(extraction.cause)
        val document = (extraction as Extraction.Read).document

        val fromPdf = PatternSizes.findInDocument(document)
        val labels = fromPdf?.first ?: knownSizes?.let(PatternSizes::labelsFrom).orEmpty()
        val index = PatternSizes.indexOf(sizeLabel, labels)
        val issueSource = fromPdf?.second?.source ?: document.lines.first().source
        val (sized, issues) = when {
            index != null -> PatternSizes.forSize(document, index, labels.size) to emptyList()
            labels.isEmpty() -> document to listOf(
                ParsingIssue("No size list was found, so every size's numbers are kept for size ${sizeLabel.trim()}.", issueSource)
            )
            else -> document to listOf(
                ParsingIssue(
                    "Size ${sizeLabel.trim()} isn't one of the pattern's sizes (${labels.joinToString(", ")}), so every size's numbers are kept.",
                    issueSource
                )
            )
        }
        return createDraft(sized, issues) {
            guideRepository.createPatternGuide(libraryItemId, sizeLabel, guideName)
        }
    }

    private sealed interface Extraction {
        data class Read(val document: ExtractedDocument) : Extraction
        data class Failed(val cause: Throwable) : Extraction
    }

    /** The PDF's text, a failure, or null when no page had any text. */
    private suspend fun extract(pdf: InputStream): Extraction? {
        // Extraction (PDFBox parsing, and on-device OCR when a page has no
        // text layer) is CPU/IO-bound work that must not run on the caller's
        // dispatcher (a ViewModel's viewModelScope defaults to Main).
        val document = try {
            withContext(Dispatchers.IO) { textExtractor.extract(pdf) }
        } catch (e: PdfTextExtractionException) {
            return Extraction.Failed(e)
        }
        return if (document.hasNoExtractableText) null else Extraction.Read(document)
    }

    private suspend fun createDraft(
        document: ExtractedDocument,
        extraIssues: List<ParsingIssue>,
        createGuide: suspend () -> Guide
    ): Result {
        val parsed = PatternTextParser.parse(document).let { it.copy(issues = extraIssues + it.issues) }
        val mapped = ParsedPatternMapper.toDraftNodes(parsed, newNodeId)

        val guide = createGuide()
        val emptyDraft = requireNotNull(guideRepository.loadDraft(guide.id)) {
            "Newly created guide ${guide.id.value} is missing its draft."
        }
        guideRepository.saveDraft(
            emptyDraft.copy(rootNodeIds = mapped.rootNodeIds, nodes = mapped.nodes)
        )

        return Result.Success(guide.id, parsed.issues.size)
    }
}
