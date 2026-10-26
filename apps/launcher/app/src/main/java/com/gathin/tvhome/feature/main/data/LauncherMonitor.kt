package com.gathin.tvhome.feature.main.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File


object LauncherMonitor {

    private const val TAG = "TvHome.Monitor"
    private var lastCpuTicks = -1L
    private var lastSampleUptime = -1L

    data class Snapshot(
        val cpuPercent: Float,
        val memoryKb: Long,
        val threadCount: Int
    )

    fun sample(): Snapshot {
        var cpuPercent = 0f
        var memoryKb = 0L
        var threadCount = Thread.activeCount()

        try {
            val statFile = File("/proc/self/stat")
            if (statFile.canRead()) {
                val stat = statFile.readText().trim().split("\\s+".toRegex())
                if (stat.size > 23) {
                    val utime = stat[13].toLongOrNull() ?: 0L
                    val stime = stat[14].toLongOrNull() ?: 0L
                    val threads = stat[19].toIntOrNull()
                    if (threads != null && threads > 0) threadCount = threads
                    val rssPages = stat[23].toLongOrNull() ?: 0L
                    memoryKb = rssPages * 4L

                    val totalTicks = utime + stime
                    val nowUptime = SystemClock.uptimeMillis()
                    if (lastCpuTicks >= 0 && lastSampleUptime > 0 && nowUptime > lastSampleUptime) {
                        val deltaTicks = totalTicks - lastCpuTicks
                        val deltaMs = nowUptime - lastSampleUptime
                        val clockSpeedHz = 100f
                        val cpuSeconds = deltaTicks / clockSpeedHz
                        val elapsedSeconds = deltaMs / 1000f
                        if (elapsedSeconds > 0) {
                            cpuPercent = (cpuSeconds / elapsedSeconds * 100f).coerceIn(0f, 400f)
                        }
                    }
                    lastCpuTicks = totalTicks
                    lastSampleUptime = nowUptime
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read launcher stats", e)
        }

        if (memoryKb <= 0L) {
            val rt = Runtime.getRuntime()
            memoryKb = (rt.totalMemory() - rt.freeMemory()) / 1024L
        }

        return Snapshot(cpuPercent, memoryKb, threadCount)
    }

    fun trim(_context: Context? = null) {
        try {
            System.gc()
        } catch (_: Throwable) {}
    }
}
