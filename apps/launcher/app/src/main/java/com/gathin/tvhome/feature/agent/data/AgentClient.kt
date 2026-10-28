package com.gathin.tvhome.feature.agent.data

import com.gathin.tvhome.core.util.ConnectionDeadline
import android.content.Context
import com.gathin.tvhome.core.util.EndpointPolicy
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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets


object AgentClient {
    const val ONLINE = 0
    const val OFFLINE = 1
    const val REFUSED = 2
    const val NO_TOKEN = 3

    private const val TOKEN_FILE = "agent_token"
    private const val URL_FILE = "agent_url"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val ANSWER_TIMEOUT_MS = 330_000
    private const val PING_TIMEOUT_MS = 2500
    private const val UNAUTHORISED = 401
    private const val HTTP_OK = 200
    private const val FIRST_ERROR = 400


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
        if (saved.isNotEmpty()) return checkedUrl(saved)

        val override = fileUrl(context)
        if (override != null) return checkedUrl(override)


        val configUrl = context.getString(R.string.agent_url).trim()
        return checkedUrl(configUrl)
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

    private fun checkedUrl(raw: String): String = runCatching {
        EndpointPolicy.validated(cleanUrl(raw)).toString()
    }.getOrDefault("")

    private fun cleanUrl(raw: String): String =
        if (raw.endsWith("/")) raw.substring(0, raw.length - 1) else raw

    @JvmStatic
    fun discover(@Suppress("UNUSED_PARAMETER") context: Context? = null): String? =
        AgentDiscovery.discover()

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
        val tok = token(context) ?: return null
        val base = baseUrl(context)
        return if (base.isEmpty()) null else "$base/#$tok"
    }

    @JvmStatic
    fun ping(context: Context): Int {
        val tok = token(context) ?: return NO_TOKEN

        val base = baseUrl(context)
        if (base.isEmpty()) return OFFLINE

        var connection: HttpURLConnection? = null
        return try {
            connection = open(base + "/v2/suggestions", tok)
            connection.connectTimeout = PING_TIMEOUT_MS
            connection.readTimeout = PING_TIMEOUT_MS
            val code = connection.responseCode
            if (code == HTTP_OK) ONLINE else if (code == UNAUTHORISED || code == 403) REFUSED else OFFLINE
        } catch (_: IOException) {
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
        request: Request,
        onPresentation: ((PresentationResponse) -> Unit)? = null
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
                onPresentation?.invoke(presentation)
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
        EndpointPolicy.validated(address)
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
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
        stream(context, "/v2/ask", event.toJson(), listener, request)
    }

    @JvmStatic
    @Throws(Exception::class)
    internal fun stream(
        context: Context,
        path: String,
        payload: JSONObject,
        listener: V2StreamListener,
        request: Request = Request()
    ) {
        val tok = token(context) ?: throw AgentException(
            R.string.agent_token_missing,
            "no token"
        )
        val base = baseUrl(context)
        if (base.isEmpty()) {
            throw AgentException(R.string.agent_status_offline, "no server url")
        }
        val connection = open(base + path, tok)
        request.attach(connection)
        val deadline = ConnectionDeadline(connection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = ANSWER_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )
            val body = payload.toString()
            connection.outputStream.use { out ->
                out.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            AgentResponseReader.readV2Answer(connection, listener)
        } finally {
            deadline.close()
            connection.disconnect()
        }
    }

    internal fun parseAndDispatchV2Event(line: String, listener: V2StreamListener) =
        AgentEventParser.dispatch(line, listener)
}
