package com.macareen.stitchbook2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectValidationTest {

    @Test
    fun blankNameIsInvalid() {
        assertNull(normalizedProjectName("  \t  "))
    }

    @Test
    fun validNameIsTrimmed() {
        assertEquals(
            "Everyday cardigan",
            normalizedProjectName("  Everyday cardigan  ")
        )
    }
}
