package com.secureledger.core.settlement

import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.crypto.encodeBase64
import kotlinx.serialization.Serializable

/** A participant's signed net balance in cents: positive means receiving, negative means paying. */
@Serializable
data class Balance(val participantId: String, val cents: Long)

/** One deterministic settlement transfer expressed in cents. */
@Serializable
data class Transfer(val from: String, val to: String, val cents: Long)

/** Settlement proof binds calculated transfers to the ledger Merkle root. */
@Serializable
data class SettlementProof(
    val projectId: String,
    val settlementId: String,
    val ledgerMerkleRoot: String,
    val participants: List<String>,
    val balances: List<Balance>,
    val transfers: List<Transfer>,
    val createdAt: Long,
    val hash: String,
    val signatures: List<SettlementSignature>,
    val selectedRecordIds: List<String> = emptyList(),
    val allocations: List<MemberAllocation> = emptyList()
) {
    fun canonicalBytes(): ByteArray = buildString {
        append(projectId).append('|').append(settlementId).append('|').append(ledgerMerkleRoot).append('|')
        participants.sorted().forEach { append(it).append(',') }
        append('|')
        balances.sortedBy { it.participantId }.forEach { append(it.participantId).append('=').append(it.cents).append(',') }
        append('|')
        transfers.sortedWith(compareBy({ it.from }, { it.to })).forEach { append(it.from).append('>').append(it.to).append('=').append(it.cents).append(',') }
        append('|').append(createdAt)
        // Empty extension preserves canonical bytes of existing settlement proofs.
        if (selectedRecordIds.isNotEmpty() || allocations.isNotEmpty()) {
            append("|selected-v1|")
            selectedRecordIds.sorted().forEach { append(it).append(',') }
            append('|')
            allocations.sortedBy { it.participantId }.forEach {
                append(it.participantId).append('=').append(it.paidCents).append(':').append(it.owedCents).append(',')
            }
        }
    }.toByteArray(Charsets.UTF_8)

    fun verifyHash(): Boolean = HashManager.sha256Hex(canonicalBytes()) == hash

    fun verifySignatures(): Boolean = signatures.isNotEmpty() && signatures.all {
        runCatching {
            Ed25519Manager.verify(it.publicKey.decodeBase64(), hash.toByteArray(Charsets.UTF_8), it.signature.decodeBase64())
        }.getOrDefault(false)
    }
}

@Serializable
/** Detached Ed25519 confirmation by one project member. */
data class SettlementSignature(val signerId: String, val publicKey: String, val signature: String)

/** Deterministic balance-to-transfer calculation and proof construction. */
object SettlementCalculator {
    fun calculate(balances: List<Balance>): List<Transfer> {
        require(balances.size >= 2) { "At least two participants are required" }
        require(balances.map { it.participantId }.distinct().size == balances.size) {
            "Participant IDs must be unique"
        }
        require(balances.all { it.participantId.isNotBlank() }) { "Participant ID cannot be blank" }
        require(balances.sumOf { it.cents } == 0L) { "Balances do not sum to zero" }
        val creditors = balances.filter { it.cents > 0 }.sortedByDescending { it.cents }.toMutableList()
        val debtors = balances.filter { it.cents < 0 }.map { it.copy(cents = -it.cents) }.sortedByDescending { it.cents }.toMutableList()
        val result = mutableListOf<Transfer>()
        var creditorIndex = 0
        var debtorIndex = 0
        while (creditorIndex < creditors.size && debtorIndex < debtors.size) {
            val creditor = creditors[creditorIndex]
            val debtor = debtors[debtorIndex]
            val amount = minOf(creditor.cents, debtor.cents)
            if (amount > 0) result += Transfer(debtor.participantId, creditor.participantId, amount)
            creditors[creditorIndex] = creditor.copy(cents = creditor.cents - amount)
            debtors[debtorIndex] = debtor.copy(cents = debtor.cents - amount)
            if (creditors[creditorIndex].cents == 0L) creditorIndex++
            if (debtors[debtorIndex].cents == 0L) debtorIndex++
        }
        return result
    }

    fun createProof(
        projectId: String,
        settlementId: String,
        merkleRoot: String,
        balances: List<Balance>,
        createdAt: Long,
        signatures: List<SettlementSignature> = emptyList()
    ): SettlementProof {
        val unsigned = SettlementProof(projectId, settlementId, merkleRoot, balances.map { it.participantId }, balances,
            calculate(balances), createdAt, "", signatures)
        return unsigned.copy(hash = HashManager.sha256Hex(unsigned.canonicalBytes()))
    }
}
