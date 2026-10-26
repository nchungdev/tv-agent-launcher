package com.gathin.tvhome.feature.main.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.Random


class PetalShowerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density: Float = context.resources.displayMetrics.density
    private val rnd = Random()
    private val shapes = arrayOfNulls<Path>(3)
    private val veins = arrayOfNulls<Path>(3)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.045f
        strokeJoin = Paint.Join.ROUND
    }
    private val vein = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.035f
        strokeCap = Paint.Cap.ROUND
        color = EDGE
    }

    private var count = 0
    private var xFrac = FloatArray(0)
    private var delay = FloatArray(0)
    private var life = FloatArray(0)
    private var drop = FloatArray(0)
    private var size = FloatArray(0)
    private var swayAmp = FloatArray(0)
    private var swayFreq = FloatArray(0)
    private var swayPhase = FloatArray(0)
    private var rot0 = FloatArray(0)
    private var spin = FloatArray(0)
    private var flipFreq = FloatArray(0)
    private var flipPhase = FloatArray(0)
    private var color = IntArray(0)
    private var shape = IntArray(0)

    private var playing = false
    private var start = 0L
    private var duration = 0L
    private var onDone: Runnable? = null
    private val finish = Runnable { finish() }

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)

        val seeded = Random(0x9E7A1L)
        for (i in shapes.indices) {
            shapes[i] = petalShape(seeded)
            veins[i] = veinPath(seeded)
        }
    }

    fun play(petals: Int, durationMs: Long, done: Runnable?) {
        if (playing) {
            removeCallbacks(finish)
            finish()
        }
        val n = maxOf(0, petals)
        ensureCapacity(n)
        count = n
        duration = maxOf(1, durationMs)
        onDone = done
        for (i in 0 until n) {
            xFrac[i] = (i + rnd.nextFloat()) / n
            delay[i] = rnd.nextFloat() * 0.32f
            life[i] = 0.6f + rnd.nextFloat() * (1f - delay[i] - 0.6f)
            drop[i] = 0.8f + rnd.nextFloat() * 0.35f
            size[i] = 9f + rnd.nextFloat() * 7f
            swayAmp[i] = 10f + rnd.nextFloat() * 24f
            swayFreq[i] = 0.5f + rnd.nextFloat() * 0.6f
            swayPhase[i] = (rnd.nextFloat() * TAU).toFloat()
            rot0[i] = rnd.nextFloat() * 360f
            spin[i] = (40f + rnd.nextFloat() * 120f) * if (rnd.nextBoolean()) 1f else -1f
            flipFreq[i] = 0.4f + rnd.nextFloat()
            flipPhase[i] = (rnd.nextFloat() * TAU).toFloat()
            color[i] = rnd.nextInt(COLORS.size)
            shape[i] = rnd.nextInt(shapes.size)
        }
        playing = true
        start = SystemClock.uptimeMillis()
        postDelayed(finish, duration)
        postInvalidateOnAnimation()
    }

    private fun finish() {
        if (!playing) return
        playing = false
        invalidate()
        val done = onDone
        onDone = null
        done?.run()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (playing) {
            removeCallbacks(finish)
            finish()
        }
    }

    override fun onDraw(c: Canvas) {
        if (!playing) return
        val now = SystemClock.uptimeMillis()
        val elapsed = (now - start) / duration.toFloat()
        val secs = (now - start) / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0 until count) {
            val p = (elapsed - delay[i]) / life[i]
            if (p <= 0f || p >= 1f) continue
            val len = size[i] * density
            val fall = (p + p * p) * 0.5f
            val y = -len * 1.5f + (h * drop[i] + len * 1.5f) * fall
            val sway = TAU * swayFreq[i] * secs + swayPhase[i]
            val x = xFrac[i] * w + swayAmp[i] * density * Math.sin(sway).toFloat()
            val angle = rot0[i] + spin[i] * secs + 22f * Math.cos(sway).toFloat()
            val flip = Math.cos(TAU * flipFreq[i] * secs + flipPhase[i]).toFloat()
            val sx = if (Math.abs(flip) < 0.08f) (if (flip < 0f) -0.08f else 0.08f) else flip
            val a = if (p > 0.75f) (255 * (1f - (p - 0.75f) / 0.25f)).toInt() else 255

            val shade = (if (flip < 0f) 0.78f else 1f) * (0.8f + 0.2f * Math.abs(flip))
            val base = COLORS[color[i]]
            fill.color = scale(base, shade)
            fill.alpha = a
            edge.color = scale(base, 0.55f)
            edge.alpha = a * 3 / 4
            vein.alpha = a / 4

            val shp = shapes[shape[i]] ?: continue
            val vn = veins[shape[i]] ?: continue

            c.save()
            c.translate(x, y)
            c.rotate(angle)
            c.scale(len * sx, len)
            c.drawPath(shp, fill)
            c.drawPath(shp, edge)
            c.drawPath(vn, vein)
            c.restore()
        }
        postInvalidateOnAnimation()
    }

    private fun ensureCapacity(n: Int) {
        if (xFrac.size >= n) return
        xFrac = FloatArray(n)
        delay = FloatArray(n)
        life = FloatArray(n)
        drop = FloatArray(n)
        size = FloatArray(n)
        swayAmp = FloatArray(n)
        swayFreq = FloatArray(n)
        swayPhase = FloatArray(n)
        rot0 = FloatArray(n)
        spin = FloatArray(n)
        flipFreq = FloatArray(n)
        flipPhase = FloatArray(n)
        color = IntArray(n)
        shape = IntArray(n)
    }

    companion object {
        private val COLORS = intArrayOf(-0x73e6, -0x49d9, -0xd95f3, -0x36c4, -0x17a3fc)
        private const val EDGE = -0x85d800
        private const val TAU = 2 * Math.PI

        private fun petalShape(r: Random): Path {
            val p = Path()
            val wTop = 0.36f + 0.08f * r.nextFloat()
            p.moveTo(0f, 0.5f)
            p.cubicTo(-0.16f, 0.36f, -wTop - 0.04f, 0.02f, -wTop, -0.3f)
            val scallops = 4 + r.nextInt(2)
            for (k in 0 until scallops) {
                val tx = -wTop + 2 * wTop * (k + 0.5f) / scallops
                val nx = -wTop + 2 * wTop * (k + 1f) / scallops
                val tipY = -0.5f + 0.2f * sq(tx / wTop) - 0.06f - 0.05f * r.nextFloat()
                val notchY = if (k == scallops - 1) -0.3f else -0.5f + 0.2f * sq(nx / wTop) + 0.05f + 0.03f * r.nextFloat()
                p.quadTo(tx, tipY, nx, notchY)
            }
            p.cubicTo(wTop + 0.04f, 0.02f, 0.16f, 0.36f, 0f, 0.5f)
            p.close()
            return p
        }

        private fun veinPath(r: Random): Path {
            val p = Path()
            val bend = (r.nextFloat() - 0.5f) * 0.06f
            p.moveTo(0f, 0.42f)
            p.quadTo(bend, 0f, 0f, -0.36f)
            p.moveTo(0f, 0.3f)
            p.quadTo(-0.12f, -0.05f, -0.2f, -0.3f)
            p.moveTo(0f, 0.3f)
            p.quadTo(0.12f, -0.05f, 0.2f, -0.3f)
            return p
        }

        private fun sq(v: Float): Float = v * v

        private fun scale(c: Int, f: Float): Int {
            val r = minOf(255, (((c shr 16) and 0xFF) * f).toInt())
            val g = minOf(255, (((c shr 8) and 0xFF) * f).toInt())
            val b = minOf(255, ((c and 0xFF) * f).toInt())
            return -0x1000000 or (r shl 16) or (g shl 8) or b
        }
    }
}
