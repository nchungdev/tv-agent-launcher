package com.gathin.tvhome.feature.agent.data

import org.json.JSONArray
import org.json.JSONObject

object TmdbMediaParser {
    fun parse(json: JSONObject): TmdbMediaDetailResponse {
        val media = json.optJSONObject("media") ?: json.optJSONObject("title")
        return TmdbMediaDetailResponse(
            title = media?.let(CatalogParser::parseTitle) ?: CatalogTitle(),
            sources = objects(json.optJSONArray("sources")).map { source ->
                val sourceId = source.optString("source_id", "")
                MediaSourceGroup(
                    sourceId = sourceId,
                    status = source.optString("status", ""),
                    items = objects(source.optJSONArray("items")).map { parseItem(sourceId, it) },
                    playbackMode = source.optInt("playback_mode", -1)
                )
            }
        )
    }

    private fun parseItem(sourceId: String, json: JSONObject): MediaSourceItem {
        val item = CatalogParser.parseMediaSourceItem(sourceId, json)
        return item.copy(
            url = json.optString("url", ""),
            season = json.optInt("season", 0),
            servers = objects(json.optJSONArray("servers")).map { server ->
                MediaServer(server.optString("server_name", ""),
                    objects(server.optJSONArray("episodes")).map { episode ->
                        CatalogParser.parseEpisode(episode).copy(titleId = item.id)
                    })
            }
        )
    }

    fun title(source: MediaSourceGroup, item: MediaSourceItem): CatalogTitle = CatalogTitle(
        id = item.id,
        sourceId = source.sourceId,
        name = item.title,
        kind = item.kind,
        year = item.year,
        overview = item.summary,
        poster = item.poster,
        episodes = if (source.playbackMode in 1..2) item.servers.flatMap { server ->
            server.episodes.map { episode ->
                episode.copy(
                    group = server.name.ifBlank { episode.group },
                    playable = episode.playable && episode.url.isNotBlank() &&
                        episode.id.isNotBlank()
                )
            }
        } else emptyList()
    )

    private fun objects(array: JSONArray?): List<JSONObject> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull {
            array.optJSONObject(it)
        }
}
