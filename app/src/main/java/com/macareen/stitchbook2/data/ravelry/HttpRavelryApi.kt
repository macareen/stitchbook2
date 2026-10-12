package com.macareen.stitchbook2.data.ravelry

import com.macareen.stitchbook2.domain.ravelry.NotAPdfException
import com.macareen.stitchbook2.domain.ravelry.RavelryRequestStage
import com.macareen.stitchbook2.domain.ravelry.RavelryApi
import com.macareen.stitchbook2.domain.ravelry.RavelryAttachment
import com.macareen.stitchbook2.domain.ravelry.RavelryAuthException
import com.macareen.stitchbook2.domain.ravelry.RavelryCredentials
import com.macareen.stitchbook2.domain.ravelry.RavelryNeedle
import com.macareen.stitchbook2.domain.ravelry.RavelryProject
import com.macareen.stitchbook2.domain.ravelry.RavelryStashEntry
import com.macareen.stitchbook2.domain.ravelry.RavelryVolume
import com.macareen.stitchbook2.domain.ravelry.RavelryUnavailableException
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException

/**
 * Talks to api.ravelry.com over HTTPS with HTTP Basic Auth. Reads are GETs;
 * the one POST asks for a download link and changes nothing on Ravelry.
 * Responses, links, and the key are never logged.
 */
class HttpRavelryApi(
    private val baseUrl: String = "https://api.ravelry.com",
    private val fetch: (url: String, authorization: String) -> HttpResult = { url, authorization -> httpCall("GET", url, authorization) },
    private val post: (url: String, authorization: String) -> HttpResult = { url, authorization -> httpCall("POST", url, authorization) },
    private val downloadTo: (url: String, into: OutputStream) -> Unit = ::httpDownload,
    private val onHttpResult: (RavelryRequestStage, Int) -> Unit = { _, _ -> }
) : RavelryApi {

    data class HttpResult(val status: Int, val body: String)

    override suspend fun currentUsername(credentials: RavelryCredentials): String =
        parse { RavelryJson.username(get(credentials, "/current_user.json", RavelryRequestStage.ACCOUNT)) }

    override suspend fun stash(credentials: RavelryCredentials, username: String): List<RavelryStashEntry> =
        allPages(credentials, "/people/${encode(username)}/stash/list.json", RavelryJson::stashPage, RavelryRequestStage.STASH)

    override suspend fun projects(credentials: RavelryCredentials, username: String): List<RavelryProject> =
        allPages(credentials, "/projects/${encode(username)}/list.json", RavelryJson::projectsPage, RavelryRequestStage.PROJECTS)

    override suspend fun library(credentials: RavelryCredentials, username: String): List<RavelryVolume> =
        allPages(credentials, "/people/${encode(username)}/library/search.json", RavelryJson::volumesPage, RavelryRequestStage.LIBRARY)

    private suspend fun <T> allPages(
        credentials: RavelryCredentials,
        path: String,
        read: (String) -> RavelryJson.Page<T>,
        stage: RavelryRequestStage
    ): List<T> {
        val entries = mutableListOf<T>()
        var page = 1
        while (page <= MAX_PAGES) {
            val body = get(credentials, "$path?page=$page&page_size=$PAGE_SIZE", stage)
            val result = parse { read(body) }
            entries += result.entries
            if (result.isLastPage || result.entries.isEmpty()) break
            page++
        }
        return entries
    }

    override suspend fun needles(credentials: RavelryCredentials, username: String): List<RavelryNeedle> =
        parse { RavelryJson.needles(get(credentials, "/people/${encode(username)}/needles/list.json", RavelryRequestStage.NEEDLES)) }

    override suspend fun volumeAttachments(credentials: RavelryCredentials, volumeId: Long): List<RavelryAttachment> =
        parse { RavelryJson.volumeAttachments(get(credentials, "/volumes/$volumeId.json", RavelryRequestStage.VOLUME_FILES)) }

    override suspend fun downloadLink(credentials: RavelryCredentials, attachmentId: Long): String {
        val body = get(credentials, "/product_attachments/$attachmentId/generate_download_link.json", RavelryRequestStage.PDF_LINK, post)
        return parse { RavelryJson.downloadUrl(body) }
            ?: throw RavelryUnavailableException("Ravelry did not send a download link.")
    }

    override suspend fun downloadFile(url: String, into: OutputStream) {
        try {
            withContext(Dispatchers.IO) { downloadTo(url, into) }
        } catch (e: FileHttpException) {
            onHttpResult(RavelryRequestStage.PDF_FILE, e.status)
            throw RavelryUnavailableException("Ravelry denied the file download.", httpStatus = e.status, stage = RavelryRequestStage.PDF_FILE)
        } catch (e: NotAPdfException) {
            throw e
        } catch (e: IOException) {
            throw RavelryUnavailableException("Could not download the file from Ravelry.", e)
        }
    }

    private suspend fun get(
        credentials: RavelryCredentials,
        path: String,
        stage: RavelryRequestStage,
        call: (url: String, authorization: String) -> HttpResult = fetch
    ): String {
        val token = Base64.getEncoder()
            .encodeToString("${credentials.accessKey}:${credentials.personalKey}".toByteArray(Charsets.UTF_8))
        val result = try {
            withContext(Dispatchers.IO) { call(baseUrl + path, "Basic $token") }
        } catch (e: IOException) {
            throw RavelryUnavailableException("Could not reach Ravelry.", e)
        }
        onHttpResult(stage, result.status)
        return when (result.status) {
            in 200..299 -> result.body
            401, 403 -> throw RavelryAuthException("Ravelry did not accept this request (HTTP ${result.status}).", result.status, stage)
            else -> throw RavelryUnavailableException("Ravelry answered HTTP ${result.status}.", httpStatus = result.status, stage = stage)
        }
    }

    private inline fun <T> parse(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw RavelryUnavailableException("Ravelry sent a response this app could not read.", e)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private class FileHttpException(val status: Int) : IOException("File request failed (HTTP $status).")

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 50
        private const val TIMEOUT_MS = 20_000

        private fun httpCall(method: String, url: String, authorization: String): HttpResult {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Authorization", authorization)
                connection.setRequestProperty("Accept", "application/json")
                if (method == "POST") {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(0)
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                return HttpResult(status, body)
            } finally {
                connection.disconnect()
            }
        }

        /** The link is already signed by Ravelry, so no key goes with it. Only HTTPS is followed. */
        private fun httpDownload(url: String, into: OutputStream) {
            if (!url.startsWith("https://")) throw IOException("Download links must use HTTPS.")
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = DOWNLOAD_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                val status = connection.responseCode
                if (status !in 200..299) throw FileHttpException(status)
                connection.inputStream.use { it.copyTo(into) }
            } finally {
                connection.disconnect()
            }
        }

        private const val DOWNLOAD_TIMEOUT_MS = 60_000
    }
}
