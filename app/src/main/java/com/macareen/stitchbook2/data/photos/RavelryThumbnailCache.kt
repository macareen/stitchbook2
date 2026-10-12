package com.macareen.stitchbook2.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.macareen.stitchbook2.domain.ravelry.RavelryPhotoUrls
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Network runs only during an explicit Ravelry pull. Screens read this disposable cache offline. */
object RavelryThumbnailCache {
    fun file(context: Context, url: String): File? {
        val source = RavelryPhotoUrls.safe(url) ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "ravelry-thumbnails"), "$digest.jpg")
    }

    suspend fun refresh(context: Context, url: String, fetch: (String) -> ByteArray = ::download) {
        val target = file(context, url) ?: return
        withContext(Dispatchers.IO) {
            var temporary: File? = null
            try {
                if (target.exists()) return@withContext
                coroutineContext.ensureActive()
                val bytes = fetch(url)
                if (bytes.size > MAX_DOWNLOAD_BYTES) return@withContext
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
                val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext
                try {
                    coroutineContext.ensureActive()
                    target.parentFile?.mkdirs()
                    temporary = File.createTempFile("thumbnail-", ".tmp", target.parentFile)
                    temporary.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    coroutineContext.ensureActive()
                    if (!temporary.renameTo(target)) throw IOException("Could not cache thumbnail.")
                    val files = target.parentFile?.listFiles()?.filter { it.extension == "jpg" }
                        ?.sortedByDescending { it.lastModified() }.orEmpty()
                    var total = 0L
                    files.forEach { cached ->
                        total += cached.length()
                        if (total > MAX_CACHE_BYTES) cached.delete()
                    }
                } finally { image.recycle() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // An optional thumbnail must never prevent saving imported yarn records.
            } finally { temporary?.delete() }
        }
    }

    private fun download(source: String): ByteArray {
        var url = source
        repeat(4) {
            val safeUrl = RavelryPhotoUrls.safe(url) ?: throw IOException("Unsafe photo source.")
            val connection = URI(safeUrl).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: throw IOException("Missing photo redirect.")
                    url = URI(safeUrl).resolve(location).toString()
                } else {
                    if (status !in 200..299 || connection.contentLengthLong > MAX_DOWNLOAD_BYTES) {
                        throw IOException("Photo unavailable.")
                    }
                    return connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_DOWNLOAD_BYTES) throw IOException("Photo too large.")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
            } finally { connection.disconnect() }
        }
        throw IOException("Too many photo redirects.")
    }

    private const val MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024
    private const val MAX_CACHE_BYTES = 32L * 1024 * 1024
    private const val MAX_EDGE = 512
}
