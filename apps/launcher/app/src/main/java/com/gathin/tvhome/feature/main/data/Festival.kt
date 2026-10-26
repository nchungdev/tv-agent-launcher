package com.gathin.tvhome.feature.main.data

import com.gathin.tvhome.feature.main.data.Cards
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale


class Festival private constructor(
    @JvmField val name: String,
    @JvmField val until: LocalDate?,
    @JvmField val greeting: String,
    @JvmField val accent: Int
) {

    fun active(today: LocalDate): Boolean {
        return name.isNotEmpty() && (until == null || !today.isAfter(until))
    }

    fun greetingText(): String {
        if (greeting.isNotEmpty()) return greeting
        return when (name) {
            "dussehra" -> "Happy Dussehra"
            "diwali" -> "Happy Diwali"
            else -> ""
        }
    }

    fun accentColor(): Int = if (accent != 0) accent else DEFAULT_ACCENT

    companion object {
        @JvmField
        val NONE = Festival("", null, "", 0)

        const val DEFAULT_ACCENT = -0x4dc4

        @JvmStatic
        fun parse(name: String?, until: String?, greeting: String?, accent: String?): Festival {
            val n = name?.trim()?.lowercase(Locale.ROOT) ?: ""
            if (n.isEmpty() || n.length > 40) return NONE
            var end: LocalDate? = null
            if (!until.isNullOrBlank()) {
                end = try {
                    LocalDate.parse(until.trim())
                } catch (_: DateTimeParseException) {
                    return NONE
                }
            }
            val g = if (greeting == null) "" else Cards.cleanLabel(greeting, "")
            return Festival(n, end, g, parseColor(accent))
        }

        @JvmStatic
        fun parseColor(s: String?): Int {
            if (s == null) return 0
            val t = s.trim()
            if (!t.matches(Regex("#[0-9A-Fa-f]{6}"))) return 0
            return -0x1000000 or t.substring(1).toInt(16)
        }
    }
}
