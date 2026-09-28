package com.secureledger.core.ocr

import com.secureledger.core.importer.ImportedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Coordinates keep list rows separate even when OCR emits merchant and amount as different lines. */
data class OcrObservation(val text: String, val x: Float, val y: Float, val confidence: Float = 1f)

data class StatementCandidate(val transaction: ImportedTransaction, val warnings: List<String>)

/** Conservative parser for bank-list screenshots; every result remains a reviewable external import. */
class StatementParser(private val zoneId: ZoneId = ZoneId.systemDefault()) {
    private val amount = Regex("^([+-])?\\s*[¥￥]?\\s*([+-])?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)$")
    private val fullDate = Regex("(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})")
    private val monthDate = Regex("(\\d{1,2})月[/](20\\d{2})")
    private val clock = Regex("(\\d{1,2}:\\d{2}(?::\\d{2})?)")
    private val fullTimestamp = Regex("20\\d{2}-\\d{1,2}-\\d{1,2}\\s*\\d{1,2}:\\d{2}:\\d{2}")

    fun parse(observations: List<OcrObservation>, now: Long = System.currentTimeMillis()): List<StatementCandidate> {
        val lines = observations.filter { it.text.isNotBlank() && it.confidence >= 0.25f }.sortedBy { it.y }
        val allText = lines.joinToString(" ") { it.text }
        val bank = when {
            "工银借记卡" in allText || "贷记卡" in allText && "查询明细" in allText -> "ICBC"
            "交易明细查询" in allText -> "CCB"
            "账户明细" in allText && "信用卡" in allText -> "CCB_NEW"
            "人民币元" in allText && "长城" in allText -> "BOC"
            "收支分析" in allText && "全部交易" in allText -> "CITIC"
            else -> return emptyList()
        }
        val maxX = lines.maxOfOrNull { it.x } ?: return emptyList()
        val monthFromPage = lines.firstNotNullOfOrNull { monthDate.find(it.text)?.let { match ->
            match.groupValues[2].toInt() to match.groupValues[1].toInt()
        } }
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        return lines.mapIndexedNotNull { index, line ->
            if (line.x < maxX * 0.67f || line.text.contains("余额") || line.text.contains("总支出") || line.text.contains("总收入")) return@mapIndexedNotNull null
            val candidateAmount = if (bank == "BOC") line.text.replace("人民币元", "").trim() else line.text.trim()
            val match = amount.matchEntire(candidateAmount) ?: return@mapIndexedNotNull null
            val cents = runCatching { BigDecimal(match.groupValues[3].replace(",", "")).movePointRight(2).longValueExact() }.getOrNull()
                ?: return@mapIndexedNotNull null
            if (cents <= 0 || cents > 100_000_000_00L) return@mapIndexedNotNull null
            if (bank != "CCB" && bank != "CCB_NEW" && match.groupValues[1].isEmpty() && match.groupValues[2].isEmpty() && bank != "BOC") return@mapIndexedNotNull null
            val previous = lines.take(index)
            val nearby = lines.filter { it.y >= line.y - (if (bank == "BOC") 70 else 22) && it.y <= line.y + 25 && it.x < maxX * 0.65f }
            val merchant = nearby.filterNot { isNoise(it.text) }.maxByOrNull { it.x }?.text?.trim().orEmpty()
            if (merchant.isBlank()) return@mapIndexedNotNull null
            val details = lines.filter { it.y > line.y && it.y < line.y + 120 && it.x < maxX * 0.65f }
            val date = when (bank) {
                "CCB", "CCB_NEW" -> previous.asReversed().firstNotNullOfOrNull { fullDate.find(it.text)?.let(::dateOf) }
                "BOC" -> {
                    val day = previous.lastOrNull { it.x < maxX * 0.12f && it.text.trim().matches(Regex("\\d{1,2}")) }
                    val (year, month) = monthFromPage ?: (today.year to today.monthValue)
                    day?.text?.trim()?.toIntOrNull()?.let { runCatching { LocalDate.of(year, month, it) }.getOrNull() }
                }
                "ICBC" -> {
                    val day = (previous + nearby).lastOrNull { it.x < maxX * 0.12f && it.text.trim().matches(Regex("\\d{1,2}")) }
                    day?.text?.trim()?.toIntOrNull()?.let { dayNumber ->
                        runCatching { LocalDate.of(today.year, today.monthValue, dayNumber) }.getOrNull()
                    }
                }
                else -> details.firstNotNullOfOrNull { fullDate.find(it.text)?.let(::dateOf) }
            }
            val time = details.firstNotNullOfOrNull { clock.find(it.text)?.groupValues?.get(1)?.let { value ->
                runCatching { LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm[:ss]")) }.getOrNull()
            } } ?: LocalTime.NOON
            val timestamp = date?.atTime(time)?.atZone(zoneId)?.toInstant()?.toEpochMilli() ?: return@mapIndexedNotNull null
            val direction = if (match.groupValues[1] == "-" || match.groupValues[2] == "-") "DEBIT" else "CREDIT"
            val account = details.firstNotNullOfOrNull { Regex("(?:借记卡|贷记卡|信用卡)\\s*(\\d{4})|\\*{2,}\\s*(\\d{4})")
                .find(it.text)?.groupValues?.drop(1)?.firstOrNull(String::isNotBlank) }
            val description = (listOf(merchant) + details.map { it.text }).joinToString(" ").take(500)
            StatementCandidate(
                ImportedTransaction(
                    sourceType = "BANK", amountCents = cents, direction = direction,
                    merchant = merchant, transactionTime = timestamp, paymentChannel = "银行卡",
                    accountLast4 = account, category = if (direction == "CREDIT") "收入" else "未分类",
                    rawDescription = description, sourceRow = index.toLong()
                ),
                buildList {
                    if (bank == "ICBC") add("年月根据当前日期推断，请核对")
                    if (bank == "BOC" || bank == "CCB" || bank == "CCB_NEW" && time == LocalTime.NOON) add("未显示交易时分，暂按中午记录")
                    if (match.groupValues[1].isEmpty() && match.groupValues[2].isEmpty()) add("金额无符号，按收入预设，请核对方向")
                    if (line.confidence < 0.7f) add("金额 OCR 置信度较低")
                }
            )
        }
    }

    private fun dateOf(match: MatchResult): LocalDate? = runCatching {
        LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }.getOrNull()

    private fun isNoise(text: String): Boolean = text.contains("余额") || text.contains("人民币") ||
        text.contains("周") || text.contains("借记卡") || text.contains("贷记卡") ||
        text.contains("总支") || text.contains("总收") || fullDate.containsMatchIn(text) ||
        fullTimestamp.containsMatchIn(text) || text.trim().matches(Regex("\\d{1,2}")) ||
        amount.matches(text.trim()) || text.trim().startsWith("￥") || text.trim().startsWith("¥")
}
