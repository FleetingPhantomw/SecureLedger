package com.secureledger.core.protocol

import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.X25519Manager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.ledger.CanonicalRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
/** Public member material distributed inside an authenticated invitation. */
data class ProjectMemberDescriptor(
    val userId: String,
    val displayName: String,
    val signingPublicKey: String,
    val agreementPublicKey: String,
    val role: String,
    val joinedAt: Long
)

/** Signed request shown by a prospective member; it binds both identity keys. */
@Serializable
data class ProjectJoinRequest(
    val protocolVersion: Int,
    val requestId: String,
    val userId: String,
    val displayName: String,
    val signingPublicKey: String,
    val agreementPublicKey: String,
    val timestamp: Long,
    val nonce: String,
    val signature: String
) {
    fun canonicalBytes() = canonicalFields(
        "protocolVersion" to protocolVersion.toString(), "requestId" to requestId,
        "userId" to userId, "displayName" to displayName,
        "signingPublicKey" to signingPublicKey, "agreementPublicKey" to agreementPublicKey,
        "timestamp" to timestamp.toString(), "nonce" to nonce
    )

    fun validate(now: Long = System.currentTimeMillis()): String? = runCatching {
        require(protocolVersion == PROTOCOL_VERSION) { "不支持的加入请求版本" }
        require(kotlin.math.abs(now - timestamp) <= REQUEST_MAX_AGE_MS) { "加入请求已过期或设备时间异常" }
        val signing = signingPublicKey.decodeBase64()
        require(userId == userIdFor(signing)) { "用户 ID 与签名公钥不匹配" }
        require(agreementPublicKey.decodeBase64().size == X25519Manager.KEY_BYTES) { "X25519 公钥长度错误" }
        require(nonce.decodeBase64().size == 16) { "请求 nonce 长度错误" }
        require(Ed25519Manager.verify(signing, canonicalBytes(), signature.decodeBase64())) { "加入请求签名无效" }
    }.exceptionOrNull()?.message

    companion object {
        const val PROTOCOL_VERSION = 1
        const val REQUEST_MAX_AGE_MS = 15 * 60_000L
        fun userIdFor(signingPublicKey: ByteArray) = "u_" + HashManager.sha256Hex(signingPublicKey).take(16)
    }
}

@Serializable
/** Secret project key and member roster encrypted to the named recipient. */
data class ProjectSecretPayload(
    val projectKey: String,
    val keyFingerprint: String,
    val members: List<ProjectMemberDescriptor>
)

/** Recipient-bound encrypted project-key invitation, signed by the project owner. */
@Serializable
data class ProjectInvitation(
    val protocolVersion: Int,
    val invitationId: String,
    val projectId: String,
    val projectName: String,
    val inviterId: String,
    val inviterSigningPublicKey: String,
    val recipientId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val ephemeralPublicKey: String,
    val kdfSalt: String,
    val encryptedSecret: String,
    val encryptionNonce: String,
    val signature: String
) {
    fun canonicalBytes() = canonicalFields(
        "protocolVersion" to protocolVersion.toString(), "invitationId" to invitationId,
        "projectId" to projectId, "projectName" to projectName, "inviterId" to inviterId,
        "inviterSigningPublicKey" to inviterSigningPublicKey, "recipientId" to recipientId,
        "createdAt" to createdAt.toString(), "expiresAt" to expiresAt.toString(),
        "ephemeralPublicKey" to ephemeralPublicKey, "kdfSalt" to kdfSalt,
        "encryptedSecret" to encryptedSecret, "encryptionNonce" to encryptionNonce
    )

    fun validateEnvelope(now: Long = System.currentTimeMillis()): String? = runCatching {
        require(protocolVersion == ProjectJoinRequest.PROTOCOL_VERSION) { "不支持的邀请版本" }
        require(now in createdAt - 5 * 60_000L..expiresAt) { "邀请已过期或设备时间异常" }
        val signing = inviterSigningPublicKey.decodeBase64()
        require(inviterId == ProjectJoinRequest.userIdFor(signing)) { "邀请者 ID 与公钥不匹配" }
        require(ephemeralPublicKey.decodeBase64().size == X25519Manager.KEY_BYTES) { "临时公钥长度错误" }
        require(kdfSalt.decodeBase64().size == 16) { "KDF salt 长度错误" }
        require(encryptionNonce.decodeBase64().size == 12) { "AES-GCM nonce 长度错误" }
        require(Ed25519Manager.verify(signing, canonicalBytes(), signature.decodeBase64())) { "邀请签名无效" }
    }.exceptionOrNull()?.message
}

/** Creates and opens signed X25519 + HKDF + AES-GCM invitations. */
object ProjectInvitationCrypto {
    private val random = SecureRandom()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun createJoinRequest(
        userId: String,
        displayName: String,
        signingPublicKey: ByteArray,
        agreementPublicKey: ByteArray,
        privateSeed: ByteArray,
        now: Long = System.currentTimeMillis()
    ): ProjectJoinRequest {
        val unsigned = ProjectJoinRequest(
            1, "join_${java.util.UUID.randomUUID()}", userId, displayName.trim().ifBlank { "新成员" },
            signingPublicKey.encodeBase64(), agreementPublicKey.encodeBase64(), now,
            ByteArray(16).also(random::nextBytes).encodeBase64(), ""
        )
        return unsigned.copy(signature = Ed25519Manager.sign(privateSeed, unsigned.canonicalBytes()).encodeBase64())
    }

