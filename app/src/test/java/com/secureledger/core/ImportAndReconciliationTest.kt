package com.secureledger.core

import com.secureledger.core.importer.BusinessFingerprint
import com.secureledger.core.importer.DataSourceType
import com.secureledger.core.importer.DuplicateLevel
import com.secureledger.core.importer.ImportedTransaction
import com.secureledger.core.importer.ReconciliationMatcher
import com.secureledger.core.importer.ReconciliationStatus
import com.secureledger.core.importer.StatementCsvParser
import com.secureledger.core.importer.TransactionDirection
import com.secureledger.core.ledger.PlainRecordPayload
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportAndReconciliationTest {
    private val parser = StatementCsvParser(ZoneOffset.UTC)

    @Test
    fun parsesQuotedBankAndCampusRows() {
        val bank = parser.parse(
            "交易时间,支出金额,收入金额,商户,订单号,卡号,备注\n" +
                "2026-09-18 12:30:00,25.60,,\"食堂,一楼\",A-1001,622200001234,午餐\n" +
                "2026-09-18 13:00:00,,100.00,奖学金,A-1002,622200001234,入账\n",
            DataSourceType.BANK
        )
        assertTrue(bank.issues.isEmpty())
        assertEquals(2, bank.transactions.size)
        assertEquals(2560L, bank.transactions[0].amountCents)
        assertEquals("食堂,一楼", bank.transactions[0].merchant)
        assertEquals(TransactionDirection.CREDIT.name, bank.transactions[1].direction)
        assertEquals("1234", bank.transactions[0].accountLast4)

        val campus = parser.parse(
            "日期时间,金额,地点,交易类型\n2026-09-18 18:05,-8.50,东区食堂,消费\n",
            DataSourceType.CAMPUS_CARD
        )
        assertEquals(850L, campus.transactions.single().amountCents)
        assertEquals("消费", campus.transactions.single().category)
    }

    @Test
    fun fingerprintAndDuplicateLevelsAreDeterministicAndConservative() {
        val base = transaction(orderId = "ORDER-1")
        assertEquals(BusinessFingerprint.calculate(base), BusinessFingerprint.calculate(base.copy()))
        assertEquals(DuplicateLevel.CONFIRMED_DUPLICATE, BusinessFingerprint.classify(base, base.copy(merchant = "另一个显示名")))
        assertEquals(
            DuplicateLevel.HIGHLY_SUSPECTED,
            BusinessFingerprint.classify(base.copy(externalOrderId = null), base.copy(externalOrderId = null, transactionTime = base.transactionTime + 60_000))
        )
        assertEquals(
            DuplicateLevel.NOT_DUPLICATE,
            BusinessFingerprint.classify(base, base.copy(externalOrderId = "ORDER-2", amountCents = 9999, transactionTime = base.transactionTime + 3 * 86_400_000L))
        )
        assertNotEquals(BusinessFingerprint.calculate(base), BusinessFingerprint.calculate(base.copy(amountCents = 2601)))
    }

    @Test
    fun reconciliationDistinguishesMatchConflictAndMissing() {
        val external = transaction(orderId = null)
        val exact = payload(amount = 2560, merchant = "东区食堂", time = external.transactionTime + 90_000)
        assertEquals(ReconciliationStatus.MATCHED, ReconciliationMatcher.match(external, listOf("r1" to exact)).status)

        val conflict = payload(amount = 3000, merchant = "东区食堂", time = external.transactionTime + 60_000)
        assertEquals(ReconciliationStatus.CONFLICT, ReconciliationMatcher.match(external, listOf("r2" to conflict)).status)

        val unrelated = payload(amount = 2560, merchant = "书店", time = external.transactionTime + 3 * 86_400_000L)
        assertEquals(ReconciliationStatus.MISSING, ReconciliationMatcher.match(external, listOf("r3" to unrelated)).status)
    }

    private fun transaction(orderId: String?) = ImportedTransaction(
        sourceType = DataSourceType.BANK.name,
        externalOrderId = orderId,
        amountCents = 2560,
        direction = TransactionDirection.DEBIT.name,
        merchant = "东区食堂",
        transactionTime = 1_790_000_000_000,
        paymentChannel = "银行卡",
        accountLast4 = "1234",
        category = "餐饮",
        rawDescription = "午餐",
        sourceRow = 2
    )

    private fun payload(amount: Long, merchant: String, time: Long) = PlainRecordPayload(
        recordType = "EXPENSE",
        amountCents = amount,
        category = "餐饮",
        merchant = merchant,
        note = "",
        account = "default",
        paymentChannel = "manual",
        transactionTime = time
    )
}
