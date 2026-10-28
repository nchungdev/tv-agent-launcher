package com.gathin.tvhome.feature.agent.data

import com.gathin.tvhome.core.util.EndpointPolicy
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ServerPairing(baseUrl: String) {

    class Started(
        val pairId: String,
        val code: String,
        val approveUrl: String,
        val expiresInSeconds: Int,
        val intervalSeconds: Int
    )

    sealed interface Poll {
        data object Pending : Poll
        data object Expired : Poll
        data class Approved(val token: String) : Poll
    }

    private val base = EndpointPolicy.validated(baseUrl.trimEnd('/')).toString()

    @Throws(IOException::class)
    fun start(): Started {
        val json = request("POST", START_PATH).second
            ?: throw IOException("pairing unavailable")
        return Started(
            pairId = json.getString("pair_id"),
            code = json.getString("code"),
            approveUrl = json.getString("approve_url"),
            expiresInSeconds = json.optInt("expires_in", DEFAULT_EXPIRY_S),
            intervalSeconds = json.optInt("interval", DEFAULT_INTERVAL_S).coerceIn(1, 10)
        )
    }

    @Throws(IOException::class)
    fun poll(pairId: String): Poll {
        val id = URLEncoder.encode(pairId, "UTF-8")
        val (code, json) = request("GET", "$POLL_PATH?id=$id")
        if (code == HttpURLConnection.HTTP_NOT_FOUND) return Poll.Expired
        val token = json?.optString("token").orEmpty()
        return if (json?.optString("status") == APPROVED && token.isNotBlank()) {
            Poll.Approved(token)
        } else Poll.Pending
    }

    private fun request(method: String, path: String): Pair<Int, JSONObject?> {
        val connection = URL("$base$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (method == "POST") {
                connection.doOutput = true
                connection.outputStream.use { it.write(EMPTY_BODY) }
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) return code to null
            val body = connection.inputStream.bufferedReader().use { it.readText().take(MAX_BODY) }
            return code to JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val START_PATH = "/api/pair/start"
        const val POLL_PATH = "/api/pair/poll"
        const val APPROVED = "approved"
        const val TIMEOUT_MS = 4000
        const val MAX_BODY = 4096
        const val DEFAULT_EXPIRY_S = 300
        const val DEFAULT_INTERVAL_S = 2
        val EMPTY_BODY = "{}".toByteArray()
    }
}
