package com.gathin.tvhome.core.design

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import java.util.Random

class MarigoldDrawable(private val density: Float) : Drawable() {

    private val petals = Array(COUNT.size) { arrayOfNulls<Path>(COUNT[it]) }
    private val angles = Array(COUNT.size) { FloatArray(COUNT[it]) }
    private val colors = Array(COUNT.size) { IntArray(COUNT[it]) }
    private val edges = Array(COUNT.size) { IntArray(COUNT[it]) }
    private val shadowR = FloatArray(COUNT.size)
    private val shadowPaints = arrayOfNulls<Paint>(COUNT.size)
    private val core = Path()
    private val seeds = FloatArray(18)

    private val fill: Paint
    private val edge: Paint
    private val coreFill: Paint
    private val coreEdge: Paint
    private val seedPaint: Paint
    private val paints: Array<Paint>
    private val animator: ValueAnimator

    private var rotation = 0.0
    private var from = 0.0
    private var target = 0.0
    private var reduced = false
    private var alphaVal = 255

    init {
        val rnd = Random(0x6E7DAL)
        for (i in COUNT.indices) buildRing(i, rnd)
        buildCore(rnd)

        fill = Paint(Paint.ANTI_ALIAS_FLAG)
        edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.3f
            strokeJoin = Paint.Join.ROUND
        }
        for (i in COUNT.indices) {
            val inner = if (i + 1 < COUNT.size) RADIUS[i + 1] else CORE_R
            val sr = inner + 0.6f * (RADIUS[i] - inner)
            val q = inner / sr
            shadowR[i] = sr
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(
                C + SHADOW_DX, C + SHADOW_DY, sr,
                intArrayOf(0x805A1E00.toInt(), 0x805A1E00.toInt(), 0x665A1E00, 0x005A1E00),
                floatArrayOf(0f, q * 0.9f, q, 1f), Shader.TileMode.CLAMP
            )
            shadowPaints[i] = p
        }
        coreFill = Paint(Paint.ANTI_ALIAS_FLAG)
        coreFill.shader = RadialGradient(
            C - 0.7f, C - 0.7f, 3.6f,
            intArrayOf(-0x1f7de6, -0x4fb1f8, -0x85cffc), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        coreEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.3f
            strokeJoin = Paint.Join.ROUND
            color = -0xa3dbfc
        }
        seedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = -0x47b6
        }

        paints = Array(COUNT.size + 5) { Paint() }
        for (i in COUNT.indices) paints[i] = shadowPaints[i]!!
        paints[COUNT.size] = fill
        paints[COUNT.size + 1] = edge
        paints[COUNT.size + 2] = coreFill
        paints[COUNT.size + 3] = coreEdge
        paints[COUNT.size + 4] = seedPaint

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TURN_MS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { a ->
                rotation = from + (target - from) * (a.animatedValue as Float).toDouble()
                invalidateSelf()
            }
        }
    }

    fun turn() {
        if (reduced) return
        from = rotation
        target += 60.0
        animator.cancel()
        animator.start()
    }

    fun setReducedMotion(reducedMotion: Boolean) {
        reduced = reducedMotion
        if (reduced && animator.isRunning) animator.end()
    }

    private fun buildRing(ring: Int, rnd: Random) {
        val n = COUNT[ring]
        val offset = ring * (180f / n) + (rnd.nextFloat() - 0.5f) * 8f
        val edgeDepth = if (ring == 0) 0.55f else 0.45f
        for (j in 0 until n) {
            val deg = offset + j * 360f / n + (rnd.nextFloat() - 0.5f) * 10f
            val r = RADIUS[ring] * (0.96f + 0.04f * rnd.nextFloat())
            val hw = (Math.PI / n).toFloat() * (1.18f + 0.15f * rnd.nextFloat())
            val scallops = 3 + rnd.nextInt(2)
            petals[ring][j] = petal(Math.toRadians(deg.toDouble()).toFloat(), hw, r * 0.25f, r, scallops, rnd)
            angles[ring][j] = deg
            val base = lerp(SHADES[ring][0], SHADES[ring][1], rnd.nextFloat())
            colors[ring][j] = base
            edges[ring][j] = lerp(base, EDGE, edgeDepth)
        }
    }

    private fun buildCore(rnd: Random) {
        val bumps = 18
        for (k in 0..bumps * 2) {
            val a = (Math.PI * k / bumps).toFloat()
            val r = if (k % 2 == 1) CORE_R * (1.0f + 0.06f * rnd.nextFloat()) else CORE_R * 0.84f
            if (k == 0) core.moveTo(px(r, a), py(r, a))
            else if (k % 2 == 1) {
                val na = (Math.PI * (k + 1) / bumps).toFloat()
                val nr = CORE_R * 0.84f
                core.quadTo(px(r, a), py(r, a), px(nr, na), py(nr, na))
            }
        }
        core.close()
        val golden = Math.PI * (3 - Math.sqrt(5.0))
        for (k in 0 until seeds.size / 2) {
            val r = 0.62f * Math.sqrt((k + 0.5)).toFloat()
            val a = (k * golden).toFloat()
            seeds[k * 2] = px(r, a)
            seeds[k * 2 + 1] = py(r, a)
        }
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val s = minOf(b.width(), b.height()) / BOX
        val save = if (alphaVal < 255) c.saveLayerAlpha(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), alphaVal) else c.save()
        c.clipRect(b)
        c.translate(b.exactCenterX() - C * s, b.exactCenterY() - C * s)
        c.scale(s, s)

        for (i in COUNT.indices) {
            val rot = ((rotation * SPIN[i]) % 360.0).toFloat()
            c.save()
            c.rotate(rot, C, C)
            val ring = petals[i]
            for (j in ring.indices) {
                val p = ring[j] ?: continue
                val l = Math.cos(Math.toRadians((angles[i][j] + rot - LIGHT_DEG).toDouble())).toFloat()
                val base = colors[i][j]
                fill.color = if (l >= 0f) lerp(base, LIT, 0.3f * l) else lerp(base, SHADOWED, -0.24f * l)
                c.drawPath(p, fill)
                edge.color = edges[i][j]
                c.drawPath(p, edge)
            }
            c.restore()
            shadowPaints[i]?.let { c.drawCircle(C + SHADOW_DX, C + SHADOW_DY, shadowR[i], it) }
        }

        c.save()
        c.rotate(((rotation * CORE_SPIN) % 360.0).toFloat(), C, C)
        c.drawPath(core, coreFill)
        c.drawPath(core, coreEdge)
        var k = 0
        while (k < seeds.size) {
            c.drawCircle(seeds[k], seeds[k + 1], 0.26f, seedPaint)
            k += 2
        }
        c.restore()

        c.restoreToCount(save)
    }

    override fun getIntrinsicWidth(): Int = Math.round(BOX * density)

    override fun getIntrinsicHeight(): Int = Math.round(BOX * density)

    override fun setAlpha(alpha: Int) {
        if (this.alphaVal == alpha) return;
        this.alphaVal = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = alphaVal

    override fun setColorFilter(filter: ColorFilter?) {
        for (p in paints) p.colorFilter = filter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private const val BOX = 24f
        private const val C = 12f
        private val COUNT = intArrayOf(13, 11, 9, 7)
        private val RADIUS = floatArrayOf(11.6f, 9.4f, 7.2f, 5.0f)
        private val SPIN = floatArrayOf(1f, 1.04f, 1.09f, 1.15f)
        private const val CORE_SPIN = 1.2f
        private val SHADES = arrayOf(
            intArrayOf(-0x1a9ff6, -0xf7ff2), intArrayOf(-0xf7ff2, -0x865ea),
            intArrayOf(-0x65ce5, -0x4ada), intArrayOf(-0x4ada, -0x39c2)
        )
        private const val CORE_R = 2.9f
        private const val LIGHT_DEG = 225f
        private const val LIT = -0x165a
        private const val SHADOWED = -0x5bc200
        private const val EDGE = -0x91dc00
        private const val SHADOW_DX = 0.35f
        private const val SHADOW_DY = 0.45f
        private const val TURN_MS = 900L

        private fun petal(a: Float, hw: Float, r0: Float, r: Float, scallops: Int, rnd: Random): Path {
            val p = Path()
            val a0 = a - hw
            val a1 = a + hw
            p.moveTo(px(r0, a - hw * 0.35f), py(r0, a - hw * 0.35f))
            val sideA = a0 - hw * 0.08f
            p.quadTo(px(r * 0.62f, sideA), py(r * 0.62f, sideA), px(r * 0.86f, a0), py(r * 0.86f, a0))
            val steps = scallops * 2
            var k = 1
            while (k < steps) {
                val ta = a0 + (a1 - a0) * k / steps + (rnd.nextFloat() - 0.5f) * hw * 0.12f
                val na = a0 + (a1 - a0) * (k + 1) / steps
                val tr = r * (1.0f + 0.06f * rnd.nextFloat())
                val nr = if (k + 1 >= steps) r * 0.86f else r * (0.83f + 0.06f * rnd.nextFloat())
                p.quadTo(px(tr, ta), py(tr, ta), px(nr, na), py(nr, na))
                k += 2
            }
            val sideB = a1 + hw * 0.08f
            val endA = a + hw * 0.35f
            p.quadTo(px(r * 0.62f, sideB), py(r * 0.62f, sideB), px(r0, endA), py(r0, endA))
            p.close()
            return p
        }

        private fun px(r: Float, a: Float): Float = (C + r * Math.cos(a.toDouble())).toFloat()

        private fun py(r: Float, a: Float): Float = (C + r * Math.sin(a.toDouble())).toFloat()

        private fun lerp(a: Int, b: Int, t: Float): Int {
            val ar = (a shr 16) and 0xFF
            val ag = (a shr 8) and 0xFF
            val ab = a and 0xFF
            val br = (b shr 16) and 0xFF
            val bg = (b shr 8) and 0xFF
            val bb = b and 0xFF
            return -0x1000000 or
                    ((ar + (br - ar) * t).toInt() shl 16) or
                    ((ag + (bg - ag) * t).toInt() shl 8) or
                    (ab + (bb - ab) * t).toInt()
        }
    }
}
