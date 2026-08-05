package com.macareen.stitchbook2.data.parsing

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [PdfPageOcr] backed by ML Kit's bundled (on-device, no Play Services or
 * network dependency) Latin text recognizer. The recognizer is created once
 * and reused for this instance's lifetime, matching how other long-lived
 * singletons are wired through [com.macareen.stitchbook2.AppContainer].
 */
class MlKitPdfPageOcr : PdfPageOcr {

    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(page: Bitmap): List<String> {
        val result = recognizer.process(InputImage.fromBitmap(page, 0)).awaitResult()
        return result.textBlocks.flatMap { block -> block.lines.map { it.text } }
    }

    private suspend fun Task<Text>.awaitResult(): Text =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { continuation.resume(it) }
            addOnFailureListener { continuation.resumeWithException(it) }
        }
}
