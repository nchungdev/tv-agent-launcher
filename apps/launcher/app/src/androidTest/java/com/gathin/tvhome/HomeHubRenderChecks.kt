package com.gathin.tvhome

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.util.LruCache
import com.gathin.tvhome.feature.main.widget.WidgetShelf
import com.gathin.tvhome.feature.watching.data.ContinueWatching
import com.gathin.tvhome.feature.watching.data.WatchItem
import java.io.File
import java.io.FileOutputStream

class HomeHubRenderChecks(private val runner: Instrumentation) {
    fun run(activity: Activity, directory: File) {
        val shelfField = activity.javaClass.getDeclaredField("widgetShelf")
        shelfField.isAccessible = true
        val shelf = shelfField.get(activity) as WidgetShelf
        val watchingField = shelf.javaClass.getDeclaredField("watching")
        watchingField.isAccessible = true
        val original = watchingField.get(shelf) as ContinueWatching
        val watching = ContinueWatching(activity.applicationContext)
        runner.runOnMainSync { watchingField.set(shelf, watching) }
        val itemsField = watching.javaClass.getDeclaredField("items")
        itemsField.isAccessible = true
        val posterField = watching.javaClass.getDeclaredField("posters")
        posterField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val posters = posterField.get(watching) as LruCache<String, Bitmap>
        val paintContinue = shelf.javaClass.getDeclaredMethod("paintContinue")
        paintContinue.isAccessible = true
        val item = WatchItem("Fixture · Xem tiếp", "Tập 2 · 24 phút còn lại", 0.4f,
            "qa://poster", "QA", "com.gathin.tvhome", "app.open", emptyMap(), 1_440_000L)
        val poster = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        Canvas(poster).apply {
            drawColor(Color.rgb(35, 67, 90))
            val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(112, 182, 197)
            }
            drawCircle(485f, 115f, 85f, pen)
            pen.color = Color.WHITE
            pen.textSize = 42f
            drawText("CONTINUE WATCHING", 32f, 285f, pen)
        }
        runner.runOnMainSync {
            posters.put("qa://poster", poster)
            itemsField.set(watching, listOf(item))
            paintContinue.invoke(shelf)
        }
        runner.waitForIdleSync()
        val laidOut = java.util.concurrent.CountDownLatch(1)
        runner.runOnMainSync {
            activity.findViewById<View>(R.id.widgetContinue).postOnAnimation {
                activity.findViewById<View>(R.id.widgetContinue).postOnAnimation {
                    laidOut.countDown()
                }
            }
        }
        check(laidOut.await(10, java.util.concurrent.TimeUnit.SECONDS)) { "layout frame timed out" }
        runner.runOnMainSync {
            ClockRenderChecks.run(activity)
            val info = activity.findViewById<View>(R.id.clockInfo)
            val tiles = activity.findViewById<View>(R.id.smartHomeTiles)
            val card = activity.findViewById<View>(R.id.widgetContinue)
            val clock = activity.findViewById<View>(R.id.widgetClock)
            val firstTile = activity.findViewById<View>(R.id.tileTemp)
            check(tiles.top >= info.bottom) { "smarthome is still beside clock" }
            check(card.visibility == View.VISIBLE &&
                card.left + card.paddingLeft >= clock.right - clock.paddingRight) {
                "continue bounds: visibility=${card.visibility}, left=${card.left}, " +
                    "clockRight=${clock.right}, width=${card.width}"
            }
            clock.requestFocus()
            clock.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
            check(firstTile.hasFocus()) { "Down did not enter smarthome" }
            firstTile.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
            check(clock.hasFocus()) { "Up did not return to clock" }
        }
        save(directory, "hub-continue.png")
        runner.runOnMainSync {
            activity.findViewById<View>(R.id.widgetContinue).requestFocus()
            itemsField.set(watching, emptyList<WatchItem>())
            paintContinue.invoke(shelf)
            check(activity.findViewById<View>(R.id.widgetContinue).visibility == View.VISIBLE)
            check(activity.findViewById<TextView>(R.id.contTitle).text == item.title)
            activity.findViewById<View>(R.id.btnSettings).requestFocus()
        }
        runner.waitForIdleSync()
        runner.runOnMainSync {
            check(activity.findViewById<View>(R.id.widgetContinue).visibility == View.GONE)
        }
        save(directory, "hub-empty.png")
        posters.remove("qa://poster")
        runner.runOnMainSync {
            watchingField.set(shelf, original)
            paintContinue.invoke(shelf)
        }
    }

    private fun save(directory: File, name: String) {
        val bitmap = runner.uiAutomation.takeScreenshot()
        FileOutputStream(File(directory, name)).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
