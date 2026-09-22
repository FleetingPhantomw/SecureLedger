package com.secureledger.core.protocol

import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.ledger.CanonicalRecord
import com.secureledger.core.ledger.SecureRecord
import kotlinx.serialization.Serializable

/** Signed QR/LAN/relay transport envelope. Record validation happens before import. */
@Serializable
data class SecureSyncPackage(
    val protocolVersion: Int,
    val packageId: String,
    val projectId: String,
    val senderId: String,
    val senderPublicKey: String,
    val timestamp: Long,
    val nonce: String,
    val records: List<SecureRecord>,
    val merkleRoot: String,
    val payloadHash: String,
    val signature: String
) {
    fun canonicalBytes(): ByteArray = canonical(includePayloadHash = true)

    fun isPayloadHashValid(): Boolean = HashManager.sha256Hex(canonicalBytesWithoutPayloadHash()) == payloadHash

    fun isSignatureValid(): Boolean = runCatching {
        Ed25519Manager.verify(senderPublicKey.decodeBase64(), canonicalBytes(), signature.decodeBase64())
    }.getOrDefault(false)

    private fun canonicalBytesWithoutPayloadHash(): ByteArray = canonical(includePayloadHash = false)

    /** Length-delimited transport form binds every record hash, signature, and author key. */
    private fun canonical(includePayloadHash: Boolean): ByteArray = buildString {
        fun field(name: String, value: String) {
            val normalized = CanonicalRecord.text(value)
            append(name).append(':').append(normalized.toByteArray(Charsets.UTF_8).size)
                .append(':').append(normalized).append('\n')
        }
        field("protocolVersion", protocolVersion.toString())
        field("packageId", packageId)
        field("projectId", projectId)
        field("senderId", senderId)
        field("senderPublicKey", senderPublicKey)
        field("timestamp", timestamp.toString())
        field("nonce", nonce)
        records.sortedBy { it.recordId }.forEach { record ->
            field("recordUnsigned", CanonicalRecord.unsignedRecord(record).decodeToString())
            field("recordHash", record.recordHash)
            field("recordSignature", record.signature)
            field("recordAuthorPublicKey", record.authorPublicKey)
        }
        field("merkleRoot", merkleRoot)
        if (includePayloadHash) field("payloadHash", payloadHash)
    }.toByteArray(Charsets.UTF_8)

    companion object {
        fun sign(
            protocolVersion: Int,
            packageId: String,
            projectId: String,
            senderId: String,
            senderPublicKey: ByteArray,
            timestamp: Long,
            nonce: ByteArray,
            records: List<SecureRecord>,
            merkleRoot: String,
            privateSeed: ByteArray
        ): SecureSyncPackage {
            val publicKey = senderPublicKey.encodeBase64()
            val packageWithoutSignature = SecureSyncPackage(
                protocolVersion, packageId, projectId, senderId, publicKey, timestamp,
                nonce.encodeBase64(), records, merkleRoot, "", ""
            )
            val payloadHash = HashManager.sha256Hex(packageWithoutSignature.canonicalBytesWithoutPayloadHash())
            val unsigned = packageWithoutSignature.copy(payloadHash = payloadHash)
            return unsigned.copy(signature = Ed25519Manager.sign(privateSeed, unsigned.canonicalBytes()).encodeBase64())
        }
    }
}
