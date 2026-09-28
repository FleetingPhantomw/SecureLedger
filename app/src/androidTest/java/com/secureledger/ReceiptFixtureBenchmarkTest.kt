package com.secureledger

import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secureledger.integration.ocr.PaddleOcrEngine
import com.secureledger.core.ocr.OcrObservation
import com.secureledger.core.ocr.StatementParser
import com.secureledger.core.ocr.ShoppingOrderParser
import com.secureledger.core.ocr.OrderParser
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

/** Optional local-only benchmark. Screenshot fixtures are never packaged in the APK. */
@RunWith(AndroidJUnit4::class)
class ReceiptFixtureBenchmarkTest {
    @Test
    fun recognizeLocalFixtures() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<SecureLedgerApplication>()
        val directory = File(context.filesDir, "receipt-fixtures")
        Log.i("ReceiptFixtureTest", "path=${directory.absolutePath} exists=${directory.exists()} readable=${directory.canRead()} count=${directory.listFiles()?.size}")
        val inputs = directory.listFiles { file ->
            file.isFile && (file.extension.equals("jpg", true) || file.extension.equals("png", true))
        }?.sortedBy(File::getName).orEmpty()
        assumeTrue("No local receipt fixtures were staged", inputs.isNotEmpty())

        val engine = PaddleOcrEngine(context)
        val expectedBankCounts = mapOf(
            "中信银行.jpg" to 10, "中国工商银行1.jpg" to 8, "中国工商银行2.jpg" to 8,
            "中国建设银行1.jpg" to 6, "中国建设银行2.jpg" to 9, "中国银行.jpg" to 8,
            "建设银行.jpg" to 6
        )
        val expectedShoppingCounts = mapOf(
            "拼多多1.png" to 2, "拼多多2.png" to 3,
            "淘宝账单.jpg" to 2, "淘宝闪购订单页.jpg" to 2, "京东账单.jpg" to 3
        )
        val output = JSONArray()
        for (file in inputs) {
            val sample = JSONObject().put("name", file.name)
            runCatching { engine.recognize(Uri.fromFile(file)) }
                .onSuccess { result ->
                    sample.put("modelLoadMs", result.modelLoadMs)
                    sample.put("inferenceMs", result.inferenceMs)
                    val observations = result.lines.map { line ->
                        OcrObservation(line.text, line.corners.map { it.x }.average().toFloat(),
                            line.corners.map { it.y }.average().toFloat(), line.confidence)
                    }
                    val statements = StatementParser().parse(observations)
                    val shopping = ShoppingOrderParser().parse(observations)
                    val statementCount = statements.size
                    val shoppingCount = shopping.size
                    sample.put("statementCount", statementCount)
                    sample.put("shoppingCount", shoppingCount)
                    expectedBankCounts[file.name]?.let { assertEquals(file.name, it, statementCount) }
                    expectedShoppingCounts[file.name]?.let { assertEquals(file.name, it, shoppingCount) }
                    if (file.name == "建设银行.jpg") {
                        assertEquals(listOf(4835L, 5000L, 990L, 1120L, 4835L, 290L), statements.map { it.transaction.amountCents })
                        assertEquals(listOf("CREDIT", "DEBIT", "DEBIT", "DEBIT", "DEBIT", "DEBIT"), statements.map { it.transaction.direction })
                    }
                    val shoppingAmounts = mapOf(
                        "淘宝账单.jpg" to listOf(6378L, 2990L),
                        "淘宝闪购订单页.jpg" to listOf(1120L, 4590L),
                        "京东账单.jpg" to listOf(1738L, 1100L, 1755L)
                    )
                    shoppingAmounts[file.name]?.let { assertEquals(file.name, it, shopping.map { order -> order.amountCents }) }
                    if (file.name == "支付宝.jpg" || file.name.startsWith("微信")) {
                        assertEquals(file.name, if (file.name == "支付宝.jpg") 200L else 637L,
                            OrderParser().parse(result.text).amountCents)
                    }
                    sample.put("lines", JSONArray().also { lines ->
                        result.lines.forEach { line ->
                            lines.put(JSONObject()
                                .put("text", line.text)
                                .put("confidence", line.confidence)
                                .put("x", line.corners.map { it.x }.average())
                                .put("y", line.corners.map { it.y }.average()))
                        }
                    })
                }
                .onFailure { throw AssertionError("Fixture ${file.name} failed", it) }
            output.put(sample)
        }
        File(context.filesDir, "receipt-fixtures-result.json")
            .writeText(output.toString(2), Charsets.UTF_8)
    }
}
