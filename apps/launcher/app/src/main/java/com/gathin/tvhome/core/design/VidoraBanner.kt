package com.gathin.tvhome.core.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import com.gathin.tvhome.R

class VidoraBanner(context: Context, private val appIcon: Drawable?) : Drawable() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackIcon: Drawable? = context.getDrawable(R.drawable.ic_w_cloud)?.mutate()
    private val textBounds = Rect()

    init {
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textPaint.letterSpacing = 0.12f
        fallbackIcon?.setTint(0xFF38BDF8.toInt())
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        bgPaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(0xFF0A0F1D.toInt(), 0xFF162035.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(b, bgPaint)

        val text = "VIDORA"
        val textSize = h * 0.18f
        textPaint.textSize = textSize
        textPaint.getTextBounds(text, 0, text.length, textBounds)

        val icon = appIcon ?: fallbackIcon
        val iconSize = (h * 0.28f).toInt()
        val gap = (w * 0.04f).toInt()
        val textWidth = textPaint.measureText(text)
        val totalWidth = if (icon != null) iconSize + gap + textWidth else textWidth

        val startX = b.exactCenterX() - totalWidth / 2f
        val cy = b.exactCenterY()

        if (icon != null) {
            val left = startX.toInt()
            val top = (cy - iconSize / 2f).toInt()
            icon.setBounds(left, top, left + iconSize, top + iconSize)
            icon.draw(canvas)
        }

        val textX = if (icon != null) startX + iconSize + gap else startX
        val textY = cy - textBounds.exactCenterY()
        canvas.drawText(text, textX, textY, textPaint)
    }

    override fun getIntrinsicWidth(): Int = 320

    override fun getIntrinsicHeight(): Int = 180

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.OPAQUE", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    companion object {
        const val PACKAGE = "com.duinch.vidora"
    }
}
