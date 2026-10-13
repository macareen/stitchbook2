package com.macareen.stitchbook2.data.ravelry

import com.macareen.stitchbook2.domain.ravelry.RavelryAuthException
import com.macareen.stitchbook2.domain.ravelry.RavelryCredentials
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RavelryPdfDenialTest {
    @Test fun explicitReasonsBecomeFixedCategories() {
        assertEquals(RavelryPdfDenial.OAUTH_REQUIRED, RavelryPdfDenial.classify("""{"error":"OAuth is required"}"""))
        assertEquals(RavelryPdfDenial.PDF_PERMISSION_REQUIRED, RavelryPdfDenial.classify("""{"error":{"message":"Missing library-pdf permission"}}"""))
        assertEquals(RavelryPdfDenial.PDF_PERMISSION_REQUIRED, RavelryPdfDenial.classify("""{"required_scope":"library-pdf"}"""))
        assertEquals(RavelryPdfDenial.FILE_ACCESS, RavelryPdfDenial.classify("""{"message":"File is not owned by this account"}"""))
        assertEquals(RavelryPdfDenial.ATTACHMENT_REFERENCE, RavelryPdfDenial.classify("""{"error":"Invalid attachment"}"""))
        assertEquals(RavelryPdfDenial.HTML_RESPONSE, RavelryPdfDenial.classify("<html>Denied</html>"))
    }
    @Test fun ambiguousOrUnrelatedTextDoesNotEstablishAPermissionRequirement() {
        listOf("", "{}", "not json", """{"profile":"OAuth required"}""", """{"error":"Access denied"}""",
            """{"error":"OAuth is not required"}""", """{"error":"This does not require OAuth"}""",
            """{"error":"OAuth supported"}""", "x".repeat(65_537)).forEach {
            assertEquals(RavelryPdfDenial.UNKNOWN, RavelryPdfDenial.classify(it))
        }
    }
    @Test fun callbackAndExceptionNeverExposeResponseTextOrKeys() = runBlocking {
        val categories = mutableListOf<RavelryPdfDenial>()
        val api = HttpRavelryApi(post = { _, _ -> HttpRavelryApi.HttpResult(403, """{"error":"Access denied private-response-marker"}""") },
            onPdfDenial = categories::add)
        try {
            api.downloadLink(RavelryCredentials("synthetic-access", "synthetic-personal"), 1)
            fail("Expected refusal")
        } catch (error: RavelryAuthException) {
            assertEquals(403, error.httpStatus)
            assertFalse(error.message.orEmpty().contains("private-response-marker"))
            assertFalse(error.message.orEmpty().contains("synthetic-"))
            assertNull(error.cause)
        }
        assertEquals(listOf(RavelryPdfDenial.UNKNOWN), categories)
    }
    @Test fun browserLinkUsesTheChosenVolumeAttachment() {
        val item = RavelryJson.volumeAttachments("""{"volume_attachments":[{"product_attachment_id":1,"filename":"sample.pdf","ravelry_download_url":"/library/download/1"}]}""").single()
        assertEquals("https://www.ravelry.com/library/download/1", item.browserUrl)
    }
}
