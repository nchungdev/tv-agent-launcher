package com.gathin.tvhome.feature.agent.data

import android.os.Handler
import android.os.Looper
import com.gathin.tvhome.feature.smarthome.data.HassClient
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.security.SecureRandom
import com.gathin.tvhome.core.util.EndpointPolicy
import com.gathin.tvhome.core.util.ConnectionDeadline


class LocalPairingServer(
    private val serviceType: ServiceType,
    private val defaultUrl: String,
    private val onPaired: (url: String, token: String, detail: String) -> Unit
) {

    enum class ServiceType { AGENT, HOME_ASSISTANT }

    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(2))
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val consumed = AtomicBoolean(false)
    private val session = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    val fragment: String get() = "#$session"
    private val expiresAt = System.nanoTime() + TimeUnit.MINUTES.toNanos(5)
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var running = false

    val port: Int
        get() = serverSocket?.localPort ?: DEFAULT_PORT

    fun start(): Int {
        if (running) return port
        return try {
            val socket = ServerSocket(DEFAULT_PORT)
            serverSocket = socket
            running = true
            Thread({ acceptLoop(socket) }, "pairing-accept").start()
            main.postDelayed({ stop() }, 300_000L)
            socket.localPort
        } catch (_: Exception) {
            val fallback = ServerSocket(0)
            serverSocket = fallback
            running = true
            Thread({ acceptLoop(fallback) }, "pairing-accept").start()
            main.postDelayed({ stop() }, 300_000L)
            fallback.localPort
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        main.removeCallbacksAndMessages(null)
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        worker.shutdownNow()
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running && !server.isClosed) {
            try {
                val client = server.accept()
                clients.add(client)
                try {
                    worker.execute { handleClient(client) }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    clients.remove(client)
                    client.close()
                }
            } catch (_: Exception) {
                break
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            val request = PairingRequest.read(socket.getInputStream())
            val output = socket.getOutputStream()
            val host = request.headers["host"].orEmpty()
            val expectedHost = "${socket.localAddress.hostAddress}:${socket.localPort}"
            val origin = request.headers["origin"]
            if (host != expectedHost || (origin != null && origin != "http://$host")) {
                sendJsonResponse(output, 403, false, "Không được phép")
                return
            }
            if (request.method == "POST" && request.path == "/save") {
                if (!running || System.nanoTime() > expiresAt ||
                    request.headers["x-pairing-code"] != session || consumed.get()) {
                    sendJsonResponse(output, 403, false, "Phiên ghép nối không hợp lệ")
                    return
                }
                handleSave(output, request.body)
            } else if (request.method == "GET" && request.path == "/") {
                PairingPage.send(output, serviceType == ServiceType.AGENT, defaultUrl)
            } else sendJsonResponse(output, 404, false, "Không tìm thấy")
        } catch (_: Exception) {
        } finally {
            clients.remove(socket)
            runCatching { socket.close() }
        }
    }

    private fun handleSave(output: OutputStream, jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val rawUrl = json.optString("url", "").trim()
            val rawToken = json.optString("token", "").trim()

            if (rawUrl.isEmpty() || rawToken.isEmpty()) {
                sendJsonResponse(output, 400, false, "Vui lòng nhập đầy đủ URL và Token")
                return
            }

            require(rawToken.length <= 8192 && rawToken.none { it == '\r' || it == '\n' })
            EndpointPolicy.validated(rawUrl)
            if (serviceType == ServiceType.AGENT) {
                verifyAgent(output, rawUrl, rawToken)
            } else {
                verifyHomeAssistant(output, rawUrl, rawToken)
            }
        } catch (error: Exception) {
            sendJsonResponse(output, 500, false, error.message ?: "Lỗi xử lý yêu cầu")
        }
    }

    private fun verifyAgent(output: OutputStream, rawUrl: String, rawToken: String) {
        val normalized = if (rawUrl.endsWith("/")) rawUrl.dropLast(1) else rawUrl
        val startTime = System.currentTimeMillis()
        val connection = URL("$normalized/v2/suggestions").openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.setRequestProperty("Authorization", "Bearer $rawToken")
        val deadline = ConnectionDeadline(connection, 10)
        val code = try { connection.responseCode } finally {
            deadline.close()
            connection.disconnect()
        }
        val latency = System.currentTimeMillis() - startTime

        if (code == 200) {
            val detail = "$normalized · 200 OK (${latency}ms)"
            sendJsonResponse(output, 200, true, "Kết nối Agent thành công!")
            deliver(normalized, rawToken, detail)
        } else if (code == 401) {
            sendJsonResponse(output, 401, false, "Bearer Token sai hoặc không hợp lệ (401)")
        } else {
            sendJsonResponse(output, code, false, "Máy chủ phản hồi mã lỗi HTTP $code")
        }
    }

    private fun verifyHomeAssistant(output: OutputStream, rawUrl: String, rawSecret: String) {
        val normalized = HassClient.normalize(rawUrl)
        val client = HassClient(normalized, rawSecret)
        val count = client.use {
            it.verify()
            it.states().size
        }
        val detail = "$normalized · 200 OK (${count} entity)"
        sendJsonResponse(output, 200, true, "Đã kết nối Home Assistant ($count entity)!")
        deliver(normalized, rawSecret, detail)
    }

    private fun deliver(url: String, token: String, detail: String) {
        if (!consumed.compareAndSet(false, true)) return
        main.post {
            if (!running || System.nanoTime() > expiresAt) return@post
            stop()
            onPaired(url, token, detail)
        }
    }

    private fun sendJsonResponse(output: OutputStream, status: Int, ok: Boolean, message: String) {
        val json = JSONObject().apply {
            put("ok", ok)
            put("message", message)
        }
        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $status OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }


    companion object {
        private const val DEFAULT_PORT = 8791

        @JvmStatic
        fun getLocalIp(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
                for (iface in interfaces) {
                    if (iface.isLoopback || !iface.isUp) continue
                    for (address in iface.inetAddresses) {
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            val ip = address.hostAddress ?: ""
                            if (ip.isNotEmpty() && !ip.startsWith("127.")) return ip
                        }
                    }
                }
            } catch (_: Exception) {
            }
            return "127.0.0.1"
        }
    }
}
