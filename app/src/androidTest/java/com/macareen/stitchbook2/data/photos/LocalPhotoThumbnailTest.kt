package com.macareen.stitchbook2.data.photos

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalPhotoThumbnailTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun localPngBoundsDecodeDoesNotDiscardTheImage() = runBlocking {
        val file = image("bounds-original.png", Bitmap.CompressFormat.PNG)
        val result = PhotoThumbnailLoader.load(context, Uri.fromFile(file).toString(), 1024)
        assertNotNull(result)
        assertEquals(600, result!!.width)
        assertEquals(400, result.height)
    }
    @Test fun localJpegRespectsItsRecordedOrientation() = runBlocking {
        val file = image("rotated-original.jpg", Bitmap.CompressFormat.JPEG)
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val result = PhotoThumbnailLoader.load(context, Uri.fromFile(file).toString(), 1024)
        assertNotNull(result)
        assertEquals(400, result!!.width)
        assertEquals(600, result.height)
    }
    @Test fun missingOriginalReturnsAPhotoPlaceholderState() = runBlocking {
        assertNull(PhotoThumbnailLoader.load(context, Uri.fromFile(File(context.cacheDir, "missing-original-unique.png")).toString(), 512))
    }
    private fun image(name: String, format: Bitmap.CompressFormat): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        file.outputStream().use { bitmap.compress(format, 100, it) }
        bitmap.recycle()
        return file
    }
}
