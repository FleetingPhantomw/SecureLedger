package com.secureledger.integration.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Bundled on-device Chinese/Latin OCR. The selected image never leaves the device. */
class OfflineTextRecognizer(private val context: Context) {
    suspend fun recognize(uri: Uri): String {
        val input = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(input)
                    .addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result.text) }
                    .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
            }
        } finally {
            recognizer.close()
        }
    }
}
