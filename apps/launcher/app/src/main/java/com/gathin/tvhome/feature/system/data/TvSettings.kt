package com.gathin.tvhome.feature.system.data

import android.content.Context
import android.content.Intent
import android.provider.Settings


object TvSettings {

    private val VENDOR_ACTIONS = arrayOf(
        "com.xiaomi.mitv.settings.SETTINGS"
    )

    @JvmStatic
    fun main(ctx: Context): Intent {
        for (action in VENDOR_ACTIONS) {
            val i = Intent(action)
            if (i.resolveActivity(ctx.packageManager) != null) return i
        }
        return Intent(Settings.ACTION_SETTINGS)
    }

    @JvmStatic
    fun picture(ctx: Context): Intent {
        val i = Intent(Settings.ACTION_DISPLAY_SETTINGS)
        return if (i.resolveActivity(ctx.packageManager) != null) i else main(ctx)
    }
}
