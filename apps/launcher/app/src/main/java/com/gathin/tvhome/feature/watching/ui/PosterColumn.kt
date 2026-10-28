package com.gathin.tvhome.feature.watching.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.gathin.tvhome.R

class PosterColumn @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        orientation = VERTICAL
    }

    fun widthFor(height: Int): Int {
        val anyWidth = MeasureSpec.makeMeasureSpec(height * WIDE, MeasureSpec.AT_MOST)
        val posterHeight = (height - fixedHeight(anyWidth)).coerceAtLeast(0)
        return posterHeight * WIDE / TALL + paddingLeft + paddingRight
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val poster = findViewById<View>(R.id.contPosterFrame)
        if (poster == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val inner = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val room = MeasureSpec.getSize(heightMeasureSpec) - fixedHeight(widthMeasureSpec)
        val posterHeight = minOf(inner * TALL / WIDE, room).coerceAtLeast(0)
        val params = poster.layoutParams as LayoutParams
        params.height = posterHeight
        params.weight = 0f

        val width = posterHeight * WIDE / TALL + paddingLeft + paddingRight
        val exact = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        super.onMeasure(exact, heightMeasureSpec)
    }

    private fun fixedHeight(widthMeasureSpec: Int): Int {
        val anyHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var total = paddingTop + paddingBottom
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams as LayoutParams
            total += params.topMargin + params.bottomMargin
            if (child.id == R.id.contPosterFrame) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, anyHeight, 0)
            total += child.measuredHeight
        }
        return total
    }

    private companion object {
        const val WIDE = 16
        const val TALL = 9
    }
}
