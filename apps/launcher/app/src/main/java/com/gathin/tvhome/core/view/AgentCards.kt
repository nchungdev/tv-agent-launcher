package com.gathin.tvhome.core.view

import org.json.JSONArray
import org.json.JSONObject

object AgentCards {

    fun fromMaps(items: List<Map<String, Any?>>): List<AgentCardData> {
        return items.mapNotNull { fromMap(it) }
    }

    fun fromMap(item: Map<String, Any?>): AgentCardData? {
        val title = text(item["title"])
        if (title.isEmpty()) return null

        return AgentCardData(
            status = text(item["status"]),
            title = title,
            type = text(item["media_type"]).ifEmpty { text(item["type"]) },
            year = text(item["year"]),
            link = text(item["link"]),
            posterUrl = text(item["poster_url"]).ifEmpty { text(item["poster"]) },
            id = text(item["id"]),
            actions = actions(item["actions"])
        )
    }

    private fun actions(raw: Any?): List<CardAction> {
        return elements(raw).mapNotNull { action(it) }
    }

    private fun action(raw: Map<String, Any?>): CardAction? {
        val label = text(raw["label"])
        val serverName = text(raw["action_id"])
        val clientName = text(raw["action_type"])
        val kind = text(raw["type"]).ifEmpty {
            if (serverName.isNotEmpty()) CardAction.KIND_SERVER else CardAction.KIND_CLIENT
        }
        val name = if (kind == CardAction.KIND_SERVER) serverName else clientName
        if (label.isEmpty() || name.isEmpty()) return null

        return CardAction(
            id = text(raw["id"]).ifEmpty { name },
            label = label,
            kind = kind,
            name = name,
            parameters = map(raw["parameters"]),
            confirm = text(raw["confirm"])
        )
    }

    private fun elements(raw: Any?): List<Map<String, Any?>> {
        return when (raw) {
            is JSONArray -> (0 until raw.length()).mapNotNull { map(raw.opt(it)).ifEmpty { null } }
            is List<*> -> raw.mapNotNull { map(it).ifEmpty { null } }
            else -> emptyList()
        }
    }

    private fun map(raw: Any?): Map<String, Any?> {
        return when (raw) {
            is JSONObject -> raw.keys().asSequence().associateWith { raw.opt(it) }
            is Map<*, *> -> raw.entries.associate { it.key.toString() to it.value }
            else -> emptyMap()
        }
    }

    private fun text(raw: Any?): String {
        if (raw == null || raw == JSONObject.NULL) return ""
        return raw.toString().trim()
    }
}
