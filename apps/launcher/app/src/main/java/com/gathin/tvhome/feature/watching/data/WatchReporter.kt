package com.gathin.tvhome.feature.watching.data

import android.content.Context
import com.gathin.tvhome.feature.agent.data.AgentClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class WatchReporter(context: Context) {

    private val appContext = context.applicationContext
    private val history = LocalWatchHistory(appContext)

    fun report(event: WatchEvent) {
        worker.execute {
            history.record(event)
            runCatching { send(event) }
        }
    }

    private fun send(event: WatchEvent) {
        val base = AgentClient.baseUrl(appContext).trimEnd('/')
        val token = AgentClient.token(appContext)
        if (base.isEmpty() || token.isNullOrEmpty()) return

        val body = JSONObject().put("events", JSONArray().put(event.toJson()))
        val connection = URL("$base$EVENTS_PATH").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { it.write(bytes) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val EVENTS_PATH = "/v2/watch/events"
        const val TIMEOUT_MS = 4000
        val worker = Executors.newSingleThreadExecutor()
    }
}
