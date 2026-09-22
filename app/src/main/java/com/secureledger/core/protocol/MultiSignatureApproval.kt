package com.secureledger.core.protocol

import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.decodeBase64

/** Supported independent-signature quorum policies. */
enum class ApprovalRule { ALL, MAJORITY, RELEVANT_PARTIES }

/** One signer identity, public key and detached Ed25519 signature. */
data class ApprovalSignature(val signerId: String, val publicKey: String, val signature: String)

/** Independent Ed25519 approvals for settlement/correction workflows. */
object MultiSignatureApproval {
    fun isApproved(
        message: ByteArray,
        requiredSignerIds: Set<String>,
        signatures: List<ApprovalSignature>,
        rule: ApprovalRule
    ): Boolean {
        val valid = signatures.filter { approval ->
            approval.signerId in requiredSignerIds && runCatching {
                Ed25519Manager.verify(approval.publicKey.decodeBase64(), message, approval.signature.decodeBase64())
            }.getOrDefault(false)
        }.map { it.signerId }.toSet()
        return when (rule) {
            ApprovalRule.ALL, ApprovalRule.RELEVANT_PARTIES -> valid.containsAll(requiredSignerIds)
            ApprovalRule.MAJORITY -> valid.size * 2 > requiredSignerIds.size
        }
    }
}
