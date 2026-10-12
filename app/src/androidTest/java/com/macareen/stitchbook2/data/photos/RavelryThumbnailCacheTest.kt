package com.macareen.stitchbook2.data.photos

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RavelryThumbnailCacheTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun thumbnailsAreBoundedCachedOfflineAndRecreatedAfterEviction() = runBlocking<Unit> {
        val url = "https://images.example.test/cache-test.jpg"
        val file = requireNotNull(RavelryThumbnailCache.file(context, url))
        file.delete()
        val bytes = imageBytes()
        var requests = 0
        RavelryThumbnailCache.refresh(context, url) { requests++; bytes }
        assertTrue(file.canonicalPath.startsWith(context.cacheDir.canonicalPath))
        val decoded = requireNotNull(PhotoThumbnailLoader.load(context, url, 512))
        assertTrue(maxOf(decoded.width, decoded.height) <= 512)
        RavelryThumbnailCache.refresh(context, url) { requests++; bytes }
        assertEquals(1, requests)
        file.delete()
        RavelryThumbnailCache.refresh(context, url) { requests++; bytes }
        assertEquals(2, requests)
        file.delete()
    }

    @Test fun missingRemotePhotosNeverFetchWhileBrowsing() = runBlocking<Unit> {
        val url = "https://images.example.test/not-downloaded.jpg"
        RavelryThumbnailCache.file(context, url)?.delete()
        assertNull(PhotoThumbnailLoader.load(context, url, 512))
    }

    @Test fun failedOrOversizedThumbnailsDoNotCreateCacheFiles() = runBlocking<Unit> {
        val url = "https://images.example.test/invalid.jpg"
        val file = requireNotNull(RavelryThumbnailCache.file(context, url))
        file.delete()
        RavelryThumbnailCache.refresh(context, url) { throw java.io.IOException("Unavailable") }
        assertFalse(file.exists())
        RavelryThumbnailCache.refresh(context, url) { ByteArray(5 * 1024 * 1024 + 1) }
        assertFalse(file.exists())
        RavelryThumbnailCache.refresh(context, url) { byteArrayOf(1, 2, 3) }
        assertFalse(file.exists())
    }

    @Test fun cancellationIsPreserved() = runBlocking<Unit> {
        val url = "https://images.example.test/cancelled.jpg"
        RavelryThumbnailCache.file(context, url)?.delete()
        try {
            RavelryThumbnailCache.refresh(context, url) { throw CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    private fun imageBytes(): ByteArray {
        val image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.rgb(150, 100, 125))
        return try {
            ByteArrayOutputStream().use { out -> image.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        } finally { image.recycle() }
    }
}
