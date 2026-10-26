package com.gathin.tvhome.feature.agent.data

import android.content.Context
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.data.AgentContractJsonHelper
import com.gathin.tvhome.feature.agent.data.InputEvent
import com.gathin.tvhome.feature.agent.data.PresentationResponse
import com.gathin.tvhome.feature.agent.coordinator.AgentActionExecutor
import com.gathin.tvhome.feature.settings.data.AgentHomeConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.nio.charset.StandardCharsets


object AgentClient {
    const val ONLINE = 0
    const val OFFLINE = 1
    const val REFUSED = 2
    const val NO_TOKEN = 3

    private const val TOKEN_FILE = "agent_token"
    private const val URL_FILE = "agent_url"
    private const val DISCOVERY_PROBE = "TV_AGENT_DISCOVER"
    private const val DISCOVERY_PORT = 8789
    private const val DISCOVERY_TIMEOUT_MS = 1200
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val ANSWER_TIMEOUT_MS = 330_000
    private const val PING_TIMEOUT_MS = 2500
    private const val UNAUTHORISED = 401
    private const val HTTP_OK = 200
    private const val FIRST_ERROR = 400

    @Volatile
    private var discoveredBaseUrl: String? = null

    fun interface Listener {
        fun onChunk(text: String)
    }

    interface V2StreamListener {
        fun onAccepted(requestId: String, sessionId: String) {}
        fun onProgress(stage: String) {}
        fun onPresentation(presentation: PresentationResponse) {}
        fun onClientAction(action: Map<String, Any?>) {}
        fun onCompleted(requestId: String, status: String) {}
        fun onError(code: String, message: String) {}
    }

    class AgentException(@JvmField val messageRes: Int, detail: String) : IOException(detail)

    @JvmStatic
    fun baseUrl(context: Context): String {
        val saved = AgentHomeConfig(context).agentUrl
        if (saved.isNotEmpty()) return cleanUrl(saved)

        val override = fileUrl(context)
        if (override != null) return override

        discoveredBaseUrl?.let { return it }

        val configUrl = context.getString(R.string.agent_url).trim()
        return cleanUrl(configUrl)
    }

