package com.gathin.tvhome.feature.agent.widget
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.coordinator.*
import com.gathin.tvhome.feature.agent.ui.*

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.gathin.tvhome.feature.system.ui.Screensaver


class DuinchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    fun interface PlayfulListener {
        fun onPlayful(mood: Int)
    }

    fun interface SleepListener {
        fun onSleepStateChanged(sleeping: Boolean)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val bounds = RectF()
    private val path = Path()

    private var helmetShader: LinearGradient
    private var visorShader: RadialGradient
    private var earShader: LinearGradient
    private var glowShader: RadialGradient

    private var lastInteraction = SystemClock.uptimeMillis()
    private var sleeping = false
    private val nextFrame = Runnable { invalidate() }

    private var mood = IDLE
    private var talking = false
    private var targetLookX = 0f
    private var targetLookY = 0f
    private var lookX = 0f
    private var lookY = 0f
    private var startTimeNanos = 0L
    private var nextBlinkNanos = 0L
    private var blinkEndNanos = 0L
    private var nextPlayfulNanos = 0L
    private var playfulEndNanos = 0L
    private var playfulMood = IDLE
    private var playfulListener: PlayfulListener? = null
    private var sleepListener: SleepListener? = null

    init {
        helmetShader = LinearGradient(0f, 0f, 0f, BASE_HEIGHT, HELMET_COLORS, HELMET_STOPS, Shader.TileMode.CLAMP)
        visorShader = RadialGradient(140f, 87f, 160f, VISOR_COLORS, VISOR_STOPS, Shader.TileMode.CLAMP)
        earShader = LinearGradient(0f, 75f, 0f, 135f, EAR_COLORS, EAR_STOPS, Shader.TileMode.CLAMP)
        glowShader = RadialGradient(0f, 0f, 48f, GLOW_COLORS, GLOW_STOPS, Shader.TileMode.CLAMP)
    }

    fun wake() {
        lastInteraction = SystemClock.uptimeMillis()
        if (sleeping) {
            sleeping = false
            post { sleepListener?.onSleepStateChanged(false) }
        }
        invalidate()
    }

    fun setSleepListener(listener: SleepListener?) {
        sleepListener = listener
    }

    var allowUnfocusedAnimation = false

    fun isSleeping(): Boolean = sleeping

    private fun canAnimate(): Boolean =
        isAttachedToWindow && isShown && windowVisibility == VISIBLE &&
            (hasWindowFocus() || allowUnfocusedAnimation)

    override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused)
        removeCallbacks(nextFrame)
        if (focused) wake()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(nextFrame)
        super.onDetachedFromWindow()
    }

    fun setMood(nextMood: Int) {
        if (mood == nextMood) return
        wake()
        mood = nextMood
        invalidate()
    }

    fun getMood(): Int = mood

    fun setTalking(on: Boolean) {
        if (talking == on) return
        wake()
        talking = on
        invalidate()
    }

    fun isTalking(): Boolean = talking

    fun lookAt(horizontal: Float, vertical: Float) {
        wake()
        targetLookX = horizontal.coerceIn(-1f, 1f)
        targetLookY = vertical.coerceIn(-1f, 1f)
        invalidate()
    }

    fun setPlayfulListener(listener: PlayfulListener?) {
        playfulListener = listener
    }

    fun pulsePlayful(temporaryMood: Int, durationMillis: Long) {
        val now = System.nanoTime()
        playfulMood = temporaryMood
        playfulEndNanos = now + durationMillis * 1_000_000L
        invalidate()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        wake()
        animate().scaleX(if (gainFocus) 1.12f else 1f).scaleY(if (gainFocus) 1.12f else 1f)
            .setDuration(180).start()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val now = System.nanoTime()
        if (startTimeNanos == 0L) {
            startTimeNanos = now
            scheduleNextBlink(now)
            scheduleNextPlayful(now)
        }

        val seconds = (now - startTimeNanos) / NANOS_PER_SECOND.toFloat()
        val working = talking || mood == THINKING
        val wasSleeping = sleeping
        val elapsed = SystemClock.uptimeMillis() - lastInteraction
        sleeping = !working && elapsed >= resolveSleepTimeoutMs()
        if (wasSleeping != sleeping) {
            val state = sleeping
            post { sleepListener?.onSleepStateChanged(state) }
        }
        if (!sleeping) updateTimers(now)
        lookX += (targetLookX - lookX) * 0.18f
        lookY += (targetLookY - lookY) * 0.18f
        if (Math.abs(targetLookX - lookX) < 0.01f) lookX = targetLookX
        if (Math.abs(targetLookY - lookY) < 0.01f) lookY = targetLookY

        val activeMood = resolveActiveMood(now)
        val scaleFactor = if (activeMood == SLEEP) 0.82f else 0.92f
        val scale = minOf(viewWidth / BASE_WIDTH, viewHeight / BASE_HEIGHT) * scaleFactor
        val offsetX = (viewWidth - BASE_WIDTH * scale) * 0.5f
        val offsetY = if (activeMood == SLEEP) {
            (viewHeight - BASE_HEIGHT * scale) * 0.72f
        } else {
            (viewHeight - BASE_HEIGHT * scale) * 0.5f
        }

        val continuous = needsContinuousFrames(activeMood, now)
        val bob = if (continuous) {
            val bobSpeed = if (talking) 5.2f else if (isFocused) 4.2f else 2.4f
            val bobAmount = if (talking) 5.5f else if (isFocused) 5.0f else 3.5f
            bobAmount * Math.sin((seconds * bobSpeed).toDouble()).toFloat()
        } else {
            0f
        }

        val tilt = if (activeMood == WINK) -3.5f
            else if (activeMood == SLEEP) 2.5f
            else if (continuous && isFocused) 2f * Math.sin((seconds * 2.0f).toDouble()).toFloat()
            else 0f

        canvas.save()
        canvas.translate(offsetX, offsetY + bob * scale)
        canvas.scale(scale, scale)
        canvas.rotate(tilt, 140f, 105f)

        drawEars(canvas, activeMood, seconds)
        drawHelmetAndVisor(canvas, activeMood)
        drawFace(canvas, activeMood, now, seconds)

        canvas.restore()
        removeCallbacks(nextFrame)
        if (!canAnimate()) return
        if (continuous) {
            postDelayed(nextFrame, FRAME_DELAY_MS)
        } else if (!sleeping) {
            val blinkInMs = maxOf(10L, (nextBlinkNanos - now) / 1_000_000L)
            val playfulInMs = maxOf(1000L, (nextPlayfulNanos - now) / 1_000_000L)
            val sleepInMs = maxOf(1000L, resolveSleepTimeoutMs() - elapsed)
            val nextDelay = minOf(blinkInMs, minOf(playfulInMs, sleepInMs))
            postDelayed(nextFrame, nextDelay)
        }
    }

    private fun needsContinuousFrames(activeMood: Int, now: Long): Boolean {
        if (talking) return true
        if (isFocused) return true
        if (activeMood == EXCITED || activeMood == THINKING) return true
        if (now < playfulEndNanos) return true
        if (now < blinkEndNanos) return true
        if (Math.abs(targetLookX - lookX) > 0.01f || Math.abs(targetLookY - lookY) > 0.01f) {
            return true
        }
        return false
    }

    private fun resolveActiveMood(now: Long): Int {
        if (sleeping) return SLEEP
        if (isFocused) return EXCITED
        if (mood != IDLE) return mood
        if (isWaiting()) return WAITING
        if (now < playfulEndNanos) return playfulMood
        return IDLE
    }

    fun isWaiting(): Boolean {
        val elapsed = SystemClock.uptimeMillis() - lastInteraction
        return !talking && mood == IDLE && elapsed in IDLE_WAIT_MS until resolveSleepTimeoutMs()
    }

    private fun resolveSleepTimeoutMs(): Long {
        val minutes = try {
            Screensaver.minutes(context)
        } catch (_: Exception) {
            DEFAULT_SLEEP_MINUTES
        }
        val targetMinutes = if (minutes in 1..60) minutes else DEFAULT_SLEEP_MINUTES
        return targetMinutes * 60_000L
    }

    private fun updateTimers(now: Long) {
        if (now >= nextBlinkNanos) {
            blinkEndNanos = now + 150_000_000L
            scheduleNextBlink(now)
        }
        if (mood == IDLE && !isWaiting() && now >= nextPlayfulNanos) {
            val options = intArrayOf(HAPPY, WINK, LOVE, SURPRISED, SILLY)
            val nextIndex = Math.abs(now / 1_000_000L % options.size).toInt()
            playfulMood = options[nextIndex]
            playfulEndNanos = now + 2_400_000_000L
            scheduleNextPlayful(now)
            playfulListener?.onPlayful(playfulMood)
        }
    }

    private fun scheduleNextBlink(now: Long) {
        val delayMillis = 2600L + Math.abs(now / 100_000L % 2800L)
        nextBlinkNanos = now + delayMillis * 1_000_000L
    }

    private fun scheduleNextPlayful(now: Long) {
        val delayMillis = 60_000L + Math.abs(now / 100_000L % 60_000L)
        nextPlayfulNanos = now + delayMillis * 1_000_000L
    }

    private fun drawEars(canvas: Canvas, activeMood: Int, seconds: Float) {
        fillPaint.shader = null
        if (activeMood == SLEEP) {
            fillPaint.color = 0x4D004488
        } else if (talking) {
            val pulse = (200 + 55 * Math.sin((seconds * 14f).toDouble())).toInt()
            fillPaint.color = -0x1000000 or (pulse shl 16) or (pulse shl 8) or 0xFF
        } else if (activeMood == EXCITED || activeMood == LOVE) {
            fillPaint.color = if (activeMood == LOVE) -0xbf7f else -0xff0001
        } else {
            fillPaint.shader = earShader
        }
        bounds.set(2f, 75f, 18f, 135f)
        canvas.drawRoundRect(bounds, 8f, 8f, fillPaint)
        bounds.set(262f, 75f, 278f, 135f)
        canvas.drawRoundRect(bounds, 8f, 8f, fillPaint)
        fillPaint.shader = null
    }

    private fun drawHelmetAndVisor(canvas: Canvas, activeMood: Int) {
        bounds.set(10f, 0f, 270f, 210f)
        if (activeMood == SLEEP) {
            fillPaint.shader = null
            fillPaint.color = -0xf0ebe4
            canvas.drawRoundRect(bounds, 52f, 52f, fillPaint)
            strokePaint.color = -0xe5ddd2
            strokePaint.strokeWidth = 2f
            canvas.drawRoundRect(bounds, 52f, 52f, strokePaint)
        } else {
            fillPaint.shader = helmetShader
            canvas.drawRoundRect(bounds, 52f, 52f, fillPaint)
            fillPaint.shader = null
            if (activeMood == EXCITED || activeMood == LOVE) {
                strokePaint.color = if (activeMood == LOVE) -0xbf7f else -0xff0001
                strokePaint.strokeWidth = 2.2f
                canvas.drawRoundRect(bounds, 52f, 52f, strokePaint)
            }
        }

        bounds.set(24f, 16f, 256f, 194f)
        if (activeMood == SLEEP) {
            fillPaint.shader = null
            fillPaint.color = -0xfbf9f7
            canvas.drawRoundRect(bounds, 42f, 42f, fillPaint)
            strokePaint.color = -0xf4f0ea
            strokePaint.strokeWidth = 1.5f
            canvas.drawRoundRect(bounds, 42f, 42f, strokePaint)
        } else {
            fillPaint.shader = visorShader
            canvas.drawRoundRect(bounds, 42f, 42f, fillPaint)
            fillPaint.shader = null
            val border = if (activeMood == LOVE) -0xbf7f else if (activeMood == EXCITED) -0xff0001 else -0xf6f1e9
            strokePaint.color = border
            strokePaint.strokeWidth = if (activeMood == EXCITED || activeMood == LOVE) 1.8f else 3f
            canvas.drawRoundRect(bounds, 42f, 42f, strokePaint)
        }
    }

    private fun drawFace(canvas: Canvas, activeMood: Int, now: Long, seconds: Float) {
        val blinking = now < blinkEndNanos && (activeMood == IDLE || activeMood == WAITING) && !talking
        val shiftX = lookX * 10f
        val shiftY = lookY * 7f

        when (activeMood) {
            SLEEP -> drawSleepingFace(canvas)
            WAITING -> drawWaitingFace(canvas, shiftX, shiftY, blinking, seconds)
            LOVE -> drawLoveFace(canvas, shiftX, shiftY, seconds)
            SURPRISED -> drawSurprisedFace(canvas, shiftX, shiftY, seconds)
            SILLY -> drawSillyFace(canvas, shiftX, shiftY, seconds)
            SAD -> drawSadFace(canvas, shiftX, shiftY, seconds)
            HAPPY -> drawHappyFace(canvas, shiftX, shiftY, seconds)
            WINK -> drawWinkFace(canvas, shiftX, shiftY, seconds)
            EXCITED -> drawExcitedFace(canvas, shiftX, shiftY, seconds)
            THINKING -> drawThinkingFace(canvas, seconds)
            else -> drawIdleFace(canvas, shiftX, shiftY, blinking, seconds)
        }
    }

    private fun drawWaitingFace(
        canvas: Canvas,
        shiftX: Float,
        shiftY: Float,
        blinking: Boolean,
        seconds: Float
    ) {
        val driftX = if (isFocused || talking) {
            14f * Math.sin((seconds * 0.7f).toDouble()).toFloat()
        } else {
            0f
        }
        val driftY = if (isFocused || talking) {
            3.5f * Math.cos((seconds * 0.5f).toDouble()).toFloat()
        } else {
            0f
        }
        val leftX = 85f + shiftX + driftX
        val rightX = 195f + shiftX + driftX
        val eyeY = 100f + shiftY + driftY

        if (blinking) {
            strokePaint.color = -0xff2701
            strokePaint.strokeWidth = 6f
            path.reset()
            path.moveTo(leftX - 22f, eyeY)
            path.quadTo(leftX, eyeY + 8f, leftX + 22f, eyeY)
            path.moveTo(rightX - 22f, eyeY)
            path.quadTo(rightX, eyeY + 8f, rightX + 22f, eyeY)
            canvas.drawPath(path, strokePaint)
        } else {
            drawOpenEye(canvas, leftX, eyeY, 24f, 28f, -0xff2701)
            drawOpenEye(canvas, rightX, eyeY, 24f, 28f, -0xff2701)
        }

        if (talking) {
            drawTalkingMouth(canvas, shiftX, shiftY, seconds)
            return
        }
        val mx = 140f + shiftX * 0.4f + driftX * 0.3f
        val my = 152f + shiftY * 0.3f
        strokePaint.color = -0xff2701
        strokePaint.strokeWidth = 4f
        path.reset()
        path.moveTo(mx - 10f, my)
        path.quadTo(mx, my + 5f, mx + 10f, my)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawEyeGlow(canvas: Canvas, cx: Float, cy: Float, radiusScale: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(radiusScale, radiusScale)
        glowPaint.shader = glowShader
        canvas.drawCircle(0f, 0f, 48f, glowPaint)
        canvas.restore()
    }

    private fun drawOpenEye(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        drawEyeGlow(canvas, cx, cy, rx / 26f)
        fillPaint.shader = null
        fillPaint.color = color
        bounds.set(cx - rx, cy - ry, cx + rx, cy + ry)
        canvas.drawOval(bounds, fillPaint)

        fillPaint.color = -0x1
        canvas.drawCircle(cx + rx * 0.35f, cy - ry * 0.29f, rx * 0.36f, fillPaint)
        fillPaint.color = 0xD9FFFFFF.toInt()
        canvas.drawCircle(cx - rx * 0.34f, cy + ry * 0.35f, rx * 0.16f, fillPaint)
    }

    private fun drawTalkingMouth(canvas: Canvas, shiftX: Float, shiftY: Float, seconds: Float) {
        val mx = 140f + shiftX * 0.5f
        val my = 151f + shiftY * 0.4f
        val wave = (0.5f + 0.5f * (Math.sin((seconds * 22f).toDouble()) * Math.cos((seconds * 9.5f).toDouble()))).toFloat()
        if (wave < 0.16f) {
            strokePaint.color = -0xff0001
            strokePaint.strokeWidth = 4.5f
            path.reset()
            path.moveTo(mx - 14f, my)
            path.quadTo(mx, my + 6f, mx + 14f, my)
            canvas.drawPath(path, strokePaint)
            return
        }
        val halfW = 11f + wave * 11f
        val openH = 5f + wave * 17f
        fillPaint.shader = null
        fillPaint.color = -0xff0001
        bounds.set(mx - halfW, my - openH * 0.45f, mx + halfW, my + openH * 0.75f)
        canvas.drawRoundRect(bounds, halfW * 0.7f, openH * 0.6f, fillPaint)

        fillPaint.color = -0xf8f3ec
        bounds.inset(3.2f, 3.0f)
        canvas.drawRoundRect(bounds, halfW * 0.55f, openH * 0.5f, fillPaint)

        if (openH > 10f) {
            fillPaint.color = -0xac8a
            bounds.set(mx - halfW * 0.48f, my + openH * 0.1f, mx + halfW * 0.48f, my + openH * 0.62f)
            canvas.drawOval(bounds, fillPaint)
        }
    }

    private fun drawIdleFace(canvas: Canvas, shiftX: Float, shiftY: Float, blinking: Boolean, seconds: Float) {
        val leftX = 85f + shiftX
        val rightX = 195f + shiftX
        val eyeY = 100f + shiftY
        if (blinking) {
            strokePaint.color = -0xff2701
            strokePaint.strokeWidth = 6f
            path.reset()
            path.moveTo(leftX - 24f, eyeY)
            path.quadTo(leftX, eyeY + 10f, leftX + 24f, eyeY)
            path.moveTo(rightX - 24f, eyeY)
            path.quadTo(rightX, eyeY + 10f, rightX + 24f, eyeY)
            canvas.drawPath(path, strokePaint)
        } else {
            drawOpenEye(canvas, leftX, eyeY, 26f, 34f, -0xff2701)
            drawOpenEye(canvas, rightX, eyeY, 26f, 34f, -0xff2701)
        }

        if (talking) {
            drawTalkingMouth(canvas, shiftX, shiftY, seconds)
            return
        }
        strokePaint.color = -0xff2701
        strokePaint.strokeWidth = 4.5f
        path.reset()
        path.moveTo(126f + shiftX * 0.5f, 150f + shiftY * 0.4f)
        path.quadTo(140f + shiftX * 0.5f, 160f + shiftY * 0.4f, 154f + shiftX * 0.5f, 150f + shiftY * 0.4f)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawHappyFace(canvas: Canvas, shiftX: Float, shiftY: Float, seconds: Float) {
        drawHappyArch(canvas, 85f + shiftX, 98f + shiftY)
        drawHappyArch(canvas, 195f + shiftX, 98f + shiftY)

        if (talking) {
            drawTalkingMouth(canvas, shiftX, shiftY, seconds)
            return
        }
        val mx = shiftX * 0.5f
        val my = shiftY * 0.4f
        fillPaint.shader = null
        fillPaint.color = -0xff2701
        path.reset()
        path.moveTo(122f + mx, 144f + my)
        path.quadTo(140f + mx, 142f + my, 158f + mx, 144f + my)
        path.quadTo(140f + mx, 166f + my, 122f + mx, 144f + my)
        path.close()
        canvas.drawPath(path, fillPaint)

        fillPaint.color = -0xac8a
        path.reset()
        path.moveTo(130f + mx, 152f + my)
        path.quadTo(140f + mx, 147f + my, 150f + mx, 152f + my)
        path.quadTo(140f + mx, 164f + my, 130f + mx, 152f + my)
        path.close()
        canvas.drawPath(path, fillPaint)
    }

    private fun drawHappyArch(canvas: Canvas, cx: Float, cy: Float) {
        drawEyeGlow(canvas, cx, cy, 1f)
        fillPaint.shader = null
        fillPaint.color = -0xff0001
        path.reset()
        path.moveTo(cx - 27f, cy + 12f)
        path.cubicTo(cx - 25f, cy - 28f, cx + 25f, cy - 28f, cx + 27f, cy + 12f)
        path.cubicTo(cx + 17f, cy - 6f, cx - 17f, cy - 6f, cx - 27f, cy + 12f)
        path.close()
        canvas.drawPath(path, fillPaint)
    }

    private fun drawWinkFace(canvas: Canvas, shiftX: Float, shiftY: Float, seconds: Float) {
        drawOpenEye(canvas, 85f + shiftX, 100f + shiftY, 26f, 34f, -0xff2701)
        val rx = 195f + shiftX
        val ry = 102f + shiftY
        drawEyeGlow(canvas, rx, ry, 0.9f)
        strokePaint.color = -0xff2701
        strokePaint.strokeWidth = 7f
        path.reset()
        path.moveTo(rx - 23f, ry + 6f)
        path.quadTo(rx, ry - 16f, rx + 23f, ry + 6f)
        canvas.drawPath(path, strokePaint)

        if (talking) {
            drawTalkingMouth(canvas, shiftX, shiftY, seconds)
            return
        }
        path.reset()
        strokePaint.strokeWidth = 4.5f
        path.moveTo(128f + shiftX * 0.5f, 148f + shiftY * 0.4f)
        path.quadTo(146f + shiftX * 0.5f, 156f + shiftY * 0.4f, 156f + shiftX * 0.5f, 144f + shiftY * 0.4f)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawExcitedFace(canvas: Canvas, shiftX: Float, shiftY: Float, seconds: Float) {
        drawOpenEye(canvas, 85f + shiftX, 98f + shiftY, 32f, 40f, -0xff0f01)
        drawOpenEye(canvas, 195f + shiftX, 98f + shiftY, 32f, 40f, -0xff0f01)

        if (talking) {
            drawTalkingMouth(canvas, shiftX, shiftY, seconds)
            return
        }
        val mx = 140f + shiftX * 0.5f
        val my = 150f + shiftY * 0.4f
        fillPaint.shader = null
        fillPaint.color = -0xff0f01
        bounds.set(mx - 10f, my - 13f, mx + 10f, my + 13f)
        canvas.drawOval(bounds, fillPaint)
        fillPaint.color = -0xf6f1e9
        bounds.set(mx - 6f, my - 8.5f, mx + 6f, my + 8.5f)
        canvas.drawOval(bounds, fillPaint)
    }

    private fun drawLoveFace(canvas: Canvas, shiftX: Float, shiftY: Float, seconds: Float) {
        val leftX = 85f + shiftX
        val rightX = 195f + shiftX
        val eyeY = 98f + shiftY
        val beat = 1f + 0.12f * Math.sin((seconds * 6.5f).toDouble()).toFloat()
        drawHeart(canvas, leftX, eyeY, 18f * beat, -0xd293)
        drawHeart(canvas, rightX, eyeY, 18f * beat, -0xd293)

        fillPaint.shader = null
        fillPaint.color = 0x55FF4081
        bounds.set(leftX - 22f, eyeY + 36f, leftX + 18f, eyeY + 54f)
        canvas.drawOval(bounds, fillPaint)
        bounds.set(rightX - 18f, eyeY + 36f, rightX + 22f, eyeY + 54f)
        canvas.drawOval(bounds, fillPaint)

        if (talking) {
            drawTalkingMouth(canvas, shiftX, shiftY, seconds)
            return
        }
        strokePaint.color = -0xff0001
        strokePaint.strokeWidth = 4.5f
        path.reset()
        path.moveTo(126f + shiftX * 0.5f, 148f + shiftY * 0.4f)
        path.quadTo(140f + shiftX * 0.5f, 160f + shiftY * 0.4f, 154f + shiftX * 0.5f, 148f + shiftY * 0.4f)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        drawEyeGlow(canvas, cx, cy, 0.9f)
        fillPaint.shader = null
        fillPaint.color = color
        path.reset()
        path.moveTo(cx, cy + size * 0.6f)
        path.cubicTo(cx - size, cy - size * 0.2f, cx - size * 0.9f, cy - size * 0.9f, cx, cy - size * 0.4f)
        path.cubicTo(cx + size * 0.9f, cy - size * 0.9f, cx + size, cy - size * 0.2f, cx, cy + size * 0.6f)
        path.close()
        canvas.drawPath(path, fillPaint)
    }

    private fun drawSurprisedFace(canvas: Canvas, shiftX: Float, shiftY: Float, seconds: Float) {
        val leftX = 85f + shiftX
        val rightX = 195f + shiftX
        val eyeY = 96f + shiftY
        drawOpenEye(canvas, leftX, eyeY, 34f, 42f, -0xff0001)
        drawOpenEye(canvas, rightX, eyeY, 34f, 42f, -0xff0001)

        if (talking) {
            drawTalkingMouth(canvas, shiftX, shiftY, seconds)
            return
        }
        val mx = 140f + shiftX * 0.5f
        val my = 152f + shiftY * 0.4f
        fillPaint.shader = null
        fillPaint.color = -0xff0001
        bounds.set(mx - 12f, my - 14f, mx + 12f, my + 14f)
        canvas.drawOval(bounds, fillPaint)
        fillPaint.color = -0xf6f1e9
        bounds.set(mx - 8f, my - 10f, mx + 8f, my + 10f)
        canvas.drawOval(bounds, fillPaint)
    }

    private fun drawSillyFace(canvas: Canvas, shiftX: Float, shiftY: Float, seconds: Float) {
        val leftX = 85f + shiftX
        val rightX = 195f + shiftX
        val eyeY = 98f + shiftY
        drawOpenEye(canvas, leftX, eyeY, 28f, 36f, -0xff2701)
        drawEyeGlow(canvas, rightX, eyeY, 0.9f)
        strokePaint.color = -0xff2701
        strokePaint.strokeWidth = 7f
        path.reset()
        path.moveTo(rightX - 22f, eyeY + 4f)
        path.quadTo(rightX, eyeY - 14f, rightX + 22f, eyeY + 4f)
        canvas.drawPath(path, strokePaint)

        if (talking) {
            drawTalkingMouth(canvas, shiftX, shiftY, seconds)
            return
        }
        val mx = 140f + shiftX * 0.5f
        val my = 148f + shiftY * 0.4f
        strokePaint.color = -0xff2701
        strokePaint.strokeWidth = 4.5f
        path.reset()
        path.moveTo(mx - 18f, my)
        path.quadTo(mx, my + 12f, mx + 18f, my)
        canvas.drawPath(path, strokePaint)

        fillPaint.shader = null
        fillPaint.color = -0xac8a
        bounds.set(mx - 2f, my + 4f, mx + 14f, my + 20f)
        canvas.drawRoundRect(bounds, 6f, 6f, fillPaint)
    }

    private fun drawSadFace(canvas: Canvas, shiftX: Float, shiftY: Float, seconds: Float) {
        val leftX = 85f + shiftX
        val rightX = 195f + shiftX
        val eyeY = 104f + shiftY
        drawOpenEye(canvas, leftX, eyeY, 22f, 26f, -0xb03c09)
        drawOpenEye(canvas, rightX, eyeY, 22f, 26f, -0xb03c09)

        strokePaint.color = -0xfd772f
        strokePaint.strokeWidth = 4f
        path.reset()
        path.moveTo(leftX - 20f, eyeY - 26f)
        path.lineTo(leftX + 20f, eyeY - 32f)
        path.moveTo(rightX + 20f, eyeY - 26f)
        path.lineTo(rightX - 20f, eyeY - 32f)
        canvas.drawPath(path, strokePaint)

        if (talking) {
            drawTalkingMouth(canvas, shiftX, shiftY, seconds)
            return
        }
        val mx = 140f + shiftX * 0.5f
        val my = 156f + shiftY * 0.4f
        strokePaint.color = -0xb03c09
        strokePaint.strokeWidth = 4.5f
        path.reset()
        path.moveTo(mx - 14f, my)
        path.quadTo(mx, my - 8f, mx + 14f, my)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawThinkingFace(canvas: Canvas, seconds: Float) {
        val sway = 6f * Math.sin((seconds * 3.2f).toDouble()).toFloat()
        drawOpenEye(canvas, 92f + sway, 85f, 25f, 32f, -0xff2701)
        drawOpenEye(canvas, 202f + sway, 85f, 25f, 32f, -0xff2701)

        strokePaint.color = -0xff2701
        strokePaint.strokeWidth = 4.5f
        path.reset()
        path.moveTo(126f, 148f)
        path.quadTo(134f, 140f, 142f, 148f)
        path.quadTo(150f, 156f, 158f, 148f)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawSleepingFace(canvas: Canvas) {
        strokePaint.color = 0xB30099EE.toInt()
        strokePaint.strokeWidth = 5.5f
        path.reset()
        path.moveTo(64f, 105f)
        path.quadTo(85f, 122f, 106f, 105f)
        path.moveTo(174f, 105f)
        path.quadTo(195f, 122f, 216f, 105f)
        canvas.drawPath(path, strokePaint)

        canvas.save()
        canvas.rotate(10f, 208f, 58f)
        textPaint.textSize = 22f
        textPaint.color = 0x8000B4FF.toInt()
        canvas.drawText("z", 208f, 58f, textPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(14f, 240f, 22f)
        textPaint.textSize = 34f
        textPaint.color = 0xB300D4FF.toInt()
        canvas.drawText("Z", 240f, 22f, textPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(18f, 274f, -12f)
        textPaint.textSize = 48f
        textPaint.color = 0xFF00E5FF.toInt()
        canvas.drawText("Z", 274f, -12f, textPaint)
        canvas.restore()
    }

    companion object {
        const val IDLE = 0
        const val HAPPY = 1
        const val WINK = 2
        const val EXCITED = 3
        const val SLEEP = 4
        const val THINKING = 5
        const val LOVE = 6
        const val SURPRISED = 7
        const val SILLY = 8
        const val SAD = 9
        const val WAITING = 10

        private const val BASE_WIDTH = 280f
        private const val BASE_HEIGHT = 210f
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private val HELMET_COLORS = intArrayOf(-0xd0906, -0x2f2418, -0x634f3a)
        private val HELMET_STOPS = floatArrayOf(0f, 0.6f, 1f)
        private val VISOR_COLORS = intArrayOf(-0xf1e9dc, -0xfaeff2)
        private val VISOR_STOPS = floatArrayOf(0f, 1f)
        private val EAR_COLORS = intArrayOf(-0xff0001, -0xff8802)
        private val EAR_STOPS = floatArrayOf(0f, 1f)
        private val GLOW_COLORS = intArrayOf(0x5900F0FF.toInt(), 0x1A00A2FF.toInt(), 0x00000000)
        private val GLOW_STOPS = floatArrayOf(0f, 0.7f, 1f)

        private const val IDLE_WAIT_MS = 20_000L
        private const val DEFAULT_SLEEP_MINUTES = 10
        private const val FRAME_DELAY_MS = 50L
    }
}
