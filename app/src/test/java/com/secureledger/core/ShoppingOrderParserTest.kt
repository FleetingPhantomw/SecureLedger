package com.secureledger.core

import com.secureledger.core.ocr.OcrObservation
import com.secureledger.core.ocr.ShoppingOrderParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingOrderParserTest {
    private val parser = ShoppingOrderParser()
    private fun line(text: String, x: Int, y: Int) = OcrObservation(text, x.toFloat(), y.toFloat())

    @Test
    fun repeatedProductNamesAreIndependentAndOnlyClosedCardIsExcluded() {
        val result = parser.parse(listOf(
            line("搜索订单", 240, 100),
            line("幸福人大药房旗舰店>", 240, 390), line("处方审核通过", 730, 390),
            line("同一商品", 450, 460), line("实付款 ¥63.78", 730, 816),
            line("幸福人大药房旗舰店>", 240, 1022), line("交易关闭", 730, 1022),
            line("同一商品", 450, 1090), line("应付款 ¥63.78", 730, 1332),
            line("幸福人大药房旗舰店>", 240, 1537), line("商品未入仓", 730, 1537),
            line("同一商品 ¥29.9", 558, 1611)
        ))
        assertEquals(2, result.size)
        assertEquals(listOf(6378L, 2990L), result.map { it.amountCents })
        assertEquals(result[0].merchant, result[1].merchant)
        assertNull(result[0].transactionTime)
    }

    @Test
    fun sameMerchantSeparateJdCardsRemainSeparate() {
        val result = parser.parse(listOf(
            line("搜索我的订单", 286, 120),
            line("塔斯汀·中国汉堡(无锡店)>", 418, 424), line("完成", 786, 424),
            line("¥17.38", 766, 531),
            line("塔斯汀·中国汉堡(无锡店)>", 418, 952), line("完成", 786, 952),
            line("¥11.00", 769, 1058)
        ))
        assertEquals(listOf(1738L, 1100L), result.map { it.amountCents })
    }

    @Test
    fun taobaoRefundStatusRemainsVisibleForUserDecision() {
        val result = parser.parse(listOf(
            line("搜索订单", 240, 100),
            line("幸福人大药房旗舰店>", 240, 390), line("退款成功", 730, 390),
            line("实付款 ¥63.78", 730, 816)
        ))
        assertEquals(1, result.size)
        assertEquals("淘宝", result.single().paymentChannel)
        assertEquals(6378L, result.single().amountCents)
        assertEquals(true, result.single().warnings.any { "退款成功" in it })
    }
}
