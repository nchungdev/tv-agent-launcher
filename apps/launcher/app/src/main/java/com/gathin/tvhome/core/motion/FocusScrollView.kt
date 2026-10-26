package com.gathin.tvhome.core.motion

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.widget.ScrollView
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation

class FocusScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    private val focusedBounds = Rect()
    private var scrollSpring: SpringAnimation? = null
    private var animations = true

    init {
        isSmoothScrollingEnabled = false
    }

    fun setMotion(animations: Boolean, reducedMotion: Boolean) {
        this.animations = animations
        scrollSpring = Motion.DEFAULT_SPATIAL.reduced(reducedMotion)
            .apply(SpringAnimation(this, SCROLL_Y))
    }

    override fun executeKeyEvent(event: KeyEvent): Boolean {
        val direction = verticalDirection(event)
        if (direction == 0) return super.executeKeyEvent(event)

        val focused = findFocus()
        val next = FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: return super.executeKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) next.requestFocus(direction)
        return true
    }

    override fun requestChildFocus(child: View?, focused: View) {
        super.requestChildFocus(child, focused)
        centre(focused)
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0

    private fun centre(focused: View) {
        if (childCount == 0 || !isLaidOut) return

        focused.getDrawingRect(focusedBounds)
        offsetDescendantRectToMyCoords(focused, focusedBounds)
        val visibleHeight = height - paddingTop - paddingBottom
        val maxScroll = maxOf(0, getChildAt(0).height - visibleHeight)
        val centred = focusedBounds.centerY() - paddingTop - visibleHeight / 2
        val target = centred.coerceIn(0, maxScroll)
        if (target == scrollY && (scrollSpring == null || !scrollSpring!!.isRunning)) return

        val sp = scrollSpring
        if (!animations || sp == null) {
            scrollTo(scrollX, target)
            return
        }

        sp.animateToFinalPosition(target.toFloat())
    }

    companion object {
        private val SCROLL_Y = object : FloatPropertyCompat<FocusScrollView>("scrollY") {
            override fun getValue(view: FocusScrollView): Float = view.scrollY.toFloat()

            override fun setValue(view: FocusScrollView, value: Float) {
                view.scrollTo(view.scrollX, Math.round(value))
            }
        }

        private fun verticalDirection(event: KeyEvent): Int {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
                KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                else -> 0
            }
        }
    }
}
