package com.macareen.stitchbook2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectFromPatternTest {

    @Test
    fun theTitleNamesTheType() {
        assertEquals(ProjectType.CARDIGAN, ProjectFromPattern.typeOf("Meadow Cardigan"))
        assertEquals(ProjectType.SWEATER, ProjectFromPattern.typeOf("Seaside Sweater"))
        assertEquals(ProjectType.SOCKS, ProjectFromPattern.typeOf("SOS Basic Sock"))
        assertEquals(ProjectType.HAT, ProjectFromPattern.typeOf("Cosy Beanie"))
        assertEquals(ProjectType.SHAWL, ProjectFromPattern.typeOf("Moss Shawl"))
    }

    @Test
    fun tagsHelpWhenTheTitleDoesNotSay() {
        assertEquals(ProjectType.BLANKET, ProjectFromPattern.typeOf("Granny Squares", listOf("crochet", "blanket")))
        assertEquals(ProjectType.OTHER, ProjectFromPattern.typeOf("Something Lovely"))
    }

    @Test
    fun wordsInsideOtherWordsDoNotCount() {
        // "top" inside "Topaz" and "hat" inside "Chatter" are not types.
        assertEquals(ProjectType.OTHER, ProjectFromPattern.typeOf("Topaz Chatter"))
    }

    @Test
    fun theProjectCopiesTitleCraftAndDescriptionAndStartsPlanned() {
        val pattern = LibraryItem(
            id = "pattern-1", title = "Meadow Cardigan", craft = Craft.CROCHET, author = "Ana Example",
            sourceUrl = null, tags = emptyList(), notes = "A relaxed cardigan.", bookmarked = false,
            createdAt = 0, updatedAt = 0
        )

        val project = ProjectFromPattern.project(pattern, id = "project-1", now = 42)

        assertEquals("Meadow Cardigan", project.name)
        assertEquals(Craft.CROCHET, project.craft)
        assertEquals(ProjectType.CARDIGAN, project.projectType)
        assertEquals(ProjectStatus.PLANNED, project.status)
        assertEquals("A relaxed cardigan.", project.description)
        assertEquals(42L, project.createdAt)
    }
}
