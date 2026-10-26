package com.gathin.tvhome.feature.main.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.DockIconNormalizer
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.motion.Motion
import com.gathin.tvhome.core.util.SoundEffects


class BottomDockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    data class DockItem(
        val key: String, val packageName: String, val label: String, val icon: Drawable?
    )

    interface Listener {
        fun onAllAppsClicked()
        fun onAppClicked(item: DockItem)
        fun onAppLongClicked(item: DockItem, anchor: View)
        fun onItemFocused(index: Int, item: DockItem?)
        fun onNavigateUp(index: Int): Boolean
    }

    private data class SlotStyle(
        val bg: GradientDrawable, val border: GradientDrawable, val allApps: Boolean, val edgeColor: Int
    )

    private val density = resources.displayMetrics.density
    private val dockBar = LinearLayout(context)
    private val tooltipView = TextView(context)
    private val barBackground = GradientDrawable()
    private val tooltipBackground = GradientDrawable()
    private val slotViews = ArrayList<View>()
    private val slotStyles = ArrayList<SlotStyle>()
    private var dividerView: View? = null
    private var allAppsIcon: ImageView? = null
    private var palette = Palette.currentOrBaseline()
    private var animations = true
    private var reducedMotion = false
    private var listener: Listener? = null
    private var lastFocusedIndex = 0

    init {
        clipChildren = false
        clipToPadding = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        tooltipBackground.cornerRadius = dp(14).toFloat()
        tooltipView.background = tooltipBackground
        tooltipView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        tooltipView.typeface = resources.getFont(R.font.sans_medium)
        tooltipView.maxLines = 1
        tooltipView.ellipsize = TextUtils.TruncateAt.END
        tooltipView.setPadding(dp(12), dp(5), dp(12), dp(5))
        tooltipView.alpha = 0f
        addView(tooltipView, LayoutParams(-2, -2, Gravity.TOP or Gravity.START))

        barBackground.cornerRadius = dp(26).toFloat()
        dockBar.orientation = LinearLayout.HORIZONTAL
        dockBar.gravity = Gravity.CENTER_VERTICAL
        dockBar.clipChildren = false
        dockBar.clipToPadding = false
        dockBar.background = barBackground
        dockBar.setPadding(dp(14), dp(10), dp(14), dp(10))
        dockBar.elevation = dp(8).toFloat()
        addView(dockBar, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(40)
        })
        applyColors()
    }

    fun setMotion(animationsEnabled: Boolean, reducedMotionEnabled: Boolean) {
        animations = animationsEnabled
        reducedMotion = reducedMotionEnabled
    }

    fun setPalette(nextPalette: Palette) {
        palette = nextPalette
        applyColors()
    }

    fun bindItems(items: List<DockItem>, dockListener: Listener) {
        listener = dockListener
        dockBar.removeAllViews()
        slotViews.clear()
        slotStyles.clear()
        dividerView = null
        allAppsIcon = null

        val appsLabel = context.getString(R.string.app_library)
        val appsSlot = createSlotView(
            0, appsLabel, context.getDrawable(R.drawable.ic_grid_view), true, null
        )
        dockBar.addView(appsSlot)
        slotViews.add(appsSlot)

        if (items.isNotEmpty()) {
            val divParams = LinearLayout.LayoutParams(dp(1), dp(36)).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
            }
            dividerView = View(context).apply { layoutParams = divParams }.also { dockBar.addView(it) }
        }

        for (index in items.indices) {
            val item = items[index]
            val appSlot = createSlotView(index + 1, item.label, item.icon, false, item)
            if (index > 0) (appSlot.layoutParams as LinearLayout.LayoutParams).marginStart = dp(10)
            dockBar.addView(appSlot)
            slotViews.add(appSlot)
        }
        applyColors()
    }

    fun slotCount(): Int = slotViews.size

    fun lastIndex(): Int = lastFocusedIndex

    fun focusSlot(index: Int): Boolean =
        slotViews.isNotEmpty() && slotViews[index.coerceIn(0, slotViews.size - 1)].requestFocus()

    private fun createSlotView(
        index: Int, label: String, iconDrawable: Drawable?, allApps: Boolean, dockItem: DockItem?
    ): View {
        val sizePx = dp(58)
        val radiusPx = dp(15).toFloat()
        val norm = if (allApps) null else DockIconNormalizer.normalize(resources, iconDrawable)
        val fullBleed = norm?.fullBleed == true
        val edgeColor = if (fullBleed) norm?.edgeColor ?: Color.TRANSPARENT else Color.TRANSPARENT
        val bgDrawable = GradientDrawable().apply { cornerRadius = radiusPx }
        val borderDrawable = GradientDrawable().apply {
            cornerRadius = radiusPx
            setColor(Color.TRANSPARENT)
        }
        val style = SlotStyle(bgDrawable, borderDrawable, allApps, edgeColor)
        slotStyles.add(style)

        val slot = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            background = bgDrawable
            foreground = borderDrawable
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                }
            }
            clipToOutline = true
        }

        val shownIcon = norm?.drawable ?: iconDrawable ?: context.getDrawable(R.drawable.ic_apps)
        val image = ImageView(context).apply {
            scaleType = if (fullBleed) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            setImageDrawable(shownIcon)
        }
        val iconSize = if (allApps) dp(28) else if (fullBleed) LayoutParams.MATCH_PARENT else dp(38)
        slot.addView(image, LayoutParams(iconSize, iconSize, Gravity.CENTER))
        if (allApps) allAppsIcon = image

        attachSlotFocus(slot, style, index, label, dockItem)
        slot.setOnClickListener {
            if (allApps) listener?.onAllAppsClicked()
            else if (dockItem != null) listener?.onAppClicked(dockItem)
        }
        if (dockItem != null) {
            slot.setOnLongClickListener { view ->
                listener?.onAppLongClicked(dockItem, view)
                true
            }
        }
        slot.setOnKeyListener { _, keyCode, event -> handleSlotKey(index, keyCode, event) }
        return slot
    }

    private fun attachSlotFocus(
        slot: View, style: SlotStyle, index: Int, label: String, item: DockItem?
    ) {
        val spec = Motion.FAST_SPATIAL.reduced(reducedMotion)
        val scaleX = spec.apply(SpringAnimation(slot, DynamicAnimation.SCALE_X))
        val scaleY = spec.apply(SpringAnimation(slot, DynamicAnimation.SCALE_Y))
        val liftY = spec.apply(SpringAnimation(slot, DynamicAnimation.TRANSLATION_Y))
        slot.setOnFocusChangeListener { view, hasFocus ->
            updateSlotStyle(style, hasFocus)
            val targetScale = if (hasFocus) FOCUS_SCALE else 1f
            val targetLift = if (hasFocus) -dp(6).toFloat() else 0f
            if (animations) {
                scaleX.animateToFinalPosition(targetScale)
                scaleY.animateToFinalPosition(targetScale)
                liftY.animateToFinalPosition(targetLift)
            } else {
                for (anim in arrayOf(scaleX, scaleY, liftY)) anim.cancel()
                view.scaleX = targetScale
                view.scaleY = targetScale
                view.translationY = targetLift
            }
            if (hasFocus) {
                lastFocusedIndex = index
                showTooltip(view, label)
                listener?.onItemFocused(index, item)
            } else post { if (!hasFocus()) hideTooltip() }
        }
    }

    private fun handleSlotKey(index: Int, keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (index > 0) moveHorizontalFocus(index - 1, SoundEffectConstants.NAVIGATION_LEFT)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (index + 1 < slotViews.size) {
                    moveHorizontalFocus(index + 1, SoundEffectConstants.NAVIGATION_RIGHT)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> return listener?.onNavigateUp(index) ?: false
            KeyEvent.KEYCODE_DPAD_DOWN -> return true
        }
        return false
    }

    private fun moveHorizontalFocus(targetIndex: Int, soundEffect: Int) {
        slotViews[targetIndex].requestFocus()
        if (SoundEffects.isEnabled(context)) playSoundEffect(soundEffect)
    }

    private fun showTooltip(slot: View, label: String) {
        tooltipView.text = label
        tooltipView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val slotCenter = dockBar.left + slot.left + slot.width / 2f
        val maxLeft = maxOf(0f, (width - tooltipView.measuredWidth).toFloat())
        tooltipView.translationX = (slotCenter - tooltipView.measuredWidth / 2f).coerceIn(0f, maxLeft)
        fadeTooltip(1f, 140L)
    }

    private fun hideTooltip() = fadeTooltip(0f, 120L)

    private fun fadeTooltip(targetAlpha: Float, durationMs: Long) {
        tooltipView.animate().cancel()
        if (animations) tooltipView.animate().alpha(targetAlpha).setDuration(durationMs).start()
        else tooltipView.alpha = targetAlpha
    }

    private fun applyColors() {
        barBackground.setColor(Palette.alpha(palette.surfaceContainerHigh, 0xD9))
        barBackground.setStroke(dp(1), Palette.alpha(palette.outlineVariant, 0x66))
        tooltipBackground.setColor(Palette.alpha(palette.inverseSurface, 0xF2))
        tooltipView.setTextColor(palette.inverseOnSurface)
        dividerView?.setBackgroundColor(Palette.alpha(palette.outlineVariant, 0x55))
        allAppsIcon?.imageTintList = ColorStateList.valueOf(palette.onSurface)
        for (index in slotViews.indices) {
            updateSlotStyle(slotStyles[index], slotViews[index].isFocused)
        }
    }

    private fun updateSlotStyle(style: SlotStyle, focused: Boolean) {
        val fill = if (style.allApps) palette.secondaryContainer
        else if (style.edgeColor != Color.TRANSPARENT) style.edgeColor
        else palette.surfaceContainerHighest
        style.bg.setColor(fill)
        val stroke = if (focused) palette.primary else Palette.alpha(palette.outlineVariant, 0x45)
        style.border.setStroke(if (focused) dp(3) else dp(1), stroke)
        if (style.allApps) {
            val iconColor = if (focused) palette.primary else palette.onSecondaryContainer
            allAppsIcon?.imageTintList = ColorStateList.valueOf(iconColor)
        }
    }

    private fun dp(value: Int): Int = Math.round(value * density)
}

private const val FOCUS_SCALE = 1.15f
