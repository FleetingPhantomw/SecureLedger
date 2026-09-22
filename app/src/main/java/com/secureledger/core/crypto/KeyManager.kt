package com.secureledger.core.crypto

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Owns the local vault lifecycle. A random master key encrypts ledger payloads;
 * the Android Keystore key protects a password envelope, and the password-derived
 * Argon2id key unwraps the master key. Private Ed25519 material is encrypted by
 * the master key and never placed in Room as plaintext.
 */
class KeyManager(context: Context, storageNamespace: String = MAIN_NAMESPACE) {
    data class UserIdentity(
        val userId: String,
        val publicKey: ByteArray,
        val masterKey: ByteArray,
        val privateSeed: ByteArray,
        val agreementPublicKey: ByteArray,
        val agreementPrivateKey: ByteArray
    )

    /** Sanitized timings only; no password or key material is retained. */
    data class UnlockPerformance(
        val keystoreUnwrapMs: Long,
        val argon2Ms: Long,
        val masterKeyDecryptMs: Long,
        val identityDecryptMs: Long,
        val agreementIdentityMs: Long,
        val totalMs: Long
    )

    data class ProfiledUnlock(val identity: UserIdentity, val performance: UnlockPerformance)

    private data class PasswordEnvelope(
        val salt: ByteArray,
        val masterCiphertext: AesGcmManager.Ciphertext,
        val seedCiphertext: AesGcmManager.Ciphertext
    ) {
        fun encode(): ByteArray {
            val output = ByteArrayOutputStream()
            write(output, salt)
            write(output, masterCiphertext.nonce)
            write(output, masterCiphertext.bytes)
            write(output, seedCiphertext.nonce)
            write(output, seedCiphertext.bytes)
            return output.toByteArray()
        }

        private fun write(output: ByteArrayOutputStream, value: ByteArray) {
            output.write(ByteBuffer.allocate(4).putInt(value.size).array())
            output.write(value)
        }

        companion object {
            fun decode(bytes: ByteArray): PasswordEnvelope {
                val input = ByteBuffer.wrap(bytes)
                fun read(): ByteArray {
                    val size = input.int
                    require(size in 1..1_000_000) { "Corrupt vault envelope" }
                    return ByteArray(size).also(input::get)
                }
                return PasswordEnvelope(
                    salt = read(),
                    masterCiphertext = AesGcmManager.Ciphertext(read(), read()),
                    seedCiphertext = AesGcmManager.Ciphertext(read(), read())
                )
            }
        }
    }

    private val namespace = storageNamespace.also {
        require(it.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid key storage namespace" }
    }
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        if (namespace == MAIN_NAMESPACE) PREFERENCES else "$PREFERENCES.$namespace",
        Context.MODE_PRIVATE
    )
    private val random = SecureRandom()

    fun isInitialized(): Boolean = preferences.contains(KEY_DEVICE_ENVELOPE)

    fun createUser(password: CharArray): UserIdentity {
        check(!isInitialized()) { "本机账本已经创建" }
        require(password.size >= 8) { "密码至少需要 8 位" }
        val pair = Ed25519Manager.generateKeyPair()
        val agreementPair = X25519Manager.generateKeyPair()
        val masterKey = ByteArray(32).also(random::nextBytes)
        val salt = ByteArray(16).also(random::nextBytes)
        val passwordKey = PasswordKdf.derive(password, salt)
        val masterCipher = try {
            AesGcmManager.encrypt(passwordKey, masterKey, aad("master"))
        } finally {
            passwordKey.fill(0)
        }
        val seedCipher = AesGcmManager.encrypt(masterKey, pair.privateSeed, aad("identity"))
        val envelope = PasswordEnvelope(salt, masterCipher, seedCipher)
        val agreementCipher = AesGcmManager.encrypt(masterKey, agreementPair.privateKey, aad("agreement-identity"))
        persist(pair.publicKey, wrapForDevice(envelope.encode()), newUserId(pair.publicKey), agreementPair.publicKey, agreementCipher)
        return UserIdentity(newUserId(pair.publicKey), pair.publicKey, masterKey, pair.privateSeed,
            agreementPair.publicKey, agreementPair.privateKey)
    }

    fun unlock(password: CharArray): UserIdentity = unlockProfiled(password).identity

