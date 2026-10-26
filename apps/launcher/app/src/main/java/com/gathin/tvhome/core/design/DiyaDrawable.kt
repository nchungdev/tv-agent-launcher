package com.gathin.tvhome.core.design

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import java.util.Random

class DiyaDrawable(private val density: Float) : Drawable(), Animatable {

    private val noise = FloatArray(256)
    private val body = Path()
    private val opening = Path()
    private val rimLip = Path()
    private val sheen = Path()
    private val flame = Path()
    private val core = Path()
    private val smoke = Path()
    private val foot = RectF(7.2f, 20.0f, 12.4f, 22.4f)
    private val oval = RectF()
    private val matrix = Matrix()

    private val footPaint: Paint
    private val bodyPaint: Paint
    private val warmPaint: Paint
    private val openingPaint: Paint
    private val openingEdge: Paint
    private val dotPaint: Paint
    private val lipPaint: Paint
    private val sheenPaint: Paint
    private val outlinePaint: Paint
    private val wickPaint: Paint
    private val glowPaint: Paint
    private val flamePaint: Paint
    private val edgePaint: Paint
    private val corePaint: Paint
    private val smokePaint: Paint
    private val emberPaint: Paint
    private val sparkPaint: Paint
    private val rayPaint: Paint
    private val paints: Array<Paint>
    private val glowShader: Shader
    private val flameShader: Shader
    private val coreShader: Shader
    private val smokeShader: Shader
    private val sparkShader: Shader

