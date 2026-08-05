package com.macareen.stitchbook2.domain.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractedDocumentTest {

    @Test
    fun `source reference rejects non-positive page or line numbers`() {
        assertThrows(IllegalArgumentException::class.java) { SourceReference(pageNumber = 0, lineNumber = 1) }
        assertThrows(IllegalArgumentException::class.java) { SourceReference(pageNumber = 1, lineNumber = 0) }
    }

    @Test
    fun `extracted line rejects blank text`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtractedLine(text = "   ", source = SourceReference(1, 1))
        }
    }

    @Test
    fun `document rejects a line whose page exceeds the page count`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtractedDocument(
                pageCount = 1,
                lines = listOf(ExtractedLine("Cast on 80 stitches.", SourceReference(pageNumber = 2, lineNumber = 1)))
            )
        }
    }

    @Test
    fun `hasNoExtractableText is true only when no lines were extracted`() {
        val empty = ExtractedDocument(pageCount = 2, lines = emptyList())
        val nonEmpty = ExtractedDocument(
            pageCount = 1,
            lines = listOf(ExtractedLine("Cast on 80 stitches.", SourceReference(1, 1)))
        )

        assertTrue(empty.hasNoExtractableText)
        assertFalse(nonEmpty.hasNoExtractableText)
    }

    @Test
    fun `pagesWithoutText reports pages that contributed no lines`() {
        val document = ExtractedDocument(
            pageCount = 3,
            lines = listOf(
                ExtractedLine("Cast on 80 stitches.", SourceReference(pageNumber = 1, lineNumber = 1)),
                ExtractedLine("Knit all stitches.", SourceReference(pageNumber = 3, lineNumber = 1))
            )
        )

        assertEquals(listOf(2), document.pagesWithoutText())
    }

    @Test
    fun `pagesWithoutText is empty when every page contributed text`() {
        val document = ExtractedDocument(
            pageCount = 1,
            lines = listOf(ExtractedLine("Cast on 80 stitches.", SourceReference(1, 1)))
        )

        assertTrue(document.pagesWithoutText().isEmpty())
    }
}
