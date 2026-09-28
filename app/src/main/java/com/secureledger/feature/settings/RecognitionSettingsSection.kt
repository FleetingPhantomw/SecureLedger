package com.secureledger.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.secureledger.core.accessibility.TransactionRule
import com.secureledger.integration.accessibility.AccessibilityPreferences
import com.secureledger.integration.accessibility.PaymentAccessibilityService
import com.secureledger.integration.accessibility.PromptMode
import java.util.UUID

@Composable
internal fun RecognitionSettingsSection(preferences: AccessibilityPreferences) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by preferences.state.collectAsState()
    var osEnabled by remember { mutableStateOf(isServiceEnabled(context)) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationsGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    var customPackage by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var editingRule by remember { mutableStateOf<TransactionRule?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
        if (!granted) message = "通知权限未开启，无法显示服务状态与受保护页面的备用提醒"
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                osEnabled = isServiceEnabled(context)
                overlayGranted = Settings.canDrawOverlays(context)
                notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Text("自动识别消费", style = MaterialTheme.typography.titleMedium)
    Text("开启后，仅查看你选择的应用中的可访问页面文字，识别已完成的付款并提醒你确认。不会自动记账、操作支付或保存完整页面。", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("自动识别消费")
        Switch(checked = state.enabled, onCheckedChange = { enabled ->
            preferences.setEnabled(enabled)
            if (enabled && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        })
    }
    Text(if (osEnabled) "系统无障碍服务已启用" else "还需在系统设置中启用 SecureLedger 消费识别", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
        Text("打开系统无障碍设置")
    }
    Text(if (notificationsGranted) "通知权限已开启：可显示服务状态与备用提醒" else "通知权限未开启：无法显示后台状态与备用提醒", style = MaterialTheme.typography.bodySmall)
    if (!notificationsGranted && Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = {
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }, modifier = Modifier.fillMaxWidth()) { Text("允许通知") }
    Text(if (overlayGranted) "悬浮窗权限已开启" else "悬浮窗权限未开启：系统会尝试通知提醒", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")))
    }, modifier = Modifier.fillMaxWidth()) { Text("打开悬浮窗权限设置") }
    Text("识别应用", style = MaterialTheme.typography.labelLarge)
    val commonPackages = listOf(
        "支付宝" to "com.eg.android.AlipayGphone", "微信" to "com.tencent.mm",
        "淘宝" to "com.taobao.taobao", "京东" to "com.jingdong.app.mall", "拼多多" to "com.xunmeng.pinduoduo"
    )
    commonPackages.forEach { (label, packageName) ->
        FilterChip(state.selectedPackages.contains(packageName), { preferences.setPackageSelected(packageName, !state.selectedPackages.contains(packageName)) }, { Text(label) })
    }
    state.selectedPackages.filterNot { selected -> commonPackages.any { it.second == selected } }.forEach { packageName ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(packageName, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { preferences.setPackageSelected(packageName, false) }) { Text("移除") }
        }
    }
    OutlinedTextField(customPackage, { customPackage = it.trim() }, label = { Text("其他应用包名") }, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = {
        runCatching { preferences.setPackageSelected(customPackage, true) }
            .onSuccess { customPackage = ""; message = "应用已加入" }
            .onFailure { message = "请输入有效的应用包名" }
    }) { Text("添加应用") }
    Text("提示方式", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.promptMode == PromptMode.NOTIFICATION, {
            preferences.setPromptMode(PromptMode.NOTIFICATION)
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }, { Text("通知提醒") })
        FilterChip(state.promptMode == PromptMode.OVERLAY, {
            preferences.setPromptMode(PromptMode.OVERLAY)
            if (!Settings.canDrawOverlays(context)) message = "请在上方开启悬浮窗权限；未授权时自动改用通知提醒"
        }, { Text("即时浮层") })
    }
    Text("识别规则", style = MaterialTheme.typography.labelLarge)
    state.rules.forEach { rule ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { editingRule = rule }) { Text("${rule.name} · ${rule.targetPackage}") }
            Switch(rule.enabled, { preferences.saveRule(rule.copy(enabled = it)) })
        }
    }
    OutlinedButton(onClick = {
        editingRule = TransactionRule(
            id = UUID.randomUUID().toString(), name = "", targetPackage = "",
            positiveKeywords = listOf("支付成功"), negativeKeywords = listOf("退款", "原价", "优惠"),
            amountRegex = "(?:[¥￥]\\s*)?(\\d{1,7}(?:[.,]\\d{1,2})?)"
        )
    }, modifier = Modifier.fillMaxWidth()) { Text("新增识别规则") }
    message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
    editingRule?.let { rule ->
        RuleEditor(rule, onDismiss = { editingRule = null }, onSave = {
            runCatching { preferences.saveRule(it) }.onSuccess { editingRule = null; message = "规则已保存" }
                .onFailure { error -> message = error.message ?: "规则无效" }
        }, onDelete = {
            preferences.deleteRule(rule.id)
            editingRule = null
        })
    }
}

