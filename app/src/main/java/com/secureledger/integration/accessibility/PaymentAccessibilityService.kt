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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ruleEngine = TransactionRuleEngine()
    private val deduplicator = CandidateDeduplicator()
    private val lastScan = HashMap<String, Long>()
    private val lastContentHash = HashMap<String, Int>()
    private val preferences get() = (application as SecureLedgerApplication).accessibilityPreferences
    private lateinit var dispatcher: PromptDispatcher

    override fun onServiceConnected() {
        super.onServiceConnected()
        dispatcher = PromptDispatcher(this)
        scope.launch {
            preferences.state.collect { settings ->
                serviceInfo = serviceInfo.apply {
                    packageNames = if (settings.enabled && settings.selectedPackages.isNotEmpty())
                        settings.selectedPackages.toTypedArray()
                    else arrayOf("com.secureledger.no_apps_selected")
                }
                if (!settings.enabled) dispatcher.dismissOverlay()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val settings = preferences.state.value
        if (!settings.enabled || packageName !in settings.selectedPackages) return
        val now = SystemClock.elapsedRealtime()
        if (now - (lastScan[packageName] ?: Long.MIN_VALUE / 2) < 800) return
        lastScan[packageName] = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != packageName) return
        val lines = AccessibilityTextExtractor.extract(root)
        val contentHash = lines.hashCode()
        if (lastContentHash[packageName] == contentHash) return
        lastContentHash[packageName] = contentHash
        val rules = settings.rules.filter { it.enabled && it.targetPackage == packageName }
        val candidate = ruleEngine.evaluate(packageName, lines, rules, System.currentTimeMillis()) ?: return
        if (candidate.confidence < 0.75f) return
        val rule = rules.firstOrNull { it.id == candidate.ruleId } ?: return
        if (deduplicator.shouldPrompt(candidate, rule.cooldownMs, now)) dispatcher.dispatch(candidate, settings.promptMode)
    }

    override fun onInterrupt() { if (::dispatcher.isInitialized) dispatcher.dismissOverlay() }

    override fun onDestroy() {
        if (::dispatcher.isInitialized) dispatcher.dismissOverlay()
        scope.cancel()
        super.onDestroy()
    }
}
