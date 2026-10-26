package com.gathin.tvhome.feature.main.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import com.gathin.tvhome.feature.system.data.Weather
import com.gathin.tvhome.feature.main.data.Sky
import com.gathin.tvhome.core.design.WeatherShapes
import java.util.Calendar


open class SkyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    override fun hasOverlappingRendering(): Boolean = false

    private var intensity = 0
    private var lastMinute = -1
    private var texture: Bitmap? = null
    private var drawable: BitmapDrawable? = null

    private var weather = Weather.Sky.CLEAR
    private var day = true
    private val shapes = WeatherShapes()

    private val sunPaint = fill(0xD9FFD27A.toInt())
    private val moonPaint = fill(0xE6EDE9FF.toInt())
    private val starPaint = fill(0x99FFFFFF.toInt())
    private val cloudPaint = fill(0x52FFFFFF)
    private val boltPaint = fill(0xD9FFE27A.toInt())
    private val snowPaint = fill(0xB3FFFFFF.toInt())
    private val fogPaint = fill(0x29FFFFFF)
    private val rainPaint = stroke(0x80C8DCFF.toInt())

    fun setIntensity(percent: Int) {
        intensity = percent.coerceIn(0, 100)
        alpha = intensity / 100f
        visibility = if (intensity == 0) GONE else VISIBLE
        if (intensity > 0) render(true)
    }

    fun update() {
        if (intensity == 0 || visibility != VISIBLE) return
        render(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (intensity > 0) render(true)
    }

    private fun render(force: Boolean) {
        val now = Calendar.getInstance()
        val minute = Sky.minuteOfDay(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        if (!force && minute == lastMinute) return
        lastMinute = minute
        readWeather(minute)

        val sky = Sky.weathered(Sky.at(minute), weather)
        var bmp = texture
        if (bmp == null) {
            bmp = Bitmap.createBitmap(TEX_W, TEX_H, Bitmap.Config.ARGB_8888)
            texture = bmp
        }
        val canvas = Canvas(bmp)
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, TEX_H.toFloat(),
            intArrayOf(sky.top, sky.mid, sky.bottom),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, TEX_W.toFloat(), TEX_H.toFloat(), paint)

        if (sky.sun > 0) {
            val warm = Sky.lerpColor(sky.bottom, 0xFFFFD9A0.toInt(), 0.65f)
            paint.shader = RadialGradient(
                sky.sunX * TEX_W, TEX_H * 0.94f, TEX_W * 0.42f,
                intArrayOf((sky.sun shl 24) or (warm and 0x00FFFFFF), warm and 0x00FFFFFF),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, TEX_W.toFloat(), TEX_H.toFloat(), paint)
        }

        if (drawable == null) {
            drawable = BitmapDrawable(resources, bmp).apply {
                gravity = Gravity.FILL
            }
            background = drawable
        }
        invalidate()
    }

    private fun readWeather(minute: Int) {
        val report = Weather.cached(context)
        val now = System.currentTimeMillis()
        val known = report != null && now - report.fetchedAt < WEATHER_KEPT_MS
        val nowWeather = if (known && report != null) Weather.sky(report.code) else Weather.Sky.CLEAR
        val nowDay = if (known && report != null && !report.stale(now)) report.day else minute in DAY_FROM until DAY_UNTIL
        if (nowWeather == weather && nowDay == day) return
        weather = nowWeather
        day = nowDay
        shapes.build(width, height, weather, day)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shapes.build(w, h, weather, day)
        rainPaint.strokeWidth = h * 0.006f
        boltPaint.pathEffect = CornerPathEffect(h * 0.006f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(shapes.fog, fogPaint)
        canvas.drawPath(shapes.stars, starPaint)
        canvas.drawPath(shapes.sun, sunPaint)
        canvas.drawPath(shapes.moon, moonPaint)
        canvas.drawPath(shapes.rain, rainPaint)
        canvas.drawPath(shapes.snow, snowPaint)
        canvas.drawPath(shapes.clouds, cloudPaint)
        canvas.drawPath(shapes.bolt, boltPaint)
    }

    companion object {
        private const val TEX_W = 480
        private const val TEX_H = 270
        private const val WEATHER_KEPT_MS = 3 * 60 * 60_000L
        private const val DAY_FROM = 360
        private const val DAY_UNTIL = 1110

        private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        private fun stroke(color: Int): Paint = fill(color).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }
}
