package com.secureledger.core.accessibility

enum class DetectionSource { SCREEN, NOTIFICATION }
data class FusionResult(val candidate: TransactionCandidate, val isNew: Boolean)

/** Bounded cross-source fusion only; independent orders in the same source are never merged. */
class RecognitionFusion {
    private data class Seen(var candidate: TransactionCandidate, val sources: MutableSet<DetectionSource>, val time: Long)
    private val events = LinkedHashMap<String, Long>()
    private val recent = mutableListOf<Seen>()
    fun accept(candidate: TransactionCandidate, source: DetectionSource, eventKey: String, now: Long): FusionResult? {
        events.entries.removeAll { now - it.value > 120_000 }
        recent.removeAll { now - it.time > 5_000 }
        if (events.containsKey(eventKey)) return null
        events[eventKey] = now
        while (events.size > 200) events.remove(events.keys.first())
        val match = recent.singleOrNull {
            source !in it.sources && it.candidate.sourcePackage == candidate.sourcePackage &&
                it.candidate.amountCents == candidate.amountCents &&
                (it.candidate.merchant.isBlank() || candidate.merchant.isBlank() || it.candidate.merchant == candidate.merchant)
        }
        if (match != null) {
            match.sources += source
            val enriched = if (source == DetectionSource.SCREEN || match.candidate.merchant.isBlank())
                candidate.copy(id = match.candidate.id, transactionTime = match.candidate.transactionTime) else match.candidate
            match.candidate = enriched
            return FusionResult(enriched, false)
        }
        recent += Seen(candidate, mutableSetOf(source), now)
        return FusionResult(candidate, true)
    }
}
