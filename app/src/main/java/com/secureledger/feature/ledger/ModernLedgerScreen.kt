package com.secureledger.feature.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secureledger.core.ledger.*
import com.secureledger.data.repository.DecryptedRecord
import com.secureledger.data.entity.ProjectEntity
import com.secureledger.design.LocalLedgerSemanticColors
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.math.BigInteger

@Composable
fun ModernLedgerScreen(padding: PaddingValues, records: List<DecryptedRecord>, projects: List<ProjectEntity>, onOpen: (DecryptedRecord) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var grouping by rememberSaveable { mutableStateOf(LedgerGrouping.DAY) }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    val month = YearMonth.parse(monthText)
    val zone = ZoneId.systemDefault()
    val scoped = remember(records, projectId) { records.filter { projectId == null || it.secure.projectId == projectId } }
    val monthly = remember(scoped, month, zone) { scoped.filter { YearMonth.from(LedgerAnalytics.date(it.payload.transactionTime, zone)) == month }
        .sortedByDescending { it.payload.transactionTime } }
    val analytics = remember(monthly, month, zone) { LedgerAnalytics.calculate(monthly.map {
        AnalyticsEntry(it.secure.recordId, it.payload.transactionTime, it.payload.recordType, it.payload.amountCents, it.payload.category)
    }, month, zone) }
    val groups = remember(monthly, grouping, zone) { monthly.groupBy { LedgerAnalytics.groupDate(it.payload.transactionTime, grouping, zone) } }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("‹ 上月") }
                Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { monthText = month.plusMonths(1).toString() }) { Text("下月 ›") }
            }
            if (projects.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(projectId == null, { projectId = null }, { Text("全部账本") })
                projects.forEach { project -> FilterChip(projectId == project.projectId, { projectId = project.projectId }, { Text(project.name) }) }
            }
        }
        item { MonthlyCharts(analytics, month) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerGrouping.entries.forEach { mode -> FilterChip(grouping == mode, { grouping = mode }, { Text(mode.label) }) }
            }
        }
        if (monthly.isEmpty()) item {
            Text("这个月还没有记录", style = MaterialTheme.typography.titleMedium)
            Text("切换月份或添加一笔账目，收支图表将同步更新。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        groups.forEach { (date, rows) ->
            item(key = "header-$date") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(when (grouping) {
                        LedgerGrouping.MONTH -> "${date.monthValue}月"
                        LedgerGrouping.WEEK -> "${date.format(DateTimeFormatter.ofPattern("MM/dd"))} — ${date.plusDays(6).format(DateTimeFormatter.ofPattern("MM/dd"))}"
                        LedgerGrouping.DAY -> date.format(DateTimeFormatter.ofPattern("MM月dd日 E", java.util.Locale.CHINA))
                    }, style = MaterialTheme.typography.titleSmall)
                    val expense = rows.filter { it.payload.recordType == "EXPENSE" }.fold(BigInteger.ZERO) { sum, row -> sum + BigInteger.valueOf(row.payload.amountCents) }
                    Text("支出 ${formatYuan(expense)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(rows, key = { it.secure.recordId }) { record ->
                val payload = record.payload
                val semantic = LocalLedgerSemanticColors.current
                Surface(Modifier.fillMaxWidth().clickable { onOpen(record) }, shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(Modifier.size(42.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) { Text(payload.category.take(1).ifBlank { "账" }, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(payload.merchant.ifBlank { payload.category }, style = MaterialTheme.typography.titleSmall)
                            Text("${payload.category} · ${DateTimeFormatter.ofPattern("HH:mm").format(java.time.Instant.ofEpochMilli(payload.transactionTime).atZone(zone))}${if ("time-estimated" in payload.tags) " · 估计时间" else ""}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${if (payload.recordType == "INCOME") "+" else if (payload.recordType == "EXPENSE") "−" else ""}${formatYuan(BigInteger.valueOf(payload.amountCents))}",
                            color = when (payload.recordType) { "INCOME" -> semantic.income; "EXPENSE" -> semantic.expense; else -> MaterialTheme.colorScheme.onSurface }, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyCharts(data: MonthlyAnalytics, month: YearMonth) {
    val colors = listOf(MaterialTheme.colorScheme.primary, Color(0xFF45B79B), Color(0xFFFFB25B), Color(0xFFEF80A4), Color(0xFF69B9EC), Color(0xFFAC92EB), Color(0xFFAAAEBC))
    val semantic = LocalLedgerSemanticColors.current
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("月账单", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth()) {
                listOf(Triple("支出", data.expense, semantic.expense), Triple("收入", data.income, semantic.income)).forEach { (title, total, color) ->
                    Column(Modifier.weight(1f)) {
                        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text(formatYuan(total), color = color, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            Text("结余 ${formatYuan(data.net)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("每日支出", style = MaterialTheme.typography.labelLarge)
            val bar = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = "${month.monthValue}月每日支出柱状图，合计${formatYuan(data.expense)}" }) {
                val maximum = data.dailyExpenses.maxOrNull()?.toDouble()?.coerceAtLeast(1.0) ?: 1.0
                val step = size.width / data.dailyExpenses.size
                data.dailyExpenses.forEachIndexed { index, value ->
                    val height = (value.toDouble() / maximum * size.height).toFloat()
                    drawRoundRect(bar.copy(alpha = if (height > 0) 1f else 0.15f), Offset(index * step + step * 0.18f, size.height - height.coerceAtLeast(3f)),
                        Size(step * 0.64f, height.coerceAtLeast(3f)), androidx.compose.ui.geometry.CornerRadius(3f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("1日", style = MaterialTheme.typography.bodySmall); Text("${month.lengthOfMonth()}日", style = MaterialTheme.typography.bodySmall) }
            if (data.categories.isNotEmpty()) {
                Text("支出分类", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Canvas(Modifier.size(106.dp).semantics { contentDescription = "分类支出占比，详细金额见右侧列表" }) {
                        var start = -90f
                        data.categories.forEachIndexed { index, (_, amount) ->
                            val sweep = (amount.toDouble() / data.expense.toDouble() * 360).toFloat()
                            drawArc(colors[index % colors.size], start, sweep, false, Offset(12f, 12f), Size(size.width - 24f, size.height - 24f), style = Stroke(22f, cap = StrokeCap.Butt))
                            start += sweep
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.categories.forEachIndexed { index, (name, amount) ->
                            Text("● $name  ${formatYuan(amount)}", color = colors[index % colors.size], style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
