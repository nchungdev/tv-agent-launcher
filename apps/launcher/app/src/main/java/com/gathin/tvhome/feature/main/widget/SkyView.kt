package com.gathin.tvhome.feature.main.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.gathin.tvhome.core.design.WeatherShapes
import com.gathin.tvhome.feature.main.data.SkyPlacement
import com.gathin.tvhome.feature.main.data.WeatherSceneState
import com.gathin.tvhome.feature.system.data.Weather

open class SkyView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private var intensity = 0
    private var scene = WeatherSceneState.from(null, 0)
    private var area = SkyPlacement.Box(0f, 0f, 0f, 0f)
    private var exclusions: List<SkyPlacement.Box> = emptyList()
    private val shapes = WeatherShapes()
    private val backgroundPaint = fill(0xFF111D31.toInt())
    private val sunPaint = fill(0xFFFFDC98.toInt())
    private val haloPaint = fill(0x20FFD68C)
    private val moonPaint = fill(0xFFF0EDDF.toInt())
    private val starPaint = fill(0x99FFFFFF.toInt())
    private val cloudPaint = fill(0x8891ADC4.toInt())
    private val cloudLight = fill(0x55DDECF4)
    private val boltPaint = fill(0xFFFFDB83.toInt())
    private val snowPaint = fill(0xBBFFFFFF.toInt())
    private val fogPaint = fill(0x307F9AAE)
    private val rainPaint = fill(0x99BCD9ED.toInt()).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var paletteWeather: Weather.Sky? = null
    private var paletteDay = false
    val decorationCount: Int get() =
        (if (shapes.sun.isEmpty && shapes.moon.isEmpty) 0 else 1) +
            (if (shapes.clouds.isEmpty) 0 else 1)
    var geometryBuilds = 0
        private set
    var drawCount = 0
        private set

    override fun hasOverlappingRendering(): Boolean = false

    fun setIntensity(percent: Int) {
        val next = percent.coerceIn(0, 100)
        if (intensity == next) return
        intensity = next
        alpha = intensity / 100f
        visibility = if (intensity == 0) GONE else VISIBLE
        invalidate()
    }

    fun setScene(next: WeatherSceneState) {
        val geometryChanged = scene.sky != next.sky || scene.day != next.day ||
            scene.clouds != next.clouds
        scene = next
        val colorsChanged = updateColors()
        if (geometryChanged) rebuildShapes()
        if (geometryChanged || colorsChanged) invalidate()
    }

    fun setSceneBounds(bounds: SkyPlacement.Box, blocked: List<SkyPlacement.Box>) {
        if (area == bounds && exclusions == blocked) return
        area = bounds
        exclusions = blocked
        rebuildShapes()
        invalidate()
    }

    private fun rebuildShapes() {
        val count = when (scene.sky) {
            Weather.Sky.UNKNOWN -> 0
            Weather.Sky.CLEAR -> scene.clouds
            Weather.Sky.PARTLY -> maxOf(1, scene.clouds)
            else -> 3
        }
        val layout = SkyPlacement.arrange(area, exclusions, count, resources.displayMetrics.density)
        shapes.build(layout, scene.sky, scene.day)
        geometryBuilds++
    }

    private fun updateColors(): Boolean {
        if (paletteWeather == scene.sky && paletteDay == scene.day) return false
        paletteWeather = scene.sky
        paletteDay = scene.day
        val unknown = scene.sky == Weather.Sky.UNKNOWN
        val storm = scene.sky == Weather.Sky.RAIN || scene.sky == Weather.Sky.THUNDER
        val top = when {
            unknown -> 0xFF111D31
            !scene.day -> 0xFF090F2C
            storm -> 0xFF17293C
            else -> 0xFF133A62
        }.toInt()
        val bottom = when {
            unknown -> 0xFF29394B
            !scene.day -> 0xFF293758
            storm -> 0xFF3E5363
            else -> 0xFF66869B
        }.toInt()
        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, height.coerceAtLeast(1).toFloat(), top, bottom, Shader.TileMode.CLAMP
        )
        cloudPaint.color = when {
            !scene.day -> 0x80506483
            storm -> 0xB06C8193
            else -> 0xA0A5BAC8
        }.toInt()
        cloudLight.color = when {
            !scene.day -> 0x307F91AD
            storm -> 0x407E96AB
            else -> 0x70E7F0F5
        }.toInt()
        sunPaint.alpha = if (scene.sky == Weather.Sky.CLOUDY || storm) 100 else 255
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paletteWeather = null
        updateColors()
        if (area.width <= 0 || area.height <= 0) {
            area = SkyPlacement.Box(0f, 0f, w.toFloat(), h.toFloat())
        }
        rainPaint.strokeWidth = (resources.displayMetrics.density * 1.5f).coerceAtLeast(1f)
        rebuildShapes()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (intensity == 0) return
        drawCount++
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.drawPath(shapes.stars, starPaint)
        canvas.drawPath(shapes.halo, haloPaint)
        canvas.drawPath(shapes.sun, sunPaint)
        canvas.drawPath(shapes.moon, moonPaint)
        canvas.drawPath(shapes.clouds, cloudPaint)
        canvas.drawPath(shapes.cloudLight, cloudLight)
        canvas.drawPath(shapes.rain, rainPaint)
        canvas.drawPath(shapes.snow, snowPaint)
        canvas.drawPath(shapes.bolt, boltPaint)
        canvas.drawPath(shapes.fog, fogPaint)
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
}
