package com.gathin.tvhome.feature.agent.data

import org.json.JSONObject

internal object AgentEventParser {
    fun dispatch(line: String, listener: AgentClient.V2StreamListener) {
        val obj = JSONObject(line)
        val eventType = obj.optString("type", "")
        val payload = obj.optJSONObject("payload") ?: JSONObject()
        when (eventType) {
            "accepted" -> {
                listener.onAccepted(
                    payload.optString("request_id"),
                    payload.optString("session_id")
                )
            }
            "progress" -> {
                listener.onProgress(payload.optString("stage"))
            }
            "presentation" -> {
                val presentation = PresentationResponse.fromJson(payload)
                listener.onPresentation(presentation)
            }
            "client_action" -> {
                listener.onClientAction(AgentContractJsonHelper.jsonObjectToMap(payload))
            }
            "completed" -> {
                listener.onCompleted(
                    payload.optString("request_id"),
                    payload.optString("status")
                )
            }
            "error" -> {
                listener.onError(
                    payload.optString("code", "pipeline_error"),
                    payload.optString("message", "Unknown error")
                )
            }
        }
    }
}
