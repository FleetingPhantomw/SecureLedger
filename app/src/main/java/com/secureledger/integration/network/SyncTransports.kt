package com.secureledger.integration.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.net.URL
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LanShareSession(val url: String, val token: String, val projectId: String)

/** Serves one already signed/encrypted sync package on the local network. */
class LanSyncController(private val context: Context) {
    private var server: PackageServer? = null
    private var registration: NsdManager.RegistrationListener? = null

    @Synchronized
    fun start(projectId: String, encodedPackage: String): LanShareSession {
        stop()
        require(encodedPackage.length <= MAX_PACKAGE_CHARS) { "同步包过大" }
        val token = ByteArray(24).also(SecureRandom()::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val packageServer = PackageServer(encodedPackage, token).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        server = packageServer
        advertise(projectId, packageServer.listeningPort)
        val host = localIpv4() ?: "127.0.0.1"
        return LanShareSession("http://$host:${packageServer.listeningPort}/v1/package", token, projectId)
    }

    @Synchronized
    fun stop() {
        registration?.let { listener ->
            runCatching { (context.getSystemService(Context.NSD_SERVICE) as NsdManager).unregisterService(listener) }
        }
        registration = null
        server?.stop()
        server = null
    }

    suspend fun fetch(url: String, token: String): String = withContext(Dispatchers.IO) {
        val parsed = URL(url)
        require(parsed.protocol == "http") { "局域网地址必须使用 http" }
        require(isPrivateAddress(parsed.host)) { "仅允许私有局域网地址" }
        val connection = parsed.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("X-SecureLedger-Token", token)
            require(connection.responseCode == 200) { "局域网端返回 HTTP ${connection.responseCode}" }
            connection.inputStream.use { readBounded(it) }
        } finally { connection.disconnect() }
    }

    private fun advertise(projectId: String, port: Int) {
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        val info = NsdServiceInfo().apply {
            serviceName = "SecureLedger-${projectId.takeLast(8)}"
            serviceType = "_secureledger._tcp."
            setPort(port)
        }
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener); registration = listener }
    }

    private class PackageServer(private val payload: String, private val token: String) : NanoHTTPD(0) {
        override fun serve(session: IHTTPSession): Response {
            if (session.method != Method.GET || session.uri != "/v1/package") {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
            if (session.headers["x-secureledger-token"] != token) {
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
            }
            return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", payload)
        }
    }

    private fun localIpv4(): String? = Collections.list(NetworkInterface.getNetworkInterfaces()).asSequence()
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .flatMap { Collections.list(it.inetAddresses).asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress

    companion object {
        const val MAX_PACKAGE_CHARS = 2_000_000
        internal fun isPrivateAddress(host: String): Boolean = runCatching {
            val address = java.net.InetAddress.getByName(host)
            address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress
        }.getOrDefault(false)

        internal fun readBounded(input: java.io.InputStream): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                require(output.size() <= MAX_PACKAGE_CHARS) { "同步响应过大" }
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}

/** HTTPS adapter for a half-trusted relay that stores opaque SL1 packages only. */
class RelaySyncClient {
    suspend fun upload(endpoint: String, bearerToken: String, encodedPackage: String): String = withContext(Dispatchers.IO) {
        require(encodedPackage.length <= LanSyncController.MAX_PACKAGE_CHARS) { "同步包过大" }
        request(endpoint, bearerToken, "POST", encodedPackage)
    }

    suspend fun download(endpoint: String, bearerToken: String): String = withContext(Dispatchers.IO) {
        request(endpoint, bearerToken, "GET", null)
    }

    private fun request(endpoint: String, bearerToken: String, method: String, body: String?): String {
        val uri = URI(endpoint)
        require(uri.scheme == "https" || (uri.scheme == "http" && (uri.host == "127.0.0.1" || uri.host == "localhost"))) {
            "中继地址必须使用 HTTPS"
        }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "text/plain")
            if (bearerToken.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            require(status in 200..299) { "中继端返回 HTTP $status" }
            connection.inputStream.use(LanSyncController::readBounded)
        } finally { connection.disconnect() }
    }
}