    fun seal(
        projectId: String,
        projectName: String,
        inviterId: String,
        inviterSigningPublicKey: ByteArray,
        inviterPrivateSeed: ByteArray,
        request: ProjectJoinRequest,
        projectKey: ByteArray,
        members: List<ProjectMemberDescriptor>,
        now: Long = System.currentTimeMillis()
    ): ProjectInvitation {
        require(request.validate(now) == null) { request.validate(now)!! }
        val invitationId = "invite_${java.util.UUID.randomUUID()}"
        val ephemeral = X25519Manager.generateKeyPair()
        val salt = ByteArray(16).also(random::nextBytes)
        val wrappingKey = X25519Manager.deriveWrappingKey(
            X25519Manager.agreement(ephemeral.privateKey, request.agreementPublicKey.decodeBase64()),
            salt, context(invitationId, projectId, request.userId)
        )
        val secret = ProjectSecretPayload(projectKey.encodeBase64(), HashManager.sha256Hex(projectKey), members)
        val cipher = AesGcmManager.encrypt(
            wrappingKey,
            json.encodeToString(ProjectSecretPayload.serializer(), secret).toByteArray(Charsets.UTF_8),
            context(invitationId, projectId, request.userId)
        )
        val unsigned = ProjectInvitation(
            1, invitationId, projectId, projectName, inviterId, inviterSigningPublicKey.encodeBase64(),
            request.userId, now, now + INVITATION_TTL_MS, ephemeral.publicKey.encodeBase64(), salt.encodeBase64(),
            cipher.bytes.encodeBase64(), cipher.nonce.encodeBase64(), ""
        )
        ephemeral.privateKey.fill(0)
        wrappingKey.fill(0)
        return unsigned.copy(signature = Ed25519Manager.sign(inviterPrivateSeed, unsigned.canonicalBytes()).encodeBase64())
    }

    fun open(invitation: ProjectInvitation, recipientId: String, recipientPrivateKey: ByteArray, now: Long = System.currentTimeMillis()): ProjectSecretPayload {
        require(invitation.validateEnvelope(now) == null) { invitation.validateEnvelope(now)!! }
        require(invitation.recipientId == recipientId) { "该邀请不属于当前用户" }
        val context = context(invitation.invitationId, invitation.projectId, recipientId)
        val wrappingKey = X25519Manager.deriveWrappingKey(
            X25519Manager.agreement(recipientPrivateKey, invitation.ephemeralPublicKey.decodeBase64()),
            invitation.kdfSalt.decodeBase64(), context
        )
        return try {
            val clear = AesGcmManager.decrypt(
                wrappingKey,
                AesGcmManager.Ciphertext(invitation.encryptionNonce.decodeBase64(), invitation.encryptedSecret.decodeBase64()),
                context
            )
            json.decodeFromString(ProjectSecretPayload.serializer(), clear.toString(Charsets.UTF_8)).also {
                require(HashManager.sha256Hex(it.projectKey.decodeBase64()) == it.keyFingerprint) { "项目密钥指纹不匹配" }
            }
        } finally { wrappingKey.fill(0) }
    }

    private fun context(invitationId: String, projectId: String, recipientId: String) =
        "SecureLedger/invitation/v1|$invitationId|$projectId|$recipientId".toByteArray(Charsets.UTF_8)

    const val INVITATION_TTL_MS = 24 * 60 * 60_000L
}

object ProjectInvitationCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    fun encodeJoin(request: ProjectJoinRequest) = encode(JOIN_PREFIX, json.encodeToString(ProjectJoinRequest.serializer(), request))
    fun decodeJoin(value: String) = json.decodeFromString(ProjectJoinRequest.serializer(), decode(value, JOIN_PREFIX))
    fun encodeInvitation(invitation: ProjectInvitation) = encode(INVITE_PREFIX, json.encodeToString(ProjectInvitation.serializer(), invitation))
    fun decodeInvitation(value: String) = json.decodeFromString(ProjectInvitation.serializer(), decode(value, INVITE_PREFIX))

    private fun encode(prefix: String, value: String): String {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(value.toByteArray(Charsets.UTF_8)) }
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
    }

    private fun decode(value: String, prefix: String): String {
        require(value.startsWith(prefix)) { "二维码类型不正确" }
        require(value.length <= 100_000) { "邀请数据过大" }
        val compressed = Base64.getUrlDecoder().decode(value.removePrefix(prefix))
        require(compressed.size <= 75_000) { "邀请数据过大" }
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            val buffer = ByteArray(4096)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                require(output.size() <= 250_000) { "邀请解压后过大" }
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private const val JOIN_PREFIX = "SLJ1:"
    private const val INVITE_PREFIX = "SLI1:"
}

private fun canonicalFields(vararg fields: Pair<String, String>): ByteArray = buildString {
    fields.forEach { (name, value) ->
        val normalized = CanonicalRecord.text(value)
        append(name).append(':').append(normalized.toByteArray(Charsets.UTF_8).size)
            .append(':').append(normalized).append('\n')
    }
}.toByteArray(Charsets.UTF_8)
