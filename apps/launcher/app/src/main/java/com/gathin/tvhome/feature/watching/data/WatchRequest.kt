package com.gathin.tvhome.feature.watching.data

import android.os.CancellationSignal
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection

class WatchRequest : Closeable {
    val signal = CancellationSignal()
    @Volatile private var closed = false
    private var connection: HttpURLConnection? = null
    private var stream: Closeable? = null

    @Synchronized
    fun attach(active: HttpURLConnection) {
        if (closed) {
            active.disconnect()
            throw IOException("watch request cancelled")
        }
        connection = active
    }

    @Synchronized
    fun attach(active: Closeable) {
        if (closed) {
            active.close()
            throw IOException("watch request cancelled")
        }
        stream = active
    }

    override fun close() {
        val activeConnection: HttpURLConnection?
        val activeStream: Closeable?
        synchronized(this) {
            closed = true
            activeConnection = connection
            activeStream = stream
            connection = null
            stream = null
        }
        signal.cancel()
        runCatching { activeStream?.close() }
        activeConnection?.disconnect()
    }
}
