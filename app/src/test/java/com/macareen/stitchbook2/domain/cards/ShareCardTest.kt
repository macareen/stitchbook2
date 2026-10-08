package com.macareen.stitchbook2.domain.cards

import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.PhotoRole
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCardTest {

    private val project = Project(
        id = "p", name = "Harbour cardigan", craft = Craft.KNITTING, projectType = ProjectType.CARDIGAN,
        status = ProjectStatus.COMPLETED, notes = "Secret gift for Sam", createdAt = 0, updatedAt = 0,
        startDate = "2026-01-01", completedDate = "2026-01-31"
    )

    private fun photo(id: String, role: PhotoRole = PhotoRole.NONE, createdAt: Long = 0) =
        Photo(id, "p", null, "content://$id", null, null, null, null, role, createdAt, createdAt)

    private fun source(photos: List<Photo> = emptyList()) = CardSource(
        project = project,
        projects = listOf(project),
        photos = photos,
        milestones = emptyList(),
        sessions = emptyList(),
        allocations = emptyList(),
        stashItems = emptyList(),
        today = LocalDate.of(2026, 2, 1),
        now = 0
    )

    @Test
    fun privateFieldsAreNeverOnByDefault() {
        CardTemplate.entries.forEach { template ->
            assertTrue(template.defaultFields.none { it.isPrivate })
        }
        val content = buildCardContent(CardTemplate.COMPLETED, CardTemplate.COMPLETED.defaultFields, null, source())
        assertTrue(content.facts.none { it.kind == CardFactKind.NOTES })
    }

    @Test
    fun notesAppearOnlyWhenExplicitlySelected() {
        val fields = CardTemplate.COMPLETED.defaultFields + CardField.NOTES
        val content = buildCardContent(CardTemplate.COMPLETED, fields, null, source())
        assertEquals(CardValue.Text("Secret gift for Sam"), content.facts.single { it.kind == CardFactKind.NOTES }.value)
    }

    @Test
    fun fieldsOutsideATemplateAreIgnored() {
        val content = buildCardContent(CardTemplate.YARN, setOf(CardField.NOTES, CardField.DATES), null, source())
        assertTrue(content.facts.isEmpty())
    }

    @Test
    fun datesIncludeDurationForFinishedProjects() {
        val content = buildCardContent(CardTemplate.COMPLETED, setOf(CardField.DATES), null, source())
        assertEquals(CardValue.DaysValue(30), content.facts.single { it.kind == CardFactKind.DURATION_DAYS }.value)
    }

    @Test
    fun photoNeedsThePhotoFieldAndBeforeAfterNeedsBothRoles() {
        val photos = listOf(photo("a", createdAt = 1), photo("b", createdAt = 2))
        assertEquals("b", defaultPhotoId(CardTemplate.PROGRESS, source(photos)))
        assertEquals(
            listOf("content://a"),
            buildCardContent(CardTemplate.PROGRESS, setOf(CardField.PHOTO), "a", source(photos)).photoUris
        )
        assertTrue(buildCardContent(CardTemplate.PROGRESS, emptySet(), "a", source(photos)).photoUris.isEmpty())

        val pair = listOf(photo("x", PhotoRole.BEFORE), photo("y", PhotoRole.AFTER))
        assertEquals(
            listOf("content://x", "content://y"),
            buildCardContent(CardTemplate.BEFORE_AFTER, emptySet(), null, source(pair)).photoUris
        )
    }

    @Test
    fun summariesCarryNoProjectDetails() {
        val content = buildCardContent(CardTemplate.WEEKLY, CardTemplate.WEEKLY.defaultFields, null, source())
        assertNull(content.title)
        assertFalse(content.facts.any { it.kind == CardFactKind.NOTES || it.kind == CardFactKind.CRAFT })
        assertEquals(LocalDate.of(2026, 1, 26), content.periodStart)
    }
}
