package com.secureledger.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** User-managed payment card reference; a full card number is never accepted or stored. */
@Entity(tableName = "bank_cards", indices = [Index("enabled"), Index("createdAt")])
@Serializable
data class BankCardEntity(
    @PrimaryKey val id: String,
    val bankName: String,
    val cardAlias: String,
    val lastFourDigits: String,
    val enabled: Boolean,
    val createdAt: Long
) {
    init {
        require(bankName.trim().isNotEmpty()) { "银行名称不能为空" }
        require(cardAlias.length <= 30) { "银行卡别名过长" }
        require(lastFourDigits.matches(Regex("\\d{4}"))) { "仅保存银行卡后四位" }
    }

    val displaySnapshot: String
        get() = listOf(bankName.trim(), cardAlias.trim()).filter(String::isNotEmpty).joinToString(" · ") + " · $lastFourDigits"
}