    fun unlockProfiled(password: CharArray): ProfiledUnlock {
        val totalStarted = SystemClock.elapsedRealtime()
        check(isInitialized()) { "本机账本尚未创建" }
        val publicKey = preferences.getString(KEY_PUBLIC_KEY, null)?.decodeBase64()
            ?: error("本机身份信息缺失")
        val userId = preferences.getString(KEY_USER_ID, null) ?: error("本机身份信息缺失")
        val keystoreStarted = SystemClock.elapsedRealtime()
        val envelopeBytes = unwrapFromDevice(
            preferences.getString(KEY_DEVICE_ENVELOPE, null)?.decodeBase64()
                ?: error("本机安全数据缺失"),
            preferences.getString(KEY_DEVICE_NONCE, null)?.decodeBase64()
                ?: error("本机安全数据缺失")
        )
        val keystoreMs = SystemClock.elapsedRealtime() - keystoreStarted
        val envelope = PasswordEnvelope.decode(envelopeBytes)
        val argonStarted = SystemClock.elapsedRealtime()
        val passwordKey = PasswordKdf.derive(password, envelope.salt)
        val argonMs = SystemClock.elapsedRealtime() - argonStarted
        val masterStarted = SystemClock.elapsedRealtime()
        val masterKey = try {
            AesGcmManager.decrypt(passwordKey, envelope.masterCiphertext, aad("master"))
        } finally {
            passwordKey.fill(0)
        }
        val masterMs = SystemClock.elapsedRealtime() - masterStarted
        val identityStarted = SystemClock.elapsedRealtime()
        val seed = AesGcmManager.decrypt(masterKey, envelope.seedCiphertext, aad("identity"))
        check(Ed25519Manager.publicKeyFromSeed(seed).contentEquals(publicKey)) {
            "本机身份验证失败"
        }
        val identityMs = SystemClock.elapsedRealtime() - identityStarted
        val agreementStarted = SystemClock.elapsedRealtime()
        val agreement = loadOrCreateAgreement(masterKey)
        val agreementMs = SystemClock.elapsedRealtime() - agreementStarted
        return ProfiledUnlock(
            UserIdentity(userId, publicKey, masterKey, seed, agreement.publicKey, agreement.privateKey),
            UnlockPerformance(
                keystoreUnwrapMs = keystoreMs,
                argon2Ms = argonMs,
                masterKeyDecryptMs = masterMs,
                identityDecryptMs = identityMs,
                agreementIdentityMs = agreementMs,
                totalMs = SystemClock.elapsedRealtime() - totalStarted
            )
        )
    }

    fun publicKeyFingerprint(): String? = preferences.getString(KEY_PUBLIC_KEY, null)?.decodeBase64()
        ?.let(HashManager::sha256)?.copyOf(8)?.toHex()

    /** Restores a validated portable identity into a fresh device-bound vault. */
    fun restoreUser(
        password: CharArray,
        userId: String,
        publicKey: ByteArray,
        masterKey: ByteArray,
        privateSeed: ByteArray,
        agreementPublicKey: ByteArray,
        agreementPrivateKey: ByteArray
    ): UserIdentity {
        check(!isInitialized()) { "恢复仅允许在空保险库执行" }
        require(password.size >= 8) { "新保险库密码至少 8 位" }
        require(masterKey.size == 32) { "主密钥长度错误" }
        require(Ed25519Manager.publicKeyFromSeed(privateSeed).contentEquals(publicKey)) { "签名身份密钥不匹配" }
        require(newUserId(publicKey) == userId) { "用户 ID 与签名公钥不匹配" }
        require(X25519Manager.publicKeyFromPrivate(agreementPrivateKey).contentEquals(agreementPublicKey)) {
            "密钥协商身份不匹配"
        }
        val salt = ByteArray(16).also(random::nextBytes)
        val passwordKey = PasswordKdf.derive(password, salt)
        val envelope = PasswordEnvelope(
            salt,
            try {
                AesGcmManager.encrypt(passwordKey, masterKey, aad("master"))
            } finally {
                passwordKey.fill(0)
            },
            AesGcmManager.encrypt(masterKey, privateSeed, aad("identity"))
        )
        val agreementCipher = AesGcmManager.encrypt(masterKey, agreementPrivateKey, aad("agreement-identity"))
        persist(publicKey, wrapForDevice(envelope.encode()), userId, agreementPublicKey, agreementCipher)
        return UserIdentity(userId, publicKey, masterKey, privateSeed, agreementPublicKey, agreementPrivateKey)
    }

