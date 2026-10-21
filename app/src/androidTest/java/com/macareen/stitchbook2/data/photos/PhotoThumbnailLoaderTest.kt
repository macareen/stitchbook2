package com.macareen.stitchbook2.data.photos

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PhotoThumbnailLoaderTest {
    @Test fun rotationMetadataIsAppliedWithoutChangingOriginalBytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val orientations = listOf(
            ExifInterface.ORIENTATION_NORMAL to Color.RED,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to Color.GREEN,
            ExifInterface.ORIENTATION_FLIP_VERTICAL to Color.BLUE,
            ExifInterface.ORIENTATION_TRANSPOSE to Color.RED,
            ExifInterface.ORIENTATION_TRANSVERSE to Color.YELLOW,
            ExifInterface.ORIENTATION_ROTATE_90 to Color.BLUE,
            ExifInterface.ORIENTATION_ROTATE_180 to Color.YELLOW,
            ExifInterface.ORIENTATION_ROTATE_270 to Color.GREEN
        )
        orientations.forEach { (orientation, expectedTopLeft) ->
            val file = File.createTempFile("synthetic-orientation-", ".jpg", context.cacheDir)
            try {
                val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
                for (y in 0 until 40) for (x in 0 until 80) bitmap.setPixel(x, y,
                    if (y < 20) { if (x < 40) Color.RED else Color.GREEN }
                    else { if (x < 40) Color.BLUE else Color.YELLOW })
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
                bitmap.recycle()
                ExifInterface(file).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes() // Test fixture only; production never writes the original.
                }
                val before = file.readBytes()
                val thumbnail = requireNotNull(PhotoThumbnailLoader.load(context, Uri.fromFile(file).toString(), 100))
                val sideways = orientation in listOf(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE)
                assertEquals(if (sideways) 40 else 80, thumbnail.width)
                assertEquals(if (sideways) 80 else 40, thumbnail.height)
                val actual = thumbnail.getPixel(5, 5)
                assertTrue(kotlin.math.abs(Color.red(actual) - Color.red(expectedTopLeft)) < 20)
                assertTrue(kotlin.math.abs(Color.green(actual) - Color.green(expectedTopLeft)) < 20)
                assertTrue(kotlin.math.abs(Color.blue(actual) - Color.blue(expectedTopLeft)) < 20)
                assertArrayEquals(before, file.readBytes())
            } finally { file.delete() }
        }
    }
}
