package com.gathin.tvhome.core.design

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.TextView
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import com.gathin.tvhome.core.motion.Expressive
import com.gathin.tvhome.core.motion.Motion

class LabelReveal(
    private val view: TextView,
    private val animations: Boolean,
    reducedMotion: Boolean
) {
    private val density: Float = view.resources.displayMetrics.density
    private val width: SpringAnimation
    private var widthPx = 0f

    init {
        val inset = Math.round((WIDTH_DP - ICON_DP) / 2f * density)
        view.setPaddingRelative(inset, 0, Math.round(END_DP * density), 0)
        view.compoundDrawablePadding = Math.round(LABEL_GAP_DP * density)
        view.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        view.isSingleLine = true
        view.ellipsize = null
        width = WIDTH_SPRING.reduced(reducedMotion).apply(SpringAnimation(this, WIDTH))
        width.setMinimumVisibleChange(1f)
        setWidth(collapsed())
        view.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { update() }
        })
    }

    fun update() {
        val target = if (view.isFocused || view.isActivated) expanded() else collapsed()
        if (!animations) {
            width.cancel()
            setWidth(target)
        } else {
            width.animateToFinalPosition(target)
        }
    }

    private fun collapsed(): Float = WIDTH_DP * density

    private fun expanded(): Float {
        val text = view.text
        if (text.isNullOrEmpty()) return collapsed()
        return view.paddingStart + ICON_DP * density + view.compoundDrawablePadding +
                view.paint.measureText(text.toString()) + view.paddingEnd
    }

    private fun setWidth(px: Float) {
        widthPx = px
        val open = expanded() - collapsed()
        val shown = if (open <= 0f) 0f else ((px - collapsed()) / open).coerceIn(0f, 1f)
        val e = Expressive.of(view)
        e?.labelAlpha(shown * shown)
        val lp = view.layoutParams ?: return
        val w = Math.round(maxOf(collapsed(), px))
        if (lp.width != w) {
            lp.width = w
            view.layoutParams = lp
        }
    }

    companion object {
        const val WIDTH_DP = 48f
        const val ICON_DP = 22f
        const val LABEL_GAP_DP = 8f
        const val END_DP = 16f
        private val WIDTH_SPRING = Motion.Spec(Motion.DEFAULT_SPATIAL.damping, Motion.FAST_SPATIAL.stiffness)

        private val WIDTH = object : FloatPropertyCompat<LabelReveal>("width") {
            override fun getValue(r: LabelReveal): Float = r.widthPx

            override fun setValue(r: LabelReveal, value: Float) {
                r.setWidth(value)
            }
        }
    }
}
