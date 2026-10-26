package com.gathin.tvhome.feature.system.data

import android.content.Context
import com.gathin.tvhome.feature.main.data.LocalAdb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets


class AdbCpuFeed(context: Context) {

    private val adb = LocalAdb(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var feedJob: Job? = null

    @Volatile private var session: LocalAdb.Session? = null
    @Volatile private var latestLine: String? = null
    @Volatile private var isFailed = false

    fun start(mayPrompt: Boolean) {
        if (session != null && !isFailed) return
        stop()
        isFailed = false
        latestLine = null
        val running = adb.Session()
        session = running
        feedJob = scope.launch {
            val pending = StringBuilder()
            try {
                running.run(COMMAND, mayPrompt) { chunk ->
                    if (session === running) keepLast(pending, chunk)
                }
            } catch (_: IOException) {}
            if (session === running) isFailed = true
        }
    }

    fun stop() {
        feedJob?.cancel()
        feedJob = null
        session?.close()
        session = null
    }

    fun latest(): String? = latestLine

    fun failed(): Boolean = isFailed

    private fun keepLast(pending: StringBuilder, chunk: ByteArray) {
        pending.append(String(chunk, StandardCharsets.US_ASCII))
        var end: Int
        while (pending.indexOf("\n").also { end = it } >= 0) {
            val line = pending.substring(0, end).trim()
            pending.delete(0, end + 1)
            if (line.startsWith("cpu ")) latestLine = line
        }
    }

    companion object {
        private const val COMMAND = "while :; do head -n 1 /proc/stat; sleep 10; done"
    }
}
