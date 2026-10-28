package com.gathin.tvhome

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.main.widget.BottomDockView
import com.gathin.tvhome.feature.watching.data.WatchItem
import java.io.File

class DockRenderChecks(private val instrumentation: Instrumentation) {
    private val targetContext get() = instrumentation.targetContext

    fun run(): String {
        instrumentation.runOnMainSync { checkDock() }
        return "empty, preview, focus pinning, removal and D-pad passed"
    }

    private fun checkDock() {
        val context = targetContext
        val density = context.resources.displayMetrics.density
        val dock = BottomDockView(context, null)
        dock.setMotion(false, true)
        dock.setPalette(Palette.currentOrBaseline())
        var menusOpened = 0
        val listener = object : BottomDockView.Listener {
            override fun onAllAppsClicked() {}
            override fun onAppClicked(item: BottomDockView.DockItem) {}
            override fun onAppLongClicked(item: BottomDockView.DockItem, anchor: View) {}
            override fun onItemFocused(index: Int, item: BottomDockView.DockItem?) {}
            override fun onNavigateUp(index: Int): Boolean = true
            override fun onWatchNextLongClicked(anchor: View) { menusOpened++ }
        }
        val apps = (1..7).map {
            BottomDockView.DockItem(
                "app-$it", "com.example.app$it", "App $it", context.getDrawable(R.drawable.ic_apps)
            )
        }
        dock.bindItems(apps, listener)
        check(dock.slotCount() == 8)

        val scene = FrameLayout(context)
        scene.setBackgroundColor(0xFF162032.toInt())
        scene.addView(dock, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER))
        val poster = poster()
        val first = item("Interstellar")
        var opened = ""
        dock.bindWatchNext(first, poster) { opened = first.title }
        check(dock.slotCount() == 9)
        layout(scene, (900 * density).toInt(), (140 * density).toInt())
        check(dock.focusSlot(8))
        check(dock.findFocus().contentDescription.toString().contains(first.title))

        val second = item("Arrival")
        dock.bindWatchNext(second, poster) { opened = second.title }
        dock.findFocus().performClick()
        check(opened == "Interstellar")
        dock.focusSlot(7)
        dock.focusSlot(8)
        dock.findFocus().performClick()
        check(opened == "Arrival")
        dock.findFocus().performLongClick()
        check(menusOpened == 1)

        layout(scene, scene.width, scene.height)
        save(scene, "dock-watch-next-qa.png")
        dock.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        check(dock.lastIndex() == 7)
        dock.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        check(dock.lastIndex() == 8)

        dock.bindWatchNext(null, null) {}
        check(dock.slotCount() == 9)
        dock.focusSlot(7)
        check(dock.slotCount() == 8)
        dock.bindWatchNext(first, poster) {}
        dock.bindItems(apps, listener)
        check(dock.slotCount() == 9)

        layout(scene, (480 * density).toInt(), (140 * density).toInt())
        dock.focusSlot(8)
        layout(scene, scene.width, scene.height)
        check(dock.measuredWidth <= scene.measuredWidth)
        save(scene, "dock-watch-next-narrow-qa.png")
    }

    private fun item(title: String) = WatchItem(
        title, "Preview fixture", 0.42f, null, "Player", "com.example.player",
        "app.open", mapOf("package_name" to "com.example.player"), 25 * 60_000L
    )

    private fun poster(): Bitmap {
        val bitmap = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF18314F.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
        }
        canvas.drawText("WATCH NEXT", 30f, 150f, paint)
        return bitmap
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
    }

    private fun save(view: android.view.View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
