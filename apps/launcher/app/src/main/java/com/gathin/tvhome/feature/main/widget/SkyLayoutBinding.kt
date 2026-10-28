package com.gathin.tvhome.feature.main.widget

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.main.data.SkyPlacement

class SkyLayoutBinding(private val root: View, private val sky: SkyView) :
    ViewTreeObserver.OnGlobalLayoutListener, View.OnAttachStateChangeListener {
    private val location = IntArray(2)
    private val origin = IntArray(2)
    private val gap = 10 * root.resources.displayMetrics.density
    private val protectedIds = intArrayOf(R.id.clockTime, R.id.clockAmPm, R.id.clockDay,
        R.id.clockLunar, R.id.clockDate, R.id.tileTemp, R.id.tileLight, R.id.tileSwitch)

    init {
        root.addOnAttachStateChangeListener(this)
        if (root.isAttachedToWindow) onViewAttachedToWindow(root)
    }

    override fun onGlobalLayout() {
        sky.getLocationInWindow(origin)
        val header = root.findViewById<View>(R.id.headerRow)
        val dock = root.findViewById<View>(R.id.bottomDock)
        val insets = root.rootWindowInsets
        val left = (insets?.systemWindowInsetLeft ?: 0).toFloat() + gap
        val right = sky.width - (insets?.systemWindowInsetRight ?: 0) - gap
        val top = if (header.isShown) bounds(header).bottom + gap else gap
        val bottom = if (dock.isShown) bounds(dock).top - gap * 2 else
            sky.height - (insets?.systemWindowInsetBottom ?: 0) - gap
        val blocked = ArrayList<SkyPlacement.Box>()
        for (id in protectedIds) {
            val content = root.findViewById<View>(id)
            if (content.isShown && content.width > 0 && content.height > 0) {
                blocked.add(bounds(content).padded(gap))
            }
        }
        val shelf = root.findViewById<ViewGroup>(R.id.widgetShelf)
        for (index in 0 until shelf.childCount) {
            val card = shelf.getChildAt(index)
            if (card.id != R.id.widgetClock && card.isShown) {
                blocked.add(bounds(card).padded(gap))
            }
        }
        sky.setSceneBounds(SkyPlacement.Box(left, top, right, bottom), blocked)
    }

    private fun bounds(view: View): SkyPlacement.Box {
        view.getLocationInWindow(location)
        val extraX = view.width * 0.08f
        val extraY = view.height * 0.08f
        val left = location[0] - origin[0] - extraX
        val top = location[1] - origin[1] - extraY
        return SkyPlacement.Box(left, top, left + view.width + extraX * 2,
            top + view.height + extraY * 2)
    }

    override fun onViewAttachedToWindow(view: View) {
        root.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onViewDetachedFromWindow(view: View) {
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }
}
