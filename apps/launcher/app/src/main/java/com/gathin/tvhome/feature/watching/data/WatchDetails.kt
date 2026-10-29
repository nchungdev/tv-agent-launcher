package com.gathin.tvhome.feature.watching.data

import org.json.JSONObject

data class WatchDetails(
    val kind: String = "",
    val year: Int = 0,
    val season: Int = 0,
    val episode: Int = 0,
    val channel: String = ""
) {
    fun putInto(json: JSONObject): JSONObject = json
        .put("kind", kind)
        .put("year", year)
        .put("season", season)
        .put("episode", episode)
        .put("channel", channel)

    companion object {
        const val MOVIE = "movie"
        const val SERIES = "series"
        const val VIDEO = "video"

        private val SEASON_EPISODE = Regex("""\bS(\d{1,2})\s*[·:.\- ]?\s*E(\d{1,4})\b""",
            RegexOption.IGNORE_CASE)
        private val SEASON = Regex("""\b(?:season|mùa|phần)\s*(\d{1,2})\b""",
            RegexOption.IGNORE_CASE)
        private val EPISODE = Regex("""\b(?:episode|ep|tập)\s*(\d{1,4})\b""",
            RegexOption.IGNORE_CASE)

        fun fromJson(json: JSONObject): WatchDetails = WatchDetails(
            kind = json.optString("kind"),
            year = json.optInt("year"),
            season = json.optInt("season"),
            episode = json.optInt("episode"),
            channel = json.optString("channel")
        )

        fun guess(text: String, year: Int): WatchDetails {
            val pair = SEASON_EPISODE.find(text)
            val season = pair?.groupValues?.get(1)?.toIntOrNull()
                ?: SEASON.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val episode = pair?.groupValues?.get(2)?.toIntOrNull()
                ?: EPISODE.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val kind = when {
                season > 0 || episode > 0 -> SERIES
                year > 0 -> MOVIE
                else -> ""
            }
            return WatchDetails(kind, year, season, episode)
        }
    }
}
