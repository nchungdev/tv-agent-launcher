package com.gathin.tvhome.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.view.View
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

object ImageLoader {

    private const val MAX_CACHE_BYTES = 4 * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 4000
    private const val READ_TIMEOUT_MS = 4000
    private const val MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024
    private const val BUFFER_SIZE = 16 * 1024

    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val memoryCache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val pending = HashMap<String, Deferred<Bitmap?>>()
    private val slots = Semaphore(2)
    private val targets = WeakHashMap<ImageView, Job>()

    fun load(url: String?, targetView: ImageView, reqWidth: Int, reqHeight: Int) {
        targets.remove(targetView)?.cancel()
        val cleanUrl = url?.trim().orEmpty()
        targetView.tag = cleanUrl
        targetView.setImageDrawable(null)
        if (cleanUrl.isEmpty() || !EndpointPolicy.webLink(cleanUrl)) return
        val width = reqWidth.coerceIn(1, 1024)
        val height = reqHeight.coerceIn(1, 1024)
        val key = "$cleanUrl|$width|$height"
        memoryCache.get(key)?.let {
            targetView.setImageBitmap(it)
            return
        }
        val result = synchronized(pending) {
            pending[key] ?: if (pending.size >= 16) null else {
                loaderScope.async {
                    slots.withPermit { downloadAndDecode(cleanUrl, width, height) }
                }.also { task ->
                    pending[key] = task
                    task.invokeOnCompletion { synchronized(pending) { pending.remove(key) } }
                }
            }
        } ?: return
        val target = WeakReference(targetView)
        val job = loaderScope.launch(Dispatchers.Main) {
            val bitmap = result.await() ?: return@launch
            memoryCache.put(key, bitmap)
            target.get()?.takeIf { it.tag == cleanUrl && it.isAttachedToWindow }
                ?.setImageBitmap(bitmap)
        }
        targets[targetView] = job
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {}
            override fun onViewDetachedFromWindow(view: View) {
                job.cancel()
            }
        }
        targetView.addOnAttachStateChangeListener(listener)
        job.invokeOnCompletion {
            target.get()?.post {
                target.get()?.removeOnAttachStateChangeListener(listener)
            }
        }
    }

    fun clear() {
        memoryCache.evictAll()
    }

    private fun downloadAndDecode(urlStr: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            val connectionUrl = URL(urlStr)
            connection = (connectionUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doInput = true
            }
            val deadline = ConnectionDeadline(connection, 12)
            val bytes = try {
                readBoundedStream(connection) ?: return null
            } finally { deadline.close() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeWithImageDecoder(bytes, reqWidth, reqHeight)
            } else {
                decodeWithBitmapFactory(bytes, reqWidth, reqHeight)
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBoundedStream(connection: HttpURLConnection): ByteArray? {
        val inputStream = connection.inputStream ?: return null
        val buffer = ByteArray(BUFFER_SIZE)
        val outputStream = ByteArrayOutputStream()
        var totalBytesRead = 0
        inputStream.use { stream ->
            var chunkRead: Int
            while (stream.read(buffer).also { chunkRead = it } != -1) {
                totalBytesRead += chunkRead
                if (totalBytesRead > MAX_DOWNLOAD_BYTES) return null
                outputStream.write(buffer, 0, chunkRead)
            }
        }
        return if (totalBytesRead > 0) outputStream.toByteArray() else null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap {
        val buffer = ByteBuffer.wrap(bytes)
        val source = ImageDecoder.createSource(buffer)
        return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            if (reqWidth > 0 && reqHeight > 0) {
                decoder.setTargetSize(reqWidth, reqHeight)
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
        }
    }

    private fun decodeWithBitmapFactory(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        var sampleSize = 1
        while (options.outWidth / (sampleSize * 2) >= reqWidth &&
            options.outHeight / (sampleSize * 2) >= reqHeight) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }
}