@Composable
private fun RuleEditor(rule: TransactionRule, onDismiss: () -> Unit, onSave: (TransactionRule) -> Unit, onDelete: () -> Unit) {
    var name by remember(rule.id) { mutableStateOf(rule.name) }
    var target by remember(rule.id) { mutableStateOf(rule.targetPackage) }
    var positive by remember(rule.id) { mutableStateOf(rule.positiveKeywords.joinToString("，")) }
    var negative by remember(rule.id) { mutableStateOf(rule.negativeKeywords.joinToString("，")) }
    var amount by remember(rule.id) { mutableStateOf(rule.amountRegex) }
    var merchant by remember(rule.id) { mutableStateOf(rule.merchantRegex) }
    var time by remember(rule.id) { mutableStateOf(rule.timeRegex) }
    var channel by remember(rule.id) { mutableStateOf(rule.paymentChannelRegex) }
    var priority by remember(rule.id) { mutableStateOf(rule.priority.toString()) }
    var cooldown by remember(rule.id) { mutableStateOf((rule.cooldownMs / 1000).toString()) }
    var error by remember(rule.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule.name.isBlank()) "新增规则" else "编辑规则") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") })
                OutlinedTextField(target, { target = it }, label = { Text("应用包名") })
                OutlinedTextField(positive, { positive = it }, label = { Text("正向关键词（逗号分隔）") })
                OutlinedTextField(negative, { negative = it }, label = { Text("排除关键词（逗号分隔）") })
                OutlinedTextField(amount, { amount = it }, label = { Text("金额正则（第一个分组为金额）") })
                OutlinedTextField(merchant, { merchant = it }, label = { Text("商户正则（可选）") })
                OutlinedTextField(time, { time = it }, label = { Text("时间正则（可选）") })
                OutlinedTextField(channel, { channel = it }, label = { Text("支付渠道正则（可选）") })
                OutlinedTextField(priority, { priority = it }, label = { Text("优先级") })
                OutlinedTextField(cooldown, { cooldown = it }, label = { Text("提醒间隔（秒）") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    rule.copy(
                        name = name.trim(), targetPackage = target.trim(),
                        positiveKeywords = splitKeywords(positive), negativeKeywords = splitKeywords(negative),
                        amountRegex = amount, merchantRegex = merchant, timeRegex = time,
                        paymentChannelRegex = channel, priority = priority.toInt(),
                        cooldownMs = cooldown.toLong() * 1000
                    ).also(TransactionRule::validate)
                }.onSuccess(onSave).onFailure { error = it.message ?: "请检查规则" }
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (rule.name.isNotBlank()) TextButton(onClick = onDelete) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

private fun splitKeywords(value: String) = value.split(',', '，', '\n').map(String::trim).filter(String::isNotEmpty)

private fun isServiceEnabled(context: android.content.Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java)
    return manager.getEnabledAccessibilityServiceList(-1).any {
        it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == PaymentAccessibilityService::class.java.name
    }
}
