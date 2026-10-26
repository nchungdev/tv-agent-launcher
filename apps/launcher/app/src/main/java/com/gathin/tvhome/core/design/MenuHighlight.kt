package com.gathin.tvhome.core.design

import android.animation.ArgbEvaluator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import com.gathin.tvhome.R
import com.gathin.tvhome.core.motion.Motion

class MenuHighlight private constructor(
    private val panel: ViewGroup,
    private val fill: Drawable,
    color: Int,
    private val radius: Float,
    private val animations: Boolean,
    private val reducedMotion: Boolean
) : Drawable() {

    private val pill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topSpring: SpringAnimation
    private val bottomSpring: SpringAnimation
    private val rowRect = Rect()
    private var top = 0f
    private var bottom = 0f
    private var left = 0f
    private var right = 0f
    private var shown = false
    private val argb = ArgbEvaluator()
    private val tinted = ArrayList<Tinted>()
    private var collected = false

    private class Tinted(
        @JvmField val view: View,
        @JvmField val row: View,
        @JvmField val rest: Int,
        @JvmField val on: Int
    )

    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus -> moveTo(newFocus) }

    init {
        pill.color = color
        topSpring = TRAIL.reduced(reducedMotion).apply(SpringAnimation(this, TOP))
        bottomSpring = TRAIL.reduced(reducedMotion).apply(SpringAnimation(this, BOTTOM))
        topSpring.setMinimumVisibleChange(0.5f)
        bottomSpring.setMinimumVisibleChange(0.5f)
    }

    private fun followScroll() {
        if (!shown) return
        val row = rowOf(panel.findFocus()) ?: return
        topSpring.cancel()
        bottomSpring.cancel()
        rowRect.set(0, 0, row.width, row.height)
        panel.offsetDescendantRectToMyCoords(row, rowRect)
        top = rowRect.top.toFloat()
        bottom = rowRect.bottom.toFloat()
        moved()
    }

    private fun detach() {
        panel.viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
        topSpring.cancel()
        bottomSpring.cancel()
    }

    private fun moveTo(focused: View?) {
        val row = rowOf(focused)
        if (row == null) {
            if (shown) {
                shown = false
                moved()
            }
            return
        }
        rowRect.set(0, 0, row.width, row.height)
        panel.offsetDescendantRectToMyCoords(row, rowRect)
        left = rowRect.left.toFloat()
        right = rowRect.right.toFloat()
        if (!shown || !animations) {
            topSpring.cancel()
            bottomSpring.cancel()
            top = rowRect.top.toFloat()
            bottom = rowRect.bottom.toFloat()
            shown = true
            moved()
            return
        }
        val down = rowRect.top > top
        (if (down) TRAIL else LEAD).reduced(reducedMotion).apply(topSpring)
        (if (down) LEAD else TRAIL).reduced(reducedMotion).apply(bottomSpring)
        topSpring.animateToFinalPosition(rowRect.top.toFloat())
        bottomSpring.animateToFinalPosition(rowRect.bottom.toFloat())
    }

    private fun moved() {
        invalidateSelf()
        tint()
    }

    private fun tint() {
        if (!collected) {
            collected = true
            collect(panel, null)
        }
        for (t in tinted) {
            var cover = 0f
            if (shown && t.row.height > 0) {
                rowRect.set(0, 0, t.row.width, t.row.height)
                panel.offsetDescendantRectToMyCoords(t.row, rowRect)
                val overlap = minOf(rowRect.bottom.toFloat(), bottom) - maxOf(rowRect.top.toFloat(), top)
                cover = (overlap / rowRect.height()).coerceIn(0f, 1f)
            }
            val c = argb.evaluate(cover, t.rest, t.on) as Int
            if (t.view is TextView) t.view.setTextColor(c)
            else if (t.view is ImageView) t.view.imageTintList = ColorStateList.valueOf(c)
        }
    }

    private fun collect(v: View, parentRow: View?) {
        var row = parentRow
        if (java.lang.Boolean.TRUE == v.getTag(R.id.m3_menu_highlight)) row = v
        if (row != null) {
            val c = when (v) {
                is TextView -> v.textColors
                is ImageView -> v.imageTintList
                else -> null
            }
            if (c != null && c.isStateful) {
                val rest = c.defaultColor
                v.setTag(R.id.m3_menu_colors, intArrayOf(rest, c.getColorForState(FOCUSED, rest)))
            }
            val saved = v.getTag(R.id.m3_menu_colors)
            if (saved is IntArray) {
                tinted.add(Tinted(v, row, saved[0], saved[1]))
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) collect(v.getChildAt(i), row)
        }
    }

    private fun rowOf(v: View?): View? {
        var curr = v
        while (curr != null && curr !== panel) {
            if (java.lang.Boolean.TRUE == curr.getTag(R.id.m3_menu_highlight)) return curr
            curr = if (curr.parent is View) curr.parent as View else null
        }
        return null
    }

    override fun onBoundsChange(bounds: Rect) {
        fill.bounds = bounds
    }

    override fun draw(c: Canvas) {
        fill.draw(c)
        if (!shown) return
        val r = minOf(radius, (bottom - top) / 2f)
        c.drawRoundRect(left, top, right, bottom, r, r, pill)
    }

    override fun getOutline(outline: Outline) {
        fill.getOutline(outline)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private val LEAD = Motion.Spec(Motion.DEFAULT_SPATIAL.damping, Motion.FAST_SPATIAL.stiffness)
        private val TRAIL = Motion.DEFAULT_SPATIAL
        private val FOCUSED = intArrayOf(android.R.attr.state_focused)

        private val TOP = object : FloatPropertyCompat<MenuHighlight>("top") {
            override fun getValue(h: MenuHighlight): Float = h.top

            override fun setValue(h: MenuHighlight, value: Float) {
                h.top = value
                h.moved()
            }
        }

        private val BOTTOM = object : FloatPropertyCompat<MenuHighlight>("bottom") {
            override fun getValue(h: MenuHighlight): Float = h.bottom

            override fun setValue(h: MenuHighlight, value: Float) {
                h.bottom = value
                h.moved()
            }
        }

        @JvmStatic
        fun attach(panel: ViewGroup, fill: Drawable, color: Int, radiusPx: Float, animations: Boolean, reducedMotion: Boolean) {
            val existing = panel.getTag(R.id.m3_menu_highlight)
            if (existing is MenuHighlight) existing.detach()
            val h = MenuHighlight(panel, fill, color, radiusPx, animations, reducedMotion)
            panel.setTag(R.id.m3_menu_highlight, h)
            panel.background = h
            panel.viewTreeObserver.addOnGlobalFocusChangeListener(h.focusListener)
            val scroll = findScroll(panel)
            scroll?.setOnScrollChangeListener { _, _, _, _, _ -> h.followScroll() }
            panel.post { h.moveTo(panel.findFocus()) }
        }

        @JvmStatic
        fun markRow(row: View) {
            row.setTag(R.id.m3_menu_highlight, java.lang.Boolean.TRUE)
        }

        private fun findScroll(v: View): ScrollView? {
            if (v is ScrollView) return v
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    val s = findScroll(v.getChildAt(i))
                    if (s != null) return s
                }
            }
            return null
        }
    }
}
