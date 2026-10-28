package com.gathin.tvhome.feature.smarthome.data

import org.json.JSONArray
import org.json.JSONObject

data class TvMode(val id: String, val name: String, val icon: String, val description: String)

data class TvModesState(val active: String, val modes: List<TvMode>) {

    fun toJson(): JSONObject {
        val rows = JSONArray()
        for (mode in modes) {
            rows.put(JSONObject().put("id", mode.id).put("name", mode.name)
                .put("icon", mode.icon).put("description", mode.description))
        }
        return JSONObject().put("active", active).put("modes", rows)
    }

    companion object {
        const val NORMAL = "normal"
        private const val MAX_MODES = 12

        fun fromJson(json: JSONObject): TvModesState {
            val rows = json.optJSONArray("modes") ?: JSONArray()
            val modes = (0 until minOf(rows.length(), MAX_MODES)).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id")
                val name = row.optString("name")
                if (id.isBlank() || name.isBlank()) null
                else TvMode(id, name.take(40), row.optString("icon").take(4),
                    row.optString("description").take(60))
            }
            return TvModesState(json.optString("active"), modes)
        }
    }
}
