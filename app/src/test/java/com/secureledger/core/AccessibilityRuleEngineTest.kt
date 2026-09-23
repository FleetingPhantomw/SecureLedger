package com.secureledger.core

import com.secureledger.core.accessibility.CandidateDeduplicator
import com.secureledger.core.accessibility.PresetTransactionRules
import com.secureledger.core.accessibility.TransactionRule
import com.secureledger.core.accessibility.TransactionRuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityRuleEngineTest {
    private val engine = TransactionRuleEngine()
    private val alipay = PresetTransactionRules.rules.first()

    @Test
    fun selectsPaidAmountInsteadOfDiscountOrOriginalPrice() {
        val candidate = engine.evaluate(
            alipay.targetPackage,
            listOf("支付成功", "商户：午餐食堂", "原价 ¥35.00", "优惠 ¥6.50", "实付 ¥28.50"),
            listOf(alipay), 1000
        )!!
        assertEquals(2850, candidate.amountCents)
        assertEquals("午餐食堂", candidate.merchant)
        assertEquals("支付宝", candidate.paymentChannel)
    }

    @Test
    fun rejectsUnrelatedPackagesAndRefunds() {
        val lines = listOf("支付成功", "实付 ¥28.50")
        assertNull(engine.evaluate("com.example.other", lines, listOf(alipay), 1000))
        assertNull(engine.evaluate(alipay.targetPackage, listOf("退款成功", "原价 ¥28.50"), listOf(alipay), 1000))
        assertNull(engine.evaluate(alipay.targetPackage, listOf("支付成功", "订单号 12345678"), listOf(alipay), 1000))
    }

    @Test
    fun userRuleSupportsCustomPatternsAndPriority() {
        val rule = TransactionRule(
            id = "custom", name = "自定义", targetPackage = "com.example.payment",
            positiveKeywords = listOf("付款完成"), negativeKeywords = listOf("预计"),
            amountRegex = "金额[:：]\\s*(\\d+\\.\\d{2})",
            merchantRegex = "店铺[:：]\\s*(.+)", priority = 5, cooldownMs = 10_000
        )
        rule.validate()
        val candidate = engine.evaluate("com.example.payment", listOf("付款完成", "店铺：面馆", "实付金额：18.80"), listOf(rule), 42)!!
        assertEquals(1880, candidate.amountCents)
        assertEquals("面馆", candidate.merchant)
    }

    @Test
    fun repeatedEventsAreSuppressedUntilCooldown() {
        val candidate = engine.evaluate(alipay.targetPackage, listOf("支付成功", "实付 ¥28.50"), listOf(alipay), 1000)!!
        val deduplicator = CandidateDeduplicator()
        assertTrue(deduplicator.shouldPrompt(candidate, 60_000, 1000))
        assertFalse(deduplicator.shouldPrompt(candidate, 60_000, 2000))
        assertTrue(deduplicator.shouldPrompt(candidate, 60_000, 61_000))
    }
}
