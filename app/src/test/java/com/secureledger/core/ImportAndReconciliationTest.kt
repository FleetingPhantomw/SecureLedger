package com.secureledger.core

import com.secureledger.core.importer.BusinessFingerprint
import com.secureledger.core.importer.DataSourceType
import com.secureledger.core.importer.DuplicateLevel
import com.secureledger.core.importer.ImportedTransaction
import com.secureledger.core.importer.ReconciliationMatcher
import com.secureledger.core.importer.ReconciliationStatus
import com.secureledger.core.importer.TransactionDirection
import com.secureledger.core.ledger.PlainRecordPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportAndReconciliationTest {
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
