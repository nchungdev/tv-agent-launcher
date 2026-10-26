package com.gathin.tvhome.core.util

import android.content.Context
import android.provider.Settings
import com.gathin.tvhome.feature.settings.data.Config

object SoundEffects {

    const val PREF_KEY = "sound_effects_enabled"

    @JvmStatic
    fun isEnabled(ctx: Context): Boolean {
        return try {
            Settings.System.getInt(
                ctx.contentResolver,
                Settings.System.SOUND_EFFECTS_ENABLED,
                1
            ) == 1
        } catch (_: Exception) {
            ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_KEY, true)
        }
    }

    @JvmStatic
    fun setEnabled(ctx: Context, enabled: Boolean): Boolean {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_KEY, enabled).apply()
        return try {
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SOUND_EFFECTS_ENABLED,
                if (enabled) 1 else 0
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
