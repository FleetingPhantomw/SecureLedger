package com.secureledger.integration.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.secureledger.SecureLedgerApplication
import com.secureledger.core.accessibility.CandidateDeduplicator
import com.secureledger.core.accessibility.TransactionRuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaymentAccessibilityService : AccessibilityService() {
    private data class DeferredOverlay(
        val candidate: com.secureledger.core.accessibility.TransactionCandidate,
        val originalContentHash: Int,
        val queuedAt: Long
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ruleEngine = TransactionRuleEngine()
    private val deduplicator = CandidateDeduplicator()
    private val lastScan = HashMap<String, Long>()
    private val lastContentHash = HashMap<String, Int>()
    private var deferredOverlay: DeferredOverlay? = null
    private val preferences get() = (application as SecureLedgerApplication).accessibilityPreferences
    private lateinit var dispatcher: PromptDispatcher

    override fun onServiceConnected() {
        super.onServiceConnected()
        dispatcher = PromptDispatcher(this) { handledId ->
            if (deferredOverlay?.candidate?.id == handledId) deferredOverlay = null
        }
        scope.launch {
            preferences.state.collect { settings ->
                serviceInfo = serviceInfo.apply {
                    // Window-exit events are needed for a single retry after protected payment pages.
                    // Text is still read only from user-selected packages below.
                    packageNames = if (settings.enabled && settings.selectedPackages.isNotEmpty()) null
                    else arrayOf("com.secureledger.no_apps_selected")
                }
                dispatcher.updateServiceStatus(settings.enabled && settings.selectedPackages.isNotEmpty(), settings.selectedPackages.size)
                if (!settings.enabled) {
                    dispatcher.dismissOverlay()
                    deferredOverlay = null
                    lastContentHash.clear()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val settings = preferences.state.value
        if (!settings.enabled) return
        val now = SystemClock.elapsedRealtime()
        val deferred = deferredOverlay
        if (deferred != null && packageName != deferred.candidate.sourcePackage && packageName != this.packageName &&
            !packageName.startsWith("com.android.systemui") && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            retryAfterWindowExit(deferred, now)
        }
        if (packageName !in settings.selectedPackages) return
        if (now - (lastScan[packageName] ?: Long.MIN_VALUE / 2) < 250) return
        lastScan[packageName] = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != packageName) return
        val lines = AccessibilityTextExtractor.extract(root)
        val contentHash = lines.hashCode()
        if (deferred != null && deferred.candidate.sourcePackage == packageName &&
            contentHash != deferred.originalContentHash && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            now - deferred.queuedAt >= 1_500) {
            retryAfterWindowExit(deferred, now)
        }
        if (lastContentHash[packageName] == contentHash) return
        lastContentHash[packageName] = contentHash
        val rules = settings.rules.filter { it.enabled && it.targetPackage == packageName }
        val candidate = ruleEngine.evaluate(packageName, lines, rules, System.currentTimeMillis()) ?: return
        if (candidate.confidence < 0.75f) return
        val rule = rules.firstOrNull { it.id == candidate.ruleId } ?: return
        if (deduplicator.shouldPrompt(candidate, rule.cooldownMs, now)) {
            val fused = (application as SecureLedgerApplication).recognitionCoordinator.accept(candidate,
                com.secureledger.core.accessibility.DetectionSource.SCREEN, "$packageName|${event.windowId}|$contentHash|${now / 60_000}") ?: return
            if (!fused.isNew) {
                if (CandidateInbox.pending.value?.id == fused.candidate.id) CandidateInbox.offer(fused.candidate)
                return
            }
            val delivery = dispatcher.dispatch(fused.candidate, settings.promptMode)
            deferredOverlay = if (delivery == PromptDelivery.OVERLAY) DeferredOverlay(fused.candidate, contentHash, now) else null
        }
    }

    private fun retryAfterWindowExit(deferred: DeferredOverlay, now: Long) {
        if (deferredOverlay !== deferred) return
        deferredOverlay = null // one event-driven retry only; no polling loop
        dispatcher.dismissOverlay()
        if (now - deferred.queuedAt <= 120_000) dispatcher.dispatch(deferred.candidate, preferences.state.value.promptMode)
    }

    override fun onInterrupt() {
        if (::dispatcher.isInitialized) {
            dispatcher.dismissOverlay()
            dispatcher.updateServiceStatus(false, 0)
        }
        deferredOverlay = null
    }

    override fun onDestroy() {
        if (::dispatcher.isInitialized) {
            dispatcher.dismissOverlay()
            dispatcher.updateServiceStatus(false, 0)
        }
        scope.cancel()
        super.onDestroy()
    }
}
