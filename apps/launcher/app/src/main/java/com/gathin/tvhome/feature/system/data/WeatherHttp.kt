package com.gathin.tvhome.feature.system.data

import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class WeatherHttp : Closeable {
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var closed = false

    fun get(url: String): JSONObject {
        val active = URL(url).openConnection() as HttpURLConnection
        synchronized(this) {
            if (closed) {
                active.disconnect()
                throw IOException("weather request cancelled")
            }
            connection = active
        }
        active.connectTimeout = 8000
        active.readTimeout = 8000
        active.setRequestProperty("User-Agent", "tvhome-launcher")
        try {
            if (active.responseCode != 200) throw IOException("weather HTTP ${active.responseCode}")
            val body = active.inputStream.bufferedReader().use { it.readText() }
            if (closed) throw IOException("weather request cancelled")
            return JSONObject(body)
        } finally {
            active.disconnect()
            synchronized(this) { if (connection === active) connection = null }
        }
    }

    override fun close() {
        synchronized(this) {
            closed = true
            connection?.disconnect()
            connection = null
        }
    }
}
