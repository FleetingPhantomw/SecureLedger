package com.secureledger.core

import com.secureledger.core.ledger.ExpenseCategoryCode
import com.secureledger.core.ledger.PaymentChannelCode
import com.secureledger.data.entity.BankCardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTaxonomyTest {
    @Test fun legacyPaymentChannelsMapToStructuredCodes() {
        assertEquals(PaymentChannelCode.WECHAT, PaymentChannelCode.fromLegacy("微信支付"))
        assertEquals(PaymentChannelCode.BANK_CARD, PaymentChannelCode.fromLegacy("招商银行储蓄卡"))
        assertEquals(PaymentChannelCode.ALIPAY, PaymentChannelCode.fromLegacy("支付宝"))
        assertEquals(PaymentChannelCode.LEGACY_OTHER, PaymentChannelCode.fromLegacy("手动录入"))
    }

    @Test fun legacyCategoriesMapConservatively() {
        assertEquals(ExpenseCategoryCode.FOOD_TOBACCO_ALCOHOL, ExpenseCategoryCode.fromLegacy("餐饮"))
        assertEquals(ExpenseCategoryCode.TRANSPORT_COMMUNICATION, ExpenseCategoryCode.fromLegacy("地铁"))
        assertEquals(ExpenseCategoryCode.OTHER, ExpenseCategoryCode.fromLegacy("无法识别的旧分类"))
    }

    @Test fun bankCardStoresOnlyFourDigits() {
        assertTrue(runCatching { BankCardEntity("id", "招商银行", "工资卡", "6222021234564821", true, 1) }.isFailure)
        val card = BankCardEntity("id", "招商银行", "工资卡", "4821", true, 1)
        assertEquals("招商银行 · 工资卡 · 4821", card.displaySnapshot)
    }
}
