package com.gathin.tvhome.feature.system.coordinator
import com.gathin.tvhome.feature.system.data.*
import com.gathin.tvhome.feature.system.widget.*

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.text.format.Formatter
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.system.data.CpuMeter
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.net.Inet4Address
import java.text.NumberFormat
import java.util.ArrayList


class SystemPages(private val context: Context) {

    class Line(
        @JvmField val icon: Int,
        @JvmField val name: String,
        @JvmField val value: String,
        @JvmField val fill: Float
    ) {
        companion object {
            const val NO_BAR = -1f
        }
    }

    fun lines(page: Int, cpu: Int, showCpu: Boolean): List<Line> {
        return when (page) {
            NETWORK -> network()
            DEVICE -> device()
            SPACE -> space()
            CHIP -> chip(cpu)
            else -> usage(cpu, showCpu)
        }
    }

    private fun usage(cpu: Int, showCpu: Boolean): List<Line> {
        val lines = ArrayList<Line>()
        if (showCpu && cpu != CpuMeter.UNAVAILABLE) lines.add(cpuLine(cpu))
        val memory = memory()
        val usedMemory = memory.totalMem - memory.availMem
        lines.add(Line(R.drawable.ic_ram, text(R.string.widget_ram), usedOf(usedMemory, memory.totalMem), share(usedMemory, memory.totalMem)))
        val data = stat(Environment.getDataDirectory())
        val total = data?.totalBytes ?: 0L
        val used = if (data == null) 0L else total - data.availableBytes
        lines.add(Line(R.drawable.ic_storage, text(R.string.widget_storage), if (data == null) "" else usedOf(used, total), share(used, total)))
        return lines
    }

    private fun network(): List<Line> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val active: Network? = cm?.activeNetwork
        val caps: NetworkCapabilities? = if (active != null) cm.getNetworkCapabilities(active) else null
        val link: LinkProperties? = if (active != null) cm.getLinkProperties(active) else null
        val wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val wired = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val info = if (wifi) wifiInfo() else null

