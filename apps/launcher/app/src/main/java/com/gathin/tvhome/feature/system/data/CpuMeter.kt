package com.gathin.tvhome.feature.system.data

import android.content.Context
import android.os.SystemClock
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException


class CpuMeter @JvmOverloads constructor(private val context: Context? = null) {

    private var totalPrev: Long = -1
    private var idleStatPrev: Long = -1
    private var procStatDead = false
    private var cpuidleDead = false
    private var uptimeDead = false
    private var idlePrev: Long = -1
    private var wallPrev: Long = -1
    private var uptimeIdlePrev = -1.0
    private var uptimePrev = -1.0
    private var selfTicksPrev: Long = -1
    private var selfWallPrev: Long = -1
    private var smoothedLoad = -1.0
    private var feed: AdbCpuFeed? = null
    private var feedStarted = false
    private var mayPrompt = false

    fun sample(): Int {
        if (!procStatDead) {
            val fromStat = fromProcStat()
            if (fromStat != UNAVAILABLE) return fromStat
            procStatDead = true
        }
        if (!cpuidleDead) {
            val fromIdle = fromIdle()
            if (fromIdle != UNAVAILABLE) return fromIdle
            cpuidleDead = true
        }
        if (!uptimeDead) {
            val fromUptime = fromUptime()
            if (fromUptime != UNAVAILABLE) return fromUptime
            uptimeDead = true
        }
        val feedSample = fromFeed()
        if (feedSample >= 0) return feedSample
        val freqSample = fromScalingFreq()
        if (freqSample != UNAVAILABLE) return freqSample
        return feedSample
    }

    fun askToAllow() {
        stop()
        mayPrompt = true
    }

    fun stop() {
        feed?.stop()
        feedStarted = false
    }

    private fun fromFeed(): Int {
        val ctx = context ?: return UNAVAILABLE
        if (feed == null) feed = AdbCpuFeed(ctx)
        if (!feedStarted) {
            feed?.start(mayPrompt)
            feedStarted = true
            mayPrompt = false
        }
        if (feed?.failed() == true) return UNAVAILABLE
        val line = feed?.latest()
        return if (line == null) NOT_YET else fromStatLine(line)
    }

    private fun fromProcStat(): Int = fromStatLine(readFirstLine("/proc/stat"))

