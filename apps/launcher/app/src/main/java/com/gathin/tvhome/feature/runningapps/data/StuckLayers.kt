package com.gathin.tvhome.feature.runningapps.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.gathin.tvhome.feature.main.data.LocalAdb

object StuckLayers {
    private const val CHECK_DELAY_MS = 2_000L
    private const val COMMAND =
        "dumpsys SurfaceFlinger | sed -n '/HWC layers:/,/h.w composer state/p'"
    private const val SURFACE_VIEW = "SurfaceView - "
    private val WINDOW = Regex("^([A-Za-z][\\w.]*)/\\S+#\\d+$")

    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun scheduleCheck(context: Context, stillHome: () -> Boolean) {
        cancel()
        val appContext = context.applicationContext
        val check = Runnable { Thread { clear(appContext, stillHome) }.start() }
        pending = check
        main.postDelayed(check, CHECK_DELAY_MS)
    }

    fun cancel() {
        pending?.let { main.removeCallbacks(it) }
        pending = null
    }

    fun parse(output: String, candidates: Set<String>): Set<String> {
        val stuck = LinkedHashSet<String>()
        for (line in output.lines()) {
            val name = line.trim().removePrefix(SURFACE_VIEW)
            val pkg = WINDOW.find(name)?.groupValues?.get(1) ?: continue
            if (pkg in candidates) stuck.add(pkg)
        }
        return stuck
    }

    private fun clear(context: Context, stillHome: () -> Boolean) {
        try {
            val adb = LocalAdb(context)
            val layers = adb.shell(COMMAND, false)
            val stuck = parse(layers, BackgroundApps.launchable(context))
            if (stuck.isNotEmpty() && stillHome()) BackgroundApps.stop(adb, stuck)
        } catch (_: Exception) {}
    }
}
