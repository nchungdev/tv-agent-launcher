package com.gathin.tvhome.feature.main.adapter

import android.graphics.Outline
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.recyclerview.widget.RecyclerView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Shapes
import com.gathin.tvhome.core.motion.Motion


class AppTileViewHolder(
    v: View,
    listener: AppAdapter.TileListener?,
    private val animations: Boolean,
    private val advancedEffects: Boolean,
    reducedMotion: Boolean
) : RecyclerView.ViewHolder(v) {

    @JvmField val card: View = v.findViewById(R.id.card)
    @JvmField val banner: ImageView = v.findViewById(R.id.banner)
    @JvmField val icon: ImageView = v.findViewById(R.id.icon)
    @JvmField val label: TextView = v.findViewById(R.id.label)
    @JvmField val name: TextView = v.findViewById(R.id.name)
    private val density: Float = v.resources.displayMetrics.density

    @JvmField var cornerDp: Float = Shapes.TILE_REST
    private val scaleX: SpringAnimation
    private val scaleY: SpringAnimation
    private val corner: SpringAnimation
    private val nameAlpha: SpringAnimation
    private val nameShift: SpringAnimation
    private val bannerX: SpringAnimation
    private val bannerY: SpringAnimation

    init {
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, AppAdapter.cornerRadius(view.width, density, cornerDp))
            }
        }
        card.clipToOutline = true

        scaleX = SpringAnimation(card, DynamicAnimation.SCALE_X)
        scaleY = SpringAnimation(card, DynamicAnimation.SCALE_Y)
        corner = SpringAnimation(this, CORNER)
        nameAlpha = SpringAnimation(name, DynamicAnimation.ALPHA)
        nameShift = SpringAnimation(name, DynamicAnimation.TRANSLATION_Y)
        bannerX = Motion.DEFAULT_SPATIAL.reduced(reducedMotion).apply(SpringAnimation(banner, DynamicAnimation.SCALE_X))
        bannerY = Motion.DEFAULT_SPATIAL.reduced(reducedMotion).apply(SpringAnimation(banner, DynamicAnimation.SCALE_Y))

        Motion.FAST_SPATIAL.reduced(reducedMotion).apply(scaleX)
        Motion.FAST_SPATIAL.reduced(reducedMotion).apply(scaleY)
        Motion.FAST_SPATIAL.reduced(reducedMotion).apply(corner)
        Motion.FAST_EFFECTS.apply(nameAlpha)
        Motion.FAST_SPATIAL.reduced(reducedMotion).apply(nameShift)

        scaleX.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
        scaleY.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
        nameAlpha.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA)
        corner.setMinimumVisibleChange(0.1f)

        card.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return@setOnKeyListener false
            val pos = bindingAdapterPosition
            listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode)
        }

        card.setOnFocusChangeListener { view, focused ->
            if (!animations) {
                snap(focused)
            } else {
                scaleX.animateToFinalPosition(1f)
                scaleY.animateToFinalPosition(1f)
                corner.animateToFinalPosition(if (focused) Shapes.TILE_FOCUSED else Shapes.TILE_REST)
                nameAlpha.animateToFinalPosition(if (focused) 1f else 0f)
                if (focused) nameShift.animateToFinalPosition(density * AppAdapter.NAME_GAP_DP)
                if (advancedEffects) {
                    bannerX.animateToFinalPosition(if (focused) BANNER_ZOOM else 1f)
                    bannerY.animateToFinalPosition(if (focused) BANNER_ZOOM else 1f)
                }
            }
            val pos = bindingAdapterPosition
            if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onTileFocused(pos, view)
            }
        }
    }

    fun snap(focused: Boolean) {
        for (s in arrayOf(scaleX, scaleY, corner, nameAlpha, nameShift)) s.cancel()
        card.scaleX = 1f
        card.scaleY = 1f
        card.translationY = 0f
        card.translationZ = 0f
        cornerDp = if (focused) Shapes.TILE_FOCUSED else Shapes.TILE_REST
        card.invalidateOutline()
        bannerX.cancel()
        bannerY.cancel()
        banner.scaleX = if (focused && advancedEffects) BANNER_ZOOM else 1f
        banner.scaleY = if (focused && advancedEffects) BANNER_ZOOM else 1f
        name.alpha = if (focused) 1f else 0f
        name.translationY = if (focused && animations) density * AppAdapter.NAME_GAP_DP else -density * 6f
    }

    companion object {
        const val BANNER_ZOOM = 1.06f

        private val CORNER = object : FloatPropertyCompat<AppTileViewHolder>("cornerDp") {
            override fun getValue(h: AppTileViewHolder): Float = h.cornerDp

            override fun setValue(h: AppTileViewHolder, value: Float) {
                h.cornerDp = value
                h.card.invalidateOutline()
            }
        }
    }
}
