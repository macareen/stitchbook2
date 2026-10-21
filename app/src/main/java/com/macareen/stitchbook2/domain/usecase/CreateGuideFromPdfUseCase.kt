package com.macareen.stitchbook2.domain.usecase

import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.library.PatternFolderPlanner
import com.macareen.stitchbook2.domain.parsing.PatternDocumentTitle
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.ExtractedLine
import com.macareen.stitchbook2.domain.parsing.SourceReference
import com.macareen.stitchbook2.domain.parsing.ParsedPatternMapper
import com.macareen.stitchbook2.domain.parsing.ParsingIssue
import com.macareen.stitchbook2.domain.parsing.PatternDetails
import com.macareen.stitchbook2.domain.parsing.SizeSpecificInstructions
import com.macareen.stitchbook2.domain.parsing.PatternSizes
import com.macareen.stitchbook2.domain.parsing.PatternTextParser
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractionException
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    private val newNodeId: () -> String,
    /** When given, a pattern guide also fills the pattern's empty details from the PDF's front matter. */
    private val libraryRepository: LibraryRepository? = null
) {
    sealed interface Result {
        data class Success(val guideId: GuideId, val issueCount: Int) : Result
        /** No text was found even after OCR fallback -- see [PdfTextExtractor]. */
        data object NoExtractableText : Result
        data class ExtractionFailed(val cause: Throwable) : Result
    }

    suspend fun patternInfo(pdf: InputStream): com.macareen.stitchbook2.domain.library.PatternPdfInfo? {
        val extraction = extract(pdf) as? Extraction.Read ?: return null
        val title = PatternDocumentTitle.find(extraction.document)
        val details = PatternTextParser.parseWithDetails(extraction.document, title).second
        return com.macareen.stitchbook2.domain.library.PatternPdfInfo(title, details, PatternSizes.findInDocument(extraction.document)?.first.orEmpty())
    }

    /** Read and fill only blank pattern fields, guarded against concurrent edits/removal. */
    suspend fun setupPattern(libraryItemId: String, pdf: InputStream, expectedPdfUri: String? = null): com.macareen.stitchbook2.domain.library.PatternPdfInfo? {
        val repository = libraryRepository ?: return patternInfo(pdf)
        val expected = repository.observeLibraryItem(libraryItemId).first() ?: return null
        if (expectedPdfUri != null && expected.pdfUri != expectedPdfUri) return null
        val info = patternInfo(pdf) ?: return null
        val filled = com.macareen.stitchbook2.domain.library.PatternSuggestions.apply(expected, info)
        if (filled != expected) repository.saveIfUnchanged(expected, filled.copy(updatedAt = System.currentTimeMillis()))
        return info
    }

    /** Reads choices before guide creation without saving or changing the pattern. */
    suspend fun sizeChoices(pdf: InputStream): List<String> {
        val extraction = extract(pdf)
        return if (extraction is Extraction.Read) PatternSizes.findInDocument(extraction.document)?.first.orEmpty()
            else emptyList()
    }

    suspend operator fun invoke(projectId: String, guideName: String, pdf: InputStream, preferDocumentTitle: Boolean = false): Result {
        val document = extract(pdf) ?: return Result.NoExtractableText
        if (document is Extraction.Failed) return Result.ExtractionFailed(document.cause)
        val extracted = (document as Extraction.Read).document
        val title = PatternDocumentTitle.find(extracted)
        return createDraft(extracted, extractionIssues(extracted), patternTitle = title ?: guideName) {
            guideRepository.createGuide(projectId, if (preferDocumentTitle) title ?: guideName else guideName)
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
        val item = libraryRepository?.observeLibraryItem(libraryItemId)?.first()
        val extraction = extract(pdf) ?: return Result.NoExtractableText
        if (extraction is Extraction.Failed) return Result.ExtractionFailed(extraction.cause)
        val document = (extraction as Extraction.Read).document

        val originalTitle = item?.title ?: guideName.substringBefore(" \u00b7 ")
        val detectedTitle = PatternDocumentTitle.find(document)
        val automaticTitle = item?.pdfFileName?.let {
            PatternFolderPlanner.titleFor(it) == originalTitle
        } == true
        val title = detectedTitle ?: originalTitle
        val updatedGuideName = if (automaticTitle && detectedTitle != null &&
            guideName == "$originalTitle \u00b7 $sizeLabel") "$detectedTitle \u00b7 $sizeLabel" else guideName
        val details = PatternTextParser.parseWithDetails(document, title).second
        val fromPdf = PatternSizes.findInDocument(document)
        val labels = fromPdf?.first ?: knownSizes?.let(PatternSizes::labelsFrom).orEmpty()
        val index = PatternSizes.indexOf(sizeLabel, labels)
        val issueSource = fromPdf?.second?.source ?: document.lines.first().source
        val (sized, issues) = when {
            index != null -> SizeSpecificInstructions.select(document.withoutLine(fromPdf?.second), labels, index).let {
                it.document to it.issues
            }
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
        return createDraft(
            document = sized,
            extraIssues = extractionIssues(document) + issues,
            patternTitle = title,
            selectedSizeCount = labels.size.takeIf { index != null },
            storeReferenceOnGuide = libraryRepository == null,
            onDetails = { fillPatternDetails(item, details, fromPdf?.second?.text, detectedTitle.takeIf { automaticTitle }) }
        ) {
            guideRepository.createPatternGuide(libraryItemId, sizeLabel, updatedGuideName)
        }
    }

    /**
     * Copies what the pattern says about itself into the Library entry's empty
     * fields. Anything the user already wrote is kept as it is.
     */
    private suspend fun fillPatternDetails(expected: com.macareen.stitchbook2.domain.model.LibraryItem?, details: PatternDetails, sizesLine: String?, detectedTitle: String? = null) {
        val repository = libraryRepository ?: return
        if (details.isEmpty && sizesLine == null && detectedTitle == null) return
        val item = expected ?: return
        val info = com.macareen.stitchbook2.domain.library.PatternPdfInfo(detectedTitle, details,
            sizesLine?.let(PatternSizes::labelsFrom).orEmpty())
        val filled = com.macareen.stitchbook2.domain.library.PatternSuggestions.apply(item, info)
        if (filled != item) repository.saveIfUnchanged(item, filled.copy(updatedAt = System.currentTimeMillis()))
    }

    // Check the original extraction, before size selection or cleanup removes intentional text.
    private fun extractionIssues(document: ExtractedDocument): List<ParsingIssue> = document.pagesWithoutText().map { page ->
        ParsingIssue("No readable text was found on PDF page $page. Check the original page for images, charts or missing instructions before following this guide.",
            SourceReference(page, 1))
    }

    private fun ExtractedDocument.withoutLine(line: ExtractedLine?): ExtractedDocument =
        if (line == null) this else copy(lines = lines - line)

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
        patternTitle: String? = null,
        storeReferenceOnGuide: Boolean = true,
        selectedSizeCount: Int? = null,
        onDetails: suspend (PatternDetails) -> Unit = {},
        createGuide: suspend () -> Guide
    ): Result {
        val (steps, details) = PatternTextParser.parseWithDetails(document, patternTitle)
        val parsed = steps.copy(issues = extraIssues + steps.issues + selectedSizeCount?.let { PatternSizes.unresolvedIssues(steps, it) }.orEmpty())
        val mapped = ParsedPatternMapper.toDraftNodes(parsed, newNodeId, reviewIssuesFirst = true)

        val guide = createGuide()
        val emptyDraft = requireNotNull(guideRepository.loadDraft(guide.id)) {
            "Newly created guide ${guide.id.value} is missing its draft."
        }
        guideRepository.saveDraft(
            emptyDraft.copy(rootNodeIds = mapped.rootNodeIds, nodes = mapped.nodes)
        )

        if (storeReferenceOnGuide) com.macareen.stitchbook2.domain.guide.GuideReference.notes(details)?.let { reference ->
            guideRepository.updateGuideMetadata(guide.id, guide.name, reference)
        }
        onDetails(details)
        return Result.Success(guide.id, parsed.issues.size)
    }
}
