package com.macareen.stitchbook2.domain.parsing

import org.junit.Assert.*
import org.junit.Test

class PatternDocumentTitleTest {
    private fun document(vararg lines: String, metadata: String? = null) = ExtractedDocument(
        1, lines.mapIndexed { index, text -> ExtractedLine(text, SourceReference(1, index + 1)) }, metadata
    )
    @Test fun visibleTitleWinsOverWordExportFilename() {
        assertEquals("AUTUMN CARDIGAN", PatternDocumentTitle.find(document("AUTUMN CARDIGAN", "Sizes: S, M, L",
            metadata = "Microsoft Word - Autumn Cardigan ENGLISH.docx")))
    }
    @Test fun arbitraryVisibleTitleRequiresMatchingMetadata() {
        assertEquals("Wandering Meadow", PatternDocumentTitle.find(document("Wandering Meadow",
            metadata = "Microsoft Word - Wandering Meadow.docx")))
        assertNull(PatternDocumentTitle.find(document("Wandering Meadow")))
    }
    @Test fun staleMetadataAndInstructionHeadingsDoNotBecomeTitles() {
        assertNull(PatternDocumentTitle.find(document("BODY", "Row 1: Knit.", metadata = "Old document")))
        assertNull(PatternDocumentTitle.find(document("This cardigan uses simple stitches")))
        assertNull(PatternDocumentTitle.find(document("Autumn Cardigan by A Designer")))
        assertNull(PatternDocumentTitle.find(document("A cosy hat by Ana Example")))
    }
    @Test fun laterPagesAreNotUsedToInventATitle() {
        assertNull(PatternDocumentTitle.find(ExtractedDocument(2, listOf(ExtractedLine("Autumn Cardigan", SourceReference(2, 1))))))
    }
}
