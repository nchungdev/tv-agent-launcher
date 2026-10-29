package com.gathin.tvhome.feature.watching.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.watching.data.WatchDetails
import com.gathin.tvhome.feature.watching.data.WatchItem

object WatchFormat {
    private const val MINUTE_MS = 60_000L
    private const val MINUTES_PER_HOUR = 60L
    private const val SEPARATOR = " · "
    private const val DOT = "●"
    private const val MAX_DOTS = 8
    private const val DIM_DOT = 0x59FFFFFF
    private val CO_AUTHORS = Regex("""\s+(và \d+ người khác|and \d+ (more|others?))\s*$""",
        RegexOption.IGNORE_CASE)

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

    fun subtitle(context: Context, item: WatchItem): String {
        val details = item.details
        val parts = when (details.kind) {
            WatchDetails.VIDEO -> listOf(channel(details.channel.ifBlank { item.subtitle }))
            WatchDetails.MOVIE -> listOf(
                context.getString(R.string.watch_kind_movie), yearText(details.year)
            )
            WatchDetails.SERIES -> listOf(
                context.getString(R.string.watch_kind_series),
                yearText(details.year),
                countText(context, R.string.watch_season, details.season),
                countText(context, R.string.watch_episode, details.episode)
            )
            else -> listOf(channel(item.subtitle))
        }
        return parts.filter { it.isNotBlank() }.joinToString(SEPARATOR)
    }

    fun pageDots(index: Int, count: Int): CharSequence {
        if (count < 2) return ""
        val shown = minOf(count, MAX_DOTS)
        val current = index.coerceIn(0, shown - 1)
        val dots = SpannableStringBuilder()
        for (dot in 0 until shown) {
            val start = dots.length
            dots.append(DOT)
            if (dot != current) {
                dots.setSpan(ForegroundColorSpan(DIM_DOT), start, dots.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (dot < shown - 1) dots.append(' ')
        }
        return dots
    }

    fun channel(text: String): String = text.replace(CO_AUTHORS, "").trim()

    private fun yearText(year: Int): String = if (year > 0) year.toString() else ""

    private fun countText(context: Context, format: Int, count: Int): String =
        if (count > 0) context.getString(format, count) else ""

    fun appIcon(context: Context, packageName: String): Drawable? {
        if (packageName.isBlank()) return null
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
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
