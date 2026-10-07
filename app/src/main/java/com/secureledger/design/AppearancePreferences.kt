package com.secureledger.design

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("纯黑") }
data class AppearanceSettings(val mode: ThemeMode = ThemeMode.SYSTEM, val accentHex: String = "7367F0")

/** Stores appearance only; vault passwords and financial data never enter these preferences. */
class AppearancePreferences(context: Context) {
    private val storage = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(AppearanceSettings(
        runCatching { ThemeMode.valueOf(storage.getString("mode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        storage.getString("accent", "7367F0")!!.takeIf { it.matches(Regex("[0-9A-Fa-f]{6}")) } ?: "7367F0"
    ))
    val state = mutable.asStateFlow()
    fun update(mode: ThemeMode = mutable.value.mode, accentHex: String = mutable.value.accentHex) {
        val hex = accentHex.removePrefix("#").uppercase()
        require(hex.matches(Regex("[0-9A-F]{6}"))) { "请输入 6 位十六进制颜色，如 7367F0" }
        check(storage.edit().putString("mode", mode.name).putString("accent", hex).commit())
        mutable.value = AppearanceSettings(mode, hex)
    }
}
