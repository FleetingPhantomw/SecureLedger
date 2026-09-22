package com.secureledger.core.ledger

import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.merkle.MerkleTree
import java.security.GeneralSecurityException

/** Performs concrete, record-level integrity checks for the security centre. */
class IntegrityVerifier {
    fun verify(records: List<SecureRecord>, masterKey: ByteArray? = null): IntegrityReport {
        return verifyWithKeys(records) { masterKey }
    }

    fun verifyWithKeys(records: List<SecureRecord>, keyForProject: (String) -> ByteArray?): IntegrityReport {
        val issues = mutableListOf<IntegrityIssue>()
        records.forEach { record ->
            val calculated = SecureRecord.calculateHash(record)
            if (calculated != record.recordHash) {
                issues += IntegrityIssue(record.recordId, IntegrityFailure.HASH_MISMATCH,
                    "expected=$calculated actual=${record.recordHash}")
            }
            val signatureValid = runCatching {
                Ed25519Manager.verify(
                    record.authorPublicKey.decodeBase64(),
                    CanonicalRecord.signedRecord(record),
                    record.signature.decodeBase64()
                )
            }.getOrDefault(false)
            if (!signatureValid) {
                issues += IntegrityIssue(record.recordId, IntegrityFailure.SIGNATURE_INVALID,
                    "Ed25519 signature does not match the signed record envelope")
            }
            val encryptionKey = keyForProject(record.projectId)
            if (encryptionKey != null) {
                runCatching {
                    AesGcmManager.decrypt(
                        encryptionKey,
                        AesGcmManager.Ciphertext(record.nonce.decodeBase64(), record.encryptedPayload.decodeBase64()),
                        aad(record)
                    )
                }.onFailure {
                    issues += IntegrityIssue(record.recordId, IntegrityFailure.DECRYPTION_FAILED,
                        "AES-GCM authentication failed")
                }
            }
        }
        records.groupBy { it.projectId to it.authorId }.values.forEach { authorRecords ->
            issues += LedgerHashChain.validate(authorRecords)
        }
        return IntegrityReport(records.size, issues.distinct(), MerkleTree(records).calculateRoot(), System.currentTimeMillis())
    }

    private fun aad(record: SecureRecord): ByteArray =
        listOf(record.recordId, record.authorId, record.projectId, record.recordType, record.schemaVersion)
            .joinToString("|").toByteArray(Charsets.UTF_8)
}
