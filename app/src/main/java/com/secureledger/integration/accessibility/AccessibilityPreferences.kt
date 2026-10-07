package com.secureledger.integration.accessibility

import android.content.Context
import com.secureledger.core.accessibility.PresetTransactionRules
import com.secureledger.core.accessibility.TransactionRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class PromptMode { NOTIFICATION, OVERLAY }

@Serializable
data class RecognitionSettings(
    val enabled: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val promptMode: PromptMode = PromptMode.OVERLAY,
    val rules: List<TransactionRule> = PresetTransactionRules.rules,
    val schemaVersion: Int = 1,
    val notificationCapture: Boolean = false
)

/** Persists configuration only, never third-party screen content or transaction candidates. */
class AccessibilityPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("payment_recognition", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(loadSettings())
    val state = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) = save(_state.value.copy(enabled = enabled))

    fun setPackageSelected(packageName: String, selected: Boolean) {
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,120}")))
        val packages = _state.value.selectedPackages.toMutableSet()
        if (selected) packages.add(packageName) else packages.remove(packageName)
        save(_state.value.copy(selectedPackages = packages))
    }

    fun setPromptMode(mode: PromptMode) = save(_state.value.copy(promptMode = mode))
    fun setNotificationCapture(enabled: Boolean) = save(_state.value.copy(notificationCapture = enabled))

    fun saveRule(rule: TransactionRule) {
        rule.validate()
        val rules = _state.value.rules.filterNot { it.id == rule.id } + rule
        save(_state.value.copy(rules = rules))
    }

    fun deleteRule(id: String) = save(_state.value.copy(rules = _state.value.rules.filterNot { it.id == id }))

    private fun loadSettings(): RecognitionSettings {
        val stored = preferences.getString("settings", null)?.let {
            runCatching { json.decodeFromString<RecognitionSettings>(it) }.getOrNull()
        } ?: return RecognitionSettings(schemaVersion = 2)
        if (stored.schemaVersion >= 2) return stored
        val additions = PresetTransactionRules.rules.filter { preset ->
            preset.id in setOf("taobao-payment", "jd-payment", "pdd-payment") &&
                stored.rules.none { it.id == preset.id }
        }
        val upgraded = stored.copy(rules = stored.rules + additions, schemaVersion = 2)
        preferences.edit().putString("settings", json.encodeToString(upgraded)).apply()
        return upgraded
    }

    private fun save(settings: RecognitionSettings) {
        check(preferences.edit().putString("settings", json.encodeToString(settings)).commit()) { "无法保存识别设置" }
        _state.value = settings
    }
}
