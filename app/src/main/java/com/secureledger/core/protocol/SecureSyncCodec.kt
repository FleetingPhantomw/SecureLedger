package com.secureledger.core.protocol

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Compact, versioned JSON transport used by QR, clipboard, LAN, and relay adapters. */
object SecureSyncCodec {
    const val PREFIX = "SL1:"
    private const val MAX_COMPRESSED_BYTES = 512 * 1024
    private const val MAX_JSON_BYTES = 2 * 1024 * 1024
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun encode(syncPackage: SecureSyncPackage): String {
        val jsonBytes = json.encodeToString(SecureSyncPackage.serializer(), syncPackage).toByteArray(Charsets.UTF_8)
        require(jsonBytes.size <= MAX_JSON_BYTES) { "Sync package is too large" }
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(jsonBytes) }
            output.toByteArray()
        }
        require(compressed.size <= MAX_COMPRESSED_BYTES) { "Compressed sync package is too large" }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    fun decode(encoded: String): SecureSyncPackage {
        val normalized = encoded.trim()
        require(normalized.startsWith(PREFIX)) { "Not a SecureLedger sync package" }
        val compressed = Base64.getUrlDecoder().decode(normalized.removePrefix(PREFIX))
        require(compressed.size <= MAX_COMPRESSED_BYTES) { "Compressed sync package is too large" }
        val jsonBytes = GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                require(output.size() <= MAX_JSON_BYTES) { "Expanded sync package is too large" }
            }
            output.toByteArray()
        }
        return json.decodeFromString(SecureSyncPackage.serializer(), jsonBytes.toString(Charsets.UTF_8))
    }
}
