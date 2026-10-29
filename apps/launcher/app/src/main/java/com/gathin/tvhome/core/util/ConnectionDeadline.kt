package com.gathin.tvhome.core.util

import java.io.Closeable
import java.net.HttpURLConnection
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ConnectionDeadline(connection: HttpURLConnection, seconds: Long = 120) : Closeable {
    private val future = timer.schedule({ connection.disconnect() }, seconds, TimeUnit.SECONDS)
    override fun close() { future.cancel(false) }

    companion object {
        private val timer = ScheduledThreadPoolExecutor(1).apply {
            removeOnCancelPolicy = true
        }
    }
}