    private fun persist(
        publicKey: ByteArray,
        deviceEnvelope: AesGcmManager.Ciphertext,
        userId: String,
        agreementPublicKey: ByteArray,
        agreementCipher: AesGcmManager.Ciphertext
    ) {
        preferences.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_PUBLIC_KEY, publicKey.encodeBase64())
            .putString(KEY_DEVICE_ENVELOPE, deviceEnvelope.bytes.encodeBase64())
            .putString(KEY_DEVICE_NONCE, deviceEnvelope.nonce.encodeBase64())
            .putString(KEY_AGREEMENT_PUBLIC_KEY, agreementPublicKey.encodeBase64())
            .putString(KEY_AGREEMENT_PRIVATE, agreementCipher.bytes.encodeBase64())
            .putString(KEY_AGREEMENT_NONCE, agreementCipher.nonce.encodeBase64())
            .apply()
    }

    /** Adds the X25519 identity to vaults created before project invitations existed. */
    private fun loadOrCreateAgreement(masterKey: ByteArray): X25519Manager.KeyPair {
        val public = preferences.getString(KEY_AGREEMENT_PUBLIC_KEY, null)?.decodeBase64()
        val encrypted = preferences.getString(KEY_AGREEMENT_PRIVATE, null)?.decodeBase64()
        val nonce = preferences.getString(KEY_AGREEMENT_NONCE, null)?.decodeBase64()
        if (public != null && encrypted != null && nonce != null) {
            val private = AesGcmManager.decrypt(
                masterKey, AesGcmManager.Ciphertext(nonce, encrypted), aad("agreement-identity")
            )
            check(X25519Manager.publicKeyFromPrivate(private).contentEquals(public)) { "Agreement identity verification failed" }
            return X25519Manager.KeyPair(private, public)
        }
        val pair = X25519Manager.generateKeyPair()
        val cipher = AesGcmManager.encrypt(masterKey, pair.privateKey, aad("agreement-identity"))
        preferences.edit()
            .putString(KEY_AGREEMENT_PUBLIC_KEY, pair.publicKey.encodeBase64())
            .putString(KEY_AGREEMENT_PRIVATE, cipher.bytes.encodeBase64())
            .putString(KEY_AGREEMENT_NONCE, cipher.nonce.encodeBase64())
            .commit()
        return pair
    }

    private fun wrapForDevice(envelope: ByteArray): AesGcmManager.Ciphertext =
        AesGcmManager.encrypt(deviceKey(), envelope, aad("vault-envelope"))

    private fun unwrapFromDevice(bytes: ByteArray, nonce: ByteArray): ByteArray =
        AesGcmManager.decrypt(deviceKey(), AesGcmManager.Ciphertext(nonce, bytes), aad("vault-envelope"))

    private fun deviceKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(deviceAlias(), null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                deviceAlias(),
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // AesGcmManager generates and persists a fresh 96-bit nonce per operation.
                // Android Keystore otherwise rejects that caller-provided IV on some devices.
                .setRandomizedEncryptionRequired(false)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun newUserId(publicKey: ByteArray): String = "u_" + HashManager.sha256Hex(publicKey).take(16)

    private fun aad(label: String): ByteArray {
        val value = if (namespace == MAIN_NAMESPACE) "SecureLedger/v1/$label" else "SecureLedger/v1/$namespace/$label"
        return value.toByteArray(Charsets.UTF_8)
    }

    private fun deviceAlias(): String =
        if (namespace == MAIN_NAMESPACE) DEVICE_ALIAS else "$DEVICE_ALIAS.$namespace"

    companion object {
        private const val PREFERENCES = "secure_vault"
        private const val MAIN_NAMESPACE = "main"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_DEVICE_ENVELOPE = "device_envelope"
        private const val KEY_DEVICE_NONCE = "device_nonce"
        private const val KEY_AGREEMENT_PUBLIC_KEY = "agreement_public_key"
        private const val KEY_AGREEMENT_PRIVATE = "agreement_private"
        private const val KEY_AGREEMENT_NONCE = "agreement_nonce"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEVICE_ALIAS = "secureledger.device-protection.v1"
    }
}
