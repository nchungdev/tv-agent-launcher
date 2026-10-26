package com.gathin.tvhome.feature.main.data

import com.gathin.tvhome.feature.system.data.Weather


object Sky {

    class Stop(
        @JvmField val minute: Int,
        @JvmField val top: Int,
        @JvmField val mid: Int,
        @JvmField val bottom: Int,
        @JvmField val sun: Int,
        @JvmField val sunX: Float
    )

    class Palette(
        @JvmField val top: Int,
        @JvmField val mid: Int,
        @JvmField val bottom: Int,
        @JvmField val sun: Int,
        @JvmField val sunX: Float
    )

    private const val NIGHT_TOP = -0xfbf9f2
    private const val NIGHT_MID = -0xf8f3e6
    private const val NIGHT_BOTTOM = -0xf4ecda

    private val STOPS = arrayOf(
        Stop(0, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
        Stop(320, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
        Stop(390, -0xe9f0d2, -0xc4dcae, -0x75b595, 100, 0.24f),
        Stop(450, -0xeddfc1, -0xa3c59d, -0x2c98b1, 160, 0.30f),
        Stop(570, -0xf1e5cd, -0xe0caa9, -0xbc9b75, 90, 0.38f),
        Stop(780, -0xf4eaad, -0xe8cfb1, -0xcea781, 60, 0.50f),
        Stop(1020, -0xf2eada, -0xdfc6a4, -0xb3ab90, 90, 0.62f),
        Stop(1140, -0xe4efd0, -0x93d1ae, -0x1d8fbed, 190, 0.76f),
        Stop(1185, -0xebf3d9, -0xb5dfb7, -0x5ab5a4, 130, 0.82f),
        Stop(1260, -0xf5f5e4, -0xe6edd0, -0xc5e0b7, 60, 0.86f),
        Stop(1350, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
        Stop(1440, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f)
    )

    @JvmStatic
    fun at(minuteOfDay: Int): Palette {
        val m = minuteOfDay.coerceIn(0, 1440)
        var a = STOPS[0]
        var b = STOPS[STOPS.size - 1]
        for (i in 0 until STOPS.size - 1) {
            if (m in STOPS[i].minute..STOPS[i + 1].minute) {
                a = STOPS[i]
                b = STOPS[i + 1]
                break
            }
        }
        val t = if (b.minute == a.minute) 0f else (m - a.minute).toFloat() / (b.minute - a.minute)
        return Palette(
            lerpColor(a.top, b.top, t),
            lerpColor(a.mid, b.mid, t),
            lerpColor(a.bottom, b.bottom, t),
            lerp(a.sun, b.sun, t),
            lerp(a.sunX, b.sunX, t)
        )
    }

    @JvmStatic
    fun weathered(clear: Palette, weather: Weather.Sky): Palette {
        return when (weather) {
            Weather.Sky.PARTLY -> covered(clear, 0.20f, 1.00f, 0.70f)
            Weather.Sky.CLOUDY -> covered(clear, 0.60f, 1.05f, 0.25f)
            Weather.Sky.FOG -> covered(clear, 0.75f, 1.15f, 0f)
            Weather.Sky.RAIN -> covered(clear, 0.55f, 0.80f, 0f)
            Weather.Sky.THUNDER -> covered(clear, 0.45f, 0.65f, 0f)
            Weather.Sky.SNOW -> covered(clear, 0.70f, 1.25f, 0f)
            else -> clear
        }
    }

    private fun covered(clear: Palette, grey: Float, brightness: Float, sun: Float): Palette {
        return Palette(
            cover(clear.top, grey, brightness),
            cover(clear.mid, grey, brightness),
            cover(clear.bottom, grey, brightness),
            (clear.sun * sun).toInt(),
            clear.sunX
        )
    }

    private fun cover(color: Int, grey: Float, brightness: Float): Int {
        val l = luminance(color)
        val greyed = lerpColor(color, -0x1000000 or (l shl 16) or (l shl 8) or l, grey)
        val r = (((greyed shr 16) and 0xFF) * brightness).toInt().coerceIn(0, 255)
        val g = (((greyed shr 8) and 0xFF) * brightness).toInt().coerceIn(0, 255)
        val b = ((greyed and 0xFF) * brightness).toInt().coerceIn(0, 255)
        return -0x1000000 or (r shl 16) or (g shl 8) or b
    }

    @JvmStatic
    fun luminance(color: Int): Int {
        return (((color shr 16) and 0xFF) * 299 + ((color shr 8) and 0xFF) * 587 + (color and 0xFF) * 114) / 1000
    }

    @JvmStatic
    fun chroma(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return maxOf(r, g, b) - minOf(r, g, b)
    }

    @JvmStatic
    fun minuteOfDay(hour: Int, minute: Int): Int {
        return hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)
    }

    @JvmStatic
    fun lerpColor(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val a = (((from ushr 24) and 0xFF) + (((to ushr 24) and 0xFF) - ((from ushr 24) and 0xFF)) * f).toInt()
        val r = (((from shr 16) and 0xFF) + (((to shr 16) and 0xFF) - ((from shr 16) and 0xFF)) * f).toInt()
        val g = (((from shr 8) and 0xFF) + (((to shr 8) and 0xFF) - ((from shr 8) and 0xFF)) * f).toInt()
        val b = ((from and 0xFF) + ((to and 0xFF) - (from and 0xFF)) * f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerp(from: Int, to: Int, t: Float): Int {
        return (from + (to - from) * t.coerceIn(0f, 1f)).toInt()
    }

    private fun lerp(from: Float, to: Float, t: Float): Float {
        return from + (to - from) * t.coerceIn(0f, 1f)
    }
}
