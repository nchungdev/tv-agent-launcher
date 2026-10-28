package com.gathin.tvhome.feature.agent.data

import com.gathin.tvhome.core.util.ConnectionDeadline
import android.content.Context
import com.gathin.tvhome.core.util.BoundedInput
import com.gathin.tvhome.core.util.EndpointPolicy
import com.gathin.tvhome.R
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object FlowClient {

    interface FlowListener {
        fun onPlanReady(plan: FlowPlan) {}
        fun onTaskStarted(task: FlowTask) {}
        fun onTaskResult(result: FlowResult) {}
        fun onNeedsInput(question: String) {}
        fun onCompleted(status: String) {}
        fun onError(code: String, message: String) {}
        fun onLegacyFallback() {}
    }

    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 120_000
    private const val MAX_LINE_CHARS = 65536

    @JvmStatic
    fun execute(
        context: Context,
        input: FlowInput,
        listener: FlowListener,
        request: AgentClient.Request = AgentClient.Request()
    ) {
        val base = AgentClient.baseUrl(context)
        if (base.isEmpty()) {
            listener.onError("no_server", context.getString(R.string.agent_status_offline))
            return
        }
        val token = AgentClient.token(context) ?: run {
            listener.onError("no_token", context.getString(R.string.agent_token_missing))
            return
        }

        val endpoint = "$base/v2/flow"
        val connection = (URL(EndpointPolicy.validated(endpoint).toString()).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val deadline = ConnectionDeadline(connection)
        try {
            request.attach(connection)
            val body = input.toJson().toString()
            connection.outputStream.use { out ->
                out.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val code = connection.responseCode
            if (code == 404 || code == 405) {
                listener.onLegacyFallback()
                return
            }
            if (code == 401 || code == 403) {
                listener.onError("unauthorized", context.getString(R.string.agent_unauthorised))
                return
            }
            if (code >= 400) {
                val err = connection.errorStream?.reader()?.use { readLimited(it) } ?: ""
                listener.onError("http_$code", "Máy chủ phản hồi lỗi $code: $err")
                return
            }

            val contentType = connection.contentType.orEmpty()
            if (contentType.contains("application/json")) {
                val text = connection.inputStream.reader(StandardCharsets.UTF_8).use { readLimited(it) }
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (json?.optString("status") == "legacy_route") {
                    listener.onLegacyFallback()
                    return
                }
                throw IOException("Unexpected flow JSON")
            }

            val state = FlowSearchState()
            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                var total = 0
                val started = System.nanoTime()
                var line: String?
                while (BoundedInput.line(reader).also { line = it } != null) {
                    total += line!!.length
                    if (total > BoundedInput.MAX_STREAM ||
                        System.nanoTime() - started > 120_000_000_000L) {
                        throw java.io.IOException("Response budget exceeded")
                    }
                    val raw = line!!.trim()
                    if (raw.isEmpty()) continue
                    if (raw.length > MAX_LINE_CHARS) continue

                    val json = runCatching { JSONObject(raw) }.getOrNull() ?: continue
                    val event = FlowParser.parseEvent(json)
                    if (!state.accept(event)) continue

                    when (event.type) {
                        "plan_ready" -> {
                            val plan = event.plan ?: FlowPlan()
                            if (plan.understanding.needsInput) {
                                listener.onNeedsInput(plan.understanding.question)
                            }
                            listener.onPlanReady(plan)
                        }
                        "task_started" -> {
                            event.task?.let { listener.onTaskStarted(it) }
                        }
                        "task_result" -> {
                            event.result?.let { listener.onTaskResult(it) }
                        }
                        "response_completed" -> {
                            listener.onCompleted(event.status.ifBlank { "completed" })
                            break
                        }
                    }
                }
            }
            if (!state.finished) throw IOException("Flow ended before completion")
        } catch (e: Exception) {
            listener.onError("network_error", e.message ?: "Lỗi kết nối flow (${e.javaClass.simpleName})")
        } finally {
            deadline.close()
            connection.disconnect()
        }
    }

    private fun readLimited(reader: Reader): String {
        val result = StringBuilder()
        while (true) {
            val value = reader.read()
            if (value == -1) return result.toString()
            if (result.length >= MAX_LINE_CHARS) throw IOException("Flow body limit")
            result.append(value.toChar())
        }
    }

    private fun readLine(reader: Reader): String? {
        val result = StringBuilder()
        while (true) {
            val value = reader.read()
            if (value == -1) return result.toString().takeIf { it.isNotEmpty() }
            if (value == 10) return result.toString()
            if (result.length >= MAX_LINE_CHARS) throw IOException("Flow line limit")
            result.append(value.toChar())
        }
    }

}
