package com.secureledger.core.accessibility

import java.math.BigDecimal
import java.util.UUID

/** Independent implementation of Biller's per-app notification extraction approach. */
object PaymentNotificationParser {
    private val amount = Regex("[¥￥]\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?|\\d{1,7}(?:\\.\\d{1,2})?)|(?<![\\d.])(\\d{1,7}(?:\\.\\d{1,2})?)\\s*元")
    fun parse(packageName: String, title: String, body: String, timestamp: Long): TransactionCandidate? {
        val channel = when (packageName) { "com.tencent.mm" -> "微信"; "com.eg.android.AlipayGphone" -> "支付宝"; else -> return null }
        val text = "$title\n$body".take(3000)
        if (listOf("退款", "收款成功", "收到转账", "到账", "付款失败", "支付失败", "待支付", "验证码").any(text::contains)) return null
        if (listOf("支付成功", "付款成功", "成功支付", "支出", "已付款", "付款给").none(text::contains) && !Regex("向.{1,60}付款").containsMatchIn(text)) return null
        // Multiple monetary values may include discounts or balances; ask through manual OCR instead.
        val amounts = amount.findAll(text).mapNotNull { match ->
            runCatching { BigDecimal(match.groupValues[1].ifBlank { match.groupValues[2] }.replace(",", "")).movePointRight(2).longValueExact() }.getOrNull()
        }.distinct().toList()
        val cents = amounts.singleOrNull()?.takeIf { it in 1..100_000_000 } ?: return null
        val merchant = Regex("向(.{1,60}?)付款|付款给\\s*(.{1,60}?)(?=[¥￥\\n，,。]|$)|(?:商户|收款方)[:：]\\s*([^\\n，,。¥￥]{1,60})")
            .find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return TransactionCandidate(UUID.randomUUID().toString(), cents, merchant, channel, timestamp,
            packageName, "notification-${if (channel == "微信") "wechat" else "alipay"}", 0.9f)
    }
}
