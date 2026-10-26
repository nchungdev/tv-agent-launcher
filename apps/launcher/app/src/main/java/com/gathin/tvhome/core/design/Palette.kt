package com.gathin.tvhome.core.design

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.Score

@SuppressLint("RestrictedApi")
class Palette private constructor(
    @JvmField val seed: Int,
    @JvmField val dark: Boolean
) {
    @JvmField val primary: Int
    @JvmField val onPrimary: Int
    @JvmField val primaryContainer: Int
    @JvmField val onPrimaryContainer: Int
    @JvmField val secondary: Int
    @JvmField val secondaryContainer: Int
    @JvmField val onSecondaryContainer: Int
    @JvmField val tertiary: Int
    @JvmField val tertiaryContainer: Int
    @JvmField val surface: Int
    @JvmField val surfaceContainer: Int
    @JvmField val surfaceContainerHigh: Int
    @JvmField val surfaceContainerHighest: Int
    @JvmField val onSurface: Int
    @JvmField val onSurfaceVariant: Int
    @JvmField val outlineVariant: Int
    @JvmField val surfaceVariant: Int
    @JvmField val inverseSurface: Int
    @JvmField val inverseOnSurface: Int
    @JvmField val inversePrimary: Int
    @JvmField val outline: Int
    @JvmField val error: Int

    init {
        val s = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
        val c = MaterialDynamicColors()
        primary = c.primary().getArgb(s)
        onPrimary = c.onPrimary().getArgb(s)
        primaryContainer = c.primaryContainer().getArgb(s)
        onPrimaryContainer = c.onPrimaryContainer().getArgb(s)
        secondary = c.secondary().getArgb(s)
        secondaryContainer = c.secondaryContainer().getArgb(s)
        onSecondaryContainer = c.onSecondaryContainer().getArgb(s)
        tertiary = c.tertiary().getArgb(s)
        tertiaryContainer = c.tertiaryContainer().getArgb(s)
        surface = c.surface().getArgb(s)
        surfaceContainer = c.surfaceContainer().getArgb(s)
        surfaceContainerHigh = c.surfaceContainerHigh().getArgb(s)
        surfaceContainerHighest = c.surfaceContainerHighest().getArgb(s)
        onSurface = c.onSurface().getArgb(s)
        onSurfaceVariant = c.onSurfaceVariant().getArgb(s)
        surfaceVariant = c.surfaceVariant().getArgb(s)
        inverseSurface = c.inverseSurface().getArgb(s)
        inverseOnSurface = c.inverseOnSurface().getArgb(s)
        inversePrimary = c.inversePrimary().getArgb(s)
        outline = c.outline().getArgb(s)
        outlineVariant = c.outlineVariant().getArgb(s)
        error = c.error().getArgb(s)
    }

    companion object {
        const val BASELINE_SEED = -0x98af5c
        private const val SAMPLE_EDGE = 128

        @Volatile
        private var currentPalette: Palette? = null

        @JvmStatic
        private var darkMode = true

        private val listeners = ArrayList<Runnable>()

        @JvmStatic
        fun fromSeed(seed: Int): Palette = Palette(seed, darkMode)

        @JvmStatic
        fun fromBackground(color: Int): Palette {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            return Palette(if (hsv[1] > 0.15f && hsv[2] > 0.1f) color or -0x1000000 else BASELINE_SEED, darkMode)
        }

        @JvmStatic
        fun fromWallpaper(wallpaper: Bitmap): Palette {
            val w = wallpaper.width
            val h = wallpaper.height
            val scale = minOf(1f, SAMPLE_EDGE / maxOf(w, h).toFloat())
            val sw = maxOf(1, Math.round(w * scale))
            val sh = maxOf(1, Math.round(h * scale))
            val small = Bitmap.createScaledBitmap(wallpaper, sw, sh, true)
            val pixels = IntArray(sw * sh)
            small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
            if (small != wallpaper) small.recycle()
            val quantized = QuantizerCelebi.quantize(pixels, 128)
            val ranked = Score.score(quantized, 1, BASELINE_SEED)
            return Palette(if (ranked.isEmpty()) BASELINE_SEED else ranked[0], darkMode)
        }

        @JvmStatic
        fun current(): Palette? = currentPalette

        @JvmStatic
        fun currentOrBaseline(): Palette = currentPalette ?: fromSeed(BASELINE_SEED)

        @JvmStatic
        fun darkMode(): Boolean = darkMode

        @JvmStatic
        fun setDarkMode(dark: Boolean) {
            darkMode = dark
            val curr = currentPalette
            if (curr != null && curr.dark != dark) publish(Palette(curr.seed, dark))
        }

        @JvmStatic
        fun loadDarkMode(context: Context) {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            setDarkMode(night != Configuration.UI_MODE_NIGHT_NO)
        }

        @JvmStatic
        fun dark(p: Palette): Palette = if (p.dark) p else Palette(p.seed, true)

        @JvmStatic
        fun publish(palette: Palette?) {
            if (Looper.myLooper() != Looper.getMainLooper()) throw IllegalStateException("main thread only")
            if (palette == null) return
            val curr = currentPalette
            if (curr != null && curr.seed == palette.seed && curr.dark == palette.dark) return
            currentPalette = palette
            for (listener in ArrayList(listeners)) listener.run()
        }

        @JvmStatic
        fun addListener(listener: Runnable) {
            listeners.add(listener)
        }

        @JvmStatic
        fun removeListener(listener: Runnable) {
            listeners.remove(listener)
        }

        @JvmStatic
        fun alpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0xFFFFFF)
    }
}
