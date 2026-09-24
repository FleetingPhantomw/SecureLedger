package com.secureledger.core

import com.secureledger.core.ocr.OcrObservation
import com.secureledger.core.ocr.StatementParser
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementParserTest {
    private val parser = StatementParser(ZoneOffset.UTC)
    private fun line(text: String, x: Int, y: Int) = OcrObservation(text, x.toFloat(), y.toFloat())

    @Test
    fun rejectsSummaryAndReadsSeparateCcbRows() {
        val rows = listOf(
            line("交易明细查询", 400, 120), line("总支出", 120, 400),
            line("￥1,296.94", 200, 480), line("￥1,223.39", 720, 480),
            line("2026/09/23 周三", 200, 680),
            line("支付宝-咖啡店", 280, 752), line("2.00", 750, 753),
            line("6221 *** 8073", 170, 796), line("人民币", 758, 797),
            line("2026/09/22 周二", 200, 900),
            line("支付宝-超市", 280, 976), line("15.71", 750, 976)
        )
        val result = parser.parse(rows)
        assertEquals(2, result.size)
        assertEquals(listOf(200L, 1571L), result.map { it.transaction.amountCents })
        assertEquals("8073", result.first().transaction.accountLast4)
        assertTrue(result.all { it.warnings.any(String::isNotBlank) })
    }

    @Test
    fun signedRowsAreSeparatedFromBalances() {
        val rows = listOf(
            line("收支分析", 500, 120), line("全部交易", 100, 250),
            line("银联二维码支付", 220, 394), line("-¥14.00", 750, 392),
            line("2026-09-24 12:24:42", 210, 442), line("余额15,658.52", 700, 442),
            line("退货", 220, 552), line("+¥6.50", 750, 551),
            line("2026-09-23 01:00:31", 210, 602), line("余额15,672.52", 700, 602)
        )
        val result = parser.parse(rows)
        assertEquals(2, result.size)
        assertEquals(listOf("DEBIT", "CREDIT"), result.map { it.transaction.direction })
        assertEquals(650L, result.last().transaction.amountCents)
        assertEquals(LocalDate.of(2026, 9, 24), java.time.Instant.ofEpochMilli(result.first().transaction.transactionTime).atZone(ZoneOffset.UTC).toLocalDate())
    }
}
