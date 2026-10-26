package com.gathin.tvhome.feature.system.widget
import com.gathin.tvhome.feature.system.data.*
import com.gathin.tvhome.feature.system.coordinator.*

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextWatcher
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.motion.Expressive
import com.gathin.tvhome.core.motion.Motion
import java.util.Locale
import java.util.concurrent.Executors


class SysInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val barBounds = RectF()

    private var density: Float = resources.displayMetrics.density
    private var animations = false
    private var widthAnimation: SpringAnimation? = null
    private var widthPx = 0f

    private var ramUsage = 0f
    private var cpuUsage = 0f
    private var storageUsage = 0f
    private var lastCpu = -1
    private var storageReadAt = 0L
    private var statsReading = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private var ramText = ""
    private var cpuText = "--%"
    private var romText = ""
    private var barAlpha = 1f

    init {
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = null
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(editable: Editable?) {
                if (isFocused || isActivated) updateReveal()
            }
        })
    }

    fun init(enableAnimations: Boolean, reducedMotion: Boolean) {
        animations = enableAnimations
        val horizontalPadding = Math.round(TEXT_PADDING_DP * density)
        setPaddingRelative(horizontalPadding, 0, horizontalPadding, 0)

        widthAnimation = WIDTH_SPRING.reduced(reducedMotion).apply(SpringAnimation(this, WIDTH))
        widthAnimation?.setMinimumVisibleChange(1f)
        setWidthPx(collapsedWidth())
    }

    fun updateReveal() {
        val target = if (isFocused || isActivated) expandedWidth() else collapsedWidth()
        if (widthPx == target && (widthAnimation == null || !widthAnimation!!.isRunning)) return

        if (!animations) {
            widthAnimation?.cancel()
            setWidthPx(target)
            return
        }
        widthAnimation?.animateToFinalPosition(target)
    }

    private fun collapsedWidth(): Float = COLLAPSED_WIDTH_DP * density

    private fun expandedWidth(): Float {
        val currentText = text
        if (currentText.isNullOrEmpty()) return collapsedWidth()
        val textWidth = StaticLayout.getDesiredWidth(currentText, paint)
        return textWidth + 2f * TEXT_PADDING_DP * density
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        paintStats()
        updateReveal()
    }

    private fun setWidthPx(pixels: Float) {
        widthPx = pixels
        val collapsed = collapsedWidth()
        val open = expandedWidth() - collapsed
        val fraction = if (open <= 0f) 0f else ((pixels - collapsed) / open).coerceIn(0f, 1f)

        barAlpha = (1f - fraction * 2.5f).coerceAtLeast(0f)
        val labelAlpha = ((fraction - 0.4f) / 0.6f).coerceAtLeast(0f)

        Expressive.of(this)?.labelAlpha(labelAlpha)

        val params = layoutParams
        if (params != null) {
            val targetWidth = Math.round(maxOf(collapsed, pixels))
            if (params.width != targetWidth) {
                params.width = targetWidth
                layoutParams = params
            }
        }
        invalidate()
    }

    fun updateStats(cpuPercent: Int) {
        updateCpu(cpuPercent)
        if (!statsReading && isAttachedToWindow) {
            statsReading = true
            val now = SystemClock.uptimeMillis()
            val readStorage = romText.isEmpty() || now - storageReadAt >= 60_000
            statsWorker.execute {
                val manager = context.getSystemService(ActivityManager::class.java)
                val memory = ActivityManager.MemoryInfo()
                var totalStorage = -1L
                var freeStorage = -1L
                try {
                    manager?.getMemoryInfo(memory)
                    if (readStorage) {
                        val disk = StatFs(Environment.getDataDirectory().absolutePath)
                        totalStorage = disk.totalBytes
                        freeStorage = disk.availableBytes
                    }
                } catch (_: RuntimeException) {}

                mainHandler.post {
                    statsReading = false
                    if (!isAttachedToWindow) return@post
                    ramUsage = if (memory.totalMem > 0) {
                        1f - memory.availMem.toFloat() / memory.totalMem
                    } else 0f
                    ramText = formatCompactSize(memory.availMem)
                    if (totalStorage >= 0) {
                        storageUsage = if (totalStorage > 0) {
                            1f - freeStorage.toFloat() / totalStorage
                        } else 0f
                        romText = formatGb(freeStorage)
                        storageReadAt = SystemClock.uptimeMillis()
                    }
                    paintStats()
                }
            }
        }
        paintStats()
    }

    private fun paintStats() {
        val builder = SpannableStringBuilder()
        val iconSize = Math.round(10.5f * density)

        appendMetric(builder, R.drawable.ic_ram, ramText.ifEmpty { "--" }, iconSize)
        builder.append(" ")
        appendMetric(builder, R.drawable.ic_memory, cpuText, iconSize)
        builder.append(" ")
        appendMetric(builder, R.drawable.ic_storage, romText.ifEmpty { "--" }, iconSize)

        text = builder
        invalidate()
    }

    private fun appendMetric(
        builder: SpannableStringBuilder,
        iconRes: Int,
        value: String,
        iconSize: Int
    ) {
        val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate()
        if (drawable != null) {
            drawable.setBounds(0, 0, iconSize, iconSize)
            drawable.setTint(currentTextColor)
            val start = builder.length
            builder.append(" ")
            builder.setSpan(
                CenteredImageSpan(drawable),
                start,
                start + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.append("\u200A")
        }
        builder.append(value)
    }

    private fun updateCpu(cpuPercent: Int) {
        if (cpuPercent >= 0) lastCpu = cpuPercent
        val effectiveCpu = if (cpuPercent >= 0) cpuPercent else lastCpu
        if (effectiveCpu >= 0) {
            cpuUsage = (effectiveCpu / 100f).coerceIn(0f, 1f)
            cpuText = "$effectiveCpu%"
            return
        }
        cpuUsage = 0f
        cpuText = "--%"
    }

    override fun onDetachedFromWindow() {
        widthAnimation?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (barAlpha <= 0f) super.onDraw(canvas) else drawBars(canvas)
    }

    private fun drawBars(canvas: Canvas) {
        val barWidth = BAR_WIDTH_DP * density
        val barHeight = BAR_HEIGHT_DP * density
        val barGap = BAR_GAP_DP * density
        val cornerRadius = barWidth / 2f
        val totalWidth = 3f * barWidth + 2f * barGap
        val startX = scrollX + (width - totalWidth) / 2f
        val topY = scrollY + (height - barHeight) / 2f
        val bottomY = topY + barHeight

        val palette = Palette.currentOrBaseline()
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val usages = floatArrayOf(ramUsage, cpuUsage, storageUsage)
        val barColors = intArrayOf(palette.primary, palette.tertiary, palette.secondary)

        for (barIndex in 0 until 3) {
            val slot = if (isRtl) 2 - barIndex else barIndex
            val barLeft = startX + slot * (barWidth + barGap)
            val barRight = barLeft + barWidth
            val color = barColors[barIndex]

            trackPaint.color = Palette.alpha(color, Math.round(0x33 * barAlpha))
            barBounds.set(barLeft, topY, barRight, bottomY)
            canvas.drawRoundRect(barBounds, cornerRadius, cornerRadius, trackPaint)

            val usage = usages[barIndex]
            if (usage > 0f) {
                val fillHeight = (barHeight * usage).coerceIn(barWidth, barHeight)
                progressPaint.color = Palette.alpha(color, Math.round(0xEE * barAlpha))
                barBounds.set(barLeft, bottomY - fillHeight, barRight, bottomY)
                canvas.drawRoundRect(barBounds, cornerRadius, cornerRadius, progressPaint)
            }
        }
    }

    companion object {
        private const val COLLAPSED_WIDTH_DP = 44f
        private const val BAR_WIDTH_DP = 4.5f
        private const val BAR_HEIGHT_DP = 20f
        private const val BAR_GAP_DP = 3.5f
        private const val TEXT_PADDING_DP = 9.5f

        private val WIDTH_SPRING = Motion.Spec(
            Motion.DEFAULT_SPATIAL.damping,
            Motion.FAST_SPATIAL.stiffness
        )
        private val statsWorker = Executors.newSingleThreadExecutor()

        private val WIDTH = object : FloatPropertyCompat<SysInfoView>("width") {
            override fun getValue(view: SysInfoView): Float = view.widthPx
            override fun setValue(view: SysInfoView, value: Float) = view.setWidthPx(value)
        }

        private fun formatCompactSize(bytes: Long): String {
            if (bytes <= 0) return ""
            return if (bytes >= 1024L * 1024 * 1024) formatGb(bytes)
            else "${bytes / (1024 * 1024)}M"
        }

        private fun formatGb(bytes: Long): String {
            if (bytes <= 0) return ""
            val gigabytes = bytes / (1024.0 * 1024.0 * 1024.0)
            return if (gigabytes >= 10.0) String.format(Locale.US, "%.0fG", gigabytes)
            else String.format(Locale.US, "%.1fG", gigabytes)
        }
    }

    private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {
        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val d = drawable
            canvas.save()
            val transY = top + (bottom - top - d.bounds.height()) / 2
            canvas.translate(x, transY.toFloat())
            d.draw(canvas)
            canvas.restore()
        }
    }
}
