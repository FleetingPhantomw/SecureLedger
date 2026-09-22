package com.secureledger.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secureledger.data.repository.SyncImportResult
import com.secureledger.integration.network.LanShareSession

@Composable
fun NetworkSyncSection(
    exportedPackage: String?,
    exportedProjectId: String?,
    onStartLan: (String, String) -> Result<LanShareSession>,
    onStopLan: () -> Unit,
    onReceiveLan: (String, String, (Result<SyncImportResult>) -> Unit) -> Unit,
    onRelayUpload: (String, String, String, (Result<String>) -> Unit) -> Unit,
    onRelayDownload: (String, String, (Result<SyncImportResult>) -> Unit) -> Unit
) {
    var lanSession by remember { mutableStateOf<LanShareSession?>(null) }
    var lanUrl by remember { mutableStateOf("") }
    var lanToken by remember { mutableStateOf("") }
    var relayEndpoint by remember { mutableStateOf("") }
    var relayToken by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("局域网 P2P", style = MaterialTheme.typography.titleMedium)
            Text("SL1 包中的账目本身已加密并签名；临时令牌限制同一局域网内的读取。停止分享后端口立即关闭。", style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = exportedPackage != null && exportedProjectId != null,
                onClick = {
                    val result = onStartLan(exportedProjectId.orEmpty(), exportedPackage.orEmpty())
                    result.onSuccess { lanSession = it; message = "局域网分享已启动" }
                        .onFailure { message = it.message ?: "启动失败" }
                }, modifier = Modifier.fillMaxWidth()
            ) { Text("分享当前同步包") }
            lanSession?.let { session ->
                Text("地址：${session.url}", style = MaterialTheme.typography.bodySmall)
                Text("临时令牌：${session.token}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onStopLan(); lanSession = null; message = "分享已停止" }, modifier = Modifier.fillMaxWidth()) { Text("停止分享") }
            }
            OutlinedTextField(lanUrl, { lanUrl = it }, label = { Text("对方局域网地址") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(lanToken, { lanToken = it }, label = { Text("临时令牌") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                onReceiveLan(lanUrl, lanToken) { result ->
                    message = result.fold({ "局域网导入：${it.status}" }, { it.message ?: "局域网导入失败" })
                }
            }, enabled = lanUrl.isNotBlank() && lanToken.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("从局域网接收并验证") }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("半可信中继", style = MaterialTheme.typography.titleMedium)
            Text("中继只能存储/转发已加密的 SL1 文本。除本机测试外强制 HTTPS；导入仍执行签名、哈希链、Merkle 与防重放检查。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(relayEndpoint, { relayEndpoint = it }, label = { Text("中继包端点（HTTPS）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(relayToken, { relayToken = it }, label = { Text("Bearer Token（可选）") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onRelayUpload(relayEndpoint, relayToken, exportedPackage.orEmpty()) { result ->
                        message = result.fold({ "中继上传成功" }, { it.message ?: "上传失败" })
                    }
                }, enabled = relayEndpoint.isNotBlank() && exportedPackage != null, modifier = Modifier.weight(1f)) { Text("上传") }
                Button(onClick = {
                    onRelayDownload(relayEndpoint, relayToken) { result ->
                        message = result.fold({ "中继导入：${it.status}" }, { it.message ?: "下载失败" })
                    }
                }, enabled = relayEndpoint.isNotBlank(), modifier = Modifier.weight(1f)) { Text("下载并验证") }
            }
        }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}
