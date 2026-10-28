package com.gathin.tvhome.feature.watching.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class WatchPosterLoader(private val context: Context) {
    fun load(uri: String, request: WatchRequest): Bitmap? {
        if (uri.length > 8192) return null
        val parsed = Uri.parse(uri)
        val bytes = when (parsed.scheme) {
            "http", "https" -> runCatching { remote(uri, request) }.getOrNull() ?: return null
            "content" -> {
                val stream = runCatching {
                    context.contentResolver.openAssetFileDescriptor(parsed, "r", request.signal)?.createInputStream()
                }.getOrNull() ?: runCatching {
                    context.contentResolver.openInputStream(parsed)
                }.getOrNull() ?: return null
                runCatching { read(stream, request) }.getOrNull() ?: return null
            }
            "file" -> {
                val file = runCatching { File(parsed.path ?: return null).canonicalFile }.getOrNull() ?: return null
                val root = File(context.filesDir, "watch_artwork").canonicalPath + File.separator
                if (!file.path.startsWith(root)) return null
                runCatching { read(file.inputStream(), request) }.getOrNull() ?: return null
            }
            else -> return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 360) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
    }

    private fun remote(uri: String, request: WatchRequest): ByteArray {
        val connection = URL(uri).openConnection() as HttpURLConnection
        request.attach(connection)
        try {
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            if (connection.responseCode != 200) throw IOException("watch poster HTTP")
            return read(connection.inputStream, request)
        } finally {
            connection.disconnect()
        }
    }

    private fun read(stream: InputStream, request: WatchRequest): ByteArray {
        request.attach(stream)
        return stream.use { it.readBytesBounded(2 * 1024 * 1024) }
    }
}
