package com.gathin.tvhome.core.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.gathin.tvhome.R

class SettingsBanner(context: Context) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val gear: Drawable = context.getDrawable(R.drawable.ic_settings_filled)!!.mutate()
    private var palette: Palette? = null

    fun setPalette(p: Palette) {
        palette = p
        invalidateSelf()
    }

    override fun draw(c: Canvas) {
        val p = palette ?: Palette.currentOrBaseline()
        val b = bounds
        c.drawColor(p.surfaceContainerHighest)
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = b.height() * 0.36f
        ExpressiveBackdrop.shapePath(path, cx, cy, r, 9, 0.07f, 0f)
        paint.color = p.primary
        c.drawPath(path, paint)
        val s = Math.round(r * 1.05f)
        gear.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f), Math.round(cx + s / 2f), Math.round(cy + s / 2f))
        gear.setTint(p.onPrimary)
        gear.draw(c)
    }

    override fun getIntrinsicWidth(): Int = 320

    override fun getIntrinsicHeight(): Int = 180

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.OPAQUE", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    companion object {
        const val PACKAGE = "com.android.tv.settings"
    }
}
