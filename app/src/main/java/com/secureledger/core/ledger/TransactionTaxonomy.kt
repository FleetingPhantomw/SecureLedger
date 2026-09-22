package com.secureledger.core.ledger

/** Stable payment identifiers; display labels may change without rewriting history. */
enum class PaymentChannelCode(val displayName: String) {
    ALIPAY("支付宝"),
    WECHAT("微信"),
    BANK_CARD("银行卡"),
    /** Compatibility marker for old free-text channels; never offered for new records. */
    LEGACY_OTHER("其他（历史）");

    companion object {
        fun fromLegacy(value: String): PaymentChannelCode = when {
            value.contains("微信", ignoreCase = true) -> WECHAT
            value.contains("银行", ignoreCase = true) || value.contains("信用卡", ignoreCase = true) ||
                value.contains("储蓄卡", ignoreCase = true) || value.contains("校园卡", ignoreCase = true) -> BANK_CARD
            value.contains("支付宝", ignoreCase = true) -> ALIPAY
            else -> LEGACY_OTHER
        }
    }
}

/** Fixed primary categories based on the eight common resident-consumption groups. */
enum class ExpenseCategoryCode(val displayName: String) {
    FOOD_TOBACCO_ALCOHOL("食品烟酒"),
    CLOTHING("衣着"),
    HOUSING("居住"),
    HOUSEHOLD_SERVICES("生活用品及服务"),
    TRANSPORT_COMMUNICATION("交通通信"),
    EDUCATION_CULTURE_ENTERTAINMENT("教育文化娱乐"),
    HEALTHCARE("医疗保健"),
    OTHER("其他用品及服务");

    companion object {
        fun fromLegacy(value: String): ExpenseCategoryCode = when {
            listOf("餐", "食品", "烟酒", "外卖", "咖啡", "奶茶").any(value::contains) -> FOOD_TOBACCO_ALCOHOL
            listOf("衣", "服饰", "鞋帽").any(value::contains) -> CLOTHING
            listOf("房", "租", "水电", "居住").any(value::contains) -> HOUSING
            listOf("日用", "家政", "生活用品").any(value::contains) -> HOUSEHOLD_SERVICES
            listOf("交通", "地铁", "公交", "打车", "通信", "话费").any(value::contains) -> TRANSPORT_COMMUNICATION
            listOf("教育", "文化", "娱乐", "电影", "课程").any(value::contains) -> EDUCATION_CULTURE_ENTERTAINMENT
            listOf("医疗", "药", "医院", "保健").any(value::contains) -> HEALTHCARE
            else -> OTHER
        }
    }
}
