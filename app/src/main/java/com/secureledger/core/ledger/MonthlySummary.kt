package com.secureledger.core.ledger

import java.math.BigInteger
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.text.NumberFormat

data class MonthlyTransaction(val timestampMillis: Long, val type: String, val amountCents: Long)

/** Cash-flow totals use the effective decrypted payload, after corrections have been applied. */
data class MonthlySummary(
    val month: YearMonth,
    val expenseCents: BigInteger,
    val incomeCents: BigInteger,
    val transactionCount: Int
) {
    val balanceChangeCents: BigInteger get() = incomeCents - expenseCents

    companion object {
        fun calculate(entries: List<MonthlyTransaction>, nowMillis: Long, zoneId: ZoneId): MonthlySummary {
            val month = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zoneId))
            var expense = BigInteger.ZERO
            var income = BigInteger.ZERO
            var count = 0
            entries.forEach { entry ->
                if (YearMonth.from(Instant.ofEpochMilli(entry.timestampMillis).atZone(zoneId)) != month) return@forEach
                val amount = BigInteger.valueOf(entry.amountCents)
                when (entry.type) {
                    "EXPENSE" -> { expense += amount; count++ }
                    "INCOME" -> { income += amount; count++ }
                    // A transfer changes accounts, not net monthly cash flow.
                }
            }
            return MonthlySummary(month, expense, income, count)
        }
    }
}

fun formatYuan(cents: BigInteger): String {
    val absolute = cents.abs()
    val whole = NumberFormat.getIntegerInstance(Locale.CHINA).format(absolute.divide(BigInteger.valueOf(100)))
    val fraction = absolute.remainder(BigInteger.valueOf(100)).toString().padStart(2, '0')
    return "${if (cents.signum() < 0) "-" else ""}¥$whole.$fraction"
}
