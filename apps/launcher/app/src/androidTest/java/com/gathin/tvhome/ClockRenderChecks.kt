package com.gathin.tvhome

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.widget.TextView
import kotlin.math.abs

object ClockRenderChecks {
    fun run(activity: Activity) {
        val time = activity.findViewById<TextView>(R.id.clockTime)
        val period = activity.findViewById<TextView>(R.id.clockAmPm)
        val date = activity.findViewById<TextView>(R.id.clockDay)
        val timeBottom = ink(time).bottom
        if (period.text.isNotEmpty()) {
            check(abs(ink(period).bottom - timeBottom) <= 1) { "AM/PM ink is not bottom aligned" }
        }
        val dateGap = ink(date).top - timeBottom
        val density = activity.resources.displayMetrics.density
        check(dateGap >= 0 && dateGap <= 20 * density) { "clock/date gap is $dateGap pixels" }
        val info = activity.findViewById<View>(R.id.clockInfo)
        val tiles = activity.findViewById<View>(R.id.smartHomeTiles)
        check(tiles.top - info.bottom >= 20 * density) { "smart-home section gap is too small" }
    }

    private fun ink(view: TextView): Rect {
        val text = view.text.toString()
        val box = Rect()
        view.paint.getTextBounds(text, 0, text.length, box)
        val location = IntArray(2)
        view.getLocationInWindow(location)
        box.offset(location[0] + view.totalPaddingLeft, location[1] + view.baseline)
        return box
    }
}
