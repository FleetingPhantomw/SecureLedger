package com.secureledger.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.data.entity.BankCardEntity
import com.secureledger.integration.accessibility.AccessibilityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    identity: KeyManager.UserIdentity,
    accessibilityPreferences: AccessibilityPreferences,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onCreateBackup: (CharArray, (Result<ByteArray>) -> Unit) -> Unit,
    bankCards: List<BankCardEntity>,
    onSaveBankCard: (String?, String, String, String, (Result<Unit>) -> Unit) -> Unit,
    onSetBankCardEnabled: (BankCardEntity, Boolean) -> Unit,
    onDeleteBankCard: (BankCardEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var editingCardId by remember { mutableStateOf<String?>(null) }
    var bankName by remember { mutableStateOf("") }
    var cardAlias by remember { mutableStateOf("") }
    var lastFourDigits by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<BankCardEntity?>(null) }
    var showIdentityDetails by remember { mutableStateOf(false) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("无法写入备份文件")
                    }
                }.onSuccess { message = "加密备份已保存" }
                    .onFailure { message = it.message ?: "保存失败" }
            }
        }
        pendingBytes = null
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("设置") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("身份与隐私", style = MaterialTheme.typography.headlineSmall)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("身份编号：${identity.userId}")
                    Text("账目、图片识别和身份信息默认仅保存在本机。", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { showIdentityDetails = !showIdentityDetails }) {
                        Text(if (showIdentityDetails) "收起技术详情" else "查看技术详情")
                    }
                    if (showIdentityDetails) {
                        Text("Ed25519：${HashManager.sha256Hex(identity.publicKey).take(16)}", style = MaterialTheme.typography.bodySmall)
                        Text("X25519：${HashManager.sha256Hex(identity.agreementPublicKey).take(16)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            RecognitionSettingsSection(accessibilityPreferences)
            Text("银行卡", style = MaterialTheme.typography.titleMedium)
            Text("只保存银行名称、别名和后四位，不保存完整卡号。历史账目保留保存时的显示快照。", style = MaterialTheme.typography.bodySmall)
            bankCards.forEach { card ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(card.displaySnapshot, style = MaterialTheme.typography.titleSmall)
                                Text(if (card.enabled) "记账时可选" else "已停用", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = card.enabled, onCheckedChange = { onSetBankCardEnabled(card, it) })
                        }
                        Row {
                            TextButton(onClick = {
                                editingCardId = card.id
                                bankName = card.bankName
                                cardAlias = card.cardAlias
                                lastFourDigits = card.lastFourDigits
                            }) { Text("编辑") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { pendingDelete = card }) { Text("删除") }
                        }
                    }
                }
            }
            OutlinedTextField(bankName, { bankName = it }, label = { Text("银行名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cardAlias, { cardAlias = it }, label = { Text("别名（可选）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(lastFourDigits, { lastFourDigits = it.filter(Char::isDigit).take(4) }, label = { Text("卡号后四位") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onSaveBankCard(editingCardId, bankName, cardAlias, lastFourDigits) { result ->
                        result.onSuccess {
                            message = if (editingCardId == null) "银行卡已添加" else "银行卡已更新"
                            editingCardId = null
                            bankName = ""
                            cardAlias = ""
                            lastFourDigits = ""
                        }.onFailure { message = it.message ?: "银行卡保存失败" }
                    }
                }, modifier = Modifier.weight(1f)) { Text(if (editingCardId == null) "添加银行卡" else "保存修改") }
                if (editingCardId != null) {
                    OutlinedButton(onClick = {
                        editingCardId = null
                        bankName = ""
                        cardAlias = ""
                        lastFourDigits = ""
                    }) { Text("取消") }
                }
            }
            Text("加密备份与换机", style = MaterialTheme.typography.titleMedium)
            Text("备份包含身份和全部账目，并使用独立密码加密。请把备份密码和文件分开保管。")
            OutlinedTextField(password, { password = it }, label = { Text("备份密码（至少 10 位）") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(confirmation, { confirmation = it }, label = { Text("确认备份密码") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                if (password != confirmation) { message = "两次备份密码不一致"; return@Button }
                onCreateBackup(password.toCharArray()) { result ->
                    result.onSuccess {
                        pendingBytes = it
                        createDocument.launch("SecureLedger-${System.currentTimeMillis()}.sldb")
                    }.onFailure { message = it.message ?: "备份失败" }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("创建加密备份") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text("恢复与锁定", style = MaterialTheme.typography.titleMedium)
            Text("忘记本机密码时无法直接解锁，请使用备份文件和备份密码恢复。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onLock, modifier = Modifier.fillMaxWidth()) { Text("立即锁定") }
            Text("SecureLedger 1.0.0", style = MaterialTheme.typography.bodySmall)
        }
    }
    pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除银行卡？") },
            text = { Text("仅删除可选银行卡；历史账目中的银行卡显示快照不会改变。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteBankCard(card)
                    if (editingCardId == card.id) {
                        editingCardId = null
                        bankName = ""
                        cardAlias = ""
                        lastFourDigits = ""
                    }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}
