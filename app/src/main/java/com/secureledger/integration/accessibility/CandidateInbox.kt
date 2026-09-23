package com.secureledger.integration.accessibility

import com.secureledger.core.accessibility.TransactionCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-memory draft waiting for explicit user confirmation. */
object CandidateInbox {
    private val _pending = MutableStateFlow<TransactionCandidate?>(null)
    val pending = _pending.asStateFlow()

    fun offer(candidate: TransactionCandidate) { _pending.value = candidate }
    fun dismiss() { _pending.value = null }
}

object AppVisibility {
    @Volatile var isVisible: Boolean = false
}
