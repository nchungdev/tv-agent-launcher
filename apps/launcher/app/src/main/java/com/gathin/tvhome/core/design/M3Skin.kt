package com.gathin.tvhome.core.design

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.view.ShapeBadge
import com.gathin.tvhome.core.motion.Expressive
import com.gathin.tvhome.core.motion.Motion

class M3Skin private constructor(
    private val context: Context,
    private val p: Palette,
    private val animations: Boolean,
    private val reducedMotion: Boolean
) {
    private val density: Float = context.resources.displayMetrics.density
    private var menuPanel: ViewGroup? = null

    private fun walk(v: View, inPanel: Boolean, inRow: Boolean) {
        val tag = v.tag
        val attached = Expressive.of(v)
        attached?.palette(p)
        var isRow = false
        var currentInPanel = inPanel

        if ("m3:panel" == tag) {
            menuPanel = if (v is ViewGroup && skinPanel(v)) v else null
            currentInPanel = true
        } else if (currentInPanel && !inRow && v is ViewGroup && v.isFocusable() && v.isClickable()) {
            skinRow(v, menuPanel != null)
            isRow = true
        } else if ("m3:button" == tag && v is TextView && Expressive.of(v) == null) {
            Expressive.attach(v, animations, reducedMotion, BUTTON_FOCUS_SCALE).palette(p)
        } else if ("m3:choice" == tag && v is TextView && Expressive.of(v) == null) {
            skinChoice(v)
        } else if ("m3:listitem" == tag && v is TextView && Expressive.of(v) == null) {
            skinListItem(v)
        } else if (v is ShapeBadge) {
            v.setPalette(p)
        }

        if (v is TextView && Expressive.of(v) == null) skinText(v, inRow || isRow)
        if (v is ImageView) skinIcon(v, inRow || isRow)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), currentInPanel, inRow || isRow)
        }
    }

    private fun skinPanel(v: View): Boolean {
        val menu = v.paddingTop <= (MENU_PADDING_DP * density).toInt()
        val d = GradientDrawable()
        d.setColor(p.surfaceContainerHigh)
        d.cornerRadius = (if (menu) MENU_RADIUS_DP else DIALOG_RADIUS_DP) * density
        if (menu && v is ViewGroup) {
            MenuHighlight.attach(v, d, p.inverseSurface, MENU_ROW_DP / 2f * density, animations, reducedMotion)
        } else {
            v.background = d
        }
        val pad = (if (menu) MENU_PADDING_DP else DIALOG_PADDING_DP) * density
        v.setPadding(pad.toInt(), pad.toInt(), pad.toInt(), if (menu) pad.toInt() else (20 * density).toInt())
        v.elevation = if (menu) 3 * density else 6 * density
        return menu && v is ViewGroup
    }

    private fun skinRow(v: View, inMenu: Boolean) {
        val lp = v.layoutParams
        if (lp != null && lp.height > 0 && lp.height < MENU_ROW_DP * density) {
            lp.height = (MENU_ROW_DP * density).toInt()
        }
        val padH = (16 * density).toInt()
        v.setPadding(padH, v.paddingTop, padH, v.paddingBottom)
        if (inMenu) {
            MenuHighlight.markRow(v)
            v.background = null
            return
        }
        val states = StateListDrawable()
        states.addState(intArrayOf(android.R.attr.state_focused), rounded(p.inverseSurface, MENU_ROW_DP / 2f))
        states.addState(intArrayOf(), rounded(0, MENU_ROW_DP / 2f))
        if (animations) {
            states.setEnterFadeDuration(Motion.FAST_EFFECTS.durationMs().toInt())
            states.setExitFadeDuration(Motion.FAST_EFFECTS.durationMs().toInt())
        }
        v.background = states
    }

    private fun skinChoice(v: TextView) {
        val e = Expressive.attach(v, animations, reducedMotion, 1f).asChoice().palette(p)
        val parent = v.parent as ViewGroup
        var count = 0
        var index = -1
        for (i in 0 until parent.childCount) {
            val c = parent.getChildAt(i)
            if (c.background == null && Expressive.of(c) == null) continue
            if (c === v) index = count
            count++
        }
        val inner = Shapes.SMALL * density
        val full = Expressive.FULL
        val group = parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL && count > 1
        if (group) {
            val first = index == 0
            val last = index == count - 1
            e.corners(
                floatArrayOf(if (first) full else inner, if (last) full else inner, if (last) full else inner, if (first) full else inner),
                floatArrayOf(full, full, full, full)
            )
            val lp = v.layoutParams as ViewGroup.MarginLayoutParams
            lp.marginEnd = if (last) 0 else (2 * density).toInt()
            lp.marginStart = 0
            v.layoutParams = lp
        } else {
            val rest = Shapes.LARGE * density
            e.corners(floatArrayOf(rest, rest, rest, rest), floatArrayOf(full, full, full, full))
        }
        val lp = v.layoutParams
        lp.height = (40 * density).toInt()
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        v.typeface = font(R.font.sans_medium)
        val pad = (16 * density).toInt()
        v.setPadding(pad, 0, pad, 0)
        e.update()
    }

    private fun skinListItem(v: TextView) {
        val e = Expressive.attach(v, animations, reducedMotion, LIST_ITEM_FOCUS_SCALE).asListItem().tintIcons(false).palette(p)
        val parent = v.parent as ViewGroup
        var count = 0
        var index = -1
        for (i in 0 until parent.childCount) {
            val c = parent.getChildAt(i)
            if ("m3:listitem" != c.tag || c.visibility == View.GONE) continue
            if (c === v) index = count
            count++
        }
        val outer = Shapes.LARGE_INCREASED * density
        val inner = Shapes.EXTRA_SMALL * density
        val focus = Shapes.EXTRA_LARGE * density
        val first = index == 0
        val last = index == count - 1
        e.corners(
            floatArrayOf(if (first) outer else inner, if (first) outer else inner, if (last) outer else inner, if (last) outer else inner),
            floatArrayOf(focus, focus, focus, focus)
        )
        if (!first) {
            val lp = v.layoutParams as ViewGroup.MarginLayoutParams
            lp.topMargin = (2 * density).toInt()
            v.layoutParams = lp
        }
        e.update()
    }

    private fun skinText(t: TextView, inRow: Boolean) {
        if ("m3:grouplabel" == t.tag) {
            t.setTextColor(p.primary)
            return
        }
        val saved = t.getTag(R.id.m3_text_role)
        val role: Int = if (saved is Int) {
            saved
        } else {
            val c = t.textColors
            val rest = c.defaultColor
            val focused = c.getColorForState(intArrayOf(android.R.attr.state_focused), rest)
            val calculated = (if ((rest ushr 24) >= 0xE6) 1 else 0) or (if (focused != rest) 2 else 0)
            t.setTag(R.id.m3_text_role, calculated)
            calculated
        }
        val strong = (role and 1) != 0
        if (inRow || (role and 2) != 0) {
            val on = if (strong) p.inverseOnSurface else Palette.alpha(p.inverseOnSurface, 0xCC)
            t.setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(on, if (strong) p.onSurface else p.onSurfaceVariant)))
        } else {
            t.setTextColor(if (strong) p.onSurface else p.onSurfaceVariant)
        }
        val tf = t.typeface
        if (tf == null || tf == Typeface.DEFAULT || tf == Typeface.DEFAULT_BOLD) {
            val sp = t.textSize / context.resources.displayMetrics.scaledDensity
            t.typeface = font(if (sp >= 28) R.font.sans_headline else if (sp >= 18) R.font.sans_medium else R.font.sans_regular)
        }
        if (Build.VERSION.SDK_INT >= 28 && t.isAllCaps) {
            t.isAllCaps = false
            t.letterSpacing = 0.01f
        }
    }

    private fun skinIcon(i: ImageView, inRow: Boolean) {
        val tint = i.imageTintList
        if (tint == null && !inRow) return
        i.imageTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(p.inverseOnSurface, p.onSurfaceVariant))
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = radiusDp * density
        return d
    }

    private fun font(res: Int): Typeface {
        return try {
            context.resources.getFont(res)
        } catch (_: RuntimeException) {
            Typeface.DEFAULT
        }
    }

    companion object {
        const val MENU_PADDING_DP = 8f
        const val MENU_ROW_DP = 48f
        const val MENU_RADIUS_DP = MENU_ROW_DP / 2f + MENU_PADDING_DP
        const val DIALOG_RADIUS_DP = Shapes.EXTRA_LARGE
        const val DIALOG_PADDING_DP = 24f
        const val BUTTON_FOCUS_SCALE = 1.1f
        const val LIST_ITEM_FOCUS_SCALE = 1f

        @JvmStatic
        fun apply(root: View, p: Palette, animations: Boolean, reducedMotion: Boolean) {
            val skin = M3Skin(root.context, p, animations, reducedMotion)
            if (root.background is ColorDrawable) root.setBackgroundColor(p.surface)
            skin.walk(root, inPanel = false, inRow = false)
        }

        @JvmStatic
        fun refresh(root: View) {
            val e = Expressive.of(root)
            e?.update()
            if (root is ViewGroup) {
                for (i in 0 until root.childCount) refresh(root.getChildAt(i))
            }
        }
    }
}
