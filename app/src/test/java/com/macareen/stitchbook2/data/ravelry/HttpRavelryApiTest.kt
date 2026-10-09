package com.macareen.stitchbook2.data.ravelry

import com.macareen.stitchbook2.domain.ravelry.RavelryAuthException
import com.macareen.stitchbook2.domain.ravelry.RavelryCredentials
import com.macareen.stitchbook2.domain.ravelry.RavelryUnavailableException
import com.macareen.stitchbook2.domain.ravelry.NotAPdfException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Responses below follow the result objects in Ravelry's API documentation, with made-up values. */
class HttpRavelryApiTest {

    private val credentials = RavelryCredentials("access-key", "personal-key")
    private val requests = mutableListOf<Pair<String, String>>()

    private val posts = mutableListOf<String>()

    private fun api(
        responses: Map<String, HttpRavelryApi.HttpResult>,
        postResponses: Map<String, HttpRavelryApi.HttpResult> = emptyMap(),
        download: (String, OutputStream) -> Unit = { _, _ -> }
    ) = HttpRavelryApi(
        baseUrl = "https://api.test",
        fetch = { url, auth ->
            requests += url to auth
            responses[url.removePrefix("https://api.test")] ?: HttpRavelryApi.HttpResult(404, "")
        },
        post = { url, _ ->
            posts += url
            postResponses[url.removePrefix("https://api.test")] ?: HttpRavelryApi.HttpResult(404, "")
        },
        downloadTo = download
    )

    @Test
    fun usesBasicAuthWithTheKeyPairAndReadsTheUsername() = runBlocking {
        val api = api(mapOf("/current_user.json" to ok("""{"user": {"id": 7, "username": "Ana Knits"}}""")))

        assertEquals("Ana Knits", api.currentUsername(credentials))
        val expected = "Basic " + Base64.getEncoder().encodeToString("access-key:personal-key".toByteArray())
        assertEquals(expected, requests.single().second)
    }

    @Test
    fun stashIsReadAcrossEveryPageWithTheAccountNameEncoded() = runBlocking {
        val api = api(
            mapOf(
                "/people/Ana%20Knits/stash/list.json?page=1&page_size=100" to ok(page(listOf(STASH_ENTRY), page = 1, last = 2)),
                "/people/Ana%20Knits/stash/list.json?page=2&page_size=100" to ok(page(listOf("""{"id": 2, "name": "Mystery handspun", "yarn": null, "primary_pack": null}"""), page = 2, last = 2))
            )
        )

        val stash = api.stash(credentials, "Ana Knits")

        assertEquals(listOf(1L, 2L), stash.map { it.id })
        val first = stash[0]
        assertEquals("Merino Worsted", first.yarnName)
        assertEquals("Acme Mills", first.companyName)
        assertEquals("Worsted", first.weightName)
        assertEquals("Lagoon", first.colorway)
        assertEquals("A12", first.dyeLot)
        assertEquals("Blue box", first.location)
        assertEquals(3.5, first.skeins!!, 0.0)
        assertEquals(218.0, first.yardsPerSkein!!, 0.0)
        assertEquals(100.0, first.gramsPerSkein!!, 0.0)
        assertEquals(555L, first.yarnId)
        assertEquals("Mystery handspun", stash[1].name)
        assertNull(stash[1].skeins)
    }

    @Test
    fun needlesReadTheirTypeAndComment() = runBlocking {
        val api = api(
            mapOf(
                "/people/ana/needles/list.json" to ok(
                    """{"needle_records": [
                        {"id": 9, "comment": "Bamboo", "needle_type_id": 3,
                         "needle_type": {"id": 3, "name": "US 6 - 4.0 mm", "metric_name": "4.0 mm", "type_name": "circular", "length": "24 inch"}}
                    ]}"""
                )
            )
        )

        val needle = api.needles(credentials, "ana").single()
        assertEquals(9L, needle.id)
        assertEquals("US 6 - 4.0 mm", needle.name)
        assertEquals("4.0 mm", needle.metricName)
        assertEquals("circular", needle.typeName)
        assertEquals("24 inch", needle.length)
        assertEquals("Bamboo", needle.comment)
    }

    @Test
    fun projectsAndLibraryVolumesAreRead() = runBlocking {
        val api = api(
            mapOf(
                "/projects/ana/list.json?page=1&page_size=100" to ok(
                    """{"projects": [{"id": 42, "name": "Lagoon cardigan", "craft_name": "Knitting", "status_name": "Finished",
                        "pattern_name": null, "personal_source_name": "Notebook sketch", "started": "2026/03/05",
                        "completed": "2026/09/30", "finish_by": null}],
                       "paginator": {"page": 1, "last_page": 1}}"""
                ),
                "/people/ana/library/search.json?page=1&page_size=100" to ok(
                    """{"volumes": [{"id": 7, "title": "Seaside Cardigan", "author_name": "A. Designer", "pattern_id": 900}],
                       "paginator": {"page": 1, "last_page": 1}}"""
                )
            )
        )

        val project = api.projects(credentials, "ana").single()
        assertEquals("Notebook sketch", project.patternName)
        assertEquals("2026/09/30", project.completed)
        val volume = api.library(credentials, "ana").single()
        assertEquals(900L, volume.patternId)
        assertEquals("A. Designer", volume.authorName)
    }

