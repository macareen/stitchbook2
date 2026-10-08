package com.macareen.stitchbook2.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Renders the first page of a pattern PDF as its cover picture. Most patterns
 * open on a photo of the finished piece, so this gives library cards a real
 * picture without copying anything.
 *
 * Covers are reproducible thumbnails, so they live only in the app's cache
 * directory and can be deleted at any time; the original PDF is never touched.
 * Rendering is serialized because PdfRenderer pages are memory-heavy.
 */
class PdfCoverCache private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val directory = File(appContext.cacheDir, "pdf-covers")
    private val renderLock = Mutex()

    /** The cover at [widthPx] wide, or null when the file can't be opened or rendered. */
    suspend fun cover(pdfUri: String, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(directory, "${cacheKey(pdfUri)}-$widthPx.jpg")
        if (file.exists()) {
            BitmapFactory.decodeFile(file.path)?.let { return@withContext it }
        }
        renderLock.withLock {
            try {
                render(pdfUri, widthPx)?.also { bitmap ->
                    directory.mkdirs()
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A moved file or a lost folder permission just means no cover.
                null
            }
        }
    }

    private fun render(pdfUri: String, widthPx: Int): Bitmap? {
        val descriptor = appContext.contentResolver.openFileDescriptor(Uri.parse(pdfUri), "r") ?: return null
        return descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val heightPx = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also { bitmap ->
                        // PDF pages are transparent; paper is white.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }

    private fun cacheKey(pdfUri: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(pdfUri.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        @Volatile
        private var instance: PdfCoverCache? = null

        fun get(context: Context): PdfCoverCache =
            instance ?: synchronized(this) {
                instance ?: PdfCoverCache(context).also { instance = it }
            }
    }
}
