package com.secureledger.core.accessibility

import kotlinx.serialization.Serializable

@Serializable
data class TransactionRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val targetPackage: String,
    val positiveKeywords: List<String>,
    val negativeKeywords: List<String>,
    val amountRegex: String,
    val merchantRegex: String = "",
    val timeRegex: String = "",
    val paymentChannelRegex: String = "",
    val priority: Int = 0,
    val cooldownMs: Long = 60_000
) {
    fun validate() {
        require(id.isNotBlank() && name.isNotBlank()) { "规则名称不能为空" }
        require(targetPackage.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,120}"))) { "应用包名无效" }
        require(positiveKeywords.any(String::isNotBlank)) { "请添加至少一个正向关键词" }
        require(amountRegex.length in 1..160) { "金额规则长度无效" }
        require(merchantRegex.length <= 160 && timeRegex.length <= 160 && paymentChannelRegex.length <= 160) { "规则过长" }
        require(cooldownMs in 5_000..3_600_000) { "提醒间隔应为 5 秒至 1 小时" }
        listOf(amountRegex, merchantRegex, timeRegex, paymentChannelRegex)
            .filter(String::isNotEmpty).forEach { Regex(it) }
    }
}

@Serializable
data class TransactionCandidate(
    val id: String,
    val amountCents: Long,
    val merchant: String,
    val paymentChannel: String,
    val transactionTime: Long,
    val sourcePackage: String,
    val ruleId: String,
    val confidence: Float
)

object PresetTransactionRules {
    private const val AMOUNT = "(?:[¥￥]\\s*)?(\\d{1,7}(?:[.,]\\d{1,2})?)\\s*(?:元)?"

    val rules = listOf(
        TransactionRule(
            id = "alipay-payment", name = "支付宝付款", targetPackage = "com.eg.android.AlipayGphone",
            positiveKeywords = listOf("支付成功", "付款成功", "交易成功"),
            negativeKeywords = listOf("退款", "原价", "优惠", "预计", "余额"),
            amountRegex = AMOUNT, merchantRegex = "(?:收款方|商家|商户)[:：\\s]*(.+)",
            paymentChannelRegex = "支付宝|花呗", priority = 10
        ),
        TransactionRule(
            id = "wechat-payment", name = "微信付款", targetPackage = "com.tencent.mm",
            positiveKeywords = listOf("支付成功", "付款成功", "交易成功"),
            negativeKeywords = listOf("退款", "原价", "优惠", "预计", "余额"),
            amountRegex = AMOUNT, merchantRegex = "(?:收款方|商家|商户)[:：\\s]*(.+)",
            paymentChannelRegex = "微信|零钱|银行卡", priority = 10
        ),
        TransactionRule(
            id = "taobao-payment", name = "淘宝付款完成", targetPackage = "com.taobao.taobao",
            positiveKeywords = listOf("支付成功", "付款成功"),
            negativeKeywords = listOf("退款", "交易关闭", "已取消", "原价", "优惠", "应付款"),
            amountRegex = AMOUNT, merchantRegex = "(?:商家|店铺|商户)[:：\\s]*(.+)", priority = 8
        ),
        TransactionRule(
            id = "jd-payment", name = "京东付款完成", targetPackage = "com.jingdong.app.mall",
            positiveKeywords = listOf("支付成功", "付款成功"),
            negativeKeywords = listOf("退款", "已取消", "原价", "优惠", "应付款"),
            amountRegex = AMOUNT, merchantRegex = "(?:商家|店铺|商户)[:：\\s]*(.+)", priority = 8
        ),
        TransactionRule(
            id = "pdd-payment", name = "拼多多付款完成", targetPackage = "com.xunmeng.pinduoduo",
            positiveKeywords = listOf("支付成功", "付款成功"),
            negativeKeywords = listOf("退款", "已取消", "原价", "优惠", "应付款"),
            amountRegex = AMOUNT, merchantRegex = "(?:商家|店铺|商户)[:：\\s]*(.+)", priority = 8
        )
    )
}
