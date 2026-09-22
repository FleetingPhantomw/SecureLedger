package com.secureledger.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    identity: KeyManager.UserIdentity,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onCreateBackup: (CharArray, (Result<ByteArray>) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
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
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("身份与隐私", style = MaterialTheme.typography.headlineSmall)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("用户：${identity.userId}")
                    Text("Ed25519：${HashManager.sha256Hex(identity.publicKey).take(16)}", style = MaterialTheme.typography.bodySmall)
                    Text("X25519：${HashManager.sha256Hex(identity.agreementPublicKey).take(16)}", style = MaterialTheme.typography.bodySmall)
                    Text("Local First：OCR、账目和私钥默认不上传。", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("加密备份与换机", style = MaterialTheme.typography.titleMedium)
            Text("备份包含身份密钥和全部数据库内容；文件使用独立密码经 Argon2id 派生密钥后 AES-256-GCM 加密，并附 Ed25519 签名。请把密码与文件分开保管。")
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
            }, modifier = Modifier.fillMaxWidth()) { Text("验证账本并创建备份") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text("异常与恢复", style = MaterialTheme.typography.titleMedium)
            Text("忘记保险库密码时无法绕过加密；应使用备份文件和备份密码在空安装中恢复。密码学错误不会静默忽略。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onLock, modifier = Modifier.fillMaxWidth()) { Text("立即锁定") }
            Text("SecureLedger 1.0.0 · 数据库 v6", style = MaterialTheme.typography.bodySmall)
        }
    }
}