        val lines = ArrayList<Line>()
        lines.add(Line(if (wired) R.drawable.ic_ethernet else R.drawable.ic_wifi,
            text(if (wired) R.string.sys_ethernet else R.string.sys_wifi),
            if (caps == null) text(R.string.sys_offline) else networkName(info), Line.NO_BAR))
        lines.add(Line(R.drawable.ic_info, text(R.string.sys_ip), ipv4(link), Line.NO_BAR))
        if (info != null) {
            val level = WifiManager.calculateSignalLevel(info.rssi, 5)
            lines.add(Line(R.drawable.ic_speed, text(R.string.sys_signal), "${info.linkSpeed} Mbps", level / 4f))
        } else if (wired) {
            lines.add(Line(R.drawable.ic_speed, text(R.string.sys_link), text(R.string.sys_wired), Line.NO_BAR))
        }
        return lines
    }

    private fun device(): List<Line> {
        val lines = ArrayList<Line>()
        lines.add(Line(R.drawable.ic_tv, text(R.string.sys_model), Build.MODEL, Line.NO_BAR))
        lines.add(Line(R.drawable.ic_info, text(R.string.sys_android), Build.VERSION.RELEASE, Line.NO_BAR))
        lines.add(Line(R.drawable.ic_timer, text(R.string.sys_uptime), uptime(), Line.NO_BAR))
        return lines
    }

    private fun space(): List<Line> {
        val lines = ArrayList<Line>()
        val memory = memory()
        lines.add(Line(R.drawable.ic_ram, text(R.string.widget_ram), free(memory.availMem), share(memory.availMem, memory.totalMem)))
        val data = stat(Environment.getDataDirectory())
        lines.add(Line(R.drawable.ic_storage, text(R.string.widget_storage),
            if (data == null) "" else free(data.availableBytes),
            if (data == null) Line.NO_BAR else share(data.availableBytes, data.totalBytes)))
        val usb = removable()
        lines.add(Line(R.drawable.ic_storage, text(R.string.sys_usb),
            if (usb == null) text(R.string.sys_none) else free(usb.availableBytes),
            if (usb == null) Line.NO_BAR else share(usb.availableBytes, usb.totalBytes)))
        return lines
    }

    private fun chip(cpu: Int): List<Line> {
        val lines = ArrayList<Line>()
        if (cpu != CpuMeter.UNAVAILABLE) lines.add(cpuLine(cpu))
        lines.add(Line(R.drawable.ic_speed, text(R.string.sys_clock), clock(), Line.NO_BAR))
        lines.add(Line(R.drawable.ic_thermostat, text(R.string.sys_thermal), thermal(), Line.NO_BAR))
        return lines
    }

    private fun cpuLine(cpu: Int): Line {
        val known = cpu >= 0
        return Line(R.drawable.ic_memory, text(R.string.widget_cpu), if (known) "$cpu%" else "", if (known) cpu / 100f else 0f)
    }

    private fun memory(): ActivityManager.MemoryInfo {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return info
    }

    private fun removable(): StatFs? {
        val dirs = context.getExternalFilesDirs(null)
        for (i in 1 until dirs.size) {
            if (dirs[i] != null) return stat(dirs[i])
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun wifiInfo(): WifiInfo? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        return wifi?.connectionInfo
    }

    private fun networkName(info: WifiInfo?): String {
        val ssid = info?.ssid
        if (ssid == null || ssid == WifiManager.UNKNOWN_SSID) return text(R.string.sys_connected)
        return if (ssid.length > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) ssid.substring(1, ssid.length - 1) else ssid
    }

    private fun uptime(): String {
        val minutes = SystemClock.elapsedRealtime() / 60_000L
        val hours = minutes / 60
        val days = hours / 24
        if (days > 0) return context.getString(R.string.uptime_days, days, hours % 24)
        if (hours > 0) return context.getString(R.string.uptime_hours, hours, minutes % 60)
        return context.getString(R.string.uptime_minutes, minutes)
    }

    private fun thermal(): String {
        if (Build.VERSION.SDK_INT < 29) return text(R.string.thermal_unknown)
        val status = context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0
        if (status <= PowerManager.THERMAL_STATUS_NONE) return text(R.string.thermal_normal)
        return text(if (status <= PowerManager.THERMAL_STATUS_MODERATE) R.string.thermal_warm else R.string.thermal_hot)
    }

    private fun text(res: Int): String = context.getString(res)

    private fun free(bytes: Long): String = context.getString(R.string.sys_free, Formatter.formatShortFileSize(context, bytes))

    private fun usedOf(used: Long, total: Long): String {
        var u = Formatter.formatShortFileSize(context, used)
        val t = Formatter.formatShortFileSize(context, total)
        val cut = lastSpace(t)
        val sameUnit = cut > 0 && lastSpace(u) > 0 && u.substring(lastSpace(u)) == t.substring(cut)
        if (total >= GIGABYTE && used < GIGABYTE) u = oneDecimal(used / GIGABYTE.toDouble())
        else if (sameUnit) u = u.substring(0, lastSpace(u))
        return context.getString(R.string.widget_used_of, u, t)
    }

    companion object {
        const val USAGE = 0
        const val NETWORK = 1
        const val DEVICE = 2
        const val SPACE = 3
        const val CHIP = 4
        const val COUNT = 5

        @JvmField
        val NAMES = intArrayOf(R.string.sys_page_usage, R.string.sys_page_network, R.string.sys_page_device, R.string.sys_page_space, R.string.sys_page_chip)

        private const val GIGABYTE = 1_000_000_000L
        private const val CLOCK_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"

        private fun stat(dir: File?): StatFs? {
            if (dir == null) return null
            return try {
                StatFs(dir.path)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun ipv4(link: LinkProperties?): String {
            if (link == null) return ""
            for (address in link.linkAddresses) {
                if (address.address is Inet4Address) return address.address.hostAddress ?: ""
            }
            return ""
        }

        private fun clock(): String {
            return try {
                BufferedReader(FileReader(CLOCK_FILE)).use { reader ->
                    val khz = reader.readLine().trim().toLong()
                    "${oneDecimal(khz / 1_000_000.0)} GHz"
                }
            } catch (_: Exception) {
                ""
            }
        }

        private fun share(part: Long, whole: Long): Float = if (whole > 0) part / whole.toFloat() else 0f

        private fun oneDecimal(value: Double): String {
            val format = NumberFormat.getNumberInstance()
            format.minimumFractionDigits = 1
            format.maximumFractionDigits = 1
            return format.format(value)
        }

        private fun lastSpace(s: String): Int {
            for (i in s.length - 1 downTo 0) if (Character.isSpaceChar(s[i])) return i
            return -1
        }
    }
}
