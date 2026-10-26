package com.gathin.tvhome.feature.system.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import java.util.function.ToIntFunction


object RemoteKeys {

    @JvmStatic
    fun parseAppKeys(entries: Array<String>): Map<Int, String> {
        return parseAppKeys(entries.toList()) { key -> KeyEvent.keyCodeFromString(key) }
    }

    @JvmStatic
    fun parseAppKeys(entries: Iterable<String>): Map<Int, String> {
        return parseAppKeys(entries) { key -> KeyEvent.keyCodeFromString(key) }
    }

    @JvmStatic
    fun parseAppKeys(entries: Iterable<String>, names: ToIntFunction<String>): Map<Int, String> {
        val keys = HashMap<Int, String>()
        for (entry in entries) {
            val parts = entry.split(":", limit = 2)
            if (parts.size != 2 || parts[1].trim().isEmpty()) continue
            val key = parts[0].trim()
            val code: Int = when {
                key.matches(Regex("[0-9]{1,6}")) -> key.toInt()
                key.matches(Regex("KEYCODE_[A-Z0-9_]+")) -> names.applyAsInt(key)
                else -> continue
            }
            if (code > 0) keys[code] = parts[1].trim()
        }
        return keys
    }

    @JvmStatic
    fun launch(ctx: Context, pkg: String) {
        var open = ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg)
        if (open == null) open = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (open == null) return
        try {
            ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
