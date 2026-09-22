package com.secureledger.core.protocol

import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.ledger.IntegrityVerifier
import com.secureledger.core.merkle.MerkleTree
import kotlin.math.abs

/** Exact reasons a package can be rejected before database mutation. */
enum class SyncFailure {
    PROTOCOL_UNSUPPORTED,
    MESSAGE_METADATA_INVALID,
    MALFORMED_IDENTITY,
    TIMESTAMP_INVALID,
    PAYLOAD_HASH_INVALID,
    PACKAGE_SIGNATURE_INVALID,
    PROJECT_MISMATCH,
    DUPLICATE_RECORD_ID,
    RECORD_AUTHOR_INVALID,
    RECORD_PAYLOAD_HASH_INVALID,
    RECORD_INTEGRITY_INVALID,
    MERKLE_ROOT_INVALID
}

/** One stateless package validation issue. */
data class SyncValidationIssue(val failure: SyncFailure, val detail: String)

/** Aggregate package result; valid means no validation issue was found. */
data class SyncValidationResult(val issues: List<SyncValidationIssue>) {
    val isValid: Boolean get() = issues.isEmpty()
}

/** Applies all stateless checks before a sync package may reach persistence. */
class SyncPackageValidator {
    fun validate(
        syncPackage: SecureSyncPackage,
        expectedProjectId: String? = null,
        now: Long = System.currentTimeMillis()
    ): SyncValidationResult {
        val issues = mutableListOf<SyncValidationIssue>()
        if (syncPackage.protocolVersion != SUPPORTED_PROTOCOL) {
            issues += SyncValidationIssue(SyncFailure.PROTOCOL_UNSUPPORTED, "protocol=${syncPackage.protocolVersion}")
        }
        val nonceValid = runCatching { syncPackage.nonce.decodeBase64().size == 16 }.getOrDefault(false)
        if (syncPackage.packageId.isBlank() || !nonceValid) {
            issues += SyncValidationIssue(SyncFailure.MESSAGE_METADATA_INVALID, "packageId or 128-bit nonce is invalid")
        }
        val expectedSender = runCatching {
            "u_" + HashManager.sha256Hex(syncPackage.senderPublicKey.decodeBase64()).take(16)
        }.getOrNull()
        if (expectedSender == null || expectedSender != syncPackage.senderId) {
            issues += SyncValidationIssue(SyncFailure.MALFORMED_IDENTITY, "senderId is not bound to senderPublicKey")
        }
        if (abs(now - syncPackage.timestamp) > MAX_CLOCK_SKEW_MS) {
            issues += SyncValidationIssue(SyncFailure.TIMESTAMP_INVALID, "package timestamp is outside the 10-minute window")
        }
        if (!syncPackage.isPayloadHashValid()) {
            issues += SyncValidationIssue(SyncFailure.PAYLOAD_HASH_INVALID, "payloadHash does not match canonical package bytes")
        }
        if (!syncPackage.isSignatureValid()) {
            issues += SyncValidationIssue(SyncFailure.PACKAGE_SIGNATURE_INVALID, "Ed25519 package signature is invalid")
        }
        if (expectedProjectId != null && syncPackage.projectId != expectedProjectId) {
            issues += SyncValidationIssue(SyncFailure.PROJECT_MISMATCH, "expected=$expectedProjectId actual=${syncPackage.projectId}")
        }
        if (syncPackage.records.any { it.projectId != syncPackage.projectId }) {
            issues += SyncValidationIssue(SyncFailure.PROJECT_MISMATCH, "one or more records belong to a different project")
        }
        if (syncPackage.records.map { it.recordId }.distinct().size != syncPackage.records.size) {
            issues += SyncValidationIssue(SyncFailure.DUPLICATE_RECORD_ID, "recordId occurs more than once")
        }
        syncPackage.records.forEach { record ->
            val expectedAuthor = runCatching {
                "u_" + HashManager.sha256Hex(record.authorPublicKey.decodeBase64()).take(16)
            }.getOrNull()
            if (expectedAuthor == null || expectedAuthor != record.authorId) {
                issues += SyncValidationIssue(SyncFailure.RECORD_AUTHOR_INVALID, "record=${record.recordId}")
            }
            val payloadHash = runCatching { HashManager.sha256Hex(record.encryptedPayload.decodeBase64()) }.getOrNull()
            if (payloadHash == null || payloadHash != record.payloadHash) {
                issues += SyncValidationIssue(SyncFailure.RECORD_PAYLOAD_HASH_INVALID, "record=${record.recordId}")
            }
        }
        val integrity = IntegrityVerifier().verify(syncPackage.records)
        integrity.issues.forEach { issue ->
            issues += SyncValidationIssue(
                SyncFailure.RECORD_INTEGRITY_INVALID,
                "record=${issue.recordId} failure=${issue.failure}"
            )
        }
        val calculatedRoot = MerkleTree(syncPackage.records).calculateRoot()
        if (calculatedRoot != syncPackage.merkleRoot) {
            issues += SyncValidationIssue(SyncFailure.MERKLE_ROOT_INVALID, "expected=$calculatedRoot actual=${syncPackage.merkleRoot}")
        }
        return SyncValidationResult(issues.distinct())
    }

    companion object {
        const val SUPPORTED_PROTOCOL = 1
        const val MAX_CLOCK_SKEW_MS = 10 * 60 * 1000L
    }
}
