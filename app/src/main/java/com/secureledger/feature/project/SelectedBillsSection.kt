package com.secureledger.feature.project

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.secureledger.core.settlement.*
import com.secureledger.data.entity.ProjectMemberEntity
import com.secureledger.data.repository.DecryptedRecord
import com.secureledger.core.ledger.formatYuan
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SelectedBillsSection(records: List<DecryptedRecord>, members: List<ProjectMemberEntity>, selfId: String,
    onCreate: (Set<String>, Set<String>, (Result<SettlementProof>) -> Unit) -> Unit,
    onCreated: (SettlementProof) -> Unit) {
    val expenses = remember(records) { records.filter { it.payload.recordType == "EXPENSE" }.sortedByDescending { it.payload.transactionTime } }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var participants by remember(members) { mutableStateOf(members.map { it.userId }.toSet()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val validSelected = selected.intersect(expenses.map { it.secure.recordId }.toSet())
    val calculation = remember(expenses, validSelected, participants) { runCatching {
        SelectedBillSplit.calculate(expenses.filter { it.secure.recordId in validSelected }.map {
            SplitBill(it.secure.recordId, it.secure.authorId, it.payload.amountCents)
        }, participants.toList())
    } }
    fun label(id: String) = if (id == selfId) "我" else members.firstOrNull { it.userId == id }?.let { "${it.displayName} · ${id.takeLast(4)}" } ?: id.takeLast(6)
    Text("从账本选择账单", style = MaterialTheme.typography.titleLarge)
    Text("选择本次共同承担的支出，付款人按记账成员计算。金额按分均摊，余下的分按成员编号顺序分配。", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (members.size < 2) Text("先在成员与邀请中加入另一位成员，并同步账本。", color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { selected = expenses.map { it.secure.recordId }.toSet() }, enabled = !busy) { Text("选择全部") }
        TextButton(onClick = { selected = emptySet() }, enabled = !busy) { Text("清空") }
    }
    if (expenses.isEmpty()) Text("这个 AA 账本还没有支出。记账时请选择该账本。")
    expenses.forEach { record ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(record.secure.recordId in validSelected, { checked -> selected = if (checked) selected + record.secure.recordId else selected - record.secure.recordId }, enabled = !busy)
                Column(Modifier.weight(1f)) {
                    Text(record.payload.merchant.ifBlank { record.payload.category }, style = MaterialTheme.typography.titleSmall)
                    Text("${label(record.secure.authorId)} 已付 · ${DateTimeFormatter.ofPattern("MM-dd").format(Instant.ofEpochMilli(record.payload.transactionTime).atZone(ZoneId.systemDefault()))}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatYuan(BigInteger.valueOf(record.payload.amountCents)))
            }
        }
    }
    Text("参与均摊的成员", style = MaterialTheme.typography.titleMedium)
    members.forEach { member ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(member.userId in participants, { checked -> participants = if (checked) participants + member.userId else participants - member.userId }, enabled = !busy)
            Text(label(member.userId))
        }
    }
    calculation.getOrNull()?.let { plan ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${validSelected.size} 笔合计 ${formatYuan(BigInteger.valueOf(plan.totalCents))}", style = MaterialTheme.typography.titleMedium)
                plan.allocations.forEach { member ->
                    Text("${label(member.participantId)} 应承担 ${formatYuan(BigInteger.valueOf(member.owedCents))}")
                    val net = member.paidCents - member.owedCents
                    Text("已付 ${formatYuan(BigInteger.valueOf(member.paidCents))} · ${if (net >= 0) "应收" else "需补"} ${formatYuan(BigInteger.valueOf(kotlin.math.abs(net)))}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (validSelected.isNotEmpty()) calculation.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        busy = true; error = null
        onCreate(validSelected, participants) { result ->
            busy = false
            result.onSuccess(onCreated).onFailure { error = it.message ?: "结算失败" }
        }
    }, enabled = !busy && calculation.isSuccess && selfId in participants, modifier = Modifier.fillMaxWidth()) {
        Text(if (busy) "正在保存…" else "保存本次 AA 结算")
    }
}
