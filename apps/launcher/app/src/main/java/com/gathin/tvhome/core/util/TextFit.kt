package com.gathin.tvhome.core.util

import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.widget.TextView

object TextFit {

    private const val MIN_SP = 9f
    private const val SLACK_DP = 4f

    @JvmStatic
    fun attach(line: TextView) {
        val base = line.textSize
        val fit = Runnable {
            val room = line.width - line.paddingLeft - line.paddingRight
            setSize(line, base * scale(room.toFloat(), width(line, base) + slack(line)))
        }
        refitOn(line, line, fit)
    }

    @JvmStatic
    fun attachRow(name: TextView, value: TextView, row: View) {
        val nameBase = name.textSize
        val valueBase = value.textSize
        val fit = Runnable {
            val room = (name.width - name.paddingLeft - name.paddingRight +
                    value.width - value.paddingLeft - value.paddingRight).toFloat()
            val scale = scale(room, width(name, nameBase) + width(value, valueBase) + slack(name))
            setSize(name, nameBase * scale)
            setSize(value, valueBase * scale)
        }
        refitOn(value, row, fit)
        refitOn(name, row, fit)
    }

    private fun refitOn(text: TextView, sized: View, fit: Runnable) {
        text.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { sized.post(fit) }
        })
        sized.addOnLayoutChangeListener { v, l, _, r, _, ol, _, or, _ ->
            if (r - l != or - ol) v.post(fit)
        }
    }

    private fun scale(room: Float, needed: Float): Float {
        return if (room <= 0 || needed <= room) 1f else room / needed
    }

    private fun slack(view: View): Float {
        return SLACK_DP * view.resources.displayMetrics.density
    }

    private fun width(text: TextView, sizePx: Float): Float {
        val now = text.paint.textSize
        text.paint.textSize = sizePx
        val width = text.paint.measureText(text.text.toString())
        text.paint.textSize = now
        return width
    }

    private fun setSize(text: TextView, px: Float) {
        val min = MIN_SP * text.resources.displayMetrics.scaledDensity
        val size = maxOf(min, px)
        if (Math.abs(text.textSize - size) > 0.5f) text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }
}
