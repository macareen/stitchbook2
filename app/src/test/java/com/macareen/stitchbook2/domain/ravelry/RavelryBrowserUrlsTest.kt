package com.macareen.stitchbook2.domain.ravelry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RavelryBrowserUrlsTest {
    @Test fun acceptsOnlyNormalHttpsAccountBrowserLinks() {
        assertEquals("https://www.ravelry.com/library/download/123", RavelryBrowserUrls.validated("/library/download/123"))
        assertEquals("https://ravelry.com/library", RavelryBrowserUrls.validated("https://ravelry.com/library"))
        listOf(null, "", "//evil.example/file", "http://ravelry.com/file", "https://ravelry.com.evil.example/file",
            "https://key@ravelry.com/file", "https://ravelry.com:8080/file", "javascript:alert(1)").forEach {
            assertNull(RavelryBrowserUrls.validated(it))
        }
    }
}
