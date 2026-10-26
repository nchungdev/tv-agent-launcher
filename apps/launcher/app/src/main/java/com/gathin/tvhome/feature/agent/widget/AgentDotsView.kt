package com.gathin.tvhome.feature.agent.widget
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.coordinator.*
import com.gathin.tvhome.feature.agent.ui.*

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View


class AgentDotsView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var volume = 0f
    private var loudest = 0f
    private var quietest = 0f
    private var level = 0f
    private var lastFrameNanos = 0L
    private var waving = false

    fun setVolume(value: Float) {
        volume = value
        loudest = maxOf(value, loudest - DRIFT)
        quietest = minOf(value, quietest + DRIFT)
    }

    fun setWaving(waving: Boolean) {
        this.waving = waving
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val now = System.nanoTime()
        followTarget((now - lastFrameNanos) / NANOS_PER_SECOND, lastFrameNanos == 0L)
        lastFrameNanos = now

        val phase = now / NANOS_PER_SECOND * WOBBLE_SPEED
        val gap = GAP_DP * density
        val startX = width / 2f - (DOT_COLORS.size - 1) * gap / 2f
        val baseline = height / 2f + BASELINE_DP * density
        val radius = (RADIUS_DP + RADIUS_GROWTH_DP * level) * density
        for (dot in DOT_COLORS.indices) {
            val wobble = 0.5f + 0.5f * Math.sin((phase - dot * WOBBLE_STAGGER).toDouble()).toFloat()
            val voiceLift = level * VOICE_LIFT_DP * (0.35f + 0.65f * wobble)
            val lift = (REST_LIFT_DP * wobble + voiceLift) * density
            paint.color = DOT_COLORS[dot]
            canvas.drawCircle(startX + dot * gap, baseline - lift, radius, paint)
        }
        postInvalidateOnAnimation()
    }

    private fun followTarget(elapsedSeconds: Float, firstFrame: Boolean) {
        val span = maxOf(loudest - quietest, MIN_SPAN)
        val spoken = ((volume - quietest) / span).coerceIn(0f, 1f)
        val target = if (waving) WAVE_LEVEL else spoken
        val settleSeconds = if (target > level) ATTACK_SECONDS else RELEASE_SECONDS
        val step = if (firstFrame) 1f else 1f - Math.exp((-elapsedSeconds / settleSeconds).toDouble()).toFloat()
        level += (target - level) * step
    }

    companion object {
        private val DOT_COLORS = intArrayOf(0xFFD0BCFF.toInt(), 0xFFEFB8C8.toInt(), 0xFF9A82DB.toInt(), 0xFF80CBC4.toInt())
        private const val ATTACK_SECONDS = 0.04f
        private const val RELEASE_SECONDS = 0.18f
        private const val MIN_SPAN = 2f
        private const val DRIFT = 0.02f
        private const val WAVE_LEVEL = 0.6f
        private const val NANOS_PER_SECOND = 1e9f
        private const val WOBBLE_SPEED = 7f
        private const val WOBBLE_STAGGER = 0.9f
        private const val GAP_DP = 13f
        private const val BASELINE_DP = 6f
        private const val REST_LIFT_DP = 7f
        private const val VOICE_LIFT_DP = 18f
        private const val RADIUS_DP = 4.5f
        private const val RADIUS_GROWTH_DP = 1.5f
    }
}
