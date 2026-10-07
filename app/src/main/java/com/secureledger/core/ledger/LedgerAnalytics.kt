package com.secureledger.core.ledger

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.math.BigInteger

enum class LedgerGrouping(val label: String) { MONTH("按月"), WEEK("按周"), DAY("按日") }
data class AnalyticsEntry(val id: String, val timestamp: Long, val type: String, val cents: Long, val category: String)
data class MonthlyAnalytics(val income: BigInteger, val expense: BigInteger,
    val dailyExpenses: List<BigInteger>, val categories: List<Pair<String, BigInteger>>) {
    val net: BigInteger get() = income - expense
}

/** Calendar boundaries follow the user's timezone; transfers do not inflate income or expense. */
object LedgerAnalytics {
    fun date(timestamp: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    fun groupDate(timestamp: Long, grouping: LedgerGrouping, zone: ZoneId): LocalDate {
        val day = date(timestamp, zone)
        return when (grouping) {
            LedgerGrouping.MONTH -> day.withDayOfMonth(1)
            LedgerGrouping.WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            LedgerGrouping.DAY -> day
        }
    }
    fun calculate(entries: List<AnalyticsEntry>, month: YearMonth, zone: ZoneId): MonthlyAnalytics {
        val selected = entries.filter { YearMonth.from(date(it.timestamp, zone)) == month }
        fun total(type: String) = selected.filter { it.type == type }.fold(BigInteger.ZERO) { sum, it -> sum + BigInteger.valueOf(it.cents) }
        val expenses = selected.filter { it.type == "EXPENSE" }
        val daily = (1..month.lengthOfMonth()).map { day -> expenses.filter { date(it.timestamp, zone).dayOfMonth == day }
            .fold(BigInteger.ZERO) { sum, it -> sum + BigInteger.valueOf(it.cents) } }
        val categories = expenses.filter { it.cents > 0 }.groupBy { it.category.ifBlank { "其他" } }.map { (category, rows) ->
            category to rows.fold(BigInteger.ZERO) { sum, it -> sum + BigInteger.valueOf(it.cents) }
        }.sortedByDescending { it.second }
        return MonthlyAnalytics(total("INCOME"), total("EXPENSE"), daily, categories)
    }
}
