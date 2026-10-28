package com.gathin.tvhome.core.design

import android.graphics.Path
import android.graphics.RectF
import com.gathin.tvhome.feature.main.data.SkyPlacement
import com.gathin.tvhome.feature.system.data.Weather

class WeatherShapes {
    val sun = Path()
    val halo = Path()
    val moon = Path()
    val stars = Path()
    val clouds = Path()
    val cloudLight = Path()
    val rain = Path()
    val bolt = Path()
    val snow = Path()
    val fog = Path()
    private val paths = arrayOf(sun, halo, moon, stars, clouds, cloudLight, rain, bolt, snow, fog)
    private val oval = RectF()

    fun build(layout: SkyPlacement.Layout, weather: Weather.Sky, day: Boolean) {
        for (path in paths) path.rewind()
        if (weather == Weather.Sky.UNKNOWN) return
        val body = layout.celestial
        if (body != null && weather != Weather.Sky.FOG) {
            val radius = body.width * 0.36f
            if (day) {
                sun.addCircle(body.cx, body.cy, radius, Path.Direction.CW)
                halo.addCircle(body.cx, body.cy, body.width * 0.5f, Path.Direction.CW)
            } else {
                val x = body.cx
                val y = body.cy
                moon.moveTo(x + radius * 0.55f, y - radius * 0.83f)
                moon.cubicTo(x - radius * 1.5f, y - radius, x - radius * 1.5f,
                    y + radius * 1.3f, x + radius * 0.55f, y + radius * 0.83f)
                moon.cubicTo(x - radius * 0.2f, y + radius * 0.4f,
                    x - radius * 0.2f, y - radius * 0.4f,
                    x + radius * 0.55f, y - radius * 0.83f)
                moon.close()
                stars.addCircle(body.left + radius * 0.2f, body.top + radius * 0.2f,
                    radius * 0.04f, Path.Direction.CW)
                stars.addCircle(body.right - radius * 0.1f, body.cy,
                    radius * 0.06f, Path.Direction.CW)
            }
        }
        for ((index, box) in layout.clouds.withIndex()) {
            val wet = weather == Weather.Sky.RAIN || weather == Weather.Sky.THUNDER
            val snowy = weather == Weather.Sky.SNOW
            val cloudBottom = box.bottom - if (wet || snowy) box.height * 0.32f else 0f
            cloud(clouds, box.left, box.top, box.right, cloudBottom)
            cloud(cloudLight, box.left + box.width * 0.04f, box.top + box.height * 0.05f,
                box.right - box.width * 0.05f, cloudBottom - box.height * 0.16f)
            if (wet) rain(box, cloudBottom)
            if (snowy) snow(box, cloudBottom)
            if (weather == Weather.Sky.THUNDER && index == 0) {
                val x = box.cx
                val y = cloudBottom - box.height * 0.1f
                val step = box.height * 0.2f
                bolt.moveTo(x, y)
                bolt.lineTo(x - step, y + step)
                bolt.lineTo(x, y + step)
                bolt.lineTo(x - step * 0.4f, y + step * 2)
                bolt.lineTo(x + step, y + step * 0.7f)
                bolt.lineTo(x + step * 0.2f, y + step * 0.7f)
                bolt.close()
            }
            if (weather == Weather.Sky.FOG) {
                for (band in 0..2) {
                    val top = box.top + band * box.height * 0.3f
                    oval.set(box.left + band * box.width * 0.05f, top,
                        box.right - band * box.width * 0.08f, top + box.height * 0.12f)
                    fog.addRoundRect(oval, box.height * 0.06f, box.height * 0.06f,
                        Path.Direction.CW)
                }
            }
        }
    }

    private fun cloud(path: Path, left: Float, top: Float, right: Float, bottom: Float) {
        val w = right - left
        val h = bottom - top
        path.moveTo(left + w * 0.18f, bottom)
        path.cubicTo(left - w * 0.08f, bottom, left - w * 0.05f,
            top + h * 0.4f, left + w * 0.23f, top + h * 0.43f)
        path.cubicTo(left + w * 0.19f, top - h * 0.17f, left + w * 0.64f,
            top - h * 0.17f, left + w * 0.67f, top + h * 0.31f)
        path.cubicTo(right - w * 0.1f, top + h * 0.09f, right + w * 0.07f,
            bottom - h * 0.1f, right - w * 0.16f, bottom)
        path.cubicTo(left + w * 0.65f, bottom + h * 0.03f,
            left + w * 0.4f, bottom - h * 0.02f, left + w * 0.18f, bottom)
        path.close()
    }

    private fun rain(box: SkyPlacement.Box, top: Float) {
        for (index in 0..4) {
            val x = box.left + box.width * (0.2f + index * 0.14f)
            val y = top + box.height * 0.08f
            rain.moveTo(x, y)
            rain.lineTo(x - box.width * 0.025f, y + box.height * 0.2f)
        }
    }

    private fun snow(box: SkyPlacement.Box, top: Float) {
        for (index in 0..3) {
            val x = box.left + box.width * (0.2f + index * 0.18f)
            val y = top + box.height * 0.17f
            snow.addCircle(x, y, box.height * 0.025f, Path.Direction.CW)
        }
    }
}
