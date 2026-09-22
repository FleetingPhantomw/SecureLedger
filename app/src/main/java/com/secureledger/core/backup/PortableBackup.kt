package com.secureledger.core.backup

import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.PasswordKdf
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.ledger.CanonicalRecord
import com.secureledger.core.ledger.SecureRecord
import com.secureledger.data.entity.ImportBatchEntity
import com.secureledger.data.entity.ImportedTransactionEntity
import com.secureledger.data.entity.ProcessedSyncMessageEntity
import com.secureledger.data.entity.ProjectEntity
import com.secureledger.data.entity.ProjectKeyEntity
import com.secureledger.data.entity.ProjectMemberEntity
import com.secureledger.data.entity.SettlementEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
/** Clear key material that exists only inside the password-encrypted backup payload. */
data class PortableVaultIdentity(
    val userId: String,
    val signingPublicKey: String,
    val signingPrivateSeed: String,
    val masterKey: String,
    val agreementPublicKey: String,
    val agreementPrivateKey: String
)

@Serializable
/** Complete portable snapshot used to migrate an identity and its local state. */
data class PortableBackupPayload(
    val schemaVersion: Int = 1,
    val identity: PortableVaultIdentity,
    val records: List<SecureRecord>,
    val projects: List<ProjectEntity>,
    val members: List<ProjectMemberEntity>,
    val projectKeys: List<ProjectKeyEntity>,
    val settlements: List<SettlementEntity>,
    val importBatches: List<ImportBatchEntity>,
    val importedTransactions: List<ImportedTransactionEntity>,
    val replayMarkers: List<ProcessedSyncMessageEntity>
)

@Serializable
/** Public SLB1 envelope whose ciphertext hash and metadata are identity-signed. */
data class PortableBackupEnvelope(
    val formatVersion: Int,
    val createdAt: Long,
    val userId: String,
    val signingPublicKey: String,
    val salt: String,
    val nonce: String,
    val encryptedPayload: String,
    val payloadHash: String,
    val signature: String
) {
    fun canonicalBytes() = buildString {
        listOf(
            "formatVersion" to formatVersion.toString(), "createdAt" to createdAt.toString(),
            "userId" to userId, "signingPublicKey" to signingPublicKey, "salt" to salt,
            "nonce" to nonce, "encryptedPayload" to encryptedPayload, "payloadHash" to payloadHash
        ).forEach { (name, value) ->
            val normalized = CanonicalRecord.text(value)
            append(name).append(':').append(normalized.toByteArray(Charsets.UTF_8).size)
                .append(':').append(normalized).append('\n')
        }
    }.toByteArray(Charsets.UTF_8)
}

/** Password-encrypted, identity-signed portable backup format. */
object PortableBackupCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val random = SecureRandom()

    fun encode(identity: KeyManager.UserIdentity, payload: PortableBackupPayload, backupPassword: CharArray): ByteArray {
        require(backupPassword.size >= 10) { "备份密码至少 10 位" }
        val createdAt = System.currentTimeMillis()
        val salt = ByteArray(16).also(random::nextBytes)
        val key = PasswordKdf.derive(backupPassword, salt)
        return try {
            val clear = gzip(json.encodeToString(PortableBackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8))
            val aad = aad(createdAt, identity.userId, identity.publicKey)
            val cipher = AesGcmManager.encrypt(key, clear, aad)
            val unsigned = PortableBackupEnvelope(
                1, createdAt, identity.userId, identity.publicKey.encodeBase64(), salt.encodeBase64(),
                cipher.nonce.encodeBase64(), cipher.bytes.encodeBase64(), HashManager.sha256Hex(cipher.bytes), ""
            )
            val signed = unsigned.copy(signature = Ed25519Manager.sign(identity.privateSeed, unsigned.canonicalBytes()).encodeBase64())
            (PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
                json.encodeToString(PortableBackupEnvelope.serializer(), signed).toByteArray(Charsets.UTF_8)
            )).toByteArray(Charsets.UTF_8)
        } finally { key.fill(0) }
    }

    fun decode(bytes: ByteArray, backupPassword: CharArray): PortableBackupPayload {
        require(bytes.size <= MAX_BACKUP_BYTES) { "备份文件过大" }
        val text = bytes.toString(Charsets.UTF_8)
        require(text.startsWith(PREFIX)) { "不是 SecureLedger 备份文件" }
        val envelope = json.decodeFromString(
            PortableBackupEnvelope.serializer(),
            Base64.getUrlDecoder().decode(text.removePrefix(PREFIX)).toString(Charsets.UTF_8)
        )
        require(envelope.formatVersion == 1) { "不支持的备份版本" }
        val publicKey = envelope.signingPublicKey.decodeBase64()
        require(envelope.userId == "u_" + HashManager.sha256Hex(publicKey).take(16)) { "备份身份不匹配" }
        require(Ed25519Manager.verify(publicKey, envelope.canonicalBytes(), envelope.signature.decodeBase64())) { "备份签名无效" }
        val ciphertext = envelope.encryptedPayload.decodeBase64()
        require(HashManager.sha256Hex(ciphertext) == envelope.payloadHash) { "备份密文摘要不匹配" }
        val key = PasswordKdf.derive(backupPassword, envelope.salt.decodeBase64())
        return try {
            val clear = AesGcmManager.decrypt(
                key, AesGcmManager.Ciphertext(envelope.nonce.decodeBase64(), ciphertext),
                aad(envelope.createdAt, envelope.userId, publicKey)
            )
            json.decodeFromString(PortableBackupPayload.serializer(), gunzip(clear).toString(Charsets.UTF_8)).also {
                require(it.schemaVersion == 1 && it.identity.userId == envelope.userId) { "备份载荷身份或版本无效" }
            }
        } finally { key.fill(0) }
    }

    private fun aad(createdAt: Long, userId: String, publicKey: ByteArray) =
        "SecureLedger/backup/v1|$createdAt|$userId|${publicKey.encodeBase64()}".toByteArray(Charsets.UTF_8)

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()

    private fun gunzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                require(output.size() <= MAX_CLEAR_BYTES) { "备份解压后过大" }
            }
        }
        return output.toByteArray()
    }

    const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    private const val MAX_CLEAR_BYTES = 128 * 1024 * 1024
    private const val PREFIX = "SLB1:"
}
