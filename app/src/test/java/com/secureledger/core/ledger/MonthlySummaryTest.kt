package com.secureledger.core.ledger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

class MonthlySummaryTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(day: Int, hour: Int = 12) =
        LocalDateTime.of(2026, 9, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun countsOnlyThisMonthsIncomeAndExpense() {
        val result = MonthlySummary.calculate(listOf(
            MonthlyTransaction(millis(1), "EXPENSE", 2350),
            MonthlyTransaction(millis(2), "INCOME", 10000),
            MonthlyTransaction(millis(3), "TRANSFER", 5000),
            MonthlyTransaction(LocalDateTime.of(2026, 8, 31, 12, 0).atZone(zone).toInstant().toEpochMilli(), "EXPENSE", 9999)
        ), millis(20), zone)
        assertEquals(BigInteger.valueOf(2350), result.expenseCents)
        assertEquals(BigInteger.valueOf(10000), result.incomeCents)
        assertEquals(BigInteger.valueOf(7650), result.balanceChangeCents)
        assertEquals(2, result.transactionCount)
    }

    @Test fun monthBoundaryUsesLocalTimeZone() {
        val justAfterMidnight = Instant.parse("2026-08-31T16:30:00Z").toEpochMilli()
        val result = MonthlySummary.calculate(listOf(
            MonthlyTransaction(justAfterMidnight, "EXPENSE", 123)
        ), millis(20), zone)
        assertEquals(BigInteger.valueOf(123), result.expenseCents)
        assertEquals(BigInteger.ZERO,
            MonthlySummary.calculate(listOf(MonthlyTransaction(justAfterMidnight, "EXPENSE", 123)), millis(20), ZoneId.of("UTC")).expenseCents)
    }

    @Test fun largeTotalsDoNotOverflowAndMoneyFormattingKeepsSign() {
        val result = MonthlySummary.calculate(listOf(
            MonthlyTransaction(millis(1), "EXPENSE", Long.MAX_VALUE),
            MonthlyTransaction(millis(2), "EXPENSE", Long.MAX_VALUE)
        ), millis(20), zone)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), result.expenseCents)
        assertEquals("-¥23.50", formatYuan(BigInteger.valueOf(-2350)))
    }
}