    private var level = 4
    private var primed = false
    private var scaleFrom = 1f
    private var scaleTo = 1f
    private var scaleStart = 0L
    private var phase = IDLE
    private var phaseStart = 0L
    private var outFrom = 0f
    private var running = false
    private var steady = false
    private var reduced = false
    private var alphaVal = 255
    private val epoch = SystemClock.uptimeMillis()

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            update(now)
            invalidateSelf()
            if (wantsFrames()) scheduleSelf(this, now + FRAME_MS)
        }
    }

    init {
        val rnd = Random(0xD1A5L)
        for (i in noise.indices) noise[i] = rnd.nextFloat() * 2f - 1f
        buildLamp()

        footPaint = fill(-0x71c1e2)
        bodyPaint = fill(-0x3b9ed1)
        bodyPaint.shader = RadialGradient(
            7.5f, 14.5f, 13f,
            intArrayOf(-0x1a73a9, -0x3b9ed1, -0x65bdde, -0x91d3ec),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
        warmPaint = fill(-0x4cc9)
        val warm = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(0xA6FFB347.toInt(), 0x40FF9A2A, 0x00FF8A1A), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        matrix.setScale(9f, 9f)
        matrix.postTranslate(WICK_X, WICK_Y)
        warm.setLocalMatrix(matrix)
        warmPaint.shader = warm
        openingPaint = fill(-0xb5e1f2)
        openingPaint.shader = LinearGradient(
            2.6f, 0f, 19f, 0f,
            intArrayOf(-0xc4e8f6, -0xa5d5f0, -0x85bbfa), null, Shader.TileMode.CLAMP
        )
        openingEdge = stroke(-0xb5e5f6, 0.3f)
        dotPaint = fill(-0x1f5d)
        lipPaint = stroke(-0x116598, 0.6f)
        sheenPaint = stroke(0x73FFE8D2.toInt(), 0.75f)
        outlinePaint = stroke(-0xc2ebf8, 0.7f)
        wickPaint = stroke(-0xd4ebf6, 0.6f)

        glowPaint = fill(0xFFFFB040.toInt())
        glowShader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(0x99FFC45A.toInt(), 0x44FF9A2A, 0x00FF8A1A), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        glowPaint.shader = glowShader
        flamePaint = fill(-0x65d6)
        flameShader = LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(-0x26bcf5, -0x85ea, -0x59d5, -0x47c1, -0xf75dc),
            floatArrayOf(0f, 0.22f, 0.55f, 0.85f, 1f), Shader.TileMode.CLAMP
        )
        flamePaint.shader = flameShader
        edgePaint = stroke(-0x47bf26, 0.28f)
        corePaint = fill(-0xf48)
        coreShader = LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(-0x2da6, -0xf48, -0x71f, -0x165a),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
        corePaint.shader = coreShader

        smokePaint = stroke(-0x5f5f60, 0.6f)
        smokeShader = LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(0x00A0A0A0, 0xB09A9A9A.toInt(), -0x717172), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        smokePaint.shader = smokeShader
        emberPaint = fill(-0xa5e6)
        sparkPaint = fill(-0xab38)
        sparkShader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(-0xab38, 0xCCFFC24A.toInt(), 0x00FF9A20), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        sparkPaint.shader = sparkShader
        rayPaint = stroke(-0xd5eed2, 0.35f)

        paints = arrayOf(
            footPaint, bodyPaint, warmPaint, openingPaint, openingEdge, dotPaint, lipPaint,
            sheenPaint, outlinePaint, wickPaint, glowPaint, flamePaint, edgePaint, corePaint, smokePaint,
            emberPaint, sparkPaint, rayPaint
        )
    }

    fun setFlame(newLevel: Int) {
        val lvl = newLevel.coerceIn(0, 4)
        if (!primed) {
            primed = true
            level = lvl
            snap()
            kick()
            return
        }
        if (lvl == level) return
        val now = SystemClock.uptimeMillis()
        update(now)
        val current = flameScale(now)
        val old = level
        level = lvl
        if (!animates()) {
            snap()
        } else if (lvl == 0) {
            outFrom = current
            phase = OUT
            phaseStart = now
        } else if (old == 0) {
            phase = RELIGHT
            phaseStart = now
            scaleTo = LEVEL_SCALE[lvl]
            scaleFrom = scaleTo
            scaleStart = now
        } else {
            scaleFrom = baseScale(now)
            scaleTo = LEVEL_SCALE[lvl]
            scaleStart = now
        }
        kick()
    }

    fun setSteady(steady: Boolean) {
        if (this.steady == steady) return
        this.steady = steady
        invalidateSelf()
    }

    fun setReducedMotion(reducedMotion: Boolean) {
        if (reduced == reducedMotion) return
        reduced = reducedMotion
        if (reduced) snap()
        kick()
    }

    override fun start() {
        if (running) return
        running = true
        kick()
    }

    override fun stop() {
        if (!running) return
        running = false
        snap()
        kick()
    }

    override fun isRunning(): Boolean = running

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (changed) {
            if (!visible) snap()
            kick()
        }
        return changed
    }

    private fun animates(): Boolean = running && !reduced && isVisible

    private fun wantsFrames(): Boolean = running && isVisible && ((level > 0 && !reduced) || phase != IDLE)

    private fun kick() {
        unscheduleSelf(tick)
        if (wantsFrames()) scheduleSelf(tick, SystemClock.uptimeMillis() + FRAME_MS)
        invalidateSelf()
    }

    private fun snap() {
        phase = IDLE
        scaleTo = LEVEL_SCALE[level]
        scaleFrom = scaleTo
    }

    private fun update(now: Long) {
        if (phase == OUT && now - phaseStart >= OUT_END_MS) phase = IDLE
        else if (phase == RELIGHT && now - phaseStart >= GROW_DELAY_MS + GROW_MS) phase = IDLE
    }

    private fun baseScale(now: Long): Float {
        val p = clamp01((now - scaleStart) / LEVEL_MS.toFloat())
        return scaleFrom + (scaleTo - scaleFrom) * decel(p)
    }

    private fun flameScale(now: Long): Float {
        return when (phase) {
            OUT -> {
                val e = (now - phaseStart) / OUT_MS.toFloat()
                if (e >= 1f) 0f else outFrom * (1f - e * e)
            }
            RELIGHT -> {
                val g = (now - phaseStart - GROW_DELAY_MS) / GROW_MS.toFloat()
                if (g <= 0f) 0f else baseScale(now) * backOut(minOf(1f, g))
            }
            else -> if (level == 0) 0f else baseScale(now)
        }
    }

    private fun flameBright(now: Long): Float {
        if (phase != OUT) return 1f
        val e = clamp01((now - phaseStart) / OUT_MS.toFloat())
        return 1f - e * Math.sqrt(e.toDouble()).toFloat()
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val now = SystemClock.uptimeMillis()
        update(now)
        val s = minOf(b.width(), b.height()) / BOX
        val save = if (alphaVal < 255) c.saveLayerAlpha(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), alphaVal) else c.save()
        c.clipRect(b)
        c.translate(b.exactCenterX() - 12f * s, b.exactCenterY() - 12f * s)
        c.scale(s, s)

        val scale = flameScale(now)
        val bright = flameBright(now)
        var hMul = 1f
        var wMul = 1f
        var sway = 0f
        if (running && !reduced && isVisible) {
            val t = (now - epoch) / 1000f
            if (steady) {
                val breath = sin(t / 4.2f)
                hMul = 1f + 0.035f * breath
                wMul = 1f + 0.02f * breath
            } else {
                hMul = 1f + 0.055f * sin(0.83f * t) + 0.035f * sin(1.71f * t + 0.21f) +
                        0.025f * sin(2.63f * t + 0.33f) + 0.03f * noise(t * 1.9f)
                wMul = 1f + 0.045f * sin(1.37f * t + 0.14f) + 0.03f * noise(t * 2.2f + 31f)
                sway = 0.45f * sin(0.47f * t + 0.1f) + 0.28f * sin(1.13f * t + 0.38f) + 0.2f * noise(t * 1.3f + 17f)
            }
        }
        val h = FLAME_H * scale * hMul
        val w = FLAME_W * minOf(1f, scale * 2.2f) * (0.5f + 0.5f * scale) * wMul
        val lean = sway * scale
        val vis = scale * bright

        if (vis > 0.01f) {
            val cx = WICK_X + lean * 0.4f
            val cy = WICK_Y - h * 0.45f
            val rx = 2.6f + 3.4f * scale
            val ry = rx * 1.2f
            matrix.setScale(rx, ry)
            matrix.postTranslate(cx, cy)
            glowShader.setLocalMatrix(matrix)
            glowPaint.alpha = Math.round(255 * bright * (0.5f + 0.5f * scale))
            oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
            c.drawOval(oval, glowPaint)
        }

        drawLamp(c, vis)

        if (phase == OUT) drawSmoke(c, now)
        if (phase == RELIGHT) drawSpark(c, now)

        if (h > 0.15f && bright > 0.01f) {
            val by = WICK_Y + 0.2f
            teardrop(flame, WICK_X, by, h, w, lean)
            matrix.setScale(1f, h)
            matrix.postTranslate(0f, by - h)
            flameShader.setLocalMatrix(matrix)
            flamePaint.alpha = Math.round(255 * bright)
            c.drawPath(flame, flamePaint)
            edgePaint.alpha = Math.round(150 * bright)
            c.drawPath(flame, edgePaint)

            val cby = WICK_Y - h * 0.06f
            val ch = h * 0.58f
            val cw = w * 0.5f
            teardrop(core, WICK_X + lean * 0.05f, cby, ch, cw, lean * 0.55f)
            matrix.setScale(1f, ch)
            matrix.postTranslate(0f, cby - ch)
            coreShader.setLocalMatrix(matrix)
            corePaint.alpha = Math.round(255 * bright * bright)
            c.drawPath(core, corePaint)
        }
        c.restoreToCount(save)
    }

    private fun drawLamp(c: Canvas, warmth: Float) {
        c.drawOval(foot, footPaint)
        c.drawOval(foot, outlinePaint)
        c.drawPath(body, bodyPaint)
        if (warmth > 0.01f) {
            warmPaint.alpha = Math.round(190 * warmth)
            c.drawPath(body, warmPaint)
        }
        c.drawPath(opening, openingPaint)
        if (warmth > 0.01f) {
            warmPaint.alpha = Math.round(255 * warmth)
            c.drawPath(opening, warmPaint)
        }
        c.drawPath(opening, openingEdge)
        var i = 0
        while (i < DOTS.size) {
            c.drawCircle(DOTS[i], DOTS[i + 1], 0.42f, dotPaint)
            i += 2
        }
        c.drawPath(rimLip, lipPaint)
        c.drawPath(sheen, sheenPaint)
        c.drawPath(body, outlinePaint)
        c.drawLine(WICK_X - 0.9f, WICK_Y + 0.7f, WICK_X, WICK_Y, wickPaint)
    }

    private fun drawSmoke(c: Canvas, now: Long) {
        val since = now - phaseStart
        val ember = 1f - since / (SMOKE_DELAY_MS + SMOKE_MS * 0.6f)
        if (ember > 0f) {
            emberPaint.alpha = Math.round(255 * ember)
            c.drawCircle(WICK_X, WICK_Y, 0.5f, emberPaint)
        }
        if (reduced) return
        val sp = (since - SMOKE_DELAY_MS) / SMOKE_MS.toFloat()
        if (sp <= 0f || sp >= 1f) return
        val head = 10.5f * decel(sp)
        val tp = (sp - 0.3f) / 0.7f
        val tail = if (tp <= 0f) 0f else minOf(head - 0.6f, 8.5f * tp * tp)
        if (head - tail < 0.3f) return
        smoke.reset()
        for (i in 0 until SMOKE_POINTS) {
            val d = tail + (head - tail) * i / (SMOKE_POINTS - 1)
            val curl = (0.2f + 0.17f * d) * Math.sin((d * 0.9f - sp * 5.5f).toDouble()).toFloat()
            val x = WICK_X - 0.28f * d + curl
            val y = WICK_Y - 0.3f - d
            if (i == 0) smoke.moveTo(x, y) else smoke.lineTo(x, y)
        }
        val top = WICK_Y - 0.3f - head
        matrix.setScale(1f, head - tail)
        matrix.postTranslate(0f, top)
        smokeShader.setLocalMatrix(matrix)
        smokePaint.strokeWidth = 0.55f + 0.35f * sp
        smokePaint.alpha = Math.round(230 * (1f - sp))
        c.drawPath(smoke, smokePaint)
    }

    private fun drawSpark(c: Canvas, now: Long) {
        val k = (now - phaseStart) / SPARK_MS.toFloat()
        if (k >= 1.5f) return
        val r = 0.5f + 2.8f * decel(minOf(1f, k))
        val a = if (k < 0.25f) k / 0.25f else 1f - (k - 0.25f) / 1.25f
        val cx = WICK_X
        val cy = WICK_Y - 0.5f
        matrix.setScale(r, r)
        matrix.postTranslate(cx, cy)
        sparkShader.setLocalMatrix(matrix)
        sparkPaint.alpha = Math.round(255 * a)
        c.drawCircle(cx, cy, r, sparkPaint)
        rayPaint.alpha = Math.round(255 * a)
        for (i in 0 until 4) {
            val ang = Math.toRadians((45 + 90 * i + 30 * k).toDouble())
            val cs = Math.cos(ang).toFloat()
            val sn = Math.sin(ang).toFloat()
            c.drawLine(cx + cs * r * 0.55f, cy + sn * r * 0.55f, cx + cs * r * 1.15f, cy + sn * r * 1.15f, rayPaint)
        }
    }

    private fun buildLamp() {
        body.moveTo(1.6f, 14.2f)
        body.cubicTo(1.8f, 18.6f, 5.4f, 21.0f, 9.6f, 21.0f)
        body.cubicTo(13.2f, 21.0f, 15.6f, 19.4f, 16.8f, 16.6f)
        body.cubicTo(17.6f, 15.0f, 18.6f, 13.6f, 20.2f, 12.4f)
        body.quadTo(20.5f, 12.0f, 19.9f, 11.9f)
        body.cubicTo(18.2f, 12.1f, 16.8f, 12.6f, 15.4f, 13.0f)
        body.cubicTo(12.0f, 11.6f, 5.0f, 11.8f, 1.6f, 14.2f)
        body.close()

        opening.moveTo(2.6f, 14.2f)
        opening.cubicTo(5.6f, 12.6f, 11.8f, 12.5f, 15.2f, 13.4f)
        opening.cubicTo(16.6f, 13.0f, 17.8f, 12.7f, 19.2f, 12.4f)
        opening.cubicTo(17.6f, 13.4f, 16.6f, 14.0f, 15.4f, 14.6f)
        opening.cubicTo(11.6f, 16.4f, 5.0f, 16.2f, 2.6f, 14.2f)
        opening.close()

        rimLip.moveTo(2.7f, 14.65f)
        rimLip.cubicTo(5.1f, 16.65f, 11.6f, 16.85f, 15.5f, 15.0f)
        rimLip.cubicTo(16.7f, 14.4f, 17.7f, 13.8f, 19.4f, 12.75f)

        sheen.moveTo(3.2f, 15.4f)
        sheen.quadTo(4.0f, 18.4f, 6.9f, 19.7f)
    }

    private fun noise(x: Float): Float {
        val i = Math.floor(x.toDouble()).toInt()
        val f = x - i
        val s = f * f * (3f - 2f * f)
        val a = noise[i and 255]
        val b = noise[(i + 1) and 255]
        return a + (b - a) * s
    }

    private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun stroke(color: Int, width: Float): Paint {
        return fill(color).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    override fun getIntrinsicWidth(): Int = Math.round(BOX * density)

    override fun getIntrinsicHeight(): Int = Math.round(BOX * density)

    override fun setAlpha(alpha: Int) {
        if (this.alphaVal == alpha) return
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
        private const val FRAME_MS = 66L
        private const val LEVEL_MS = 300L
        private const val OUT_MS = 350L
        private const val SMOKE_DELAY_MS = 260L
        private const val SMOKE_MS = 900L
        private val OUT_END_MS = maxOf(OUT_MS, SMOKE_DELAY_MS + SMOKE_MS)
        private const val SPARK_MS = 200L
        private const val GROW_DELAY_MS = 110L
        private const val GROW_MS = 500L
        private const val IDLE = 0
        private const val OUT = 1
        private const val RELIGHT = 2
        private val LEVEL_SCALE = floatArrayOf(0f, 0.46f, 0.64f, 0.82f, 1f)
        private const val WICK_X = 18.5f
        private const val WICK_Y = 12.1f
        private const val FLAME_H = 10f
        private const val FLAME_W = 2f
        private const val SMOKE_POINTS = 16
        private val DOTS = floatArrayOf(5.0f, 17.6f, 7.4f, 18.5f, 9.9f, 18.8f, 12.4f, 18.4f, 14.6f, 17.3f)

        private fun sin(turns: Float): Float = Math.sin(turns * 2.0 * Math.PI).toFloat()

        private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

        private fun decel(p: Float): Float {
            val q = 1f - p
            return 1f - q * q
        }

        private fun backOut(x: Float): Float {
            val s = 1.4f
            val q = x - 1f
            return 1f + (s + 1f) * q * q * q + s * q * q
        }

        private fun teardrop(p: Path, bx: Float, by: Float, h: Float, w: Float, lean: Float) {
            val tx = bx + lean
            val ty = by - h
            val ml = lean * 0.35f
            val wy = by - maxOf(w * 1.1f, h * 0.24f)
            val up = (wy - ty) * 0.35f
            val down = (by - wy) * 0.55f
            p.reset()
            p.moveTo(tx, ty)
            p.cubicTo(tx + w * 0.25f, ty + h * 0.3f, bx + ml + w, wy - up, bx + w, wy)
            p.cubicTo(bx + w, wy + down, bx + w * 0.55f, by, bx, by)
            p.cubicTo(bx - w * 0.55f, by, bx - w, wy + down, bx - w, wy)
            p.cubicTo(bx + ml - w, wy - up, tx - w * 0.25f, ty + h * 0.3f, tx, ty)
            p.close()
        }
    }
}
