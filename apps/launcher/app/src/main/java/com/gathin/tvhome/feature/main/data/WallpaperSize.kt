package com.gathin.tvhome.feature.main.data



object WallpaperSize {

    const val MAX_PIXELS = 8_400_000L

    @JvmStatic
    fun accepted(width: Int, height: Int): Boolean {
        return width > 0 && height > 0 && width.toLong() * height.toLong() <= MAX_PIXELS
    }

    @JvmStatic
    fun sampleSize(width: Int, height: Int, screenW: Int, screenH: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= screenW && height / (sample * 2) >= screenH) sample *= 2
        return sample
    }

    @JvmStatic
    fun coverScale(width: Int, height: Int, screenW: Int, screenH: Int): IntArray? {
        if (screenW.toLong() * height >= screenH.toLong() * width) {
            return if (width > screenW) intArrayOf(width, screenW) else null
        }
        return if (height > screenH) intArrayOf(height, screenH) else null
    }
}
