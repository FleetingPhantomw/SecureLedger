package com.secureledger.core.protocol

import java.util.concurrent.ConcurrentHashMap

/** In-memory replay guard; a database-backed implementation can replace this at sync time. */
class ReplayProtector {
    private val seen = ConcurrentHashMap.newKeySet<String>()

    fun accept(messageId: String, nonce: String, timestamp: Long, now: Long = System.currentTimeMillis()): Boolean {
        val withinWindow = kotlin.math.abs(now - timestamp) <= MAX_CLOCK_SKEW_MS
        if (!withinWindow) return false
        return seen.add("$messageId:$nonce")
    }

    fun contains(messageId: String, nonce: String): Boolean = seen.contains("$messageId:$nonce")

    companion object { private const val MAX_CLOCK_SKEW_MS = 5 * 60 * 1000L }
}
