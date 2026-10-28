package com.gathin.tvhome.feature.main.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.motion.Motion
import com.gathin.tvhome.core.util.SoundEffects
import com.gathin.tvhome.feature.watching.data.WatchItem
import com.gathin.tvhome.feature.watching.ui.DockWatchNextView


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
        fun onWatchNextLongClicked(anchor: View) {}
    }

    private val density = resources.displayMetrics.density
    private val dockBar = LinearLayout(context)
    private val tooltip = DockTooltip(this)
    private val barBackground = GradientDrawable()
    private val slotViews = ArrayList<View>()
    private val boundItems = ArrayList<DockItem>()
    private var runningPackages: Set<String> = emptySet()
    private var dividerView: View? = null
    private val watchNext = DockWatchNextView(context)
    private var watchDivider: View? = null
    private var palette = Palette.currentOrBaseline()
    private var animations = true
    private var reducedMotion = false
    private var listener: Listener? = null
    private var lastFocusedIndex = 0

    init {
        clipChildren = false
        clipToPadding = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        watchNext.setOnLongClickListener {
            listener?.onWatchNextLongClicked(it)
            true
        }

        barBackground.cornerRadius = dp(26).toFloat()
        dockBar.orientation = LinearLayout.HORIZONTAL
        dockBar.gravity = Gravity.CENTER_VERTICAL
        dockBar.clipChildren = false
        dockBar.clipToPadding = false
        dockBar.setPadding(dp(14), dp(10), dp(14), dp(10))
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            clipToPadding = false
            background = barBackground
            clipToOutline = true
            elevation = dp(8).toFloat()
            addView(dockBar)
        }
        addView(scroller, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
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
        boundItems.clear()
        boundItems.addAll(items)
        dockBar.removeAllViews()
        slotViews.clear()
        dividerView = null
        watchDivider = null

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
            dividerView = View(context).apply { layoutParams = divParams }
                .also { dockBar.addView(it) }
        }

        for (index in items.indices) {
            val item = items[index]
            val appSlot = createSlotView(index + 1, item.label, item.icon, false, item)
            if (index > 0) (appSlot.layoutParams as LinearLayout.LayoutParams).marginStart = dp(10)
            dockBar.addView(appSlot)
            slotViews.add(appSlot)
        }
        syncWatchNext()
        applyColors()
        applyRunningState()
    }

    fun updateRunningApps(running: Set<String>) {
        runningPackages = running
        applyRunningState()
    }

    private fun applyRunningState() {
        for (index in boundItems.indices) {
            val item = boundItems[index]
            val isRunning = runningPackages.contains(item.packageName)
            (slotViews.getOrNull(index + 1) as? DockAppSlot)?.setRunning(isRunning)
        }
    }

    fun slotCount(): Int = slotViews.size

    fun lastIndex(): Int = lastFocusedIndex

    fun focusSlot(index: Int): Boolean =
        slotViews.isNotEmpty() && slotViews[index.coerceIn(0, slotViews.size - 1)].requestFocus()

    private fun createSlotView(
        index: Int, label: String, iconDrawable: Drawable?, allApps: Boolean, dockItem: DockItem?
    ): View {
        val slot = DockAppSlot(context, iconDrawable, allApps)
        attachSlotFocus(slot, index, { label }, dockItem)
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
        slot: View, index: Int, label: () -> String, item: DockItem?
    ) {
        val spec = Motion.FAST_SPATIAL.reduced(reducedMotion)
        val scaleX = spec.apply(SpringAnimation(slot, DynamicAnimation.SCALE_X))
        val scaleY = spec.apply(SpringAnimation(slot, DynamicAnimation.SCALE_Y))
        val liftY = spec.apply(SpringAnimation(slot, DynamicAnimation.TRANSLATION_Y))
        slot.setOnFocusChangeListener { view, hasFocus ->
            (slot as? DockAppSlot)?.setPalette(palette)
            if (slot === watchNext) {
                watchNext.setPalette(palette)
                if (!hasFocus) {
                    watchNext.releasePending()
                    syncWatchNext()
                }
            }
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
                tooltip.show(view, label(), animations)
                listener?.onItemFocused(index, item)
            } else post { if (!hasFocus()) tooltip.hide(animations) }
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

    private fun applyColors() {
        barBackground.setColor(Palette.alpha(palette.surfaceContainerHigh, 0xD9))
        barBackground.setStroke(dp(1), Palette.alpha(palette.outlineVariant, 0x66))
        tooltip.setPalette(palette)
        dividerView?.setBackgroundColor(Palette.alpha(palette.outlineVariant, 0x55))
        watchDivider?.setBackgroundColor(Palette.alpha(palette.outlineVariant, 0x55))
        watchNext.setPalette(palette)
        for (slot in slotViews) (slot as? DockAppSlot)?.setPalette(palette)
    }

    fun bindWatchNext(item: WatchItem?, poster: Bitmap?, open: () -> Unit) {
        watchNext.bind(item, poster, open)
        syncWatchNext()
        applyColors()
    }

    private fun syncWatchNext() {
        if (watchNext.visibility == View.VISIBLE && watchNext.parent == null) {
            watchDivider = View(context).also {
                dockBar.addView(it, LinearLayout.LayoutParams(dp(1), dp(36)).apply {
                    marginStart = dp(12)
                    marginEnd = dp(12)
                })
            }
            val index = slotViews.size
            dockBar.addView(watchNext)
            slotViews.add(watchNext)
            attachSlotFocus(watchNext, index, { watchNext.contentDescription.toString() }, null)
            watchNext.setOnKeyListener { _, key, event -> handleSlotKey(index, key, event) }
        } else if (watchNext.visibility != View.VISIBLE && watchNext.parent != null) {
            dockBar.removeView(watchNext)
            watchDivider?.let { dockBar.removeView(it) }
            watchDivider = null
            slotViews.remove(watchNext)
            lastFocusedIndex = lastFocusedIndex.coerceAtMost(slotViews.lastIndex)
        }
    }

    private fun dp(value: Int): Int = Math.round(value * density)
}

private const val FOCUS_SCALE = 1.15f
