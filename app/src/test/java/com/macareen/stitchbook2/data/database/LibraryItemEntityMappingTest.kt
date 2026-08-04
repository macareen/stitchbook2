package com.macareen.stitchbook2.data.database

import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LibraryItemEntityMappingTest {

    @Test
    fun libraryItemRoundTripsThroughEntity() {
        val item = LibraryItem(
            id = "library-id",
            title = "Raglan Sweater Construction Guide",
            craft = Craft.KNITTING,
            author = "Elizabeth Zimmermann",
            sourceUrl = "https://example.com/raglan",
            tags = listOf("raglan", "construction", "sweater"),
            notes = "Great reference for top-down raglan increases.",
            bookmarked = true,
            createdAt = 100,
            updatedAt = 200
        )

        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun libraryItemWithNoTagsRoundTrips() {
        val item = LibraryItem(
            id = "library-id",
            title = "Untitled reference",
            craft = Craft.OTHER,
            author = null,
            sourceUrl = null,
            tags = emptyList(),
            notes = null,
            bookmarked = false,
            createdAt = 100,
            updatedAt = 200
        )

        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun libraryItemWithAttachedPdfRoundTripsThroughEntity() {
        val item = LibraryItem(
            id = "library-id",
            title = "Raglan Sweater Construction Guide",
            craft = Craft.KNITTING,
            author = null,
            sourceUrl = null,
            tags = emptyList(),
            notes = null,
            bookmarked = false,
            createdAt = 100,
            updatedAt = 200,
            pdfUri = "content://com.example.provider/document/42",
            pdfFileName = "Raglan Construction.pdf",
            pdfLastViewedPage = 3
        )

        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun entityUsesExplicitStableValues() {
        val entity = LibraryItem(
            id = "library-id",
            title = "Honeycomb Handbook",
            craft = Craft.TUNISIAN_CROCHET,
            author = null,
            sourceUrl = null,
            tags = listOf("honeycomb", "stitch-dictionary"),
            notes = null,
            bookmarked = false,
            createdAt = 100,
            updatedAt = 200
        ).toEntity()

        assertEquals("TUNISIAN_CROCHET", entity.craft)
        assertEquals("honeycomb,stitch-dictionary", entity.tags)
    }

    @Test
    fun unknownStoredValueFailsWithDataMappingError() {
        val entity = LibraryItemEntity(
            id = "library-id",
            title = "Legacy reference",
            craft = "UNRECOGNIZED_CRAFT",
            author = null,
            sourceUrl = null,
            tags = "",
            notes = null,
            bookmarked = false,
            createdAt = 100,
            updatedAt = 200
        )

        assertThrows(UnknownLibraryItemValueException::class.java) {
            entity.toDomain()
        }
    }
}