    @Test
    fun aRejectedKeyAndOtherFailuresAreTold() = runBlocking {
        assertTrue(failure { api(mapOf("/current_user.json" to HttpRavelryApi.HttpResult(403, ""))).currentUsername(credentials) } is RavelryAuthException)
        assertTrue(failure { api(mapOf("/current_user.json" to HttpRavelryApi.HttpResult(503, ""))).currentUsername(credentials) } is RavelryUnavailableException)
        assertTrue(failure { api(mapOf("/current_user.json" to ok("<html>"))).currentUsername(credentials) } is RavelryUnavailableException)
        val offline = HttpRavelryApi(baseUrl = "https://api.test", fetch = { _, _ -> throw IOException("offline") })
        assertTrue(failure { offline.currentUsername(credentials) } is RavelryUnavailableException)
    }

    @Test
    fun aVolumesAttachmentsAreReadByTheirProductAttachmentId() = runBlocking {
        val api = api(
            mapOf(
                "/volumes/7.json" to ok(
                    """{"volume": {"id": 7, "title": "Seaside Cardigan", "volume_attachments": [
                        {"id": 1, "product_attachment_id": 31, "filename": "Seaside_Cardigan.pdf"},
                        {"id": 2, "product_attachment_id": 32, "filename": "Charts.zip"}
                    ]}}"""
                )
            )
        )

        val attachments = api.volumeAttachments(credentials, 7)

        assertEquals(listOf(31L, 32L), attachments.map { it.id })
        assertEquals("Seaside_Cardigan.pdf", attachments[0].fileName)
    }

    @Test
    fun otherAttachmentSpellingsAreAcceptedAndAMissingListIsEmpty() {
        val plain = RavelryJson.volumeAttachments("""{"volume": {"attachments": [{"id": 5, "name": "Hat.pdf"}, {"name": "no id"}]}}""")
        assertEquals(listOf(5L), plain.map { it.id })
        assertEquals("Hat.pdf", plain.single().fileName)
        val product = RavelryJson.volumeAttachments("""{"volume": {"product_attachments": [{"id": 6, "file_name": "Socks.pdf"}]}}""")
        assertEquals("Socks.pdf", product.single().fileName)
        assertTrue(RavelryJson.volumeAttachments("""{"volume": {"id": 7}}""").isEmpty())
    }

    @Test
    fun theDownloadLinkIsAskedForWithAPostAndReadFromEitherShape() = runBlocking {
        val api = api(
            emptyMap(),
            postResponses = mapOf(
                "/product_attachments/31/generate_download_link.json" to ok(
                    """{"download_link": {"url": "https://downloads.test/abc.pdf", "expires_at": "2026/10/09"}}"""
                )
            )
        )

        assertEquals("https://downloads.test/abc.pdf", api.downloadLink(credentials, 31))
        assertEquals(listOf("https://api.test/product_attachments/31/generate_download_link.json"), posts)
        assertTrue(requests.isEmpty())
        assertEquals("https://d.test/x", RavelryJson.downloadUrl("""{"url": "https://d.test/x"}"""))
        assertNull(RavelryJson.downloadUrl("""{"download_link": {}}"""))
    }

    @Test
    fun aLinkWithoutAnAddressOrARefusedLinkIsTold() = runBlocking {
        val empty = api(emptyMap(), postResponses = mapOf("/product_attachments/1/generate_download_link.json" to ok("{}")))
        assertTrue(failure { empty.downloadLink(credentials, 1) } is RavelryUnavailableException)
        val refused = api(emptyMap(), postResponses = mapOf("/product_attachments/1/generate_download_link.json" to HttpRavelryApi.HttpResult(403, "")))
        assertTrue(failure { refused.downloadLink(credentials, 1) } is RavelryAuthException)
    }

    @Test
    fun downloadsWriteThroughAndNetworkFailuresBecomeUnavailable() = runBlocking {
        val sink = ByteArrayOutputStream()
        api(emptyMap(), download = { _, out -> out.write("%PDF-1.7".toByteArray()) }).downloadFile("https://d.test/x", sink)
        assertEquals("%PDF-1.7", sink.toString(Charsets.US_ASCII.name()))

        val offline = api(emptyMap(), download = { _, _ -> throw IOException("offline") })
        assertTrue(failure { offline.downloadFile("https://d.test/x", sink) } is RavelryUnavailableException)
        val notPdf = api(emptyMap(), download = { _, _ -> throw NotAPdfException() })
        assertTrue(failure { notPdf.downloadFile("https://d.test/x", sink) } is NotAPdfException)
    }

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    private fun ok(body: String) = HttpRavelryApi.HttpResult(200, body)

    private fun page(entries: List<String>, page: Int, last: Int) =
        """{"stash": [${entries.joinToString()}], "paginator": {"page": $page, "page_count": $last, "last_page": $last, "page_size": 100, "results": 2}}"""

    private companion object {
        const val STASH_ENTRY = """{
            "id": 1, "name": null, "colorway_name": "Lagoon", "dye_lot": "A12", "location": "Blue box",
            "yarn": {"id": 555, "name": "Merino Worsted", "yarn_company_name": "Acme Mills", "yardage": 200, "grams": 100,
                     "yarn_weight": {"id": 1, "name": "Worsted"}},
            "primary_pack": {"id": 4, "skeins": "3.5", "yards_per_skein": 218, "grams_per_skein": 100.0}
        }"""
    }
}
