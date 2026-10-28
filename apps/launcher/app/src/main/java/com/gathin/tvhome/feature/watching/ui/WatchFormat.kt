package com.gathin.tvhome.feature.watching.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.gathin.tvhome.R

object WatchFormat {
    private const val MINUTE_MS = 60_000L
    private const val MINUTES_PER_HOUR = 60L

    fun remaining(context: Context, remainingMs: Long): String? {
        if (remainingMs < MINUTE_MS) return null
        val minutes = remainingMs / MINUTE_MS
        if (minutes < MINUTES_PER_HOUR) {
            return context.getString(R.string.continue_left_minutes, minutes)
        }
        return context.getString(
            R.string.continue_left_hours,
            minutes / MINUTES_PER_HOUR,
            minutes % MINUTES_PER_HOUR
        )
    }

    fun appArt(context: Context, packageName: String): Drawable? {
        if (packageName.isBlank()) return null
        val packages = context.packageManager
        return try {
            packages.getApplicationBanner(packageName) ?: packages.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
