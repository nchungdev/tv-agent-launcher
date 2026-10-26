package com.gathin.tvhome.core.design

import android.graphics.Path
import android.graphics.RectF
import com.gathin.tvhome.feature.system.data.Weather

class WeatherShapes {
    @JvmField val sun = Path()
    @JvmField val moon = Path()
    @JvmField val stars = Path()
    @JvmField val clouds = Path()
    @JvmField val rain = Path()
    @JvmField val bolt = Path()
    @JvmField val snow = Path()
    @JvmField val fog = Path()

    private val piece = Path()

    fun build(w: Int, h: Int, weather: Weather.Sky, day: Boolean) {
        for (p in arrayOf(sun, moon, stars, clouds, rain, bolt, snow, fog)) p.rewind()
        if (w <= 0 || h <= 0) return
        val clearish = weather == Weather.Sky.CLEAR || weather == Weather.Sky.PARTLY
        if (clearish && day) addSun(w, h)
        if (clearish && !day) addNight(w, h)
        if (weather == Weather.Sky.FOG) addFog(w, h)
        if (weather == Weather.Sky.PARTLY) addClouds(w, h, ONE_CLOUD, weather)
        if (weather == Weather.Sky.CLOUDY || weather == Weather.Sky.RAIN || weather == Weather.Sky.THUNDER || weather == Weather.Sky.SNOW) {
            addClouds(w, h, CLOUDS, weather)
        }
    }

    private fun addSun(w: Int, h: Int) {
        ExpressiveBackdrop.shapePath(sun, SUN_X * w, SUN_Y * h, SUN_R * h, 12, 0.07f, 0f)
    }

    private fun addNight(w: Int, h: Int) {
        val cx = SUN_X * w
        val cy = SUN_Y * h
        val r = SUN_R * h * 0.75f
        moon.addCircle(cx, cy, r, Path.Direction.CW)
        piece.rewind()
        piece.addCircle(cx + r * 0.45f, cy - r * 0.30f, r * 0.85f, Path.Direction.CW)
        moon.op(piece, Path.Op.DIFFERENCE)
        for (i in STARS.indices) {
            val size = h * (if (i % 3 == 0) 0.016f else 0.010f)
            ExpressiveBackdrop.shapePath(piece, STARS[i][0] * w, STARS[i][1] * h, size, 4, 0.55f, 45f)
            stars.op(piece, Path.Op.UNION)
        }
    }

    private fun addFog(w: Int, h: Int) {
        val band = h * 0.026f
        val bands = arrayOf(
            floatArrayOf(0.50f, 0.08f, 0.30f), floatArrayOf(0.58f, 0.15f, 0.36f), floatArrayOf(0.52f, 0.22f, 0.26f),
            floatArrayOf(0.20f, 0.60f, 0.30f), floatArrayOf(0.62f, 0.63f, 0.34f)
        )
        for (b in bands) {
            val pill = RectF(b[0] * w, b[1] * h - band / 2, (b[0] + b[2]) * w, b[1] * h + band / 2)
            fog.addRoundRect(pill, band / 2, band / 2, Path.Direction.CW)
        }
    }

    private fun addClouds(w: Int, h: Int, places: Array<FloatArray>, weather: Weather.Sky) {
        for (i in places.indices) {
            val cx = places[i][0] * w
            val cy = places[i][1] * h
            val r = places[i][2] * h
            addCloud(cx, cy, r)
            if (weather == Weather.Sky.RAIN || weather == Weather.Sky.THUNDER) addRain(cx, cy, r)
            if (weather == Weather.Sky.SNOW) addSnow(cx, cy, r)
            if (weather == Weather.Sky.THUNDER && i == 0) addBolt(cx, cy, r)
        }
    }

    private fun addCloud(cx: Float, cy: Float, r: Float) {
        val puffs = arrayOf(floatArrayOf(0f, -0.25f, 1f), floatArrayOf(-0.95f, 0.15f, 0.68f), floatArrayOf(0.90f, 0.10f, 0.78f))
        for (puff in puffs) {
            ExpressiveBackdrop.shapePath(piece, cx + puff[0] * r, cy + puff[1] * r, puff[2] * r, 9, 0.05f, 20f)
            clouds.op(piece, Path.Op.UNION)
        }
        piece.rewind()
        piece.addRoundRect(RectF(cx - 1.55f * r, cy, cx + 1.6f * r, cy + 0.72f * r), 0.36f * r, 0.36f * r, Path.Direction.CW)
        clouds.op(piece, Path.Op.UNION)
    }

    private fun addRain(cx: Float, cy: Float, r: Float) {
        for (i in 0 until 5) {
            val x = cx - 1.1f * r + i * 0.55f * r
            val top = cy + 0.95f * r + (i % 2) * 0.25f * r
            val length = (if (i % 2 == 0) 0.55f else 0.40f) * r
            rain.moveTo(x, top)
            rain.lineTo(x - 0.18f * length, top + length)
        }
    }

    private fun addSnow(cx: Float, cy: Float, r: Float) {
        for (i in 0 until 4) {
            val x = cx - 0.9f * r + i * 0.6f * r
            val y = cy + 1.05f * r + (i % 2) * 0.35f * r
            ExpressiveBackdrop.shapePath(piece, x, y, 0.13f * r, 6, 0.35f, 0f)
            snow.op(piece, Path.Op.UNION)
        }
    }

    private fun addBolt(cx: Float, cy: Float, r: Float) {
        val points = arrayOf(
            floatArrayOf(0.10f, 0.70f), floatArrayOf(-0.30f, 1.30f), floatArrayOf(0.00f, 1.30f),
            floatArrayOf(-0.18f, 1.85f), floatArrayOf(0.38f, 1.12f), floatArrayOf(0.08f, 1.12f), floatArrayOf(0.30f, 0.70f)
        )
        bolt.moveTo(cx + points[0][0] * r, cy + points[0][1] * r)
        for (i in 1 until points.size) bolt.lineTo(cx + points[i][0] * r, cy + points[i][1] * r)
        bolt.close()
    }

    companion object {
        private val ONE_CLOUD = arrayOf(floatArrayOf(0.70f, 0.15f, 0.075f))
        private val CLOUDS = arrayOf(
            floatArrayOf(0.58f, 0.12f, 0.07f), floatArrayOf(0.80f, 0.19f, 0.06f),
            floatArrayOf(0.28f, 0.61f, 0.05f), floatArrayOf(0.90f, 0.62f, 0.045f)
        )
        private val STARS = arrayOf(
            floatArrayOf(0.52f, 0.06f), floatArrayOf(0.60f, 0.21f), floatArrayOf(0.74f, 0.05f),
            floatArrayOf(0.86f, 0.24f), floatArrayOf(0.95f, 0.08f), floatArrayOf(0.18f, 0.60f),
            floatArrayOf(0.47f, 0.63f), floatArrayOf(0.72f, 0.58f), floatArrayOf(0.05f, 0.95f), floatArrayOf(0.62f, 0.96f)
        )
        private const val SUN_X = 0.54f
        private const val SUN_Y = 0.11f
        private const val SUN_R = 0.08f
    }
}
