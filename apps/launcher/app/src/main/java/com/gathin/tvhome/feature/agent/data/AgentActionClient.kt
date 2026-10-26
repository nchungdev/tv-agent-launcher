package com.gathin.tvhome.feature.agent.data

import android.content.Context
import org.json.JSONObject

object AgentActionClient {

    @JvmStatic
    @Throws(Exception::class)
    fun send(
        context: Context,
        sessionId: String,
        actionId: String,
        parameters: Map<String, Any?>,
        listener: AgentClient.V2StreamListener
    ) {
        val payload = JSONObject()
        payload.put("request_id", REQUEST_PREFIX + System.currentTimeMillis())
        payload.put("session_id", sessionId)
        payload.put("action_id", actionId)
        payload.put("parameters", AgentContractJsonHelper.mapToJsonObject(parameters))
        payload.put("locale", LOCALE)
        AgentClient.stream(context, "/v2/action", payload, listener)
    }

    private const val REQUEST_PREFIX = "act-"
    private const val LOCALE = "vi-VN"
}
