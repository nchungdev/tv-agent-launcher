package com.gathin.tvhome.feature.watching.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.IOException
import java.net.URL
import java.util.concurrent.Executors

class ContinueWatching(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val posters = LruCache<String, Bitmap>(POSTER_CACHE)
    private var loading = false

    var items: List<WatchItem> = emptyList()
        private set

    fun poster(uri: String?): Bitmap? = uri?.let { posters.get(it) }

    fun refresh(onLoaded: () -> Unit) {
        if (loading) return
        loading = true
        worker.execute {
            val loaded = load()
            for (item in loaded) cachePoster(item.posterUri)
            mainHandler.post {
                loading = false
                items = loaded
                onLoaded()
            }
        }
    }

    private fun load(): List<WatchItem> = try {
        ServerContinueSource(context).load()
    } catch (_: Exception) {
        LocalWatchHistory(context).continueItems()
    }

    private fun cachePoster(uri: String?) {
        if (uri.isNullOrBlank() || posters.get(uri) != null) return
        val bitmap = try {
            decode(uri)
        } catch (_: Exception) {
            null
        } ?: return
        posters.put(uri, bitmap)
    }

    private fun decode(uri: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, bounds.outWidth / POSTER_WIDTH)
        }
        return open(uri).use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun open(uri: String) = if (uri.startsWith("http")) {
        URL(uri).openConnection().apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }.getInputStream()
    } else {
        context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("no stream")
    }

    private companion object {
        const val POSTER_CACHE = 8
        const val POSTER_WIDTH = 400
        const val TIMEOUT_MS = 6000
        val worker = Executors.newSingleThreadExecutor()
    }
}
