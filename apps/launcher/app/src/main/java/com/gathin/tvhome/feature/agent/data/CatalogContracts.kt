package com.gathin.tvhome.feature.agent.data

import com.gathin.tvhome.core.view.CardAction
import org.json.JSONObject

data class CatalogTitle(
    val id: String = "",
    val sourceId: String = "",
    val name: String = "",
    val kind: String = "",
    val year: Int = 0,
    val overview: String = "",
    val poster: String = "",
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

data class TmdbMediaDetailResponse(
    val title: CatalogTitle = CatalogTitle(),
    val sources: List<MediaSourceGroup> = emptyList()
)

data class MediaSourceGroup(
    val sourceId: String = "",
    val status: String = "",
    val items: List<MediaSourceItem> = emptyList()
)

data class MediaSourceItem(
    val id: String = "",
    val title: String = "",
    val kind: String = "",
    val year: Int = 0,
    val summary: String = "",
    val poster: String = "",
    val availability: String = "",
    val actions: List<CardAction> = emptyList()
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
            ?: json.optJSONArray("items")
            ?: json.optJSONArray("sources")
        val episodes = ArrayList<CatalogEpisode>()
        if (episodesArray != null) {
            for (i in 0 until episodesArray.length()) {
                episodesArray.optJSONObject(i)?.let { e ->
                    episodes.add(parseEpisode(e))
                }
            }
        }

        return CatalogTitle(
            id = json.optString("id", json.optString("title_id", "")),
            sourceId = json.optString("source_id", json.optString("source", "")),
            name = json.optString("name", json.optString("title", "")),
            kind = json.optString("kind", json.optString("type", "")),
            year = json.optInt("year", 0),
            overview = json.optString("overview", json.optString("summary", "")),
            poster = json.optString("poster", json.optString("poster_url", "")),
            seasons = seasons,
            episodes = episodes
        )
    }

    fun parseEpisode(json: JSONObject): CatalogEpisode {
        val rawId = json.optString("id", json.optString("episode_id", json.optString("url", "")))
        val rawLabel = json.optString("label", json.optString("title", json.optString("name", "")))
            .ifBlank { "Full" }
        val rawGroup = json.optString("group", json.optString("source_id", json.optString("source", "")))
        return CatalogEpisode(
            id = rawId,
            titleId = json.optString("title_id", ""),
            label = rawLabel,
            group = rawGroup,
            order = json.optInt("order", 0),
            seasonId = json.optString("season_id", ""),
            playable = json.optBoolean("playable", true)
        )
    }

    fun parseTmdbMediaDetail(json: JSONObject): TmdbMediaDetailResponse {
        val titleObj = json.optJSONObject("title")
        val title = if (titleObj != null) parseTitle(titleObj) else CatalogTitle()

        val sourcesArray = json.optJSONArray("sources")
        val sources = ArrayList<MediaSourceGroup>()
        if (sourcesArray != null) {
            for (i in 0 until sourcesArray.length()) {
                sourcesArray.optJSONObject(i)?.let { s ->
                    val sourceId = s.optString("source_id", "")
                    val status = s.optString("status", "")
                    val itemsArray = s.optJSONArray("items")
                    val items = ArrayList<MediaSourceItem>()
                    if (itemsArray != null) {
                        for (j in 0 until itemsArray.length()) {
                            itemsArray.optJSONObject(j)?.let { itemObj ->
                                items.add(parseMediaSourceItem(sourceId, itemObj))
                            }
                        }
                    }
                    sources.add(MediaSourceGroup(sourceId, status, items))
                }
            }
        }
        return TmdbMediaDetailResponse(title, sources)
    }

    fun parseMediaSourceItem(sourceId: String, json: JSONObject): MediaSourceItem {
        val actionsArray = json.optJSONArray("actions")
        val actions = ArrayList<CardAction>()
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                actionsArray.optJSONObject(i)?.let { a ->
                    val type = a.optString("type", "")
                    val label = a.optString("label", "")
                    val url = a.optString("url", "")
                    val itemSource = a.optString("source_id", sourceId)
                    val itemId = a.optString("item_id", json.optString("id", ""))
                    val params = mutableMapOf<String, Any?>(
                        "source_id" to itemSource,
                        "title_id" to itemId,
                        "episode_id" to itemId,
                        "url" to url
                    )
                    actions.add(CardAction(type, label, CardAction.KIND_CLIENT, type, params))
                }
            }
        }
        return MediaSourceItem(
            id = json.optString("id", ""),
            title = json.optString("title", ""),
            kind = json.optString("kind", ""),
            year = json.optInt("year", 0),
            summary = json.optString("summary", ""),
            poster = json.optString("poster", ""),
            availability = json.optString("availability", ""),
            actions = actions
        )
    }

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
