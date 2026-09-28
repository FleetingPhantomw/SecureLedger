package com.secureledger.core.accessibility

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Deterministic rule evaluation over short, already-filtered accessibility text. */
class TransactionRuleEngine {
    private val signedReceiptAmount = Regex("^\\s*[-−]\\s*[¥￥]?\\s*(\\d{1,7}[.,]\\d{1,2})\\s*$")
    fun evaluate(
        packageName: String,
        visibleLines: List<String>,
        rules: List<TransactionRule>,
        nowMs: Long
    ): TransactionCandidate? {
        val lines = visibleLines.take(100).map { it.trim().take(160) }.filter(String::isNotEmpty)
        if (lines.isEmpty()) return null
        val text = lines.joinToString("\n")
        return rules.asSequence().filter { it.enabled && it.targetPackage == packageName }
            .sortedByDescending(TransactionRule::priority)
            .mapNotNull { rule -> match(rule, packageName, lines, text, nowMs) }
            .maxWithOrNull(compareBy<TransactionCandidate> { it.confidence }
                .thenBy { rules.firstOrNull { rule -> rule.id == it.ruleId }?.priority ?: 0 })
    }

    private fun match(
        rule: TransactionRule,
        packageName: String,
        lines: List<String>,
        text: String,
        nowMs: Long
    ): TransactionCandidate? {
        if (rule.positiveKeywords.none { it.isNotBlank() && text.contains(it, ignoreCase = true) }) return null
        val amountPattern = runCatching { Regex(rule.amountRegex) }.getOrNull() ?: return null
        val matches = lines.flatMapIndexed { index, line ->
            if (rule.negativeKeywords.any { it.isNotBlank() && line.contains(it, ignoreCase = true) }) emptyList()
            else (amountPattern.findAll(line).map { it.groupValues.getOrNull(1).orEmpty().ifBlank { it.value } }.toList() +
                listOfNotNull(signedReceiptAmount.matchEntire(line)?.groupValues?.get(1))).mapNotNull { amount ->
                val cents = runCatching {
                    amount.replace(',', '.').replace(Regex("[^0-9.]"), "")
                        .toBigDecimal().multiply(BigDecimal(100)).longValueExact()
                }.getOrNull() ?: return@mapNotNull null
                if (cents !in 1..100_000_000) return@mapNotNull null
                val score = amountScore(line, lines, index, rule.positiveKeywords)
                if (score < 3) return@mapNotNull null
                Triple(cents, score, index)
            }.distinctBy { it.first to it.third }
        }.distinctBy { it.first to it.third }
        // A marketplace order list is not a single payment confirmation.
        if (matches.map { it.third }.distinct().size > 1) return null
        val winner = matches.maxWithOrNull(compareBy<Triple<Long, Int, Int>> { it.second }.thenBy { -it.third })
            ?: return null
        val merchant = extract(rule.merchantRegex, text)?.lineSequence()?.firstOrNull()?.trim()?.take(60)
            ?.takeUnless { it.contains("单号") || it.contains("时间") || it.contains("详情") }
            ?: lines.mapIndexed { index, value -> index to value }
                .filter { (index, value) ->
                    kotlin.math.abs(index - winner.third) in 1..4 && value.length in 2..60 &&
                        value.any { it in '\u4e00'..'\u9fff' } &&
                        listOf("账单详情", "交易详情", "支付时间", "付款方式", "当前状态", "收款方", "商品说明", "交易成功", "支付成功", "商户单号", "服务", "喜欢").none(value::contains)
                }.minByOrNull { (index, _) -> kotlin.math.abs(index - winner.third) }?.second?.take(60).orEmpty()
        val channel = extract(rule.paymentChannelRegex, text)?.let { raw ->
            when {
                raw.contains("微信") || raw.contains("零钱") -> "微信"
                raw.contains("支付宝") || raw.contains("花呗") -> "支付宝"
                raw.contains("银行卡") -> "银行卡"
                else -> raw.take(30)
            }
        } ?: when (packageName) {
            "com.tencent.mm" -> "微信"
            "com.eg.android.AlipayGphone" -> "支付宝"
            else -> ""
        }
        val confidence = (0.55f + winner.second * 0.07f + if (merchant.isNotBlank()) 0.07f else 0f)
            .coerceAtMost(0.99f)
        val idSource = "$packageName|${rule.id}|${winner.first}|$merchant|$channel"
        val id = MessageDigest.getInstance("SHA-256").digest(idSource.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        val transactionTime = extract(rule.timeRegex, text)?.let(::parseTime) ?: nowMs
        return TransactionCandidate(id, winner.first, merchant, channel, transactionTime, packageName, rule.id, confidence)
    }

    private fun amountScore(line: String, lines: List<String>, index: Int, positive: List<String>): Int {
        val strong = listOf("实付", "付款金额", "支付金额", "已支付", "实际支付", "本次付款")
        if (strong.any(line::contains)) return 5
        val nearby = lines.subList((index - 4).coerceAtLeast(0), (index + 5).coerceAtMost(lines.size))
        if (signedReceiptAmount.matches(line) && nearby.any { value -> positive.any { it.isNotBlank() && value.contains(it) } }) return 5
        val previous = lines.getOrNull(index - 1)
        val next = lines.getOrNull(index + 1)
        if (line.contains('¥') || line.contains('￥') || line.contains('元')) {
            if (strong.any { previous?.contains(it) == true || next?.contains(it) == true }) return 4
            if (nearby.any { value -> positive.any { it.isNotBlank() && value.contains(it) } }) return 3
            return 2
        }
        if (strong.any { previous?.contains(it) == true || next?.contains(it) == true }) return 3
        return 0
    }

    private fun extract(pattern: String, text: String): String? =
        if (pattern.isBlank()) null else runCatching {
            Regex(pattern, RegexOption.IGNORE_CASE).find(text)?.let { it.groupValues.getOrNull(1)?.takeIf(String::isNotBlank) ?: it.value }
        }.getOrNull()

    private fun parseTime(value: String): Long? = runCatching {
        LocalDateTime.parse(value.replace('/', '-'), DateTimeFormatter.ofPattern("yyyy-M-d H:m[:ss]"))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}

/** In-memory only: neither the source screen nor candidate fingerprints enter persistent storage. */
class CandidateDeduplicator(private val maxEntries: Int = 100) {
    private val seen = LinkedHashMap<String, Long>()

    fun shouldPrompt(candidate: TransactionCandidate, cooldownMs: Long, nowMs: Long): Boolean {
        seen.entries.removeAll { nowMs - it.value > 3_600_000 }
        val last = seen[candidate.id]
        if (last != null && nowMs - last < cooldownMs) return false
        seen[candidate.id] = nowMs
        while (seen.size > maxEntries) seen.remove(seen.keys.first())
        return true
    }
}
