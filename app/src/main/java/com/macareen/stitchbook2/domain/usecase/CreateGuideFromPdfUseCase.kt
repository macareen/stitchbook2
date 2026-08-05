package com.macareen.stitchbook2.domain.usecase

import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.parsing.ParsedPatternMapper
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
        // Extraction (PDFBox parsing, and on-device OCR when a page has no
        // text layer) is CPU/IO-bound work that must not run on the caller's
        // dispatcher (a ViewModel's viewModelScope defaults to Main).
        val document = try {
            withContext(Dispatchers.IO) { textExtractor.extract(pdf) }
        } catch (e: PdfTextExtractionException) {
            return Result.ExtractionFailed(e)
        }
        if (document.hasNoExtractableText) return Result.NoExtractableText

        val parsed = PatternTextParser.parse(document)
        val mapped = ParsedPatternMapper.toDraftNodes(parsed, newNodeId)

        val guide = guideRepository.createGuide(projectId, guideName)
        val emptyDraft = requireNotNull(guideRepository.loadDraft(guide.id)) {
            "Newly created guide ${guide.id.value} is missing its draft."
        }
        guideRepository.saveDraft(
            emptyDraft.copy(rootNodeIds = mapped.rootNodeIds, nodes = mapped.nodes)
        )

        return Result.Success(guide.id, parsed.issues.size)
    }
}
