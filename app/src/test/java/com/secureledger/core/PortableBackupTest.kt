package com.secureledger.core

import com.secureledger.core.backup.PortableBackupCodec
import com.secureledger.core.backup.PortableBackupPayload
import com.secureledger.core.backup.PortableVaultIdentity
import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.X25519Manager
import com.secureledger.core.crypto.encodeBase64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableBackupTest {
    @Test
    fun backupRoundTripAndWrongPasswordFailure() {
        val signing = Ed25519Manager.generateKeyPair()
        val agreement = X25519Manager.generateKeyPair()
        val userId = "u_" + com.secureledger.core.crypto.HashManager.sha256Hex(signing.publicKey).take(16)
        val identity = KeyManager.UserIdentity(
            userId, signing.publicKey, ByteArray(32) { 9 }, signing.privateSeed,
            agreement.publicKey, agreement.privateKey
        )
        val payload = PortableBackupPayload(
            identity = PortableVaultIdentity(
                userId, signing.publicKey.encodeBase64(), signing.privateSeed.encodeBase64(),
                identity.masterKey.encodeBase64(), agreement.publicKey.encodeBase64(), agreement.privateKey.encodeBase64()
            ),
            records = emptyList(), projects = emptyList(), members = emptyList(), projectKeys = emptyList(),
            settlements = emptyList(), importBatches = emptyList(), importedTransactions = emptyList(), replayMarkers = emptyList()
        )
        val encoded = PortableBackupCodec.encode(identity, payload, "BackupPassword123".toCharArray())
        val decoded = PortableBackupCodec.decode(encoded, "BackupPassword123".toCharArray())
        assertEquals(userId, decoded.identity.userId)
        assertTrue(runCatching { PortableBackupCodec.decode(encoded, "WrongPassword123".toCharArray()) }.isFailure)
        encoded[encoded.lastIndex] = if (encoded.last() == 'A'.code.toByte()) 'B'.code.toByte() else 'A'.code.toByte()
        assertTrue(runCatching { PortableBackupCodec.decode(encoded, "BackupPassword123".toCharArray()) }.isFailure)
    }
}
