package com.gathin.tvhome.feature.player

import com.gathin.tvhome.feature.agent.data.CatalogEpisode
import com.gathin.tvhome.feature.agent.data.CatalogTitle
import org.json.JSONArray
import org.json.JSONObject

data class PlayRequest(
    val titleId: String,
    val titleName: String,
    val series: Boolean,
    val episodes: List<CatalogEpisode>,
    val episodeId: String
) {
    val current: CatalogEpisode? get() = episodes.firstOrNull { it.id == episodeId }

    val servers: List<String> get() = episodes.map { it.group }.distinct()

    fun episodesOn(server: String): List<CatalogEpisode> =
        episodes.filter { it.group == server && it.playable }.sortedBy { it.order }

    fun next(): CatalogEpisode? {
        val now = current ?: return null
        return episodesOn(now.group).firstOrNull { it.order > now.order }
    }

    fun sameEpisodeOn(server: String): CatalogEpisode? {
        val now = current ?: return null
        val list = episodesOn(server)
        return list.firstOrNull { it.order == now.order && it.label == now.label }
            ?: list.firstOrNull { it.order == now.order }
    }

    fun displayTitle(): String {
        val now = current ?: return titleName
        return if (series) "$titleName · ${now.label}" else titleName
    }

    fun with(episode: CatalogEpisode): PlayRequest = copy(episodeId = episode.id)

    fun toJson(): String {
        val rows = JSONArray()
        for (episode in episodes) {
            rows.put(JSONObject().put("id", episode.id).put("label", episode.label)
                .put("group", episode.group).put("order", episode.order)
                .put("playable", episode.playable))
        }
        return JSONObject().put("title_id", titleId).put("title", titleName)
            .put("series", series).put("episode", episodeId).put("episodes", rows).toString()
    }

    companion object {
        fun of(title: CatalogTitle, episode: CatalogEpisode): PlayRequest = PlayRequest(
            title.id, title.name, isSeries(title), title.episodes, episode.id
        )

        fun isSeries(title: CatalogTitle): Boolean {
            if (title.kind == "series" || title.kind == "show") return true
            if (title.kind == "movie") return false
            return title.episodes.groupBy { it.group }.values.any { it.size > 1 }
        }

        fun fromJson(text: String): PlayRequest? = runCatching {
            val json = JSONObject(text)
            val rows = json.getJSONArray("episodes")
            val episodes = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                CatalogEpisode(id = row.getString("id"), titleId = json.getString("title_id"),
                    label = row.optString("label"), group = row.optString("group"),
                    order = row.optInt("order"), playable = row.optBoolean("playable", true))
            }
            PlayRequest(json.getString("title_id"), json.optString("title"),
                json.optBoolean("series"), episodes, json.getString("episode"))
        }.getOrNull()
    }
}
