package com.secureledger.core

import com.secureledger.core.ledger.*
import com.secureledger.core.settlement.*
import com.secureledger.core.accessibility.*
import java.time.*
import java.math.BigInteger
import org.junit.Test
import org.junit.Assert.*

class ModernFeaturesTest {
    @Test fun monthlyTotalsRespectTimezoneAndExcludeTransfers() {
        val zone = ZoneId.of("Asia/Shanghai")
        val entries = listOf(
            AnalyticsEntry("1", Instant.parse("2026-09-30T16:01:00Z").toEpochMilli(), "EXPENSE", 1234, "餐饮"),
            AnalyticsEntry("2", Instant.parse("2026-10-31T16:01:00Z").toEpochMilli(), "EXPENSE", 9999, "餐饮"),
            AnalyticsEntry("3", Instant.parse("2026-10-02T01:00:00Z").toEpochMilli(), "TRANSFER", 999, "转账"))
        val result = LedgerAnalytics.calculate(entries, YearMonth.of(2026, 10), zone)
        assertEquals(BigInteger.valueOf(1234), result.expense)
        assertEquals(BigInteger.ZERO, result.income)
        assertEquals(31, result.dailyExpenses.size)
        assertEquals(result.expense, result.categories.fold(BigInteger.ZERO) { sum, it -> sum + it.second })
        assertEquals(LocalDate.of(2026, 9, 28), LedgerAnalytics.groupDate(entries[0].timestamp, LedgerGrouping.WEEK, zone))
    }
    @Test fun selectedBillsConserveCentsAndRemainderIsDeterministic() {
        val bills = listOf(SplitBill("meal", "a", 12000), SplitBill("ticket", "a", 4501), SplitBill("hotel", "b", 7500))
        val plan = SelectedBillSplit.calculate(bills, listOf("b", "a"))
        assertEquals(24001L, plan.totalCents)
        assertEquals(listOf(12001L, 12000L), plan.allocations.map { it.owedCents })
        assertEquals(listOf(4500L, -4500L), plan.balances.map { it.cents })
        assertEquals(listOf(Transfer("b", "a", 4500)), SettlementCalculator.calculate(plan.balances))
        assertTrue(runCatching { SelectedBillSplit.calculate(bills, listOf("a", "c")) }.isFailure)
        assertTrue(runCatching { SelectedBillSplit.calculate(bills + bills.first(), listOf("a", "b")) }.isFailure)
        assertTrue(runCatching { SelectedBillSplit.calculate(bills, listOf("a", "a")) }.isFailure)
        assertTrue(runCatching { SelectedBillSplit.calculate(listOf(SplitBill("x", "a", -1)), listOf("a", "b")) }.isFailure)
        assertTrue(runCatching { SelectedBillSplit.calculate(listOf(SplitBill("x", "a", Long.MAX_VALUE),
            SplitBill("y", "b", 1)), listOf("a", "b")) }.isFailure)
        val legacy = SettlementCalculator.createProof("p", "s", "root", plan.balances, 1000)
        assertEquals("p|s|root|a,b,|a=4500,b=-4500,|b>a=4500,|1000", legacy.canonicalBytes().toString(Charsets.UTF_8))
        assertTrue(legacy.verifyHash())
        val selected = legacy.copy(selectedRecordIds = plan.recordIds, allocations = plan.allocations).let {
            it.copy(hash = com.secureledger.core.crypto.HashManager.sha256Hex(it.canonicalBytes()))
        }
        assertTrue(selected.verifyHash())
        assertTrue(selected.canonicalBytes().contentEquals(selected.copy(selectedRecordIds = plan.recordIds.reversed(),
            allocations = plan.allocations.reversed()).canonicalBytes()))
        assertFalse(selected.copy(selectedRecordIds = listOf("meal")).verifyHash())
        assertFalse(selected.copy(allocations = plan.allocations.map { it.copy(owedCents = it.owedCents + 1) }).verifyHash())
    }
    @Test fun fusionEnrichesOnlyCompatibleDifferentSources() {
        fun candidate(merchant: String = "") = TransactionCandidate("a", 1200, merchant, "微信", 1000, "com.tencent.mm", "test", 0.9f)
        val gate = RecognitionFusion()
        val first = gate.accept(candidate(), DetectionSource.NOTIFICATION, "n1", 1000)!!
        assertTrue(first.isNew)
        assertNull(gate.accept(candidate(), DetectionSource.NOTIFICATION, "n1", 1100))
        val fused = gate.accept(candidate("早餐店"), DetectionSource.SCREEN, "s1", 1200)!!
        assertFalse(fused.isNew)
        assertEquals("早餐店", fused.candidate.merchant)
        assertTrue(gate.accept(candidate("早餐店"), DetectionSource.SCREEN, "s2", 1300)!!.isNew)
        assertTrue(gate.accept(candidate("其他店"), DetectionSource.NOTIFICATION, "n2", 1400)!!.isNew)
    }
    @Test fun paymentNotificationsRejectIncomingAndAmbiguousAmounts() {
        assertEquals(123456L, PaymentNotificationParser.parse("com.tencent.mm", "微信支付", "向早餐店付款成功 ¥1,234.56", 1000)?.amountCents)
        assertEquals(101L, PaymentNotificationParser.parse("com.eg.android.AlipayGphone", "支付宝", "你有一笔1.01元的支出，点击领取2个支付宝积分", 1000)?.amountCents)
        assertNull(PaymentNotificationParser.parse("com.tencent.mm", "微信", "收款成功 ¥12.00", 1000))
        assertNull(PaymentNotificationParser.parse("com.tencent.mm", "微信", "支付成功 ¥12.00 余额 ¥100.00", 1000))
        assertNull(PaymentNotificationParser.parse("fake.app", "支付成功", "¥12.00", 1000))
    }
}
