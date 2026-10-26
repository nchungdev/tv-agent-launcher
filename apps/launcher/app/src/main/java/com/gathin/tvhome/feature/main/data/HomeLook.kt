package com.gathin.tvhome.feature.main.data

import android.content.SharedPreferences


object HomeLook {

    const val BACKDROP_KEY = "backdrop"
    const val SHAPES = "shapes"
    const val SKY = "sky"
    const val PLAIN = "plain"

    @JvmField
    val BACKDROPS = arrayOf(SHAPES, SKY, PLAIN)

    private const val SKY_INTENSITY_DARK = 70
    private const val SKY_INTENSITY_LIGHT = 30

    @JvmField
    val SEEDS = intArrayOf(
        -0x98af5c, -0xff9e5c, -0xff9596, -0xc795e0, -0x74afff, -0x67bf9f, -0x4cd9e2
    )

    @JvmStatic
    fun backdrop(choices: SharedPreferences): String {
        val chosen = choices.getString(BACKDROP_KEY, SHAPES)
        for (known in BACKDROPS) if (known == chosen) return known
        return SHAPES
    }

    @JvmStatic
    fun skyIntensity(backdrop: String, themeIntensity: Int, dark: Boolean): Int {
        if (SKY == backdrop) {
            if (themeIntensity > 0) return themeIntensity
            return if (dark) SKY_INTENSITY_DARK else SKY_INTENSITY_LIGHT
        }
        return if (PLAIN == backdrop) 0 else themeIntensity
    }
}
