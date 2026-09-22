package com.secureledger.core.ocr

import com.secureledger.core.ledger.PaymentDetail
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Editable structured draft produced from untrusted OCR or clipboard text. */
data class RecognizedOrder(
    val amountCents: Long?,
    val merchant: String,
    val transactionTime: Long?,
    val category: String,
    val paymentChannel: String,
    val account: String,
    val orderId: String?,
    val payments: List<PaymentDetail>,
    val rawText: String,
    val warnings: List<String>
)

/** Extracts payment components and identifies bank-direct portions for reconciliation. */
object PaymentParser {
    private val amountRegex = Regex("(?:¥|￥)?\\s*(-?\\d{1,9}(?:[.,]\\d{1,2})?)")
    private val channelKeywords = linkedMapOf(
        "银行卡" to listOf("银行卡", "储蓄卡", "信用卡", "bank"),
        "微信支付" to listOf("微信支付", "微信"),
        "支付宝" to listOf("支付宝", "花呗"),
        "校园卡" to listOf("校园卡", "一卡通"),
        "钱包余额" to listOf("钱包余额", "余额", "零钱")
    )

    fun parse(lines: List<String>): List<PaymentDetail> = lines.mapNotNull { line ->
        val channel = channelKeywords.entries.firstOrNull { (_, aliases) -> aliases.any(line::contains) }?.key
            ?: return@mapNotNull null
        val amount = amountRegex.findAll(line).lastOrNull()?.groupValues?.get(1)?.let(::parseCents)
            ?: return@mapNotNull null
        PaymentDetail(amount, channel, channel == "银行卡")
    }.filter { it.amountCents > 0 }

    internal fun parseCents(value: String): Long = value.replace(',', '.').toBigDecimal().abs()
        .multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
}

/** Uses receipt-oriented labels and noise rejection to select a plausible merchant. */
object MerchantParser {
    private val labelled = Regex("(?:商户名称|收款方|商家|商户)\\s*[:：]?\\s*(.+)", RegexOption.IGNORE_CASE)
    private val noise = listOf("支付成功", "交易成功", "订单详情", "账单详情", "付款方式", "交易时间", "订单号", "实付", "合计")

    fun parse(lines: List<String>): String {
        lines.firstNotNullOfOrNull { labelled.find(it)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank) }
            ?.let { return it.take(80) }
        return lines.firstOrNull { line ->
            line.length in 2..40 && line.any(Char::isLetter) && noise.none(line::contains) &&
                !line.matches(Regex(".*\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}.*"))
        }?.trim()?.take(80).orEmpty()
    }
}

/** Deterministic local parser that turns OCR text into an editable order draft. */
class OrderParser(private val zoneId: ZoneId = ZoneId.systemDefault()) {
    private val totalLabels = listOf("实付", "支付金额", "订单金额", "合计", "应付", "消费金额")
    private val amountRegex = Regex("(?:¥|￥)?\\s*(-?\\d{1,9}(?:[.,]\\d{1,2})?)")
    private val orderRegex = Regex("(?:订单号|交易单号|商户单号|流水号)\\s*[:：]?\\s*([A-Za-z0-9_-]{6,64})", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("(20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}(?:日)?[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)")

    fun parse(rawText: String): RecognizedOrder {
        val lines = rawText.lines().map(String::trim).filter(String::isNotBlank)
        val payments = PaymentParser.parse(lines)
        val labelledAmount = lines.firstNotNullOfOrNull { line ->
            if (totalLabels.any(line::contains)) amountRegex.findAll(line).lastOrNull()?.groupValues?.get(1)?.let(PaymentParser::parseCents)
            else null
        }
        val amount = labelledAmount ?: payments.takeIf { it.isNotEmpty() }?.sumOf(PaymentDetail::amountCents)
            ?: lines.flatMap { amountRegex.findAll(it).toList() }
                .mapNotNull { runCatching { PaymentParser.parseCents(it.groupValues[1]) }.getOrNull() }
                .filter { it in 1..100_000_000 }
                .maxOrNull()
        val merchant = MerchantParser.parse(lines)
        val orderId = orderRegex.find(rawText)?.groupValues?.getOrNull(1)
        val time = timeRegex.find(rawText)?.groupValues?.getOrNull(1)?.let(::parseTime)
        val normalizedPayments = when {
            amount == null -> emptyList()
            payments.isNotEmpty() && payments.sumOf(PaymentDetail::amountCents) == amount -> payments
            payments.size == 1 -> listOf(payments.single().copy(amountCents = amount))
            else -> emptyList()
        }
        val channel = when {
            normalizedPayments.size > 1 -> "组合支付"
            normalizedPayments.size == 1 -> normalizedPayments.single().channel
            else -> detectChannel(rawText)
        }
        val warnings = buildList {
            if (amount == null) add("未识别金额")
            if (merchant.isBlank()) add("未识别商户")
            if (time == null) add("未识别交易时间，将使用当前时间")
            if (payments.size > 1 && normalizedPayments.isEmpty()) add("组合支付明细之和与总额不一致，请手动确认")
        }
        return RecognizedOrder(
            amount, merchant, time, inferCategory(rawText), channel, "default", orderId,
            normalizedPayments, rawText.take(20_000), warnings
        )
    }

    private fun parseTime(value: String): Long? {
        val normalized = value.replace('年', '-').replace('月', '-').replace("日", "")
            .replace('/', '-').replace('.', '-').replace('T', ' ')
        return TIME_FORMATS.firstNotNullOfOrNull { format ->
            try { LocalDateTime.parse(normalized, format).atZone(zoneId).toInstant().toEpochMilli() }
            catch (_: DateTimeParseException) { null }
        }
    }

    private fun detectChannel(text: String) = when {
        text.contains("微信") -> "微信支付"
        text.contains("支付宝") || text.contains("花呗") -> "支付宝"
        text.contains("银行卡") || text.contains("信用卡") || text.contains("储蓄卡") -> "银行卡"
        text.contains("校园卡") || text.contains("一卡通") -> "校园卡"
        else -> "OCR 导入"
    }

    private fun inferCategory(text: String) = when {
        listOf("餐", "食堂", "外卖", "咖啡", "奶茶").any(text::contains) -> "餐饮"
        listOf("地铁", "公交", "打车", "停车", "加油").any(text::contains) -> "交通"
        listOf("超市", "商场", "购物").any(text::contains) -> "购物"
        else -> "未分类"
    }

    companion object {
        private val TIME_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm")
        )
    }
}
