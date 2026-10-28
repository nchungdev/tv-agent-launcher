package com.gathin.tvhome.feature.notification.data

import org.json.JSONObject


data class TvNotification(
    val id: String,
    val source: String,
    val title: String,
    val message: String,
    val severity: String = "info",
    val actionType: String? = null,
    val actionParams: Map<String, Any?>? = null,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    var isRead: Boolean = false,
    val imageUrl: String? = null
) {
    fun bounded(): TvNotification = copy(
        id = id.take(256), source = source.take(128), title = title.take(512),
        message = message.take(4096), severity = severity.take(32),
        actionType = actionType?.take(128),
        actionParams = actionParams?.takeIf {
            it.size <= 32 && JSONObject(it).toString().length <= 8192
        },
        imageUrl = imageUrl?.takeIf { it.length <= 2048 }
    )

    fun hasAction(): Boolean = !actionType.isNullOrBlank()

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("source", source)
        json.put("title", title)
        json.put("message", message)
        json.put("severity", severity)
        if (!actionType.isNullOrEmpty()) {
            json.put("action_type", actionType)
        }
        if (!actionParams.isNullOrEmpty()) {
            val paramsObj = JSONObject()
            for ((key, value) in actionParams) {
                paramsObj.put(key, value ?: JSONObject.NULL)
            }
            json.put("action_params", paramsObj)
        }
        if (!imageUrl.isNullOrEmpty()) {
            json.put("image_url", imageUrl)
        }
        json.put("timestamp", timestampEpochMs)
        json.put("is_read", isRead)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): TvNotification {
            val paramsObj = json.optJSONObject("action_params")
            val params = if (paramsObj != null) {
                val map = HashMap<String, Any?>()
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = paramsObj.opt(key)
                    map[key] = if (value == JSONObject.NULL) null else value
                }
                map
            } else {
                null
            }
            val actionRaw = json.optString("action_type", "")
            val imageRaw = json.optString("image_url", "")
                .ifEmpty { json.optString("poster_url", "") }
                .ifEmpty { json.optString("thumb", "") }
                .ifEmpty {
                    val extra = params?.get("image_url") ?: params?.get("poster_url")
                    extra?.toString() ?: ""
                }
            return TvNotification(
                id = json.optString("id", System.currentTimeMillis().toString()),
                source = json.optString("source", "nas"),
                title = json.optString("title", ""),
                message = json.optString("message", ""),
                severity = json.optString("severity", "info"),
                actionType = actionRaw.ifEmpty { null },
                actionParams = params,
                timestampEpochMs = json.optLong("timestamp", System.currentTimeMillis()),
                isRead = json.optBoolean("is_read", false),
                imageUrl = imageRaw.ifEmpty { null }
            )
        }
    }
}
