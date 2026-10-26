package com.gathin.tvhome.feature.runningapps.data

import com.gathin.tvhome.feature.main.data.LocalAdb
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.io.IOException
import java.util.function.Predicate
import java.util.regex.Pattern


object BackgroundApps {

    class App(
        @JvmField val pkg: String,
        @JvmField val memoryKb: Long,
        @JvmField val cpuPercent: Float,
        @JvmField val recency: Int
    ) {
        constructor(pkg: String, memoryKb: Long, recency: Int) : this(pkg, memoryKb, 0f, recency)
    }

    private const val SPLIT = "--recents--"
    const val LIST_COMMAND =
        "ps -A -o %CPU,RSS,NAME; echo $SPLIT; dumpsys activity recents | grep 'Recent #'"
    private val PROCESS =
        Pattern.compile("^\\s*(?:([\\d.,]+)\\s+)?(\\d+)\\s+([A-Za-z][\\w.]*)(?::\\S*)?\\s*$")
    private val TASK = Pattern.compile("A=\\d+:([\\w.]+)")
    private val PACKAGE = Pattern.compile("[A-Za-z][\\w.]*")

    @JvmStatic
    @Throws(IOException::class)
    fun list(adb: LocalAdb, listed: Predicate<String>): List<App> {
        return parse(adb.shell(LIST_COMMAND), listed)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun stop(adb: LocalAdb, packages: Collection<String>) {
        val command = StringBuilder()
        for (pkg in packages) {
            if (!PACKAGE.matcher(pkg).matches()) continue
            if (command.isNotEmpty()) command.append("; ")
            command.append("am force-stop ").append(pkg)
        }
        if (command.isNotEmpty()) adb.shell(command.toString())
    }

    @JvmStatic
    fun stopAll(context: Context) {
        val packageManager = context.packageManager
        val launchable = HashSet<String>()
        val categories = arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)

        for (category in categories) {
            val main = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (resolved in packageManager.queryIntentActivities(main, 0)) {
                resolved.activityInfo?.let { launchable.add(it.packageName) }
            }
        }

        launchable.remove(context.packageName)
        val ime = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )
        if (ime != null) launchable.remove(ime.split("/")[0])

        try {
            val adb = LocalAdb(context)
            val running = list(adb) { launchable.contains(it) }
            val targets = running.map { it.pkg }
            if (targets.isNotEmpty()) stop(adb, targets)
        } catch (_: Exception) {}
    }

    @JvmStatic
    fun parse(output: String, listed: Predicate<String>): List<App> {
        val split = output.indexOf(SPLIT)
        val ps = if (split < 0) output else output.substring(0, split)
        val recents = if (split < 0) "" else output.substring(split + SPLIT.length)
        val memory = HashMap<String, Long>()
        val cpu = HashMap<String, Float>()
        for (line in ps.split("\n")) {
            val m = PROCESS.matcher(line)
            if (!m.matches()) continue
            val pkg = m.group(3) ?: continue
            val rssStr = m.group(2) ?: continue
            if (!listed.test(pkg)) continue
            val rss = rssStr.toLongOrNull() ?: continue
            val cpuStr = m.group(1)
            val cpuVal = cpuStr?.replace(',', '.')?.toFloatOrNull() ?: 0f
            memory[pkg] = (memory[pkg] ?: 0L) + rss
            cpu[pkg] = (cpu[pkg] ?: 0f) + cpuVal
        }
        val tasks = ArrayList<String>()
        val t = TASK.matcher(recents)
        while (t.find()) {
            val taskPkg = t.group(1)
            if (taskPkg != null) tasks.add(taskPkg)
        }
        val apps = ArrayList<App>()
        for ((pkg, rss) in memory) {
            val cpuPercent = cpu[pkg] ?: 0f
            apps.add(App(pkg, rss, cpuPercent, recency(pkg, tasks)))
        }
        apps.sortWith { a, b ->
            if (a.recency != b.recency) a.recency.compareTo(b.recency)
            else b.memoryKb.compareTo(a.memoryKb)
        }
        return apps
    }

    private fun recency(pkg: String, tasks: List<String>): Int {
        for (i in tasks.indices) {
            val affinity = tasks[i]
            if (affinity == pkg || affinity.startsWith("$pkg.")) return i
        }
        return Int.MAX_VALUE
    }
}
