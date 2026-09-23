package com.secureledger.core.accessibility

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Deterministic rule evaluation over short, already-filtered accessibility text. */
class TransactionRuleEngine {
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
            else amountPattern.findAll(line).mapNotNull { match ->
                val amount = match.groupValues.getOrNull(1).orEmpty().ifBlank { match.value }
                val cents = runCatching {
                    amount.replace(',', '.').replace(Regex("[^0-9.]"), "")
                        .toBigDecimal().multiply(BigDecimal(100)).longValueExact()
                }.getOrNull() ?: return@mapNotNull null
                if (cents !in 1..100_000_000) return@mapNotNull null
                val score = amountScore(line, lines.getOrNull(index - 1), lines.getOrNull(index + 1))
                if (score < 2) return@mapNotNull null
                Triple(cents, score, index)
            }.toList()
        }
        val winner = matches.maxWithOrNull(compareBy<Triple<Long, Int, Int>> { it.second }.thenBy { -it.third })
            ?: return null
        val merchant = extract(rule.merchantRegex, text)
            ?.lineSequence()?.firstOrNull()?.trim()?.take(60).orEmpty()
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

    private fun amountScore(line: String, previous: String?, next: String?): Int {
        val strong = listOf("实付", "付款金额", "支付金额", "已支付", "实际支付", "本次付款")
        if (strong.any(line::contains)) return 5
        if (line.contains('¥') || line.contains('￥') || line.contains('元')) {
            if (strong.any { previous?.contains(it) == true || next?.contains(it) == true }) return 4
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
