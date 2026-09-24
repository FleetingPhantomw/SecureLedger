package com.secureledger.feature.importer

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
fun ReconciliationScreen(
    entries: List<ImportedTransactionView>,
    operationMessage: String?,
    onBack: () -> Unit,
    onSupplement: (String) -> Unit,
    onIgnore: (String) -> Unit,
    onRetain: (String) -> Unit
) {
    var filter by remember { mutableStateOf<ReconciliationStatus?>(null) }
    val visible = entries.filter {
        filter == null || if (filter == ReconciliationStatus.DUPLICATE_REVIEW) it.duplicateLevel != DuplicateLevel.NOT_DUPLICATE
        else it.status == filter
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("历史导入记录") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            operationMessage?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary) }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(filter == null, { filter = null }, { Text("全部") })
                FilterChip(filter == ReconciliationStatus.MISSING, { filter = ReconciliationStatus.MISSING }, { Text("漏记") })
                FilterChip(filter == ReconciliationStatus.CONFLICT, { filter = ReconciliationStatus.CONFLICT }, { Text("冲突") })
                FilterChip(filter == ReconciliationStatus.DUPLICATE_REVIEW, { filter = ReconciliationStatus.DUPLICATE_REVIEW }, { Text("重复（${entries.count { it.duplicateLevel != DuplicateLevel.NOT_DUPLICATE }}）") })
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
                            onIgnore = { onIgnore(entry.importedId) },
                            onRetain = { onRetain(entry.importedId) }
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
    onIgnore: () -> Unit = {},
    onRetain: () -> Unit = {}
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
                    if (entry.status == ReconciliationStatus.DUPLICATE_REVIEW) {
                        Button(onClick = onRetain) { Text("保留流水") }
                        TextButton(onClick = onIgnore) { Text("忽略流水") }
                    } else {
                        Button(onClick = onSupplement) { Text("确认补记") }
                        TextButton(onClick = onIgnore) { Text("忽略") }
                    }
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
    ReconciliationStatus.RETAINED -> "已保留"
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
