package com.gathin.tvhome.feature.system.widget
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.feature.system.data.*
import com.gathin.tvhome.feature.system.coordinator.*

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.View
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.system.data.Weather
import com.gathin.tvhome.feature.notification.data.NotificationRepositoryImpl
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin


class SleepingRobotScreensaverView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val startTimeMs = SystemClock.uptimeMillis()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPath1 = Path()
    private val blobPath2 = Path()
    private val robotRect = RectF()
    private val screenRect = RectF()
    private val bubbleRect = RectF()

    private val displayFont: Typeface by lazy {
        Fonts.MEDIUM
    }
    private val regularFont: Typeface by lazy {
        Fonts.REGULAR
    }

    private val calendar = Calendar.getInstance()
    private var timeText = "21:24"
    private var dateText = "Thứ Hai, 26 tháng 10"
    private var dreamThought = ""
    private var lastMinute = -1
    private var isNightDim = false
    private var wakingUp = false
    private var wakeStartMs = 0L

    private var shiftX = 0f
    private var shiftY = 0f

    private val frameTick = Runnable {
        if (isAttachedToWindow && windowVisibility == VISIBLE && isShown) {
            postInvalidateOnAnimation()
        }
    }

    init {
        buildScallopBlob(blobPath1, 145f * density, 12, 9f * density)
        buildScallopBlob(blobPath2, 95f * density, 8, 11f * density)
    }

    fun triggerWakeUp(onFinished: Runnable) {
        if (wakingUp) return
        wakingUp = true
        wakeStartMs = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
        postDelayed(onFinished, 220L)
    }

    private fun buildScallopBlob(path: Path, radius: Float, waves: Int, waveAmp: Float) {
        path.reset()
        val steps = 120
        for (i in 0..steps) {
            val angle = (i.toFloat() / steps) * (Math.PI * 2f).toFloat()
            val r = radius + sin(angle * waves) * waveAmp
            if (i == 0) path.moveTo(cos(angle) * r, sin(angle) * r)
            else path.lineTo(cos(angle) * r, sin(angle) * r)
        }
        path.close()
    }

    private fun updateClockIfNeeded() {
        calendar.timeInMillis = System.currentTimeMillis()
        val currentMin = calendar.get(Calendar.MINUTE)
        if (currentMin == lastMinute) return
        lastMinute = currentMin

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        isNightDim = hour >= 23 || hour < 6
        timeText = DateFormat.format("HH:mm", calendar).toString()

        val days = arrayOf(
            "Thứ Bảy", "Chủ Nhật", "Thứ Hai",
            "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu"
        )
        val dayOfWeek = days[calendar.get(Calendar.DAY_OF_WEEK) % 7]
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        dateText = "$dayOfWeek, $day tháng $month"

        shiftX = sin(currentMin * 0.5f) * 48f * density
        shiftY = cos(currentMin * 0.3f) * 28f * density
        dreamThought = resolveDreamThought()
    }

    private fun resolveDreamThought(): String {
        val unread = NotificationRepositoryImpl.getInstance().latestUnreadActionable()
        if (unread != null) {
            val title = unread.title.trim().ifEmpty { unread.message.trim() }
            return if (title.isNotEmpty()) "🔔 $title" else "🔔 Thông báo mới"
        }
        val rep = Weather.cached(context)
        if (rep != null) return "☁️ ${rep.temp.toInt()}°C • ${rep.place}"
        return ""
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        updateClockIfNeeded()
        val nowMs = SystemClock.uptimeMillis()
        val t = (nowMs - startTimeMs) / 1000f
        canvas.drawColor(Color.BLACK)

        val masterAlpha = if (isNightDim) 0.45f else 1f
        val centerX = w * 0.5f + shiftX
        val centerY = h * 0.52f + shiftY

        drawBlobs(canvas, centerX, centerY, t, masterAlpha)
        drawClockAndDate(canvas, centerX, centerY, masterAlpha)
        drawSleepingRobot(canvas, centerX, centerY, t, nowMs, masterAlpha)

        removeCallbacks(frameTick)
        val delay = when {
            wakingUp -> 16L
            isNightDim -> 500L
            else -> 200L
        }
        postDelayed(frameTick, delay)
    }

    private fun drawBlobs(canvas: Canvas, cx: Float, cy: Float, t: Float, alphaScale: Float) {
        paint.style = Paint.Style.FILL
        val s1 = canvas.save()
        canvas.translate(cx + 35f * density, cy + 25f * density)
        canvas.rotate(t * 2.5f)
        paint.color = applyAlpha(0xFF241E38.toInt(), alphaScale)
        canvas.drawPath(blobPath1, paint)
        canvas.restoreToCount(s1)

        val s2 = canvas.save()
        canvas.translate(cx - 110f * density, cy + 75f * density)
        canvas.rotate(-t * 1.8f)
        paint.color = applyAlpha(0xFF2E1F24.toInt(), alphaScale)
        canvas.drawPath(blobPath2, paint)
        canvas.restoreToCount(s2)
    }

    private fun drawClockAndDate(canvas: Canvas, cx: Float, cy: Float, alphaScale: Float) {
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = displayFont
        paint.textSize = 86f * density
        val clockColor = if (isNightDim) 0xFF8B5CF6.toInt() else 0xFFC4B5FD.toInt()
        paint.color = applyAlpha(clockColor, alphaScale)
        canvas.drawText(timeText, cx, cy + 22f * density, paint)

        paint.typeface = regularFont
        paint.textSize = 14.5f * density
        paint.color = applyAlpha(0xFFCBD5E1.toInt(), alphaScale * 0.85f)
        canvas.drawText(dateText, cx, cy + 56f * density, paint)
    }

    private fun drawSleepingRobot(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        t: Float,
        nowMs: Long,
        alphaScale: Float
    ) {
        val wakeProgress = if (wakingUp) {
            ((nowMs - wakeStartMs) / 220f).coerceIn(0f, 1f)
        } else 0f
        val wakeJump = sin(wakeProgress * Math.PI.toFloat()) * (-16f * density)
        val breathScaleY = 1f + sin(t * 1.6f) * 0.042f
        val breathOffsetY = sin(t * 1.6f) * 3.5f * density + wakeJump

        val botX = cx - 42f * density
        val botY = cy - 66f * density + breathOffsetY

        val saveBot = canvas.save()
        canvas.translate(botX, botY)
        val tilt = if (wakingUp) 0f else (-6f + sin(t * 0.8f) * 2.2f)
        canvas.rotate(tilt)
        canvas.scale(1f + wakeProgress * 0.08f, breathScaleY)

        robotRect.set(-28f * density, -20f * density, 28f * density, 20f * density)
        paint.style = Paint.Style.FILL
        paint.color = applyAlpha(0xFF141821.toInt(), alphaScale)
        canvas.drawRoundRect(robotRect, 12f * density, 12f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = applyAlpha(0xFF2B3548.toInt(), alphaScale)
        canvas.drawRoundRect(robotRect, 12f * density, 12f * density, paint)

        screenRect.set(-23f * density, -15f * density, 23f * density, 15f * density)
        paint.style = Paint.Style.FILL
        paint.color = applyAlpha(0xFF080A0F.toInt(), alphaScale)
        canvas.drawRoundRect(screenRect, 9f * density, 9f * density, paint)

        val twitch = (t % 14f) in 6.2f..6.7f
        if (wakingUp || twitch) {
            paint.style = Paint.Style.FILL
            paint.color = applyAlpha(0xFF38BDF8.toInt(), alphaScale)
            val eyeR = (if (wakingUp) 4.5f else 2.8f) * density
            canvas.drawCircle(-9.5f * density, 0f, eyeR, paint)
            canvas.drawCircle(9.5f * density, 0f, eyeR, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f * density
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = applyAlpha(0xFF38BDF8.toInt(), alphaScale)
            canvas.drawArc(
                -15f * density, -3.5f * density, -4f * density, 4.5f * density,
                15f, 150f, false, paint
            )
            canvas.drawArc(
                4f * density, -3.5f * density, 15f * density, 4.5f * density,
                15f, 150f, false, paint
            )
        }
        canvas.restoreToCount(saveBot)

        if (!wakingUp) {
            drawFloatingZAndDream(canvas, botX, botY, t, alphaScale)
        }
    }

    private fun drawFloatingZAndDream(
        canvas: Canvas,
        botX: Float,
        botY: Float,
        t: Float,
        alphaScale: Float
    ) {
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT_BOLD
        for (i in 0 until 2) {
            val zCycle = (t * 0.42f + i * 0.5f) % 1f
            val zAlpha = (sin(zCycle * Math.PI.toFloat()) * 225f * alphaScale)
                .toInt().coerceIn(0, 255)
            val zX = botX + 26f * density + zCycle * 16f * density +
                sin(t * 2f + i) * 3f * density
            val zY = botY - 12f * density - zCycle * 24f * density
            paint.textSize = (10f + zCycle * 6f) * density
            paint.color = Color.argb(zAlpha, 56, 189, 248)
            canvas.drawText(if (i == 0) "z" else "Z", zX, zY, paint)
        }

        if (dreamThought.isNotEmpty() && (t % 30f) < 10f) {
            val phase = (t % 30f) / 10f
            val bAlpha = (sin(phase * Math.PI.toFloat()) * 190f * alphaScale)
                .toInt().coerceIn(0, 255)
            paint.typeface = regularFont
            paint.textSize = 10.5f * density
            val tw = paint.measureText(dreamThought)
            val bx = botX + 38f * density
            val by = botY - 30f * density
            bubbleRect.set(bx, by - 16f * density, bx + tw + 14f * density, by + 4f * density)
            paint.color = Color.argb((bAlpha * 0.7f).toInt(), 30, 41, 59)
            canvas.drawRoundRect(bubbleRect, 8f * density, 8f * density, paint)
            paint.color = Color.argb(bAlpha, 226, 232, 240)
            canvas.drawText(dreamThought, bx + 7f * density, by - 2.5f * density, paint)
        }
    }

    private fun applyAlpha(color: Int, scale: Float): Int {
        val a = (((color ushr 24) and 0xFF) * scale).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) postInvalidateOnAnimation() else removeCallbacks(frameTick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frameTick)
        super.onDetachedFromWindow()
    }
}
