package com.secureledger.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.secureledger.SecureLedgerApplication
import com.secureledger.design.ThemeMode

@Composable
internal fun AppearanceSection() {
    val preferences = (LocalContext.current.applicationContext as SecureLedgerApplication).appearancePreferences
    val state by preferences.state.collectAsState()
    var custom by remember(state.accentHex) { mutableStateOf(state.accentHex) }
    var error by remember { mutableStateOf<String?>(null) }
    Text("外观", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode -> FilterChip(state.mode == mode,
            { preferences.update(mode = mode) }, { Text(mode.label) }) }
    }
    Text("主题色", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("鸢尾" to "7367F0", "海蓝" to "2563EB", "薄荷" to "0F896E", "玫瑰" to "C84072", "琥珀" to "A66B12").forEach { (name, hex) ->
            FilterChip(state.accentHex == hex, { preferences.update(accentHex = hex) }, { Text(name) },
                leadingIcon = { Surface(Modifier.size(14.dp), shape = MaterialTheme.shapes.small,
                    color = Color(android.graphics.Color.parseColor("#$hex"))) {} })
        }
    }
    OutlinedTextField(custom, { custom = it.take(7); error = null }, label = { Text("自定义颜色 HEX") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = { runCatching { preferences.update(accentHex = custom.trim()) }
        .onFailure { error = it.message } }) { Text("应用自定义颜色") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
