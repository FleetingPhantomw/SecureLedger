package com.secureledger.core.importer

import org.apache.commons.csv.CSVFormat
import java.io.StringReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Parses common Chinese/English bank and campus-card CSV exports through Apache
 * Commons CSV. Column aliases are explicit and failures are reported per row.
 */
class StatementCsvParser(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun parse(text: String, sourceType: DataSourceType): CsvImportResult {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_UTF8_BYTES) { "CSV 文件超过 5 MiB 限制" }
        val cleaned = text.removePrefix("\uFEFF")
        val parser = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get()
            .parse(StringReader(cleaned))
        val headers = parser.headerMap.keys.associateBy(::normalizeHeader)
        require(findHeader(headers, TIME_HEADERS) != null) { "缺少交易时间列" }
        require(findHeader(headers, AMOUNT_HEADERS + DEBIT_HEADERS + CREDIT_HEADERS) != null) { "缺少金额列" }

        val transactions = mutableListOf<ImportedTransaction>()
        val issues = mutableListOf<CsvImportIssue>()
        parser.use { records ->
            records.take(MAX_ROWS + 1).forEach { record ->
                if (transactions.size >= MAX_ROWS) {
                    issues += CsvImportIssue(record.recordNumber, "超过 $MAX_ROWS 行限制，后续内容未导入")
                    return@forEach
                }
                runCatching {
                    val time = parseTime(value(record, headers, TIME_HEADERS))
                    val debit = optionalAmount(valueOrNull(record, headers, DEBIT_HEADERS))
                    val credit = optionalAmount(valueOrNull(record, headers, CREDIT_HEADERS))
                    val generic = optionalAmount(valueOrNull(record, headers, AMOUNT_HEADERS))
                    val directionText = valueOrNull(record, headers, DIRECTION_HEADERS).orEmpty()
                    val direction = when {
                        credit != null && credit > 0 -> TransactionDirection.CREDIT
                        debit != null && debit > 0 -> TransactionDirection.DEBIT
                        directionText.contains("收入") || directionText.contains("贷") ||
                            directionText.contains("credit", ignoreCase = true) -> TransactionDirection.CREDIT
                        else -> TransactionDirection.DEBIT
                    }
                    val cents = credit ?: debit ?: generic ?: error("金额为空")
                    require(cents >= 0) { "金额不能为负数" }
                    val description = valueOrNull(record, headers, DESCRIPTION_HEADERS).orEmpty()
                    val merchant = valueOrNull(record, headers, MERCHANT_HEADERS)
                        ?.ifBlank { null } ?: description.ifBlank { "未识别商户" }
                    val channelDefault = if (sourceType == DataSourceType.BANK) "银行流水" else "校园卡"
                    ImportedTransaction(
                        sourceType = sourceType.name,
                        externalOrderId = valueOrNull(record, headers, ORDER_HEADERS)?.ifBlank { null },
                        amountCents = cents,
                        direction = direction.name,
                        merchant = merchant,
                        transactionTime = time,
                        paymentChannel = valueOrNull(record, headers, CHANNEL_HEADERS)?.ifBlank { channelDefault }
                            ?: channelDefault,
                        accountLast4 = valueOrNull(record, headers, ACCOUNT_HEADERS)?.let(::lastFourDigits),
                        category = valueOrNull(record, headers, CATEGORY_HEADERS)?.ifBlank { defaultCategory(sourceType) }
                            ?: defaultCategory(sourceType),
                        rawDescription = description,
                        sourceRow = record.recordNumber
                    )
                }.onSuccess(transactions::add)
                    .onFailure { issues += CsvImportIssue(record.recordNumber, it.message ?: "无法解析") }
            }
        }
        return CsvImportResult(transactions, issues)
    }

    private fun value(record: org.apache.commons.csv.CSVRecord, headers: Map<String, String>, aliases: Set<String>): String =
        valueOrNull(record, headers, aliases)?.takeIf(String::isNotBlank) ?: error("字段为空")

    private fun valueOrNull(record: org.apache.commons.csv.CSVRecord, headers: Map<String, String>, aliases: Set<String>): String? =
        findHeader(headers, aliases)?.let { header -> if (record.isMapped(header)) record.get(header).trim() else null }

    private fun findHeader(headers: Map<String, String>, aliases: Set<String>): String? =
        aliases.firstNotNullOfOrNull { headers[normalizeHeader(it)] }

    private fun normalizeHeader(value: String): String = value.trim().lowercase()
        .replace(" ", "").replace("_", "").replace("-", "")

    private fun parseTime(value: String): Long {
        val normalized = value.trim().replace('/', '-').replace('T', ' ')
        val parsed = TIME_FORMATS.firstNotNullOfOrNull { formatter ->
            try { LocalDateTime.parse(normalized, formatter) } catch (_: DateTimeParseException) { null }
        } ?: runCatching { LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay() }.getOrNull()
        ?: error("无法识别交易时间：$value")
        return parsed.atZone(zoneId).toInstant().toEpochMilli()
    }

    private fun optionalAmount(value: String?): Long? {
        val cleaned = value?.trim()?.replace(",", "")?.replace("¥", "")?.replace("￥", "")
            ?.removePrefix("+")?.takeIf(String::isNotBlank) ?: return null
        return cleaned.toBigDecimal().abs().multiply(BigDecimal(100)).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }

    private fun lastFourDigits(value: String): String? = value.filter(Char::isDigit).takeLast(4).ifBlank { null }
    private fun defaultCategory(source: DataSourceType) = if (source == DataSourceType.CAMPUS_CARD) "校园卡" else "未分类"

    companion object {
        const val MAX_UTF8_BYTES = 5 * 1024 * 1024
        const val MAX_ROWS = 10_000
        private val TIME_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm"),
            DateTimeFormatter.ofPattern("yyyyMMdd HHmmss")
        )
        private val TIME_HEADERS = setOf("交易时间", "时间", "日期时间", "transactiontime", "datetime", "date")
        private val AMOUNT_HEADERS = setOf("交易金额", "金额", "amount")
        private val DEBIT_HEADERS = setOf("支出金额", "借方发生额", "debit", "expense")
        private val CREDIT_HEADERS = setOf("收入金额", "贷方发生额", "credit", "income")
        private val DIRECTION_HEADERS = setOf("收支类型", "交易方向", "方向", "direction", "type")
        private val MERCHANT_HEADERS = setOf("商户", "交易商户", "对方户名", "地点", "merchant", "counterparty")
        private val DESCRIPTION_HEADERS = setOf("交易摘要", "交易说明", "备注", "描述", "description", "memo")
        private val ORDER_HEADERS = setOf("订单号", "交易流水号", "交易单号", "orderid", "transactionid")
        private val CHANNEL_HEADERS = setOf("支付渠道", "交易渠道", "channel")
        private val ACCOUNT_HEADERS = setOf("卡号", "账号", "卡尾号", "account", "card")
        private val CATEGORY_HEADERS = setOf("分类", "交易类型", "category")
    }
}
