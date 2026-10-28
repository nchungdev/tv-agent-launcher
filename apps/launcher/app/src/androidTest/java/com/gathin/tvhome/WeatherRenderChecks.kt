package com.gathin.tvhome

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Debug
import android.os.SystemClock
import android.view.View
import com.gathin.tvhome.feature.main.coordinator.HomeWeatherCoordinator
import com.gathin.tvhome.feature.main.data.SkyPlacement
import com.gathin.tvhome.feature.main.data.WeatherSceneState
import com.gathin.tvhome.feature.main.widget.SkyView
import com.gathin.tvhome.feature.system.data.Weather
import java.io.File
import java.io.FileOutputStream

class WeatherRenderChecks(private val runner: Instrumentation) {
    fun run(hubOnly: Boolean = false): String {
        val name = "com.gathin.tvhome.feature.main.ui.MainActivity"
        val host = runner as AgentRenderInstrumentation
        runner.targetContext.startActivity(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME).setClassName(runner.targetContext, name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var ready: android.app.Activity? = null
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (ready == null && SystemClock.uptimeMillis() < deadline) {
            runner.runOnMainSync {
                val latest = host.activeHome
                if (latest != null && !latest.isDestroyed &&
                    latest.window.decorView.isAttachedToWindow) ready = latest
            }
            if (ready == null) SystemClock.sleep(20)
        }
        val activity = ready ?: error("Home never attached its window")
        runner.waitForIdleSync()
        val sky = activity.findViewById<SkyView>(R.id.sky)
        runner.runOnMainSync {
            val field = activity.javaClass.getDeclaredField("weatherCoordinator")
            field.isAccessible = true
            (field.get(activity) as HomeWeatherCoordinator).stop()
            sky.setIntensity(100)
        }
        val directory = File(runner.targetContext.getExternalFilesDir(null), "weather-qa")
        directory.mkdirs()
        if (hubOnly) {
            HomeHubRenderChecks(runner).run(activity, directory)
            return "smarthome below clock, right continue card, remote and focus pinning passed"
        }
        for (day in listOf(true, false)) for (kind in listOf(Weather.Sky.CLEAR,
            Weather.Sky.PARTLY, Weather.Sky.CLOUDY, Weather.Sky.RAIN, Weather.Sky.UNKNOWN)) {
            val state = WeatherSceneState(kind, day,
                if (kind == Weather.Sky.CLEAR) 0 else 2, WeatherSceneState.Freshness.CURRENT)
            runner.sendStatus(0, android.os.Bundle().apply {
                putString("scene", "$kind-$day")
            })
            runner.runOnMainSync {
                sky.setScene(state)
                check(kind == Weather.Sky.UNKNOWN || sky.decorationCount > 0) {
                    "no decoration fits in actual Home: $kind"
                }
            }
            runner.waitForIdleSync()
            save(runner.uiAutomation.takeScreenshot(), File(directory, "$kind-$day.png"))
        }
        var statistics = ""
        runner.runOnMainSync {
            sky.setScene(WeatherSceneState(Weather.Sky.PARTLY, true, 2,
                WeatherSceneState.Freshness.CURRENT))
            val builds = sky.geometryBuilds
            repeat(60) {
                sky.setScene(WeatherSceneState(Weather.Sky.PARTLY, true, 2,
                    WeatherSceneState.Freshness.CACHED))
            }
            check(sky.geometryBuilds == builds) { "unchanged scene rebuilt geometry" }
            sky.setScene(WeatherSceneState(Weather.Sky.RAIN, true, 2,
                WeatherSceneState.Freshness.CURRENT))
            check(sky.geometryBuilds == builds + 1) { "same-minute change was ignored" }
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(960f / sky.width, 540f / sky.height)
            repeat(30) { sky.draw(canvas) }
            val allocated = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
            val start = Debug.threadCpuTimeNanos()
            repeat(300) { sky.draw(canvas) }
            val cpu = Debug.threadCpuTimeNanos() - start
            val bytes = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong() - allocated
            statistics = "cached draw CPU ns/frame=${cpu / 300}; process allocated bytes=$bytes"
            bitmap.recycle()
            for (width in listOf(1280, 1920)) {
                val preview = SkyView(activity)
                val height = width * 9 / 16
                preview.setIntensity(100)
                preview.layout(0, 0, width, height)
                preview.setSceneBounds(SkyPlacement.Box(width * 0.04f, height * 0.2f,
                    width * 0.96f, height * 0.75f), emptyList())
                preview.setScene(WeatherSceneState(Weather.Sky.PARTLY, true, 2,
                    WeatherSceneState.Freshness.CURRENT))
                val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                preview.draw(Canvas(image))
                save(image, File(directory, "canvas-$width.png"))
            }
        }
        val counts = IntArray(2)
        runner.waitForIdleSync()
        runner.runOnMainSync { counts[0] = sky.drawCount; counts[1] = sky.geometryBuilds }
        SystemClock.sleep(1500)
        runner.runOnMainSync {
            check(sky.drawCount == counts[0]) { "idle sky scheduled redraw" }
            check(sky.geometryBuilds == counts[1]) { "idle sky rebuilt paths" }
            activity.findViewById<View>(R.id.bottomDock).requestFocus()
        }
        runner.waitForIdleSync()
        save(runner.uiAutomation.takeScreenshot(), File(directory, "dock-focus.png"))
        if (activity.resources.displayMetrics.widthPixels /
            activity.resources.displayMetrics.density >= 840) {
            HomeHubRenderChecks(runner).run(activity, directory)
        }
        return "10 scenes, 720p/1080p Canvas, same-minute update, idle reuse passed; $statistics"
    }

    private fun save(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