    private fun fileUrl(context: Context): String? {
        val folder = context.getExternalFilesDir(null) ?: return null
        val file = File(folder, URL_FILE)
        if (!file.isFile) return null

        return try {
            BufferedReader(FileReader(file)).use { reader ->
                val line = reader.readLine()
                if (line.isNullOrBlank()) null else cleanUrl(line.trim())
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun cleanUrl(raw: String): String =
        if (raw.endsWith("/")) raw.substring(0, raw.length - 1) else raw

    @JvmStatic
    fun discover(@Suppress("UNUSED_PARAMETER") context: Context? = null) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = DISCOVERY_TIMEOUT_MS
                val probe = DISCOVERY_PROBE.toByteArray(StandardCharsets.UTF_8)
                val broadcast = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(probe, probe.size, broadcast, DISCOVERY_PORT)
                socket.send(packet)

                val buffer = ByteArray(256)
                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)

                val text = String(reply.data, 0, reply.length, StandardCharsets.UTF_8)
                val json = JSONObject(text)
                val port = json.optInt("port", 8788)
                val host = reply.address.hostAddress
                discoveredBaseUrl = "http://$host:$port"
            }
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun token(context: Context): String? {
        val config = AgentHomeConfig(context)
        val configured = config.agentToken.trim()
        if (configured.isNotEmpty()) return configured

        val folder = context.getExternalFilesDir(null) ?: return null
        val file = File(folder, TOKEN_FILE)
        if (!file.isFile) return null

        return try {
            val legacy = BufferedReader(FileReader(file)).use { reader ->
                val line = reader.readLine()
                if (line.isNullOrBlank()) null else line.trim()
            }
            if (!legacy.isNullOrEmpty()) {
                config.agentToken = legacy
                file.delete()
            }
            legacy
        } catch (_: IOException) {
            null
        }
    }

    @JvmStatic
    fun phoneUrl(context: Context): String? {
        val tok = token(context)
        return if (tok == null) null else baseUrl(context) + "/#" + tok
    }

    @JvmStatic
    fun ping(context: Context): Int {
        val tok = token(context) ?: return NO_TOKEN

        if (discoveredBaseUrl == null && fileUrl(context) == null) {
            discover(context)
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = open(baseUrl(context) + "/health", tok)
            connection.connectTimeout = PING_TIMEOUT_MS
            connection.readTimeout = PING_TIMEOUT_MS
            val code = connection.responseCode
            if (code == HTTP_OK) ONLINE else if (code == UNAUTHORISED) REFUSED else OFFLINE
        } catch (_: IOException) {
            if (discoveredBaseUrl != null && fileUrl(context) == null) {
                discoveredBaseUrl = null
            }
            OFFLINE
        } finally {
            connection?.disconnect()
        }
    }

    class Request {
        @Volatile
        private var connection: HttpURLConnection? = null

        @Volatile
        private var cancelled = false

        fun cancel() {
            cancelled = true
            connection?.disconnect()
        }

        @Throws(IOException::class)
        fun attach(active: HttpURLConnection) {
            connection = active
            if (cancelled) {
                active.disconnect()
                throw IOException("Request cancelled")
            }
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun ask(context: Context, session: String, text: String, listener: Listener) {
        ask(context, session, text, listener, Request())
    }

    @JvmStatic
    @Throws(Exception::class)
    fun ask(
        context: Context,
        session: String,
        text: String,
        listener: Listener,
        request: Request
    ) {
        val event = InputEvent(
            requestId = "req-" + System.currentTimeMillis(),
            sessionId = session,
            text = text,
            locale = "vi-VN",
            inputType = "voice"
        )
        askV2(context, event, object : V2StreamListener {
            override fun onPresentation(presentation: PresentationResponse) {
                if (presentation.speechText.isNotEmpty()) {
                    listener.onChunk(presentation.speechText)
                }
            }

            override fun onClientAction(action: Map<String, Any?>) {
                val stepId = action["step_id"] as? String ?: ""
                val actionType = action["action_type"] as? String ?: ""
                val rawParams = action["parameters"]
                val params = (rawParams as? Map<*, *>)?.entries?.associate {
                    (it.key?.toString() ?: "") to it.value
                } ?: emptyMap()
                AgentActionExecutor.execute(context, stepId, actionType, params)
            }

            override fun onError(code: String, message: String) {
                val res = if (code == "unauthorized") {
                    R.string.agent_unauthorised
                } else {
                    R.string.agent_status_offline
                }
                throw AgentException(res, message)
            }
        }, request)
    }

    private fun open(address: String, token: String): HttpURLConnection {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $token")
        return connection
    }

    @JvmStatic
    @Throws(Exception::class)
    fun askV2(
        context: Context,
        event: InputEvent,
        listener: V2StreamListener,
        request: Request = Request()
    ) {
        val tok = token(context) ?: throw AgentException(
            R.string.agent_token_missing,
            "no token"
        )
        val connection = open(baseUrl(context) + "/v2/ask", tok)
        request.attach(connection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = ANSWER_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )
            val body = event.toJson().toString()
            connection.outputStream.use { out ->
                out.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            readV2Answer(connection, listener)
        } finally {
            connection.disconnect()
        }
    }

    private fun readV2Answer(connection: HttpURLConnection, listener: V2StreamListener) {
        val code = connection.responseCode
        if (code == UNAUTHORISED) {
            throw AgentException(R.string.agent_unauthorised, "401")
        }
        val failed = code >= FIRST_ERROR
        val stream = if (failed) connection.errorStream else connection.inputStream
        if (failed) {
            val failure = StringBuilder()
            InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                val buffer = CharArray(1024)
                var length: Int
                while (reader.read(buffer).also { length = it } >= 0) {
                    failure.append(buffer, 0, length)
                }
            }
            val detail = if (failure.isNotEmpty()) failure.toString() else "HTTP $code"
            throw IOException(detail)
        }
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim() ?: continue
                if (trimmed.isEmpty()) continue
                parseAndDispatchV2Event(trimmed, listener)
            }
        }
    }

    internal fun parseAndDispatchV2Event(line: String, listener: V2StreamListener) {
        val obj = JSONObject(line)
        val eventType = obj.optString("type", "")
        val payload = obj.optJSONObject("payload") ?: JSONObject()
        when (eventType) {
            "accepted" -> {
                listener.onAccepted(
                    payload.optString("request_id"),
                    payload.optString("session_id")
                )
            }
            "progress" -> {
                listener.onProgress(payload.optString("stage"))
            }
            "presentation" -> {
                val presentation = PresentationResponse.fromJson(payload)
                listener.onPresentation(presentation)
            }
            "client_action" -> {
                listener.onClientAction(AgentContractJsonHelper.jsonObjectToMap(payload))
            }
            "completed" -> {
                listener.onCompleted(
                    payload.optString("request_id"),
                    payload.optString("status")
                )
            }
            "error" -> {
                listener.onError(
                    payload.optString("code", "pipeline_error"),
                    payload.optString("message", "Unknown error")
                )
            }
        }
    }
}
