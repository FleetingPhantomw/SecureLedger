package com.secureledger.core.ocr

import java.math.BigDecimal
import kotlin.math.abs

/** One visible marketplace card is one order, even when product and merchant names repeat. */
class ShoppingOrderParser {
    private val total = Regex("(?:实付款|实付|应付款)\\s*[:：]?\\s*[¥￥]\\s*(\\d{1,7}(?:[.,]\\d{1,2})?)")
    private val price = Regex("[¥￥]\\s*(\\d{1,7}(?:[.,]\\d{1,2})?)")
    private val canceled = listOf("交易关闭", "已取消", "订单取消")
    private val status = listOf("已送达", "完成", "处方审核通过", "商品未入仓", "待收货", "待评价", "已签收", "已5星好评", "退款成功") + canceled
    private val navigation = listOf("全部", "待付款", "待发货", "待收货", "待使用", "待评价", "拼团中", "打包中", "评价", "购物", "外卖", "服务", "筛选")

    fun parse(observations: List<OcrObservation>): List<RecognizedOrder> {
        val lines = observations.filter { it.text.isNotBlank() && it.confidence >= 0.25f }.sortedBy { it.y }
        if (lines.none { "我的订单" in it.text || "搜索订单" in it.text }) return emptyList()
        val paymentChannel = when {
            lines.any { "拼多多" in it.text || "百亿补贴" in it.text } -> "拼多多"
            lines.any { "京东" in it.text || "搜索我的订单" in it.text } -> "京东"
            else -> "淘宝"
        }
        val maxX = lines.maxOfOrNull(OcrObservation::x) ?: return emptyList()
        val headers = lines.filter { line ->
            line.x >= maxX * 0.67f && status.any { line.text.trim().contains(it) } &&
                lines.any { peer -> abs(peer.y - line.y) <= 26 && peer.x in maxX * 0.12f..maxX * 0.67f &&
                    peer.text.length >= 3 && peer.text.any { it in '\u4e00'..'\u9fff' } &&
                    navigation.none { peer.text.trim() == it } }
        }.distinctBy { it.y.toInt() / 10 }
        return headers.mapIndexedNotNull { index, header ->
            if (canceled.any(header.text::contains) ||
                (paymentChannel == "拼多多" && "退款成功" in header.text)) return@mapIndexedNotNull null
            val end = headers.getOrNull(index + 1)?.y ?: Float.POSITIVE_INFINITY
            val segment = lines.filter { it.y >= header.y - 28 && it.y < end - 28 }
            val paidLine = segment.firstOrNull { total.containsMatchIn(it.text) }
            val amountText = paidLine?.let { total.find(it.text)?.groupValues?.get(1) }
                ?: segment.filter { it.x > maxX * 0.58f && price.containsMatchIn(it.text) }
                    .takeIf { it.size == 1 }?.single()?.let { price.find(it.text)?.groupValues?.get(1) }
                ?: return@mapIndexedNotNull null
            val cents = runCatching { BigDecimal(amountText.replace(',', '.')).movePointRight(2).longValueExact() }.getOrNull()
                ?.takeIf { it > 0 } ?: return@mapIndexedNotNull null
            val merchant = lines.filter { abs(it.y - header.y) <= 26 && it.x < header.x && it.x > maxX * 0.12f }
                .filterNot { navigation.contains(it.text.trim()) || "优惠" in it.text || "..." in it.text || "…" in it.text }
                .filter { it.text.trim().length >= 4 }
                .maxWithOrNull(compareBy<OcrObservation> { if ("店" in it.text) 2 else 1 }.thenBy { it.x })
                ?.text?.trim()?.removeSuffix(">")?.trim()
                ?.removePrefix("外卖")?.removePrefix("医药")?.trim().orEmpty()
            if (merchant.length < 2) return@mapIndexedNotNull null
            RecognizedOrder(
                amountCents = cents, merchant = merchant, transactionTime = null, category = "购物",
                paymentChannel = paymentChannel, account = "default", orderId = null, payments = emptyList(),
                rawText = segment.joinToString("\n") { it.text }.takeLast(1500),
                warnings = buildList {
                    add("订单页无准确支付时间；留空时会标记为估计时间")
                    if (paidLine == null) add("未显示实付总额，暂用可见商品价格，请核对")
                    if (paidLine?.text?.contains("应付款") == true) add("页面显示应付款，需核实是否实际支付")
                    if ("退款成功" in header.text) add("页面显示退款成功，按要求仍列出；请确认是否需要记账")
                }
            )
        }
    }
}
