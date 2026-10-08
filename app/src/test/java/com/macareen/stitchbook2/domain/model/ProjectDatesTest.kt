package com.macareen.stitchbook2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectDatesTest {

    @Test
    fun blankDatesAreValidAndAbsent() {
        assertNull(parseLocalDateOrNull("  "))
        assertTrue(validateProjectDates(null, "", " ").isEmpty())
    }

    @Test
    fun malformedDatesAreReportedPerField() {
        assertEquals(
            setOf(ProjectDateError.INVALID_START, ProjectDateError.INVALID_COMPLETED),
            validateProjectDates("14/03/2026", "2026-04-01", "2026-13-01")
        )
    }

    @Test
    fun completionBeforeStartIsRejectedButEarlyTargetIsAllowed() {
        assertEquals(
            setOf(ProjectDateError.COMPLETED_BEFORE_START),
            validateProjectDates("2026-03-14", "2026-01-01", "2026-03-13")
        )
        assertTrue(validateProjectDates("2026-03-14", "2026-01-01", "2026-03-14").isEmpty())
    }
}
