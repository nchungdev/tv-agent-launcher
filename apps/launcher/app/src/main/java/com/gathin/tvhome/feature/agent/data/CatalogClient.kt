package com.gathin.tvhome.feature.agent.data

import android.content.Context
import org.json.JSONObject
import com.gathin.tvhome.feature.playback.PlaybackUrls
import com.gathin.tvhome.feature.watching.data.readBytesBounded
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object CatalogClient {

    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 20_000

    @JvmStatic
    @Throws(Exception::class)
    fun getTitle(context: Context, titleId: String,
        request: AgentClient.Request = AgentClient.Request()): CatalogTitle {
        val base = AgentClient.baseUrl(context)
        if (base.isEmpty()) throw IOException("Chưa cấu hình máy chủ Agent")
        val token = AgentClient.token(context) ?: throw IOException("Thiếu token xác thực")

        val encoded = URLEncoder.encode(titleId, StandardCharsets.UTF_8.name())
        val endpoint = "$base/v2/catalog/titles/$encoded"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }

        request.attach(connection)
        try {
            val code = connection.responseCode
            if (code != 200) {
                throw IOException("Catalog HTTP $code")
            }
            val text = connection.inputStream.use { it.readBytesBounded(2 * 1024 * 1024).toString(Charsets.UTF_8) }
            val json = JSONObject(text)
            return CatalogParser.parseTitleResponse(json)
        } finally {
            connection.disconnect()
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun resolvePlayback(context: Context, episodeId: String,
        request: AgentClient.Request = AgentClient.Request()): CatalogPlaybackResponse {
        val base = AgentClient.baseUrl(context)
        if (base.isEmpty()) throw IOException("Chưa cấu hình máy chủ Agent")
        val token = AgentClient.token(context) ?: throw IOException("Thiếu token xác thực")

        val encoded = URLEncoder.encode(episodeId, StandardCharsets.UTF_8.name())
        val endpoint = "$base/v2/catalog/episodes/$encoded/playback"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        request.attach(connection)
        try {
            connection.outputStream.use { out ->
                out.write("{}".toByteArray(StandardCharsets.UTF_8))
            }

            val code = connection.responseCode
            if (code != 200) {
                throw IOException("Playback HTTP $code")
            }
            val text = connection.inputStream.use { it.readBytesBounded(2 * 1024 * 1024).toString(Charsets.UTF_8) }
            val json = JSONObject(text)
            val response = CatalogParser.parsePlaybackResponse(json)

            val resolvedUrl = resolveStreamUrl(base, response.playback.url)
            val resolvedDescriptor = response.playback.copy(url = resolvedUrl)
            return response.copy(playback = resolvedDescriptor)
        } finally {
            connection.disconnect()
        }
    }

    @JvmStatic
    fun resolveStreamUrl(baseUrl: String, streamUrl: String): String {
        if (streamUrl.isBlank()) return ""
        return PlaybackUrls.resolve(baseUrl, streamUrl)
    }
}
