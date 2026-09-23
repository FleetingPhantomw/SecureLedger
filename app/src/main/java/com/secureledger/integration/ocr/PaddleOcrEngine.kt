package com.secureledger.integration.ocr

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.util.OpenCVUtils
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One application-scoped session; initialization and inference never run on the UI thread. */
class PaddleOcrEngine(context: Context) : OcrEngine {
    private val appContext = context.applicationContext
    private val sessionMutex = Mutex()
    private var session: PaddleOCR? = null

    override suspend fun warmUp() = withContext(Dispatchers.IO) {
        sessionMutex.withLock { session() }
        Unit
    }

    override suspend fun recognize(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_IMAGE_BYTES) { "图片过大，请选择小于 20 MB 的图片" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("无法读取所选图片")
        require(bytes.isNotEmpty()) { "图片为空" }

        sessionMutex.withLock {
            val recognizer = session()
            val started = SystemClock.elapsedRealtime()
            val result = recognizer.recognize(bytes)
            val elapsed = SystemClock.elapsedRealtime() - started
            Log.d(TAG, "ocr modelLoad=${recognizer.coldLoadTimeMs}ms inference=${elapsed}ms lines=${result.results.size}")
            OcrResult(
                lines = result.results.map { line ->
                    OcrLine(
                        line.text,
                        line.confidence,
                        line.box.points.map { OcrPoint(it.x, it.y) }
                    )
                },
                modelLoadMs = recognizer.coldLoadTimeMs,
                inferenceMs = elapsed
            )
        }
    }

    private suspend fun session(): PaddleOCR {
        session?.let { return it }
        check(OpenCVUtils.init(appContext)) { "无法加载图片识别组件" }
        val started = SystemClock.elapsedRealtime()
        return PaddleOCR.create(appContext).also {
            session = it
            Log.d(TAG, "ocr initialization=${SystemClock.elapsedRealtime() - started}ms modelLoad=${it.coldLoadTimeMs}ms")
        }
    }

    private companion object {
        const val TAG = "SecureLedgerPerf"
        const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
    }
}
