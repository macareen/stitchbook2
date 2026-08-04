package com.macareen.stitchbook2.data.database

import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectEntityMappingTest {

    @Test
    fun projectRoundTripsThroughEntity() {
        val project = Project(
            id = "project-id",
            name = "Crochet bag",
            craft = Craft.CROCHET,
            projectType = ProjectType.BAG,
            status = ProjectStatus.ACTIVE,
            notes = "Use the smaller hook for the strap.",
            createdAt = 100,
            updatedAt = 200
        )

        assertEquals(project, project.toEntity().toDomain())
    }

    @Test
    fun entityUsesExplicitStableValues() {
        val entity = Project(
            id = "project-id",
            name = "Tunisian scarf",
            craft = Craft.TUNISIAN_CROCHET,
            projectType = ProjectType.SCARF,
            status = ProjectStatus.PAUSED,
            notes = null,
            createdAt = 100,
            updatedAt = 200
        ).toEntity()

        assertEquals("TUNISIAN_CROCHET", entity.craft)
        assertEquals("SCARF", entity.projectType)
        assertEquals("PAUSED", entity.status)
    }

    @Test
    fun unknownStoredValueFailsWithDataMappingError() {
        val entity = ProjectEntity(
            id = "project-id",
            name = "Legacy project",
            craft = "UNRECOGNIZED_CRAFT",
            projectType = "OTHER",
            status = "PLANNED",
            notes = null,
            createdAt = 100,
            updatedAt = 200
        )

        assertThrows(UnknownProjectValueException::class.java) {
            entity.toDomain()
        }
    }
}
