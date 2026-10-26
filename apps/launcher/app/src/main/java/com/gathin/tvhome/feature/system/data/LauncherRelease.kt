package com.gathin.tvhome.feature.system.data

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets


class LauncherRelease private constructor(
    @JvmField val version: String,
    @JvmField val apkUrl: String,
    @JvmField val apkSize: Long,
    @JvmField val apkSha256: String
) {

    fun isNewerThan(installedVersion: String): Boolean {
        return isNewer(version, installedVersion)
    }

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/nchungdev/tv-agent-launcher/releases/latest"
        private const val APK_ASSET = "tv-launcher.apk"
        private const val SHA256_PREFIX = "sha256:"
        private const val TIMEOUT_MS = 15_000
        private const val MAX_JSON_BYTES = 256 * 1024

        @JvmStatic
        @Throws(IOException::class)
        fun latest(): LauncherRelease {
            val connection = open(LATEST_URL)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            return try {
                parse(JSONObject(readBody(connection)))
            } catch (e: JSONException) {
                throw IOException("unreadable release", e)
            } finally {
                connection.disconnect()
            }
        }

        @JvmStatic
        fun isNewer(offeredVersion: String, installedVersion: String): Boolean {
            val offered = versionParts(offeredVersion)
            val installed = versionParts(installedVersion)
            for (i in 0 until maxOf(offered.size, installed.size)) {
                val offeredPart = if (i < offered.size) offered[i] else 0
                val installedPart = if (i < installed.size) installed[i] else 0
                if (offeredPart != installedPart) return offeredPart > installedPart
            }
            return false
        }

        @JvmStatic
        @Throws(IOException::class)
        fun open(url: String): HttpURLConnection {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "tvhome-launcher")
            return connection
        }

        @JvmStatic
        fun versionParts(version: String): IntArray {
            val numbers = version.replaceFirst("^[vV]".toRegex(), "").replaceFirst("[^0-9.].*$".toRegex(), "")
            val parts = numbers.split(".")
            val values = IntArray(parts.size)
            for (i in parts.indices) {
                values[i] = if (parts[i].isEmpty()) 0 else parts[i].toInt()
            }
            return values
        }

        private fun parse(release: JSONObject): LauncherRelease {
            val tag = release.getString("tag_name")
            val assets = release.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (APK_ASSET != asset.optString("name")) continue

                val digest = asset.optString("digest")
                if (!digest.startsWith(SHA256_PREFIX)) throw IOException("release has no checksum")
                return LauncherRelease(
                    tag.replaceFirst("^[vV]".toRegex(), ""),
                    asset.getString("browser_download_url"),
                    asset.getLong("size"),
                    digest.substring(SHA256_PREFIX.length)
                )
            }
            throw IOException("release has no $APK_ASSET")
        }

        private fun readBody(connection: HttpURLConnection): String {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP " + connection.responseCode)
            }
            connection.inputStream.use { body ->
                val collected = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var read: Int
                while (body.read(buffer).also { read = it } > 0) {
                    collected.write(buffer, 0, read)
                    if (collected.size() > MAX_JSON_BYTES) throw IOException("release too large")
                }
                return collected.toString(StandardCharsets.UTF_8.name())
            }
        }
    }
}
