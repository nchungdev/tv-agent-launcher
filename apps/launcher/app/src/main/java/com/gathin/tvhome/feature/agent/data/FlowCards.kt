package com.gathin.tvhome.feature.agent.data

import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.core.view.CardAction

object FlowCards {
    fun from(results: List<FlowResult>): List<AgentCardData> = results.flatMap { result ->
        val items = result.items.distinctBy { it.id.ifBlank { it.url.ifBlank { it.title } } }
        if (items.isEmpty()) listOf(AgentCardData(result.status, status(result), "status",
            "", "", id = "status:${result.taskId}", sourceId = result.source,
            automaticActions = false))
        else items.map { item -> card(result.source, item) }
    }.take(160)

    private fun status(result: FlowResult): String = result.errorCode.ifBlank {
        when (result.status) {
            "running" -> "…"
            "empty" -> "∅"
            else -> result.status
        }
    }

    fun card(source: String, item: FlowItem): AgentCardData {
        val actions = item.actions.filter { it.enabled }.mapNotNull { action ->
            val parameters = mapOf<String, Any?>("source_id" to source,
                "title_id" to action.itemId.ifBlank { item.id },
                "episode_id" to action.itemId, "url" to action.url,
                "title" to item.title, "poster" to item.poster)
            val name = when (action.type) {
                "catalog_detail" -> "catalog_detail"
                "catalog_playback" -> "catalog_playback"
                "open_app", "open_url" -> "source.open"
                else -> return@mapNotNull null
            }
            CardAction(action.type, action.label, CardAction.KIND_CLIENT, name,
                parameters + ("source_id" to action.sourceId.ifBlank { source }))
        }.ifEmpty {
            if (item.actions.isEmpty() && item.kind == "web_link" && item.url.isNotBlank()) {
                listOf(CardAction("open", "Open", CardAction.KIND_CLIENT, "web.open",
                    mapOf("url" to item.url)))
            } else emptyList()
        }
        val status = if (item.kind == "web_link") AgentCardData.STATUS_WEB else "information"
        return AgentCardData(status, item.title.ifBlank { item.url }, item.kind,
            item.year.takeIf { it > 0 }?.toString().orEmpty(), item.url, item.poster,
            item.id, actions, source, false, item.content, item.season, item.episode, source)
    }

    fun contentType(kind: String): String = when (kind.lowercase()) {
        "movie" -> "Movie"
        "show", "series", "season", "episode" -> "Series"
        else -> "Other"
    }
}
