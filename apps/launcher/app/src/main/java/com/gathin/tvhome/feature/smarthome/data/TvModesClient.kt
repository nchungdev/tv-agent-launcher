package com.gathin.tvhome.feature.smarthome.data

import android.content.Context
import com.gathin.tvhome.core.util.EndpointPolicy
import com.gathin.tvhome.feature.agent.data.AgentClient
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TvModesClient(private val context: Context) {

    @Throws(IOException::class)
    fun load(): TvModesState = request("GET", MODES_PATH)

    @Throws(IOException::class)
    fun activate(id: String): TvModesState {
        val encoded = URLEncoder.encode(id, "UTF-8")
        return request("POST", "$MODES_PATH/$encoded/activate")
    }

    private fun request(method: String, path: String): TvModesState {
        val base = AgentClient.baseUrl(context).trimEnd('/')
        val token = AgentClient.token(context)
        if (base.isEmpty() || token.isNullOrEmpty()) throw IOException("agent not configured")

        val url = EndpointPolicy.validated("$base$path").toString()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (method == "POST") {
                connection.doOutput = true
                connection.outputStream.use { it.write(EMPTY_BODY) }
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("modes ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText().take(MAX_BODY) }
            return TvModesState.fromJson(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MODES_PATH = "/v2/modes"
        const val TIMEOUT_MS = 5000
        const val MAX_BODY = 32 * 1024
        val EMPTY_BODY = "{}".toByteArray()
    }
}
