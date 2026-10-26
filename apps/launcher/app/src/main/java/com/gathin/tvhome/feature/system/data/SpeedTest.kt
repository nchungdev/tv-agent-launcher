package com.gathin.tvhome.feature.system.data

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


class SpeedTest {

    interface Listener {
        fun progress(mbps: Int)
        fun done(mbps: Int, pingMs: Int)
        fun failed()
    }

    enum class Verdict { UHD, HD, SD, SLOW }

    private fun interface Progress {
        fun read(total: Long): Boolean
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var testJob: Job? = null

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
        testJob?.cancel()
    }

    fun start(listener: Listener) {
        cancelled = false
        testJob = scope.launch {
            try {
                var ping = Int.MAX_VALUE
                withContext(Dispatchers.IO) {
                    var i = 0
                    while (i < PINGS && !cancelled) {
                        val t0 = SystemClock.elapsedRealtime()
                        drain(open(URL_BASE + "0"), null)
                        ping = minOf(ping, (SystemClock.elapsedRealtime() - t0).toInt())
                        i++
                    }
                }
                var got = 0L
                val t0 = SystemClock.elapsedRealtime()
                withContext(Dispatchers.IO) {
                    while (!cancelled && SystemClock.elapsedRealtime() - t0 < MAX_MS) {
                        val before = got
                        drain(open(URL_BASE + BYTES)) { bytes ->
                            got = before + bytes
                            val ms = SystemClock.elapsedRealtime() - t0
                            if (ms > 250) {
                                val mbps = mbps(got, ms)
                                scope.launch { if (!cancelled) listener.progress(mbps) }
                            }
                            !cancelled && ms < MAX_MS
                        }
                    }
                }
                val mbps = mbps(got, SystemClock.elapsedRealtime() - t0)
                val pingMs = ping
                if (!cancelled) listener.done(mbps, pingMs)
            } catch (_: IOException) {
                if (!cancelled) listener.failed()
            }
        }
    }

    companion object {
        private const val URL_BASE = "https://speed.cloudflare.com/__down?bytes="
        private const val BYTES = 25_000_000L
        private const val MAX_MS = 15_000L
        private const val PINGS = 3
        private const val TIMEOUT_MS = 6000

        @JvmStatic
        fun verdict(mbps: Int): Verdict = when {
            mbps >= 15 -> Verdict.UHD
            mbps >= 5 -> Verdict.HD
            mbps >= 3 -> Verdict.SD
            else -> Verdict.SLOW
        }

        private fun mbps(bytes: Long, ms: Long): Int =
            if (ms <= 0) 0 else Math.round(bytes * 8.0 / ms / 1000.0).toInt()

        private fun open(url: String): HttpURLConnection {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.useCaches = false
            c.setRequestProperty("User-Agent", "tvhome-launcher")
            return c
        }

        private fun drain(c: HttpURLConnection, progress: Progress?) {
            try {
                val code = c.responseCode
                if (code != 200) throw IOException("HTTP $code")
                c.inputStream.use { `in` ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    var n: Int
                    while (`in`.read(buf).also { n = it } > 0) {
                        total += n
                        if (progress != null && !progress.read(total)) break
                    }
                }
            } finally {
                c.disconnect()
            }
        }
    }
}
