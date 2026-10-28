package com.gathin.tvhome.feature.agent.data

import org.json.JSONObject

data class CatalogTitle(
    val id: String = "",
    val sourceId: String = "",
    val name: String = "",
    val kind: String = "",
    val year: Int = 0,
    val seasons: List<CatalogSeason> = emptyList(),
    val episodes: List<CatalogEpisode> = emptyList()
)

data class CatalogSeason(
    val id: String = "",
    val label: String = "",
    val order: Int = 0
)

data class CatalogEpisode(
    val id: String = "",
    val titleId: String = "",
    val label: String = "",
    val group: String = "",
    val order: Int = 0,
    val seasonId: String = "",
    val playable: Boolean = true
)

data class PlaybackDescriptor(
    val url: String = "",
    val mimeType: String = "",
    val title: String = "",
    val preferredHandler: String = "",
    val preferredPlayers: List<String> = emptyList(),
    val expiresAt: String = ""
)

data class CatalogPlaybackResponse(
    val episode: CatalogEpisode = CatalogEpisode(),
    val playback: PlaybackDescriptor = PlaybackDescriptor()
)

object CatalogParser {
    fun parseTitleResponse(json: JSONObject): CatalogTitle {
        val titleObj = json.optJSONObject("title") ?: json
        return parseTitle(titleObj)
    }

    fun parseTitle(json: JSONObject): CatalogTitle {
        val seasonsArray = json.optJSONArray("seasons")
        val seasons = ArrayList<CatalogSeason>()
        if (seasonsArray != null) {
            for (i in 0 until seasonsArray.length()) {
                seasonsArray.optJSONObject(i)?.let { s ->
                    seasons.add(CatalogSeason(
                        id = s.optString("id", ""),
                        label = s.optString("label", ""),
                        order = s.optInt("order", 0)
                    ))
                }
            }
        }

        val episodesArray = json.optJSONArray("episodes")
        val episodes = ArrayList<CatalogEpisode>()
        if (episodesArray != null) {
            for (i in 0 until episodesArray.length()) {
                episodesArray.optJSONObject(i)?.let { e ->
                    episodes.add(parseEpisode(e))
                }
            }
        }

        return CatalogTitle(
            id = json.optString("id", ""),
            sourceId = json.optString("source_id", ""),
            name = json.optString("name", ""),
            kind = json.optString("kind", ""),
            year = json.optInt("year", 0),
            seasons = seasons,
            episodes = episodes
        )
    }

    fun parseEpisode(json: JSONObject): CatalogEpisode = CatalogEpisode(
        id = json.optString("id", ""),
        titleId = json.optString("title_id", ""),
        label = json.optString("label", ""),
        group = json.optString("group", ""),
        order = json.optInt("order", 0),
        seasonId = json.optString("season_id", ""),
        playable = json.optBoolean("playable", true)
    )

    fun parsePlaybackResponse(json: JSONObject): CatalogPlaybackResponse {
        val episodeObj = json.optJSONObject("episode")
        val episode = episodeObj?.let { parseEpisode(it) } ?: CatalogEpisode()

        val playbackObj = json.optJSONObject("playback")
        val playback = if (playbackObj != null) {
            val playersArray = playbackObj.optJSONArray("preferred_players")
            val players = ArrayList<String>()
            if (playersArray != null) {
                for (i in 0 until playersArray.length()) {
                    players.add(playersArray.optString(i))
                }
            }
            PlaybackDescriptor(
                url = playbackObj.optString("url", ""),
                mimeType = playbackObj.optString("mime_type", ""),
                title = playbackObj.optString("title", ""),
                preferredHandler = playbackObj.optString("preferred_handler", ""),
                preferredPlayers = players,
                expiresAt = playbackObj.optString("expires_at", "")
            )
        } else PlaybackDescriptor()

        return CatalogPlaybackResponse(episode, playback)
    }
}
