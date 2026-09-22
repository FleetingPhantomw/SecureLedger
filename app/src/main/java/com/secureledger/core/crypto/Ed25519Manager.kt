package com.secureledger.core.crypto

import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.security.SecureRandom

/** Ed25519 identity/signature facade; the private value is always a 32-byte seed. */
object Ed25519Manager {
    const val SEED_BYTES = 32
    const val PUBLIC_KEY_BYTES = 32
    const val SIGNATURE_BYTES = 64
    private val random = SecureRandom()

    data class KeyPair(val privateSeed: ByteArray, val publicKey: ByteArray)

    fun generateKeyPair(): KeyPair {
        val seed = ByteArray(SEED_BYTES).also(random::nextBytes)
        return KeyPair(seed, publicKeyFromSeed(seed))
    }

    fun publicKeyFromSeed(privateSeed: ByteArray): ByteArray {
        require(privateSeed.size == SEED_BYTES) { "Ed25519 seed must be 32 bytes" }
        return ByteArray(PUBLIC_KEY_BYTES).also { Ed25519.generatePublicKey(privateSeed, 0, it, 0) }
    }

    fun sign(privateSeed: ByteArray, message: ByteArray): ByteArray {
        require(privateSeed.size == SEED_BYTES) { "Ed25519 seed must be 32 bytes" }
        return ByteArray(SIGNATURE_BYTES).also {
            Ed25519.sign(privateSeed, 0, message, 0, message.size, it, 0)
        }
    }

    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != PUBLIC_KEY_BYTES || signature.size != SIGNATURE_BYTES) return false
        return Ed25519.verify(signature, 0, publicKey, 0, message, 0, message.size)
    }
}
