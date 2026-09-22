package com.secureledger.feature.project

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.secureledger.data.entity.ProjectEntity
import com.secureledger.data.entity.ProjectMemberEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectAccessScreen(
    project: ProjectEntity?,
    members: List<ProjectMemberEntity>,
    joinRequest: String,
    onBack: () -> Unit,
    onLoadMembers: (String) -> Unit,
    onIssueInvitation: (String, String, (Result<String>) -> Unit) -> Unit,
    onAcceptInvitation: (String, (Result<ProjectEntity>) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf(if (project == null) joinRequest else "") }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(project?.projectId) { project?.let { onLoadMembers(it.projectId) } }

    fun process(value: String) {
        input = value
        if (project == null) {
            onAcceptInvitation(value) { result ->
                result.onSuccess { message = "已安全加入项目：${it.name}" }
                    .onFailure { message = it.message ?: "接受邀请失败" }
            }
        } else {
            onIssueInvitation(project.projectId, value) { result ->
                result.onSuccess { output = it; message = "邀请已生成"; onLoadMembers(project.projectId) }
                    .onFailure { message = it.message ?: "生成邀请失败" }
            }
        }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(::process) }
    val qr = remember(output) { createQr(output) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (project == null) "加入多人项目" else "成员与邀请") },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (project == null) {
                Text("第一步：向项目创建者出示加入请求", style = MaterialTheme.typography.titleMedium)
                Text("此二维码只包含加入项目所需的公开身份信息。")
            } else {
                Text(project.name, style = MaterialTheme.typography.headlineSmall)
                Text("扫描新成员的加入请求，确认后生成一次性项目邀请。")
                Text("成员（${members.size}）", style = MaterialTheme.typography.titleMedium)
                members.forEach { member ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${member.displayName} · ${roleLabel(member.role)}")
                            Text(member.userId, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (output.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (project == null) "我的加入请求" else "给新成员的项目邀请", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        if (qr != null) Image(qr.asImageBitmap(), "项目邀请二维码", Modifier.size(260.dp))
                        else Text("内容超过单个二维码容量，请使用复制文本。", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { copy(context, output) }, modifier = Modifier.fillMaxWidth()) { Text("复制") }
                    }
                }
            }
            Text(if (project == null) "第二步：扫描创建者返回的邀请" else "扫描或粘贴新成员的请求", style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(false))
            }, modifier = Modifier.fillMaxWidth()) { Text("扫码") }
            OutlinedTextField(input, { input = it }, label = { Text("粘贴交换数据") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
            Button(onClick = { process(input) }, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (project == null) "验证并加入项目" else "验证请求并生成邀请")
            }
            message?.let { Text(it, color = if (it.contains("失败") || it.contains("无效")) MaterialTheme.colorScheme.error else Color(0xFF137333)) }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "OWNER" -> "创建者"
    "MEMBER" -> "成员"
    else -> role
}

private fun createQr(value: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 700, 700)
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
}.getOrNull()

private fun copy(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("SecureLedger Project Access", value))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}
