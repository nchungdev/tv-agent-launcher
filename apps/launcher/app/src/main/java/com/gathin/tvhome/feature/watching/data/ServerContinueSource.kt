package com.gathin.tvhome.feature.watching.data

import android.content.Context
import com.gathin.tvhome.feature.agent.data.AgentClient
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ServerContinueSource(private val context: Context) {

    @Throws(IOException::class)
    fun load(): List<WatchItem> {
        val base = AgentClient.baseUrl(context).trimEnd('/')
        val token = AgentClient.token(context)
        if (base.isEmpty() || token.isNullOrEmpty()) throw IOException("agent not configured")

        val connection = URL("$base$CONTINUE_PATH").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("continue ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): List<WatchItem> {
        val rows = JSONObject(body).optJSONArray("items") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let { toItem(it) }
        }
    }

    private fun toItem(row: JSONObject): WatchItem? {
        val title = row.optString("title").takeIf { it.isNotBlank() } ?: return null
        val packageName = row.optString("package_name")
        val action = row.optJSONObject("action")
        return WatchItem(
            title = title,
            subtitle = row.optString("subtitle"),
            progress = row.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
            posterUri = row.optString("poster_url").takeIf { it.isNotBlank() },
            appLabel = row.optString("app_label"),
            packageName = packageName,
            actionType = action?.optString("action_type").orEmpty().ifBlank { "app.open" },
            parameters = parameters(action?.optJSONObject("parameters"), packageName)
        )
    }

    private fun parameters(json: JSONObject?, packageName: String): Map<String, Any?> {
        val values = HashMap<String, Any?>()
        json?.keys()?.forEach { key -> values[key] = json.opt(key) }
        if (packageName.isNotBlank()) values.putIfAbsent("package_name", packageName)
        return values
    }

    private companion object {
        const val CONTINUE_PATH = "/v2/watch/continue"
        const val TIMEOUT_MS = 4000
    }
}
