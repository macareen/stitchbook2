package com.macareen.stitchbook2.domain.model

import com.macareen.stitchbook2.feature.projects.PieceEditor
import org.junit.Assert.*
import org.junit.Test

class PieceQuantityTest {
    @Test fun quantitiesProduceSeparatePredictableNamesWithoutChangingASingleName() {
        assertEquals(listOf("Sleeve"), pieceNamesForQuantity(" Sleeve ", 1))
        assertEquals(listOf("Sleeve 1", "Sleeve 2"), pieceNamesForQuantity("Sleeve", 2))
        val hundred = pieceNamesForQuantity("Motif", 100)!!
        assertEquals(100, hundred.distinct().size)
        assertEquals("Motif 100", hundred.last())
    }
    @Test fun invalidQuantitiesOrNamesAreRejectedWithoutTruncation() {
        for (count in listOf(Int.MIN_VALUE, -1, 0, 101, Int.MAX_VALUE)) assertNull(pieceNamesForQuantity("Motif", count))
        assertNull(pieceNamesForQuantity(" ", 2))
        assertNull(pieceNamesForQuantity("a".repeat(101), 1))
        assertNotNull(pieceNamesForQuantity("a".repeat(100), 1))
        assertNull(pieceNamesForQuantity("a".repeat(99), 2))
        assertNotNull(pieceNamesForQuantity("a".repeat(96), 100))
        assertNull(pieceNamesForQuantity("a".repeat(97), 100))
    }
    @Test fun editorSeparatesQuantityCreationFromRenameAndFreshCopy() {
        val piece = ProjectPiece("piece", "project", "guide", "Sleeve", false, 0, 0)
        assertFalse(PieceEditor(name = "Motif", guideId = "guide", quantity = "overflow").canSave)
        assertEquals(listOf("Motif 1", "Motif 2"), PieceEditor(name = "Motif", guideId = "guide", quantity = "2").names)
        assertEquals(listOf("Sleeve"), PieceEditor(expected = piece, name = "Sleeve", guideId = "guide", quantity = "99").names)
        assertEquals(listOf("Other sleeve"), PieceEditor(copySource = piece, name = "Other sleeve", guideId = "guide", quantity = "99").names)
        assertFalse(PieceEditor(name = "Sleeve", guideId = "guide", busy = true).canSave)
    }
}
