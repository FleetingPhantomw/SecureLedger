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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.secureledger.design.LedgerSpacing
import androidx.core.content.ContextCompat
import com.secureledger.core.ledger.PaymentDetail
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.core.ocr.RecognizedOrder
import com.secureledger.core.ocr.StatementCandidate
import com.secureledger.core.importer.BusinessFingerprint
import com.secureledger.core.importer.DuplicateLevel
import com.secureledger.core.importer.ImportedTransaction
import com.secureledger.data.repository.ImportedTransactionView
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrImportScreen(
    initialUri: Uri?,
    onInitialUriConsumed: () -> Unit,
    onBack: () -> Unit,
    onRecognize: (Uri, (Result<RecognizedOrder>) -> Unit) -> Unit,
    onRecognizeStatement: (Uri, (Result<List<StatementCandidate>>) -> Unit) -> Unit,
    onRecognizeShoppingOrders: (Uri, (Result<List<RecognizedOrder>>) -> Unit) -> Unit,
    onImportStatement: (List<ImportedTransaction>, (Result<Int>) -> Unit) -> Unit,
    existingImports: List<ImportedTransactionView>,
    onViewImports: () -> Unit,
    onParseText: (String) -> Result<RecognizedOrder>,
    onSave: (PlainRecordPayload, (Result<Unit>) -> Unit) -> Unit,
    onSaveBatch: (List<PlainRecordPayload>, (Result<Int>) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf<RecognizedOrder?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var cameraVisible by remember { mutableStateOf(false) }
    var statement by remember { mutableStateOf<List<StatementCandidate>?>(null) }
    var shoppingOrders by remember { mutableStateOf<List<RecognizedOrder>?>(null) }
    var selectedShopping by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var showImportsLink by remember { mutableStateOf(false) }

    fun recognize(uri: Uri) {
        processing = true
        error = null
        importMessage = null
        onRecognize(uri) { result ->
            processing = false
            result.onSuccess { draft = it; statement = null; shoppingOrders = null }.onFailure { error = it.message ?: "OCR 识别失败" }
        }
    }
    fun recognizeBank(uri: Uri) {
        processing = true
        error = null
        importMessage = null
        statement = null
        onRecognizeStatement(uri) { result ->
            processing = false
            result.onSuccess { statement = it; draft = null; shoppingOrders = null }.onFailure { error = it.message ?: "银行流水识别失败" }
        }
    }
    fun recognizeShopping(uri: Uri) {
        processing = true
        error = null
        importMessage = null
        onRecognizeShoppingOrders(uri) { result ->
            processing = false
            result.onSuccess { shoppingOrders = it; selectedShopping = it.indices.toSet(); draft = null; statement = null }
                .onFailure { error = it.message ?: "购物订单识别失败" }
        }
    }
    fun save(order: RecognizedOrder, payload: PlainRecordPayload) {
        processing = true
        error = null
        onSave(payload) { result ->
            processing = false
            result.onSuccess {
                if (shoppingOrders?.firstOrNull() == order) {
                    shoppingOrders = shoppingOrders?.drop(1)
                    selectedShopping = selectedShopping.filter { it > 0 }.map { it - 1 }.toSet()
                    importMessage = if (shoppingOrders.isNullOrEmpty()) "订单已保存" else "已保存，继续核对下一笔"
                    showImportsLink = false
                } else {
                    draft = null
                    importMessage = "账目已保存"
                    showImportsLink = false
                }
            }.onFailure { error = it.message ?: "保存失败" }
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
    val bankPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::recognizeBank) }
    val shoppingPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::recognizeShopping) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("图片识别") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(LedgerSpacing.page).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("图片识别记账", style = MaterialTheme.typography.headlineSmall)
            Text("图片仅在本机识别。请核对金额和商户后保存。")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("选择截图") }
                OutlinedButton(onClick = { cameraVisible = true }, modifier = Modifier.weight(1f)) { Text("拍摄票据") }
            }
            OutlinedButton(onClick = { bankPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("选择银行流水截图（多笔识别）")
            }
            OutlinedButton(onClick = { shoppingPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("选择购物订单列表（逐笔确认）")
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
            importMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
                if (showImportsLink) TextButton(onClick = onViewImports) { Text("查看导入流水及重复项") }
            }
            statement?.let { candidates ->
                StatementReview(candidates, existingImports, processing, onImport = { selected ->
                    processing = true
                    onImportStatement(selected) { result ->
                        processing = false
                        result.onSuccess { count ->
                            statement = null
                            importMessage = "已加密导入 $count 笔流水。重复项可在历史导入记录单独核对。"
                            showImportsLink = true
                        }.onFailure { error = it.message ?: "导入失败" }
                    }
                })
            }
            draft?.let { order ->
                OrderConfirmation(order, processing) { save(order, it) }
            }
            shoppingOrders?.firstOrNull()?.let { order ->
                Text("待确认订单：${shoppingOrders?.size ?: 0} 笔；关闭或取消订单已排除。", style = MaterialTheme.typography.titleMedium)
                Text("每张订单卡片独立记账，即使商户或商品同名。可勾选一键批量保存；金额不确定的订单请取消勾选并在下方逐笔编辑。", style = MaterialTheme.typography.bodySmall)
                shoppingOrders?.forEachIndexed { index, candidate ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(index in selectedShopping, enabled = !processing, onCheckedChange = { checked ->
                            selectedShopping = if (checked) selectedShopping + index else selectedShopping - index
                        })
                        Column {
                            Text("${candidate.merchant} · ¥${candidate.amountCents?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" } ?: "待核对"}")
                            candidate.warnings.filter { "实付总额" in it || "应付款" in it || "退款成功" in it }.forEach {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Button(onClick = {
                    val orders = shoppingOrders.orEmpty()
                    val chosen = selectedShopping
                    runCatching { chosen.sorted().map { shoppingPayload(orders[it]) } }
                        .onSuccess { payloads ->
                            processing = true
                            onSaveBatch(payloads) { result ->
                                processing = false
                                result.onSuccess { count ->
                                    shoppingOrders = orders.filterIndexed { index, _ -> index !in chosen }
                                    selectedShopping = emptySet()
                                    importMessage = "已批量记账 $count 笔；交易时间已标记为估计值。"
                                    showImportsLink = false
                                }.onFailure { error = it.message ?: "批量记账失败" }
                            }
                        }.onFailure { error = it.message ?: "请核对订单金额" }
                }, enabled = selectedShopping.isNotEmpty() && !processing, modifier = Modifier.fillMaxWidth()) {
                    Text("确认批量记账 ${selectedShopping.size} 笔")
                }
                Text("逐笔编辑当前订单", style = MaterialTheme.typography.titleSmall)
                OrderConfirmation(order, processing, allowMissingTime = true) { save(order, it) }
            }
        }
    }
}

private fun shoppingPayload(order: RecognizedOrder): PlainRecordPayload = PlainRecordPayload(
    recordType = "EXPENSE",
    amountCents = requireNotNull(order.amountCents) { "订单金额缺失" },
    category = order.category,
    merchant = order.merchant,
    note = order.rawText.take(500),
    account = order.account,
    paymentChannel = order.paymentChannel,
    orderId = order.orderId,
    tags = listOf("ocr", "shopping-batch", "user-confirmed") + if (order.transactionTime == null) listOf("time-estimated") else emptyList(),
    transactionTime = order.transactionTime ?: System.currentTimeMillis()
)

@Composable
private fun StatementReview(
    candidates: List<StatementCandidate>,
    existing: List<ImportedTransactionView>,
    processing: Boolean,
    onImport: (List<ImportedTransaction>) -> Unit
) {
    var reviewed by remember(candidates) { mutableStateOf(candidates.map { it.transaction }) }
    val duplicate = reviewed.mapIndexed { index, transaction ->
        (existing.map { it.transaction } + reviewed.take(index))
            .map { BusinessFingerprint.classify(transaction, it) }
            .minByOrNull { it.ordinal } ?: DuplicateLevel.NOT_DUPLICATE
    }
    var selected by remember(candidates, existing) {
        mutableStateOf(candidates.indices.filter {
            duplicate[it] == DuplicateLevel.NOT_DUPLICATE && candidates[it].warnings.isEmpty()
        }.toSet())
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("银行流水待确认：${candidates.size} 笔", style = MaterialTheme.typography.titleMedium)
            Text("仅保存为加密的外部流水；不会自动写入账本。请逐笔核对方向、金额与日期，特别是页面未显示时间的记录。")
            listOf(false, true).forEach { isDuplicate ->
                val indices = candidates.indices.filter { (duplicate[it] != DuplicateLevel.NOT_DUPLICATE) == isDuplicate }
                if (indices.isNotEmpty()) {
                    Text(if (isDuplicate) "疑似重复（默认不导入，可自行勾选保留）" else "新流水", style = MaterialTheme.typography.titleSmall)
                    indices.forEach { index ->
                        val item = candidates[index]
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = index in selected, onCheckedChange = { checked ->
                                selected = if (checked) selected + index else selected - index
                            })
                            Column {
                                val transaction = reviewed[index]
                                Text("${transaction.merchant} · ${if (transaction.direction == "CREDIT") "收入" else "支出"} ¥${transaction.amountCents / 100}.${(transaction.amountCents % 100).toString().padStart(2, '0')}")
                                Text(java.time.Instant.ofEpochMilli(transaction.transactionTime)
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString(), style = MaterialTheme.typography.bodySmall)
                                item.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                if (isDuplicate) Text("相似流水需人工决定", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                StatementRowEditor(transaction) { updated ->
                                    reviewed = reviewed.toMutableList().also { it[index] = updated }
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { onImport(selected.sorted().map { reviewed[it] }) },
                enabled = selected.isNotEmpty() && !processing,
                modifier = Modifier.fillMaxWidth()
            ) { Text("确认导入 ${selected.size} 笔") }
        }
    }
}

@Composable
private fun StatementRowEditor(transaction: ImportedTransaction, onChange: (ImportedTransaction) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (!editing) {
        TextButton(onClick = { editing = true }) { Text("修改此笔") }
        return
    }
    var merchant by remember(transaction) { mutableStateOf(transaction.merchant) }
    var amount by remember(transaction) { mutableStateOf("${transaction.amountCents / 100}.${(transaction.amountCents % 100).toString().padStart(2, '0')}") }
    var time by remember(transaction) { mutableStateOf(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(transaction.transactionTime))) }
    var direction by remember(transaction) { mutableStateOf(transaction.direction) }
    var error by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(merchant, { merchant = it }, label = { Text("商户 / 交易摘要") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(amount, { amount = it }, label = { Text("金额（元）") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(time, { time = it }, label = { Text("时间 yyyy-MM-dd HH:mm") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(direction == "DEBIT", { direction = "DEBIT" }, { Text("支出") })
        FilterChip(direction == "CREDIT", { direction = "CREDIT" }, { Text("收入") })
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        runCatching {
            val cents = (amount.toBigDecimal() * BigDecimal(100)).longValueExact()
            require(cents > 0) { "金额必须大于零" }
            require(merchant.isNotBlank()) { "商户不能为空" }
            val timestamp = LocalDateTime.parse(time.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            transaction.copy(amountCents = cents, merchant = merchant.trim(), transactionTime = timestamp,
                direction = direction, category = if (direction == "CREDIT") "收入" else transaction.category)
        }.onSuccess { onChange(it); editing = false; error = null }
            .onFailure { error = it.message ?: "请检查编辑内容" }
    }) { Text("应用修改") }
}

@Composable
private fun OrderConfirmation(order: RecognizedOrder, processing: Boolean, allowMissingTime: Boolean = false, onSave: (PlainRecordPayload) -> Unit) {
    var amount by remember(order) { mutableStateOf(order.amountCents?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" }.orEmpty()) }
    var merchant by remember(order) { mutableStateOf(order.merchant) }
    var category by remember(order) { mutableStateOf(order.category) }
    var channel by remember(order) { mutableStateOf(order.paymentChannel) }
    var account by remember(order) { mutableStateOf(order.account) }
    var note by remember(order) { mutableStateOf(order.rawText.take(500)) }
    var timeText by remember(order) { mutableStateOf(order.transactionTime?.let {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
    }.orEmpty()) }
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
            OutlinedTextField(timeText, { timeText = it }, label = { Text("交易时间 yyyy-MM-dd HH:mm${if (allowMissingTime) "（可留空）" else "（必填）"}") }, modifier = Modifier.fillMaxWidth())
            if (allowMissingTime && timeText.isBlank()) Text("留空将使用导入时刻，并标记为估计时间；不会声称这是支付时间。", style = MaterialTheme.typography.bodySmall)
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
                    val estimatedTime = allowMissingTime && timeText.isBlank()
                    val timestamp = if (estimatedTime) System.currentTimeMillis() else
                        LocalDateTime.parse(timeText.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    PlainRecordPayload(
                        recordType = "EXPENSE", amountCents = cents, category = category,
                        merchant = merchant, note = note, account = account, paymentChannel = channel,
                        orderId = order.orderId, payments = payments,
                        tags = listOf("ocr", "user-confirmed") +
                            (if (estimatedTime) listOf("time-estimated") else emptyList()) +
                            (if (payments.any(PaymentDetail::isBankDirect)) listOf("pending-reconciliation") else emptyList()),
                        transactionTime = timestamp
                    )
                }.onSuccess(onSave).onFailure { validation = it.message ?: "请检查金额" }
            }, enabled = !processing, modifier = Modifier.fillMaxWidth()) { Text("确认并加密记账") }
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
