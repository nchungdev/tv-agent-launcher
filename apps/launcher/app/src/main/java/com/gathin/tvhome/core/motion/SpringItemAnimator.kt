package com.gathin.tvhome.core.motion

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

class SpringItemAnimator(reducedMotion: Boolean) : DefaultItemAnimator() {

    private val spring: Motion.Spec = Motion.DEFAULT_SPATIAL.reduced(reducedMotion)

    init {
        supportsChangeAnimations = false
        moveDuration = spring.durationMs()
        addDuration = 0
        removeDuration = 0
    }

    override fun animateMove(holder: RecyclerView.ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        val pending = super.animateMove(holder, fromX, fromY, toX, toY)
        holder.itemView.animate().interpolator = spring.interpolator()
        return pending
    }
}
