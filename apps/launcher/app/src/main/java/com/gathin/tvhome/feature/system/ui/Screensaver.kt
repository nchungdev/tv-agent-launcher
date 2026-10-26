package com.gathin.tvhome.feature.system.ui
import com.gathin.tvhome.feature.system.data.*
import com.gathin.tvhome.feature.system.coordinator.*
import com.gathin.tvhome.feature.system.widget.*

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import com.gathin.tvhome.Contract
import com.gathin.tvhome.feature.system.ui.ScreensaverPreviewActivity


object Screensaver {

    const val STYLE_KEY = "dream_style"
    const val SHAPES = "shapes"
    const val GLOW = "glow"
    const val MINIMAL = "minimal"
    const val LARGE = "large"

    @JvmField
    val STYLES = arrayOf(SHAPES, GLOW, MINIMAL, LARGE)

    @JvmField
    val MINUTES = intArrayOf(0, 5, 10, 15, 30)

    private const val NEVER_MS = Int.MAX_VALUE
    private const val MINUTE_MS = 60_000

    @JvmStatic
    fun style(choices: SharedPreferences): String {
        val chosen = choices.getString(STYLE_KEY, SHAPES)
        for (known in STYLES) if (known == chosen) return known
        return SHAPES
    }

    @JvmStatic
    fun minutes(ctx: Context): Int {
        val cr = ctx.contentResolver
        val ms = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, NEVER_MS)
        if (ms >= NEVER_MS / 2) return 0
        return Math.max(1, Math.round(ms / MINUTE_MS.toFloat()))
    }

    @JvmStatic
    fun setMinutes(ctx: Context, minutes: Int): Boolean {
        return try {
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                if (minutes == 0) NEVER_MS else minutes * MINUTE_MS
            )
        } catch (_: SecurityException) {
            false
        }
    }

    @JvmStatic
    fun isEnabled(ctx: Context): Boolean {
        return try {
            val cr = ctx.contentResolver
            val enabled = Settings.Secure.getInt(cr, "screensaver_enabled", 1) == 1
            val current = Settings.Secure.getString(cr, "screensaver_components") ?: ""
            enabled && current.contains(Contract.CLOCK_DREAM)
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun setEnabled(ctx: Context, enable: Boolean): Boolean {
        return try {
            val cr = ctx.contentResolver
            if (enable) {
                val service = Contract.LAUNCHER_PACKAGE + "/" + Contract.CLOCK_DREAM
                Settings.Secure.putInt(cr, "screensaver_enabled", 1)
                Settings.Secure.putString(cr, "screensaver_components", service)
                Settings.Secure.putString(cr, "screensaver_default_component", service)
            } else {
                Settings.Secure.putInt(cr, "screensaver_enabled", 0)
            }
            true
        } catch (_: SecurityException) {
            false
        }
    }

    @JvmStatic
    fun preview(ctx: Context) {
        val intent = Intent(ctx, ScreensaverPreviewActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
