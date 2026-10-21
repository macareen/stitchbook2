package com.macareen.stitchbook2.domain.parsing

import org.junit.Assert.*
import org.junit.Test

class PdfSourceSelectionTest {
    @Test fun adjacentColumnsAreAllowedAndTheirOrderIsPreserved() {
        val right = PdfSourceRegion(2, 50, 10, 100, 90)
        val left = PdfSourceRegion(2, 0, 10, 50, 90)
        val selection = PdfSourceSelection(listOf(right, left, PdfSourceRegion(1)))
        selection.validatePageCount(2)
        assertEquals(right, selection.regions.first())
        assertTrue(selection.description().startsWith("1. p.2: left 50%"))
    }

    @Test fun overlappingDuplicateAndEmptySelectionsAreRefused() {
        for (regions in listOf(emptyList(), listOf(PdfSourceRegion(1), PdfSourceRegion(1)),
            listOf(PdfSourceRegion(1, right = 60), PdfSourceRegion(1, left = 50)))) {
            assertThrows(IllegalArgumentException::class.java) { PdfSourceSelection(regions) }
        }
    }

    @Test fun invalidCoordinatesAndMissingPagesAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { PdfSourceRegion(0) }
        assertThrows(IllegalArgumentException::class.java) { PdfSourceRegion(1, left = 50, right = 50) }
        assertThrows(IllegalArgumentException::class.java) { PdfSourceRegion(1, top = -1) }
        assertThrows(IllegalArgumentException::class.java) { PdfSourceRegion(1, bottom = 101) }
        assertThrows(IllegalArgumentException::class.java) { PdfSourceSelection(listOf(PdfSourceRegion(3))).validatePageCount(2) }
    }
}
