package com.gathin.tvhome.feature.agent.data

import com.gathin.tvhome.R
import com.gathin.tvhome.core.util.BoundedInput
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets

internal object AgentResponseReader {
    fun readV2Answer(connection: HttpURLConnection, listener: AgentClient.V2StreamListener) {
        val code = connection.responseCode
        if (code == 401) {
            throw AgentClient.AgentException(R.string.agent_unauthorised, "401")
        }
        val failed = code !in 200..299
        val stream = if (failed) connection.errorStream else connection.inputStream
        if (failed) {
            val detail = stream?.reader(StandardCharsets.UTF_8)?.use {
                BoundedInput.text(it, 8192)
            }.orEmpty().ifEmpty { "HTTP $code" }
            throw IOException(detail)
        }
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            var line: String?
            var total = 0
            val started = System.nanoTime()
            while (BoundedInput.line(reader).also { line = it } != null) {
                total += line!!.length
                if (total > BoundedInput.MAX_STREAM ||
                    System.nanoTime() - started > 120_000_000_000L) {
                    throw IOException("Response budget exceeded")
                }
                val trimmed = line?.trim() ?: continue
                if (trimmed.isEmpty()) continue
                AgentClient.parseAndDispatchV2Event(trimmed, listener)
            }
        }
    }

}
