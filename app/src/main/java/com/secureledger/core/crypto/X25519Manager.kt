package com.secureledger.core.crypto

import java.security.SecureRandom
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.math.ec.rfc7748.X25519

/** X25519 key agreement used only to wrap project keys for a named recipient. */
object X25519Manager {
    const val KEY_BYTES = 32
    private val random = SecureRandom()

    data class KeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

    fun generateKeyPair(): KeyPair {
        val privateKey = ByteArray(KEY_BYTES).also { X25519.generatePrivateKey(random, it) }
        return KeyPair(privateKey, publicKeyFromPrivate(privateKey))
    }

    fun publicKeyFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_BYTES) { "X25519 private key must be 32 bytes" }
        return ByteArray(KEY_BYTES).also { X25519.generatePublicKey(privateKey, 0, it, 0) }
    }

    fun agreement(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_BYTES && publicKey.size == KEY_BYTES) { "X25519 keys must be 32 bytes" }
        val shared = ByteArray(KEY_BYTES)
        check(X25519.calculateAgreement(privateKey, 0, publicKey, 0, shared, 0)) { "Invalid X25519 public key" }
        return shared
    }

    /** HKDF-SHA256 turns a raw ECDH secret into a context-bound AES-256 key. */
    fun deriveWrappingKey(sharedSecret: ByteArray, salt: ByteArray, context: ByteArray): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(sharedSecret, salt, context))
        return ByteArray(32).also { generator.generateBytes(it, 0, it.size) }
    }
}
