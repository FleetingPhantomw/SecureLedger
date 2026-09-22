package com.secureledger.core

import com.secureledger.core.ocr.MerchantParser
import com.secureledger.core.ocr.OrderParser
import com.secureledger.core.ocr.PaymentParser
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderParserTest {
    private val parser = OrderParser(ZoneOffset.UTC)

    @Test
    fun parsesChineseOrderAndTime() {
        val order = parser.parse(
            """
            支付成功
            商户名称：校园咖啡店
            实付 ¥36.80
            付款方式 微信支付
            交易时间 2026-09-18 09:31:20
            订单号 202609180001ABC
            """.trimIndent()
        )
        assertEquals(3680L, order.amountCents)
        assertEquals("校园咖啡店", order.merchant)
        assertEquals("微信支付", order.paymentChannel)
        assertEquals("餐饮", order.category)
        assertEquals("202609180001ABC", order.orderId)
        assertNotNull(order.transactionTime)
        assertTrue(order.warnings.isEmpty())
    }

    @Test
    fun parsesExactCombinedPaymentAndMarksBankPart() {
        val order = parser.parse(
            """
            星河超市
            订单金额 100.00
            钱包余额 30.00
            银行卡 70.00
            交易时间 2026-09-18 20:10
            """.trimIndent()
        )
        assertEquals(10_000L, order.amountCents)
        assertEquals(2, order.payments.size)
        assertEquals(3_000L, order.payments[0].amountCents)
        assertEquals(7_000L, order.payments[1].amountCents)
        assertTrue(order.payments[1].isBankDirect)
        assertEquals("组合支付", order.paymentChannel)
    }

    @Test
    fun merchantAndPaymentParsersRejectNoise() {
        assertEquals("东区食堂", MerchantParser.parse(listOf("支付成功", "东区食堂", "实付 12.00")))
        assertTrue(PaymentParser.parse(listOf("订单号 123456", "总计 20.00")).isEmpty())
    }
}
