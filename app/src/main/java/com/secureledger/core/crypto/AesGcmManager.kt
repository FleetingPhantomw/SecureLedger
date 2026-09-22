package com.secureledger.core.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM application wrapper. The nonce is generated for every call and is
 * persisted beside the ciphertext; callers must never reuse an AAD/key/nonce tuple.
 */
object AesGcmManager {
    private const val KEY_ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val NONCE_BYTES = 12
    private val random = SecureRandom()

    data class Ciphertext(val nonce: ByteArray, val bytes: ByteArray)

    fun encrypt(keyBytes: ByteArray, plaintext: ByteArray, aad: ByteArray): Ciphertext =
        encrypt(SecretKeySpec(keyBytes, KEY_ALGORITHM), plaintext, aad)

    fun encrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray): Ciphertext {
        require(key.encoded == null || key.encoded.size == 32) {
            "SecureLedger requires a 256-bit AES key"
        }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return Ciphertext(nonce, cipher.doFinal(plaintext))
    }

    @Throws(GeneralSecurityException::class)
    fun decrypt(keyBytes: ByteArray, ciphertext: Ciphertext, aad: ByteArray): ByteArray =
        decrypt(SecretKeySpec(keyBytes, KEY_ALGORITHM), ciphertext, aad)

    @Throws(GeneralSecurityException::class)
    fun decrypt(key: SecretKey, ciphertext: Ciphertext, aad: ByteArray): ByteArray {
        require(ciphertext.nonce.size == NONCE_BYTES) { "Invalid AES-GCM nonce length" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, ciphertext.nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext.bytes)
    }
}
