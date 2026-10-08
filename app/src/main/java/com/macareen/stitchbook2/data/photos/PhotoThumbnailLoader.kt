package com.macareen.stitchbook2.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes downsampled thumbnails straight from the user's original
 * `content://` photo. Only these reproducible derivatives are cached, and
 * only in memory (AGENTS.md: "internal cache may hold reproducible
 * thumbnails"); the original is opened read-only and never copied.
 * Uses platform APIs only, so no image-loading library is needed.
 */
object PhotoThumbnailLoader {

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Returns null when the file is missing, unreadable, or permission was revoked. */
    suspend fun load(context: Context, uri: String, maxSizePx: Int): Bitmap? {
        val key = "$uri@$maxSizePx"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                decode(context, Uri.parse(uri), maxSizePx)?.also { cache.put(key, it) }
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Full-resolution-ish decode for card rendering (still bounded to avoid OOM). */
    suspend fun loadForExport(context: Context, uri: String): Bitmap? = load(context, uri, EXPORT_MAX_PX)

    private fun decode(context: Context, uri: Uri, maxSizePx: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxSizePx && bounds.outHeight / (sampleSize * 2) >= maxSizePx) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private const val CACHE_BYTES = 24 * 1024 * 1024
    private const val EXPORT_MAX_PX = 1600
}
