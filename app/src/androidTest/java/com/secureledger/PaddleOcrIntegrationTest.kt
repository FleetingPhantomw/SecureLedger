package com.secureledger

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secureledger.integration.ocr.PaddleOcrEngine
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaddleOcrIntegrationTest {
    @Test
    fun recognizesChinesePaymentImageWithBoxes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<SecureLedgerApplication>()
        val image = Bitmap.createBitmap(1080, 440, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 92f
            }
            drawText("支付成功", 55f, 155f, paint)
            drawText("实付 28.50 元", 55f, 310f, paint)
        }
        val file = File(context.cacheDir, "ocr_integration_test.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()

        val result = PaddleOcrEngine(context).recognize(Uri.fromFile(file))

        assertTrue(result.lines.isNotEmpty())
        assertTrue(result.text.contains("28.50"))
        assertTrue(result.lines.all { it.corners.size == 4 })
        assertTrue(result.modelLoadMs >= 0)
        assertTrue(result.inferenceMs >= 0)
        file.delete()
        Unit
    }
}
