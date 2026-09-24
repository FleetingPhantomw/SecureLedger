package com.secureledger.core.ocr

import java.math.BigDecimal

/** Extracts distinct paid items from a marketplace order-list screenshot. Refunded items are omitted. */
class ShoppingOrderParser {
    private val paid = Regex("实付\\s*[:：]?\\s*[¥￥]\\s*(\\d{1,7}(?:[.,]\\d{1,2})?)")
    private val store = Regex("^[\\p{IsHan}A-Za-z0-9·（）()]{2,36}(?:旗舰店|专卖店|专营店|直营店)$")

    fun parse(observations: List<OcrObservation>): List<RecognizedOrder> {
        val lines = observations.filter { it.confidence >= 0.25f }.sortedBy { it.y }
        if (lines.none { "我的订单" in it.text }) return emptyList()
        val paidIndices = lines.indices.filter { paid.containsMatchIn(lines[it].text) }
        if (paidIndices.size < 2) return emptyList()
        var previous = 0
        return paidIndices.mapNotNull { index ->
            val segment = lines.subList(previous, index + 1)
            previous = index + 1
            if (segment.any { "退款成功" in it.text }) return@mapNotNull null
            val merchant = segment.asReversed().firstOrNull { store.matches(it.text.trim()) }?.text?.trim()
                ?: return@mapNotNull null
            val value = paid.find(lines[index].text)?.groupValues?.get(1) ?: return@mapNotNull null
            val cents = runCatching { BigDecimal(value.replace(',', '.')).movePointRight(2).longValueExact() }.getOrNull()
                ?: return@mapNotNull null
            RecognizedOrder(
                amountCents = cents, merchant = merchant, transactionTime = null, category = "购物",
                paymentChannel = "拼多多", account = "default", orderId = null, payments = emptyList(),
                rawText = segment.joinToString("\n") { it.text }.takeLast(1500),
                warnings = listOf("订单列表未显示支付时间，请在保存前填写并核对", "请核对退款状态和实付金额")
            )
        }
    }
}
