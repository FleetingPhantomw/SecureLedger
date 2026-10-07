package com.secureledger.integration.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SyncInbox {
    private val mutable = MutableStateFlow<String?>(null)
    val pending = mutable.asStateFlow()
    fun offer(encoded: String) { require(encoded.length <= 2_000_000 && (encoded.startsWith("SL1:") || encoded.startsWith("SLS1."))); mutable.value = encoded }
    fun dismiss() { mutable.value = null }
}

/** Standard Android shares keep large signed packages out of multi-QR manual exchanges. */
object SyncFiles {
    fun read(context: Context, uri: Uri): String = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 2_000_000) { "同步文件过大" }
            output.write(buffer, 0, count)
        }
        output.toString("UTF-8").trim()
    } ?: error("无法读取同步文件")
    fun share(context: Context, encoded: String, approval: Boolean = false) {
        val directory = File(context.cacheDir, "sync-share").apply { mkdirs() }
        val file = File(directory, if (approval) "AA结算.slsync" else "账本同步.slsync")
        file.writeText(encoded, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, if (approval) "分享 AA 结算" else "分享账本同步"))
    }
}
