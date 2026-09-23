package com.secureledger.integration.ocr

import android.net.Uri

/** OCR returns image observations only; order interpretation belongs to OrderParser. */
interface OcrEngine {
    suspend fun recognize(uri: Uri): OcrResult
    suspend fun warmUp()
}

data class OcrPoint(val x: Float, val y: Float)

data class OcrLine(
    val text: String,
    val confidence: Float,
    val corners: List<OcrPoint>
)

data class OcrResult(
    val lines: List<OcrLine>,
    val modelLoadMs: Long,
    val inferenceMs: Long
) {
    val text: String get() = lines.joinToString("\n") { it.text }
}
