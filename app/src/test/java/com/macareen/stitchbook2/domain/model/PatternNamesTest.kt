package com.macareen.stitchbook2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PatternNamesTest {
    @Test fun guideUsesTitleAndSizeOrThePdfStem() {
        assertEquals("April Cardigan \u00b7 M", PatternNames.guide(" April Cardigan ", " M ", "other.pdf"))
        assertEquals("April.v2 \u00b7 XL", PatternNames.guide("", "XL", "April.v2.PDF"))
        assertEquals("Pattern \u00b7 S", PatternNames.guide("", "S"))
    }
    @Test fun pdfNameRemovesOnlyTheLastExtensionAndUsesAnExplicitFallback() {
        assertEquals("April.v2", PatternNames.pdf(" April.v2.pdf "))
        assertEquals("April", PatternNames.pdf("April"))
        assertEquals("My project", PatternNames.pdf(null, "My project"))
        assertEquals("My project", PatternNames.pdf(".pdf", "My project"))
    }
}
