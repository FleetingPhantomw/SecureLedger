package com.secureledger.feature.ocr

import android.Manifest
import android.content.Context
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.secureledger.core.ledger.PaymentDetail
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.core.ocr.RecognizedOrder
import java.io.File
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrImportScreen(
    initialUri: Uri?,
    onInitialUriConsumed: () -> Unit,
    onBack: () -> Unit,
    onRecognize: (Uri, (Result<RecognizedOrder>) -> Unit) -> Unit,
    onParseText: (String) -> Result<RecognizedOrder>,
    onSave: (PlainRecordPayload) -> Unit
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf<RecognizedOrder?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var cameraVisible by remember { mutableStateOf(false) }

    fun recognize(uri: Uri) {
        processing = true
        error = null
        onRecognize(uri) { result ->
            processing = false
            result.onSuccess { draft = it }.onFailure { error = it.message ?: "OCR 识别失败" }
        }
    }

    LaunchedEffect(initialUri) {
        initialUri?.let {
            onInitialUriConsumed()
            recognize(it)
        }
    }
    if (cameraVisible) {
        CameraCaptureScreen(
            onBack = { cameraVisible = false },
            onCaptured = { uri -> cameraVisible = false; recognize(uri) },
            onError = { message -> error = message; cameraVisible = false }
        )
        return
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::recognize) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("图片识别") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("离线订单识别", style = MaterialTheme.typography.headlineSmall)
            Text("使用随应用提供的 ML Kit 中文模型，图片不会上传。识别结果必须由你确认后才写入可信账本。")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("选择截图") }
                OutlinedButton(onClick = { cameraVisible = true }, modifier = Modifier.weight(1f)) { Text("拍摄票据") }
            }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (text.isBlank()) error = "剪贴板中没有可识别的文本"
                else onParseText(text).onSuccess { draft = it; error = null }
                    .onFailure { error = it.message ?: "剪贴板解析失败" }
            }, modifier = Modifier.fillMaxWidth()) { Text("从剪贴板文本识别") }
            if (processing) Text("正在本机识别…", color = MaterialTheme.colorScheme.primary)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            draft?.let { order ->
                OrderConfirmation(order, onSave)
            }
        }
    }
}

@Composable
private fun OrderConfirmation(order: RecognizedOrder, onSave: (PlainRecordPayload) -> Unit) {
    var amount by remember(order) { mutableStateOf(order.amountCents?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" }.orEmpty()) }
    var merchant by remember(order) { mutableStateOf(order.merchant) }
    var category by remember(order) { mutableStateOf(order.category) }
    var channel by remember(order) { mutableStateOf(order.paymentChannel) }
    var account by remember(order) { mutableStateOf(order.account) }
    var note by remember(order) { mutableStateOf(order.rawText.take(500)) }
    var validation by remember(order) { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("确认识别结果", style = MaterialTheme.typography.titleMedium)
            order.warnings.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(amount, { amount = it }, label = { Text("金额（元）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(merchant, { merchant = it }, label = { Text("商户") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(category, { category = it }, label = { Text("分类") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(channel, { channel = it }, label = { Text("支付渠道") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(account, { account = it }, label = { Text("账户") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("备注 / OCR 原文摘要") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            if (order.payments.isNotEmpty()) {
                Text("组合支付", style = MaterialTheme.typography.labelLarge)
                order.payments.forEach { Text("${it.channel}：¥${it.amountCents / 100}.${(it.amountCents % 100).toString().padStart(2, '0')}${if (it.isBankDirect) "（待银行对账）" else ""}") }
            }
            validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                runCatching {
                    val cents = (amount.toBigDecimal() * BigDecimal(100)).longValueExact()
                    require(cents >= 0) { "金额不能为负数" }
                    val payments = order.payments.takeIf { it.sumOf(PaymentDetail::amountCents) == cents }.orEmpty()
                    PlainRecordPayload(
                        recordType = "EXPENSE", amountCents = cents, category = category,
                        merchant = merchant, note = note, account = account, paymentChannel = channel,
                        orderId = order.orderId, payments = payments,
                        tags = listOf("ocr", "user-confirmed") + if (payments.any(PaymentDetail::isBankDirect)) listOf("pending-reconciliation") else emptyList(),
                        transactionTime = order.transactionTime ?: System.currentTimeMillis()
                    )
                }.onSuccess(onSave).onFailure { validation = it.message ?: "请检查金额" }
            }, modifier = Modifier.fillMaxWidth()) { Text("确认并加密记账") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraCaptureScreen(onBack: () -> Unit, onCaptured: (Uri) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) onError("需要相机权限才能拍摄票据")
    }
    LaunchedEffect(Unit) { if (!permissionGranted) permission.launch(Manifest.permission.CAMERA) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("拍摄票据") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (permissionGranted) {
                AndroidView(
                    factory = { previewContext ->
                        PreviewView(previewContext).also { previewView ->
                            val future = ProcessCameraProvider.getInstance(previewContext)
                            future.addListener({
                                runCatching {
                                    val cameraProvider = future.get()
                                    provider = cameraProvider
                                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                                }.onFailure { onError(it.message ?: "无法打开相机") }
                            }, ContextCompat.getMainExecutor(previewContext))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Button(onClick = {
                    val file = File(context.cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) = onCaptured(Uri.fromFile(file))
                            override fun onError(exception: ImageCaptureException) = onError(exception.message ?: "拍摄失败")
                        }
                    )
                }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("拍照并识别") }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }
}
