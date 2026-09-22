package com.secureledger.core.protocol

import com.secureledger.core.settlement.SettlementProof
import java.util.Base64
import kotlinx.serialization.json.Json

/** Compact QR/clipboard transport for an AA proof and its independent signatures. */
object SettlementExchangeCodec {
    private const val PREFIX = "SLS1."
    private const val MAX_ENCODED_CHARS = 256_000
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    /** Encodes a proof without changing its signed hash. */
    fun encode(proof: SettlementProof): String {
        require(proof.verifyHash() && proof.verifySignatures()) { "结算凭证无效" }
        val payload = json.encodeToString(SettlementProof.serializer(), proof).toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    /** Decodes a bounded, versioned payload and rejects malformed proofs. */
    fun decode(encoded: String): SettlementProof {
        require(encoded.length <= MAX_ENCODED_CHARS && encoded.startsWith(PREFIX)) { "结算会签包格式无效" }
        val bytes = Base64.getUrlDecoder().decode(encoded.removePrefix(PREFIX))
        require(bytes.size <= MAX_ENCODED_CHARS) { "结算会签包过大" }
        return json.decodeFromString(SettlementProof.serializer(), bytes.toString(Charsets.UTF_8)).also {
            require(it.verifyHash() && it.verifySignatures()) { "结算会签包哈希或签名无效" }
        }
    }
}
