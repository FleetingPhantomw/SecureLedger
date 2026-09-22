package com.secureledger.core.crypto

import java.security.MessageDigest
import java.util.Base64

/** Small, dependency-free SHA-256 facade used by every integrity primitive. */
object HashManager {
    fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    fun sha256Hex(input: ByteArray): String = sha256(input).toHex()

    fun sha256Base64(input: ByteArray): String = Base64.getEncoder().encodeToString(sha256(input))
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)

fun ByteArray.encodeBase64(): String = Base64.getEncoder().encodeToString(this)
