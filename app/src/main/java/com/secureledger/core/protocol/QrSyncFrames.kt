package com.secureledger.core.protocol

import com.secureledger.core.crypto.HashManager

/** QR transport framing around the unchanged signed SL1 package. */
object QrSyncFrames {
    private const val PREFIX = "SLQ1"
    const val MAX_FRAME_CHARS = 900
    const val MAX_FRAMES = 1024
    private const val CHUNK_CHARS = 800

    fun encode(packageText: String): List<String> {
        require(packageText.startsWith(SecureSyncCodec.PREFIX)) { "无效的同步包" }
        if (packageText.length <= MAX_FRAME_CHARS) return listOf(packageText)
        val chunks = packageText.chunked(CHUNK_CHARS)
        require(chunks.size <= MAX_FRAMES) { "账本数据超过二维码分片上限" }
        val hash = HashManager.sha256Hex(packageText.toByteArray(Charsets.UTF_8))
        val id = hash.take(16)
        return chunks.mapIndexed { index, chunk -> "$PREFIX:$id:$index:${chunks.size}:$hash:$chunk" }
    }

    internal fun parse(frame: String): QrSyncFrame {
        require(frame.length <= MAX_FRAME_CHARS) { "二维码分片过长" }
        val parts = frame.split(':', limit = 6)
        require(parts.size == 6 && parts[0] == PREFIX) { "不是 SecureLedger 同步二维码" }
        val id = parts[1]
        val index = parts[2].toIntOrNull() ?: error("分片编号无效")
        val total = parts[3].toIntOrNull() ?: error("分片总数无效")
        val hash = parts[4]
        val chunk = parts[5]
        require(id.matches(Regex("[0-9a-f]{16}")) && hash.matches(Regex("[0-9a-f]{64}")) && id == hash.take(16)) { "分片标识无效" }
        require(total in 2..MAX_FRAMES && index in 0 until total) { "分片顺序无效" }
        require(chunk.length in 1..CHUNK_CHARS && chunk.all { it.isLetterOrDigit() || it in ":-_" }) { "分片内容无效" }
        return QrSyncFrame(id, index, total, hash, chunk)
    }

    fun isFrame(value: String): Boolean = value.startsWith("$PREFIX:")
}

internal data class QrSyncFrame(val transferId: String, val index: Int, val total: Int, val hash: String, val chunk: String)

data class QrAssemblyProgress(val received: Int, val total: Int, val completePackage: String? = null)

/** Accepts out-of-order scans; no import occurs until the full digest matches. */
class QrFrameAssembler {
    private var transferId: String? = null
    private var expectedHash: String? = null
    private var total = 0
    private val chunks = HashMap<Int, String>()

    fun reset() {
        transferId = null
        expectedHash = null
        total = 0
        chunks.clear()
    }

    fun accept(value: String): QrAssemblyProgress {
        val frame = QrSyncFrames.parse(value)
        if (transferId == null) {
            transferId = frame.transferId
            expectedHash = frame.hash
            total = frame.total
        }
        require(transferId == frame.transferId && expectedHash == frame.hash && total == frame.total) { "请扫描同一组同步二维码" }
        require(chunks[frame.index] == null || chunks[frame.index] == frame.chunk) { "重复分片内容不一致" }
        chunks[frame.index] = frame.chunk
        if (chunks.size != total) return QrAssemblyProgress(chunks.size, total)
        val combined = (0 until total).joinToString("") { chunks.getValue(it) }
        require(HashManager.sha256Hex(combined.toByteArray(Charsets.UTF_8)) == expectedHash) { "二维码分片校验失败" }
        require(combined.startsWith(SecureSyncCodec.PREFIX)) { "同步数据格式无效" }
        return QrAssemblyProgress(total, total, combined)
    }
}