    private fun fromStatLine(line: String?): Int {
        if (line == null || !line.startsWith("cpu")) return UNAVAILABLE
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 5) return UNAVAILABLE
        return try {
            val user = parts[1].toLong()
            val nice = parts[2].toLong()
            val sys = parts[3].toLong()
            val idle = parts[4].toLong()
            val iowait = if (parts.size > 5) parts[5].toLong() else 0L
            val irq = if (parts.size > 6) parts[6].toLong() else 0L
            val soft = if (parts.size > 7) parts[7].toLong() else 0L
            val total = user + nice + sys + idle + iowait + irq + soft
            val idleAll = idle + iowait
            if (totalPrev < 0) {
                totalPrev = total
                idleStatPrev = idleAll
                return NOT_YET
            }
            val dt = total - totalPrev
            val di = idleAll - idleStatPrev
            totalPrev = total
            idleStatPrev = idleAll
            if (dt <= 0) return NOT_YET
            clamp(Math.round((dt - di) * 100.0 / dt).toInt())
        } catch (_: NumberFormatException) {
            UNAVAILABLE
        }
    }

    private fun fromIdle(): Int {
        var idle: Long = 0
        var cores = 0
        for (i in 0 until 8) {
            var any = false
            for (state in 0 until 8) {
                val path = "/sys/devices/system/cpu/cpu$i/cpuidle/state$state/time"
                val text = readFirstLine(path) ?: break
                try {
                    idle += text.trim().toLong()
                    any = true
                } catch (_: NumberFormatException) {
                    break
                }
            }
            if (!any) break
            cores++
        }
        if (cores == 0) return UNAVAILABLE
        val wall = SystemClock.elapsedRealtime() * 1000L
        if (idlePrev < 0) {
            idlePrev = idle
            wallPrev = wall
            return NOT_YET
        }
        val di = idle - idlePrev
        val dw = (wall - wallPrev) * cores
        idlePrev = idle
        wallPrev = wall
        if (dw <= 0) return NOT_YET
        return clamp(Math.round((dw - di) * 100.0 / dw).toInt())
    }

    private fun fromUptime(): Int {
        val line = readFirstLine("/proc/uptime")
        val parts = line?.trim()?.split("\\s+".toRegex()) ?: emptyList()
        if (parts.size < 2) return UNAVAILABLE
        val uptime: Double
        val idle: Double
        try {
            uptime = parts[0].toDouble()
            idle = parts[1].toDouble()
        } catch (_: NumberFormatException) {
            return UNAVAILABLE
        }
        if (uptimePrev < 0) {
            uptimePrev = uptime
            uptimeIdlePrev = idle
            return NOT_YET
        }
        val wall = (uptime - uptimePrev) * Runtime.getRuntime().availableProcessors()
        val di = idle - uptimeIdlePrev
        uptimePrev = uptime
        uptimeIdlePrev = idle
        if (wall <= 0) return NOT_YET
        return clamp(Math.round((wall - di) * 100.0 / wall).toInt())
    }

    private fun fromScalingFreq(): Int {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val range = readFreqRange()
        var totalCur = 0L
        var activeCores = 0
        for (i in 0 until cores) {
            val curStr = readFirstLine("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                ?: continue
            val cur = curStr.trim().toLongOrNull() ?: continue
            totalCur += cur
            activeCores++
        }
        if (activeCores == 0 || range[1] <= 0L) return UNAVAILABLE

        val minTotal = range[0] * activeCores
        val maxTotal = range[1] * activeCores
        val span = (maxTotal - minTotal).coerceAtLeast(1L)
        val freqPct = ((totalCur - minTotal).coerceAtLeast(0L) * 100.0 / span).coerceIn(0.0, 100.0)
        val selfPct = sampleSelfCpu(cores)
        val estimate = (8.0 + freqPct * 0.45 + selfPct * 0.8).coerceIn(6.0, 95.0)
        smoothedLoad = if (smoothedLoad < 0.0) estimate else smoothedLoad * 0.7 + estimate * 0.3
        return clamp(Math.round(smoothedLoad).toInt())
    }

    private var cachedFreqRange: LongArray? = null

    private fun readFreqRange(): LongArray {
        cachedFreqRange?.let { return it }
        val avail = readFirstLine(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies"
        )
        if (!avail.isNullOrBlank()) {
            val values = avail.trim().split("\\s+".toRegex()).mapNotNull { it.toLongOrNull() }
            if (values.isNotEmpty()) {
                val computed = longArrayOf(values.minOrNull() ?: 0L, values.maxOrNull() ?: 0L)
                if (computed[1] > 0L) {
                    cachedFreqRange = computed
                    return computed
                }
            }
        }
        val minVal = readFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq")
            ?.trim()?.toLongOrNull()
            ?: readFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq")
                ?.trim()?.toLongOrNull() ?: 0L
        val maxVal = readFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            ?.trim()?.toLongOrNull() ?: 0L
        val fallback = longArrayOf(minVal, maxVal)
        if (fallback[1] > 0L) cachedFreqRange = fallback
        return fallback
    }

    private fun sampleSelfCpu(cores: Int): Double {
        val stat = readFirstLine("/proc/self/stat") ?: return 0.0
        val closeParen = stat.lastIndexOf(')')
        if (closeParen < 0 || closeParen + 2 >= stat.length) return 0.0
        val fields = stat.substring(closeParen + 2).trim().split("\\s+".toRegex())
        if (fields.size < 13) return 0.0
        val utime = fields[11].toLongOrNull() ?: return 0.0
        val stime = fields[12].toLongOrNull() ?: return 0.0
        val ticks = utime + stime
        val nowMs = SystemClock.elapsedRealtime()
        if (selfTicksPrev < 0 || selfWallPrev < 0) {
            selfTicksPrev = ticks
            selfWallPrev = nowMs
            return 0.0
        }
        val dtMs = (nowMs - selfWallPrev).coerceAtLeast(1L)
        val dTicks = (ticks - selfTicksPrev).coerceAtLeast(0L)
        selfTicksPrev = ticks
        selfWallPrev = nowMs
        return (dTicks * 1000.0 / (dtMs * cores)).coerceIn(0.0, 100.0)
    }

    companion object {
        const val NOT_YET = -1
        const val UNAVAILABLE = -2

        private fun clamp(percent: Int): Int = percent.coerceIn(0, 100)

        private fun readFirstLine(path: String): String? {
            return try {
                BufferedReader(FileReader(path)).use { it.readLine() }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
    }
}
