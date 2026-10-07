package com.secureledger.integration.accessibility

import android.content.Context
import android.os.SystemClock
import com.secureledger.core.accessibility.*
import com.secureledger.core.crypto.HashManager

/** Shared by both Android services. Persist only bounded event digests, never notification text. */
class RecognitionCoordinator(context: Context) {
    private val fusion = RecognitionFusion()
    private val storage = context.getSharedPreferences("recognition_event_digests", Context.MODE_PRIVATE)
    @Synchronized
    fun accept(candidate: TransactionCandidate, source: DetectionSource, eventKey: String): FusionResult? {
        val key = HashManager.sha256Hex(eventKey.toByteArray())
        val now = System.currentTimeMillis()
        if (source == DetectionSource.NOTIFICATION) {
            val previous = storage.getLong(key, 0)
            if (now - previous in 0..86_400_000) return null
            val editor = storage.edit()
            storage.all.filterValues { it is Long && now - it > 86_400_000 }.keys.forEach(editor::remove)
            if (storage.all.size >= 200) storage.all.entries.minByOrNull { it.value as? Long ?: 0 }?.key?.let(editor::remove)
            editor.putLong(key, now).apply()
        }
        return fusion.accept(candidate, source, key, SystemClock.elapsedRealtime())
    }
}
