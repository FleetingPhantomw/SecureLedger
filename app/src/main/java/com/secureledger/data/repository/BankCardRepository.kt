package com.secureledger.data.repository

import com.secureledger.data.dao.BankCardDao
import com.secureledger.data.entity.BankCardEntity
import java.util.UUID

/** Validated CRUD boundary for non-sensitive bank-card references. */
class BankCardRepository(private val dao: BankCardDao) {
    fun observeAll() = dao.observeAll()

    suspend fun save(id: String?, bankName: String, alias: String, lastFour: String, enabled: Boolean = true) {
        val existing = id?.let { dao.get(it) }
        dao.upsert(BankCardEntity(
            id = existing?.id ?: "card_${UUID.randomUUID()}",
            bankName = bankName.trim(),
            cardAlias = alias.trim(),
            lastFourDigits = lastFour.trim(),
            enabled = enabled,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        ))
    }

    suspend fun setEnabled(card: BankCardEntity, enabled: Boolean) = dao.upsert(card.copy(enabled = enabled))
    suspend fun delete(card: BankCardEntity) = dao.delete(card)
}
