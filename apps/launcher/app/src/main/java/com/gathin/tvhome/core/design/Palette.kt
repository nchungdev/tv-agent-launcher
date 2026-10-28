package com.gathin.tvhome.core.design

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import com.google.android.material.color.utilities.Hct
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
        val p = s.primaryPalette
        val sec = s.secondaryPalette
        val ter = s.tertiaryPalette
        val n = s.neutralPalette
        val nv = s.neutralVariantPalette
        val err = s.errorPalette

        primary = p.tone(if (dark) 80 else 40)
        onPrimary = p.tone(if (dark) 20 else 100)
        primaryContainer = p.tone(if (dark) 30 else 90)
        onPrimaryContainer = p.tone(if (dark) 90 else 10)
        secondary = sec.tone(if (dark) 80 else 40)
        secondaryContainer = sec.tone(if (dark) 30 else 90)
        onSecondaryContainer = sec.tone(if (dark) 90 else 10)
        tertiary = ter.tone(if (dark) 80 else 40)
        tertiaryContainer = ter.tone(if (dark) 30 else 90)
        surface = n.tone(if (dark) 6 else 98)
        surfaceContainer = n.tone(if (dark) 12 else 94)
        surfaceContainerHigh = n.tone(if (dark) 17 else 92)
        surfaceContainerHighest = n.tone(if (dark) 22 else 90)
        onSurface = n.tone(if (dark) 90 else 10)
        onSurfaceVariant = nv.tone(if (dark) 80 else 30)
        surfaceVariant = nv.tone(if (dark) 30 else 90)
        inverseSurface = n.tone(if (dark) 90 else 20)
        inverseOnSurface = n.tone(if (dark) 20 else 95)
        inversePrimary = p.tone(if (dark) 40 else 80)
        outline = nv.tone(if (dark) 60 else 50)
        outlineVariant = nv.tone(if (dark) 30 else 80)
        error = err.tone(if (dark) 80 else 40)
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
