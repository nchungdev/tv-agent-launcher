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
import java.util.concurrent.Executors


class LocalPairingServer(
    private val serviceType: ServiceType,
    private val defaultUrl: String,
    private val onPaired: (url: String, token: String, detail: String) -> Unit
) {

    enum class ServiceType { AGENT, HOME_ASSISTANT }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newCachedThreadPool()
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
            worker.execute { acceptLoop(socket) }
            socket.localPort
        } catch (_: Exception) {
            val fallback = ServerSocket(0)
            serverSocket = fallback
            running = true
            worker.execute { acceptLoop(fallback) }
            fallback.localPort
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running && !server.isClosed) {
            try {
                val client = server.accept()
                worker.execute { handleClient(client) }
            } catch (_: Exception) {
                break
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val line = reader.readLine() ?: return
            val parts = line.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val path = parts.getOrNull(1) ?: "/"

            var contentLength = 0
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
                if (header.lowercase().startsWith("content-length:")) {
                    contentLength = header.substring(15).trim().toIntOrNull() ?: 0
                }
            }

            if (method == "POST" && path == "/save") {
                val body = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = reader.read(body, read, contentLength - read)
                    if (count <= 0) break
                    read += count
                }
                handleSave(socket.getOutputStream(), String(body))
            } else {
                sendHtml(socket.getOutputStream())
            }
        } catch (_: Exception) {
        } finally {
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
        val connection = URL("$normalized/health").openConnection() as HttpURLConnection
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.setRequestProperty("Authorization", "Bearer $rawToken")
        val code = connection.responseCode
        val latency = System.currentTimeMillis() - startTime
        connection.disconnect()

        if (code == 200) {
            val detail = "$normalized · 200 OK (${latency}ms)"
            sendJsonResponse(output, 200, true, "Kết nối Agent thành công!")
            main.post { onPaired(normalized, rawToken, detail) }
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
        main.post { onPaired(normalized, rawSecret, detail) }
    }

    private fun sendJsonResponse(output: OutputStream, status: Int, ok: Boolean, message: String) {
        val json = JSONObject().apply {
            put("ok", ok)
            put("message", message)
        }
        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $status OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendHtml(output: OutputStream) {
        val isAgent = serviceType == ServiceType.AGENT
        val title = if (isAgent) "Kết nối Duinch Agent" else "Kết nối Home Assistant"
        val tokenLabel = if (isAgent) "Access Token (Bearer)" else "Long-Lived Access Token"
        val hint = if (isAgent) {
            "Mở giao diện Web Home Sentinel -> tab Authen Token để tạo hoặc sao chép token."
        } else {
            "Mở Home Assistant -> Profile -> Security -> Tạo Long-Lived Access Token."
        }
        val accentColor = if (isAgent) "#9A82DB" else "#03A9F4"

        val html = """
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<title>$title</title>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, sans-serif; }
body { background: #121214; color: #fff; display: flex; justify-content: center; padding: 24px 16px; }
.card { background: #1E1E24; border-radius: 20px; max-width: 440px; width: 100%; padding: 24px; box-shadow: 0 8px 30px rgba(0,0,0,0.5); }
h1 { font-size: 20px; font-weight: 700; margin-bottom: 6px; }
p.sub { font-size: 13px; color: #a1a1aa; margin-bottom: 20px; }
label { display: block; font-size: 13px; font-weight: 600; color: #e4e4e7; margin-bottom: 6px; }
input { width: 100%; height: 46px; background: #27272e; border: 1px solid #3f3f46; border-radius: 12px; padding: 0 14px; color: #fff; font-size: 14px; margin-bottom: 14px; outline: none; }
input:focus { border-color: $accentColor; }
.hint { font-size: 12px; color: #71717a; margin-top: -8px; margin-bottom: 18px; line-height: 1.4; }
button { width: 100%; height: 48px; background: $accentColor; border: none; border-radius: 12px; color: #000; font-size: 15px; font-weight: 700; cursor: pointer; transition: 0.2s; }
button:disabled { opacity: 0.5; }
#status { margin-top: 16px; font-size: 13px; text-align: center; line-height: 1.4; display: none; }
.success { color: #4ade80; }
.error { color: #f87171; }
</style>
</head>
<body>
<div class="card">
<h1>$title</h1>
<p class="sub">Nhập thông tin bên dưới để kết nối trực tiếp tới TV</p>
<label>URL Máy chủ</label>
<input id="url" type="url" value="$defaultUrl" placeholder="http://192.168.1.x:8788">
<label>$tokenLabel</label>
<input id="token" type="text" placeholder="Dán token vào đây">
<div class="hint">$hint</div>
<button id="btn" onclick="save()">Xác thực & Kết nối tới TV</button>
<div id="status"></div>
</div>
<script>
async function save() {
  const url = document.getElementById('url').value.trim();
  const token = document.getElementById('token').value.trim();
  const btn = document.getElementById('btn');
  const status = document.getElementById('status');
  if (!url || !token) {
    status.className = 'error'; status.style.display = 'block';
    status.innerText = 'Vui lòng nhập đầy đủ URL và Token'; return;
  }
  btn.disabled = true; btn.innerText = 'Đang xác thực…';
  status.style.display = 'none';
  try {
    const res = await fetch('/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url, token })
    });
    const data = await res.json();
    status.style.display = 'block';
    if (data.ok) {
      status.className = 'success';
      status.innerHTML = '🎉 ' + data.message + '<br>Màn hình TV đã được cập nhật!';
      btn.innerText = 'Đã kết nối thành công';
    } else {
      status.className = 'error';
      status.innerText = 'Lỗi: ' + data.message;
      btn.disabled = false; btn.innerText = 'Thử lại';
    }
  } catch (err) {
    status.className = 'error'; status.style.display = 'block';
    status.innerText = 'Không kết nối được tới TV: ' + err.message;
    btn.disabled = false; btn.innerText = 'Thử lại';
  }
}
</script>
</body>
</html>
        """.trimIndent()

        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
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
