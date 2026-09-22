package com.secureledger.core

import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.ledger.CanonicalRecord
import com.secureledger.core.ledger.IntegrityFailure
import com.secureledger.core.ledger.IntegrityVerifier
import com.secureledger.core.ledger.LedgerHashChain
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.core.ledger.SecureRecord
import com.secureledger.core.merkle.MerkleTree
import com.secureledger.core.protocol.ReplayProtector
import com.secureledger.core.protocol.SecureSyncCodec
import com.secureledger.core.protocol.SecureSyncPackage
import com.secureledger.core.protocol.SyncFailure
import com.secureledger.core.protocol.SyncPackageValidator
import com.secureledger.core.settlement.Balance
import com.secureledger.core.settlement.SettlementCalculator
import com.secureledger.core.settlement.SettlementSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoAndLedgerTest {
    @Test fun aesGcmRoundTripAndTamperDetection() {
        val key = ByteArray(32) { it.toByte() }
        val encrypted = AesGcmManager.encrypt(key, "hello".toByteArray(), "record-1".toByteArray())
        assertArrayEquals("hello".toByteArray(), AesGcmManager.decrypt(key, encrypted, "record-1".toByteArray()))
        assertFalse(runCatching { AesGcmManager.decrypt(ByteArray(32) { 9 }, encrypted, "record-1".toByteArray()) }.isSuccess)
        val tampered = encrypted.copy(bytes = encrypted.bytes.clone().also { it[0] = (it[0].toInt() xor 1).toByte() })
        assertFalse(runCatching { AesGcmManager.decrypt(key, tampered, "record-1".toByteArray()) }.isSuccess)
    }

    @Test fun ed25519RejectsChangedMessage() {
        val pair = Ed25519Manager.generateKeyPair()
        val message = "ledger".toByteArray()
        val signature = Ed25519Manager.sign(pair.privateSeed, message)
        assertTrue(Ed25519Manager.verify(pair.publicKey, message, signature))
        assertFalse(Ed25519Manager.verify(pair.publicKey, "changed".toByteArray(), signature))
    }

    @Test fun chainAndIntegrityReportLocateMiddleTamper() {
        val pair = Ed25519Manager.generateKeyPair()
        val records = (1L..3L).map { sequence -> signedRecord(pair, sequence, "r$sequence", if (sequence == 1L) "GENESIS" else "h${sequence - 1}") }
        val tampered = records.toMutableList().apply { set(1, records[1].copy(recordHash = "tampered")) }
        val issues = LedgerHashChain.validate(tampered)
        assertTrue(issues.any { it.failure == IntegrityFailure.PREVIOUS_HASH_INVALID })
        val report = IntegrityVerifier().verify(tampered)
        assertTrue(report.issues.any { it.failure == IntegrityFailure.HASH_MISMATCH })
        assertTrue(report.issues.any { it.failure == IntegrityFailure.SIGNATURE_INVALID })
    }

    @Test fun chainDetectsDeletionAndReordering() {
        val pair = Ed25519Manager.generateKeyPair()
        val records = (1L..3L).map { sequence -> signedRecord(pair, sequence, "r$sequence", if (sequence == 1L) "GENESIS" else "h${sequence - 1}") }
        assertTrue(LedgerHashChain.validate(listOf(records[0], records[2])).any { it.failure == IntegrityFailure.SEQUENCE_GAP })
        assertTrue(LedgerHashChain.validate(listOf(records[1], records[0], records[2])).any { it.failure == IntegrityFailure.PREVIOUS_HASH_INVALID })
    }

    @Test fun merkleRootIsOrderIndependentAndFindsDifference() {
        val pair = Ed25519Manager.generateKeyPair()
        val first = listOf(signedRecord(pair, 1, "a", "GENESIS"), signedRecord(pair, 2, "b", "h1"))
        val reordered = listOf(first[1], first[0])
        assertTrue(MerkleTree(first).compare(MerkleTree(reordered)))
        val changed = first + signedRecord(pair, 3, "c", "h2")
        assertTrue(MerkleTree(first).findDifferences(MerkleTree(changed)).contains("c"))
    }

    @Test fun replayProtectorAcceptsOnce() {
        val protector = ReplayProtector()
        val now = System.currentTimeMillis()
        assertTrue(protector.accept("m1", "n1", now, now))
        assertFalse(protector.accept("m1", "n1", now, now))
        assertFalse(protector.accept("m2", "n2", now - 6 * 60 * 1000, now))
    }

    @Test fun signedSyncPackageRoundTripsAndRejectsTamper() {
        val pair = Ed25519Manager.generateKeyPair()
        val senderId = "u_" + HashManager.sha256Hex(pair.publicKey).take(16)
        val now = 1_700_000_000_000L
        val syncPackage = SecureSyncPackage.sign(
            protocolVersion = 1,
            packageId = "pkg_test",
            projectId = "personal",
            senderId = senderId,
            senderPublicKey = pair.publicKey,
            timestamp = now,
            nonce = ByteArray(16) { it.toByte() },
            records = emptyList(),
            merkleRoot = MerkleTree(emptyList()).calculateRoot(),
            privateSeed = pair.privateSeed
        )
        val decoded = SecureSyncCodec.decode(SecureSyncCodec.encode(syncPackage))
        assertTrue(decoded.isPayloadHashValid())
        assertTrue(decoded.isSignatureValid())
        assertTrue(SyncPackageValidator().validate(decoded, "personal", now).isValid)

        val tampered = decoded.copy(projectId = "attacker-project")
        val failures = SyncPackageValidator().validate(tampered, "personal", now).issues.map { it.failure }
        assertTrue(failures.contains(SyncFailure.PAYLOAD_HASH_INVALID))
        assertTrue(failures.contains(SyncFailure.PACKAGE_SIGNATURE_INVALID))
        assertTrue(failures.contains(SyncFailure.PROJECT_MISMATCH))
    }

    @Test fun syncValidatorRejectsExpiredPackage() {
        val pair = Ed25519Manager.generateKeyPair()
        val senderId = "u_" + HashManager.sha256Hex(pair.publicKey).take(16)
        val createdAt = 1_700_000_000_000L
        val syncPackage = SecureSyncPackage.sign(
            1, "pkg_old", "personal", senderId, pair.publicKey, createdAt,
            ByteArray(16) { 7 }, emptyList(), MerkleTree(emptyList()).calculateRoot(), pair.privateSeed
        )
        val result = SyncPackageValidator().validate(syncPackage, "personal", createdAt + 11 * 60 * 1000)
        assertTrue(result.issues.any { it.failure == SyncFailure.TIMESTAMP_INVALID })
    }

    @Test fun settlementCreatesMinimumTransfersAndVerifiesSignature() {
        val pair = Ed25519Manager.generateKeyPair()
        val balances = listOf(
            Balance("alice", -7_000),
            Balance("bob", 5_000),
            Balance("carol", 2_000)
        )
        val transfers = SettlementCalculator.calculate(balances)
        assertEquals(2, transfers.size)
        assertEquals("alice", transfers[0].from)
        assertEquals("bob", transfers[0].to)
        assertEquals(5_000, transfers[0].cents)
        assertEquals("carol", transfers[1].to)
        assertEquals(2_000, transfers[1].cents)

        val unsigned = SettlementCalculator.createProof("p1", "s1", "root", balances, 1_700_000_000_000)
        val signed = unsigned.copy(signatures = listOf(
            SettlementSignature("alice", pair.publicKey.encodeBase64(),
                Ed25519Manager.sign(pair.privateSeed, unsigned.hash.toByteArray()).encodeBase64())
        ))
        assertTrue(signed.verifyHash())
        assertTrue(signed.verifySignatures())
    }

    @Test fun settlementRejectsUnbalancedParticipants() {
        val failed = runCatching {
            SettlementCalculator.calculate(listOf(Balance("alice", -1), Balance("bob", 0)))
        }
        assertFalse(failed.isSuccess)
    }

    private fun signedRecord(pair: Ed25519Manager.KeyPair, sequence: Long, id: String, previous: String): SecureRecord {
        val base = SecureRecord(id, "personal", "u1", sequence, "EXPENSE", 1_700_000_000_000,
            1_700_000_000_000, 1, "cipher$id", "nonce$id", HashManager.sha256Hex(id.toByteArray()), previous, "", "", pair.publicKey.encodeBase64())
        val hashed = base.copy(recordHash = SecureRecord.calculateHash(base))
        return hashed.copy(signature = Ed25519Manager.sign(pair.privateSeed, CanonicalRecord.signedRecord(hashed)).encodeBase64())
    }
}
