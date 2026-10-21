package com.macareen.stitchbook2.data.parsing

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.database.*
import com.macareen.stitchbook2.data.repository.*
import com.macareen.stitchbook2.domain.guide.*
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.parsing.*
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import com.macareen.stitchbook2.domain.library.PatternSuggestions
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class PatternMetadataImportTest {
    @Test fun setupIsPatternOwnedAndBothSizeGuidesInheritItsReference() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, StitchbookDatabase::class.java).build()
        try {
            database.libraryDao().upsert(LibraryItemEntity("pattern", "scan", "KNITTING", null, null, "", null, false, 0, 0, pdfUri = "content://synthetic/pattern.pdf", pdfFileName = "scan.pdf"))
            val library = LocalLibraryRepository(database.libraryDao())
            val guides = LocalGuideRepository(database.guideDao())
            val ocr = object : PdfPageOcr { override suspend fun recognizeText(page: android.graphics.Bitmap) = emptyList<String>() }
            val useCase = CreateGuideFromPdfUseCase(PdfBoxTextExtractor(context, ocr), guides, { java.util.UUID.randomUUID().toString() }, library)
            val pdf = PdfFixtures.buildSyntheticPages(listOf(listOf("Sample wrap", "Description: A small textured wrap.", "Sizes: S, M", "Gauge: 20 stitches over 10 cm", "Yarn: 200 (300) yards of DK wool", "Needles: 4 mm", "Techniques", "Short rows", "ABBREVIATIONS", "kfb: Knit front and back", "PATTERN", "Cast on 6 (8) sts.", "Row 1: kfb, knit to end.")))
            val info = useCase.setupPattern("pattern", ByteArrayInputStream(pdf))!!
            assertEquals(listOf("S", "M"), info.sizes)
            val pattern = library.observeLibraryItem("pattern").first()!!
            assertEquals("20 stitches over 10 cm", pattern.gauge)
            assertEquals("4 mm", pattern.recommendedTools)
            assertTrue(pattern.notes!!.contains("200 (300) yards"))
            assertTrue(pattern.notes!!.contains("## Techniques"))
            val first = useCase.forPattern("pattern", "S", null, "Small wrap", ByteArrayInputStream(pdf)) as CreateGuideFromPdfUseCase.Result.Success
            val second = useCase.forPattern("pattern", "M", null, "Medium wrap", ByteArrayInputStream(pdf)) as CreateGuideFromPdfUseCase.Result.Success
            assertNull(guides.getGuide(first.guideId)!!.notes)
            assertNull(guides.getGuide(second.guideId)!!.notes)
            assertEquals("kfb", GuideReference.glossary(guides.getReferenceNotes(second.guideId)).single().term)
            assertTrue(library.observeLibraryItem("pattern").first()!!.notes!!.contains("200 (300) yards"))
            val manual = pattern.copy(gauge = "My gauge")
            library.saveIfUnchanged(library.observeLibraryItem("pattern").first()!!, manual)
            useCase.setupPattern("pattern", ByteArrayInputStream(pdf))
            assertEquals("My gauge", library.observeLibraryItem("pattern").first()!!.gauge)
            val draft = guides.loadDraft(first.guideId)!!
            val term = DraftNode(NodeId("legacy-term"), DraftNodeType.INSTRUCTION, instructionText = "skp: Slip, knit, pass over")
            val changed = guides.saveDraft(draft.copy(rootNodeIds = draft.rootNodeIds + term.id, nodes = draft.nodes + term))
            guides.moveDraftReferences(guides.getGuide(first.guideId)!!, changed)
            assertNull(guides.getGuide(first.guideId)!!.notes)
            assertTrue(GuideReference.glossary(guides.getReferenceNotes(second.guideId)).any { it.term == "skp" })
            assertEquals("My gauge", library.observeLibraryItem("pattern").first()!!.gauge)
        } finally { database.close() }
    }
}
