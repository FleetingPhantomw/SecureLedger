package com.secureledger.core

import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.X25519Manager
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.protocol.ProjectInvitationCodec
import com.secureledger.core.protocol.ProjectInvitationCrypto
import com.secureledger.core.protocol.ProjectJoinRequest
import com.secureledger.core.protocol.ProjectMemberDescriptor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectInvitationTest {
    @Test
    fun x25519AgreementAndSignedInvitationRoundTrip() {
        val ownerSigning = Ed25519Manager.generateKeyPair()
        val memberSigning = Ed25519Manager.generateKeyPair()
        val ownerAgreement = X25519Manager.generateKeyPair()
        val memberAgreement = X25519Manager.generateKeyPair()
        assertArrayEquals(
            X25519Manager.agreement(ownerAgreement.privateKey, memberAgreement.publicKey),
            X25519Manager.agreement(memberAgreement.privateKey, ownerAgreement.publicKey)
        )
        val ownerId = ProjectJoinRequest.userIdFor(ownerSigning.publicKey)
        val memberId = ProjectJoinRequest.userIdFor(memberSigning.publicKey)
        val request = ProjectInvitationCrypto.createJoinRequest(
            memberId, "Bob", memberSigning.publicKey, memberAgreement.publicKey, memberSigning.privateSeed, NOW
        )
        assertNull(request.validate(NOW))
        val key = ByteArray(32) { (it * 7).toByte() }
        val members = listOf(
            ProjectMemberDescriptor(ownerId, "Alice", ownerSigning.publicKey.encodeBase64(), ownerAgreement.publicKey.encodeBase64(), "OWNER", NOW),
            ProjectMemberDescriptor(memberId, "Bob", memberSigning.publicKey.encodeBase64(), memberAgreement.publicKey.encodeBase64(), "MEMBER", NOW)
        )
        val invitation = ProjectInvitationCrypto.seal(
            "project-trip", "旅行", ownerId, ownerSigning.publicKey, ownerSigning.privateSeed,
            request, key, members, NOW
        )
        assertNull(invitation.validateEnvelope(NOW))
        val encoded = ProjectInvitationCodec.encodeInvitation(invitation)
        val decoded = ProjectInvitationCodec.decodeInvitation(encoded)
        val opened = ProjectInvitationCrypto.open(decoded, memberId, memberAgreement.privateKey, NOW)
        assertArrayEquals(key, java.util.Base64.getDecoder().decode(opened.projectKey))
        assertEquals(2, opened.members.size)
        assertEquals(HashManager.sha256Hex(key), opened.keyFingerprint)
    }

    @Test
    fun tamperingOrWrongRecipientIsRejected() {
        val owner = Ed25519Manager.generateKeyPair()
        val recipient = Ed25519Manager.generateKeyPair()
        val recipientAgreement = X25519Manager.generateKeyPair()
        val request = ProjectInvitationCrypto.createJoinRequest(
            ProjectJoinRequest.userIdFor(recipient.publicKey), "成员", recipient.publicKey,
            recipientAgreement.publicKey, recipient.privateSeed, NOW
        )
        val ownerId = ProjectJoinRequest.userIdFor(owner.publicKey)
        val invitation = ProjectInvitationCrypto.seal(
            "p1", "项目", ownerId, owner.publicKey, owner.privateSeed, request, ByteArray(32) { 3 },
            listOf(ProjectMemberDescriptor(ownerId, "Owner", owner.publicKey.encodeBase64(), X25519Manager.generateKeyPair().publicKey.encodeBase64(), "OWNER", NOW)), NOW
        )
        assertTrue(invitation.copy(projectName = "篡改").validateEnvelope(NOW) != null)
        assertTrue(runCatching {
            ProjectInvitationCrypto.open(invitation, "u_wrong", recipientAgreement.privateKey, NOW)
        }.isFailure)
    }

    companion object { private const val NOW = 1_790_000_000_000L }
}
