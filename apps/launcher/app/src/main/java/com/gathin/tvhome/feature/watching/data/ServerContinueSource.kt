package com.gathin.tvhome.feature.watching.data

import android.content.Context
import com.gathin.tvhome.feature.agent.data.AgentClient
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ServerContinueSource(private val context: Context) {
    fun load(request: WatchRequest): WatchResult {
        val base = AgentClient.baseUrl(context).trimEnd('/')
        val token = AgentClient.token(context)
        if (base.isEmpty() || token.isNullOrEmpty()) {
            return WatchResult(WatchAvailability.UNSUPPORTED)
        }
        val connection = URL("$base/v2/watch/continue").openConnection() as HttpURLConnection
        request.attach(connection)
        try {
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("watch HTTP")
            val stream = connection.inputStream
            request.attach(stream)
            val bytes = stream.use { it.readBytesBounded(1024 * 1024) }
            return WatchResult.loaded(parse(bytes.toString(Charsets.UTF_8)))
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun parse(body: String): List<WatchItem> {
            val rows = JSONObject(body).optJSONArray("items") ?: return emptyList()
            return (0 until minOf(rows.length(), 512)).mapNotNull { index ->
                rows.optJSONObject(index)?.let { toItem(it) }
            }
        }

        private fun toItem(row: JSONObject): WatchItem? {
            val title = row.optString("title").takeIf { it.isNotBlank() } ?: return null
            val packageName = row.optString("package_name")
            val action = row.optJSONObject("action")
            val parameters = HashMap<String, Any?>()
            action?.optJSONObject("parameters")?.let { values ->
                values.keys().forEach { parameters[it] = values.opt(it) }
            }
            parameters["package_name"] = packageName
            val link = listOf("deep_link", "uri", "url").firstNotNullOfOrNull {
                parameters[it]?.toString()?.takeIf { value -> value.isNotBlank() }
            }.orEmpty()
            return WatchItem(
                title.take(512), row.optString("subtitle").take(1024),
                WatchMerge.progress(row.optDouble("progress", 0.0).toFloat()),
                row.optString("poster_url").takeIf { it.isNotBlank() && it.length <= 8192 },
                row.optString("app_label"), packageName,
                action?.optString("action_type").orEmpty().ifBlank { "app.open" }, parameters,
                row.optLong("remaining_ms").coerceAtLeast(0), WatchSource.SERVER,
                row.optString("id"), row.optLong("updated_at_ms", row.optLong("at")),
                link.take(8192), durationMs = row.optLong("duration_ms").coerceAtLeast(0)
            )
        }
    }
}
