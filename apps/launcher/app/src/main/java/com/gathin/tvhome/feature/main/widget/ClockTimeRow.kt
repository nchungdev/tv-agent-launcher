package com.gathin.tvhome.feature.main.widget

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R

class ClockTimeRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val timeInk = Rect()
    private val periodInk = Rect()
    private var nominalHeight = (100 * resources.displayMetrics.density).toInt()

    fun setNominalHeight(pixels: Int) {
        if (nominalHeight == pixels) return
        nominalHeight = pixels
        requestLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val time = findViewById<TextView>(R.id.clockTime)
        val period = findViewById<TextView>(R.id.clockAmPm)
        if (period.text.isNullOrEmpty()) return
        val timeText = time.text.toString()
        val periodText = period.text.toString()
        time.paint.getTextBounds(timeText, 0, timeText.length, timeInk)
        period.paint.getTextBounds(periodText, 0, periodText.length, periodInk)
        val alignedTop = time.top + time.baseline + timeInk.bottom -
            period.baseline - periodInk.bottom
        period.layout(period.left, alignedTop, period.right, alignedTop + period.measuredHeight)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val time = findViewById<TextView>(R.id.clockTime)
        val period = findViewById<TextView>(R.id.clockAmPm)
        val height = nominalHeight - paddingTop - paddingBottom
        val size = height * 0.85f
        period.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.27f)
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)

        val margins = period.layoutParams as LayoutParams
        val periodWidth = period.paint.measureText(period.text.toString())
        val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight -
            periodWidth - margins.leftMargin - margins.rightMargin).coerceAtLeast(0f)
        val desired = time.paint.measureText(time.text.toString())
        if (desired > available && desired > 0f) {
            time.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * available / desired)
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val text = time.text.toString()
        time.paint.getTextBounds(text, 0, text.length, timeInk)
        val visibleHeight = paddingTop + time.baseline + timeInk.bottom + paddingBottom +
            (2 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(measuredWidth, resolveSize(visibleHeight, heightMeasureSpec))
    }
}
