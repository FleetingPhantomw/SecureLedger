package com.secureledger.feature.importer

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.secureledger.core.importer.DataSourceType
import com.secureledger.core.importer.DuplicateLevel
import com.secureledger.core.importer.ReconciliationStatus
import com.secureledger.data.repository.ImportedTransactionView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCenterScreen(
    entries: List<ImportedTransactionView>,
    operationMessage: String?,
    onBack: () -> Unit,
    onOpenReconciliation: () -> Unit,
    onImport: (DataSourceType, Uri, String) -> Unit
) {
    val context = LocalContext.current
    var source by remember { mutableStateOf(DataSourceType.BANK) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = runCatching {
                context.contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: it.lastPathSegment ?: "statement.csv"
            onImport(source, it, name)
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("导入中心") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("本地 CSV 导入", style = MaterialTheme.typography.headlineSmall)
            Text("文件只在本机解析；原始行和标准化字段会用主密钥加密。银行流水是对账锚点，不被当作消费真实性的密码学证明。")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(source == DataSourceType.BANK, { source = DataSourceType.BANK }, { Text("银行流水") })
                FilterChip(source == DataSourceType.CAMPUS_CARD, { source = DataSourceType.CAMPUS_CARD }, { Text("校园卡") })
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("支持 UTF-8 / GB18030，最大 5 MiB、10,000 行")
                    Text("必需列：交易时间，以及金额/支出金额/收入金额之一。可选：订单号、商户、渠道、卡号、分类、备注。", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel")) }, modifier = Modifier.fillMaxWidth()) {
                Text("选择 CSV 文件")
            }
            OutlinedButton(onClick = onOpenReconciliation, modifier = Modifier.fillMaxWidth()) {
                Text("打开待对账（${entries.count { it.status.needsAttention() }}）")
            }
            operationMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text("最近导入", style = MaterialTheme.typography.titleMedium)
            if (entries.isEmpty()) Text("尚未导入流水", style = MaterialTheme.typography.bodySmall)
            entries.take(5).forEach { ImportedRow(it, showActions = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconciliationScreen(
    entries: List<ImportedTransactionView>,
    onBack: () -> Unit,
    onSupplement: (String) -> Unit,
    onIgnore: (String) -> Unit
) {
    var filter by remember { mutableStateOf<ReconciliationStatus?>(null) }
    val visible = entries.filter { filter == null || it.status == filter }
    Scaffold(topBar = {
        TopAppBar(title = { Text("待对账") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(filter == null, { filter = null }, { Text("全部") })
                FilterChip(filter == ReconciliationStatus.MISSING, { filter = ReconciliationStatus.MISSING }, { Text("漏记") })
                FilterChip(filter == ReconciliationStatus.CONFLICT, { filter = ReconciliationStatus.CONFLICT }, { Text("冲突") })
                FilterChip(filter == ReconciliationStatus.DUPLICATE_REVIEW, { filter = ReconciliationStatus.DUPLICATE_REVIEW }, { Text("重复") })
            }
            if (visible.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("当前筛选下没有流水")
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.importedId }) { entry ->
                        ImportedRow(
                            entry = entry,
                            showActions = entry.status.needsAttention(),
                            onSupplement = { onSupplement(entry.importedId) },
                            onIgnore = { onIgnore(entry.importedId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportedRow(
    entry: ImportedTransactionView,
    showActions: Boolean,
    onSupplement: () -> Unit = {},
    onIgnore: () -> Unit = {}
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.transaction.merchant.ifBlank { "未识别商户" }, style = MaterialTheme.typography.titleMedium)
                Text("${if (entry.transaction.direction == "CREDIT") "+" else "−"}¥${formatCents(entry.transaction.amountCents)}")
            }
            Text("${sourceName(entry.transaction.sourceType)} · ${formatTime(entry.transaction.transactionTime)}", style = MaterialTheme.typography.bodySmall)
            Text("${statusName(entry.status)}：${entry.explanation}", style = MaterialTheme.typography.bodySmall)
            if (entry.duplicateLevel != DuplicateLevel.NOT_DUPLICATE) {
                Text("业务去重：${duplicateName(entry.duplicateLevel)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (showActions) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSupplement) { Text("确认补记") }
                    TextButton(onClick = onIgnore) { Text("忽略") }
                }
            }
        }
    }
}

private fun ReconciliationStatus.needsAttention() = this == ReconciliationStatus.MISSING ||
    this == ReconciliationStatus.POSSIBLE || this == ReconciliationStatus.CONFLICT ||
    this == ReconciliationStatus.DUPLICATE_REVIEW

private fun statusName(status: ReconciliationStatus) = when (status) {
    ReconciliationStatus.MATCHED -> "已匹配"
    ReconciliationStatus.POSSIBLE -> "疑似匹配"
    ReconciliationStatus.MISSING -> "疑似漏记"
    ReconciliationStatus.CONFLICT -> "冲突"
    ReconciliationStatus.DUPLICATE_REVIEW -> "疑似重复"
    ReconciliationStatus.IGNORED -> "已忽略"
    ReconciliationStatus.SUPPLEMENTED -> "已补记"
}

private fun duplicateName(level: DuplicateLevel) = when (level) {
    DuplicateLevel.CONFIRMED_DUPLICATE -> "确认重复"
    DuplicateLevel.HIGHLY_SUSPECTED -> "高度疑似"
    DuplicateLevel.POSSIBLE_DUPLICATE -> "可能重复"
    DuplicateLevel.NOT_DUPLICATE -> "非重复"
}

private fun sourceName(source: String) = if (source == DataSourceType.CAMPUS_CARD.name) "校园卡" else "银行"
private fun formatCents(cents: Long) = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
private fun formatTime(value: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
