package com.gathin.tvhome.feature.agent.data

import android.content.Context
import com.gathin.tvhome.feature.notification.data.NotificationRepositoryImpl
import com.gathin.tvhome.feature.notification.data.TvNotification
import com.gathin.tvhome.feature.main.ui.MainActivity
import com.gathin.tvhome.feature.notification.widget.NotificationHudOverlay
import com.gathin.tvhome.feature.agent.ui.DuinchAiGreeting
import com.gathin.tvhome.feature.agent.widget.DuinchView
import com.gathin.tvhome.feature.agent.coordinator.AgentActionExecutor
import org.json.JSONObject
import java.util.Locale


class DuinchPayloadRouter(
    private val context: Context,
    private val onGreetingReceived: (String, Int) -> Unit
) {
    private val appContext = context.applicationContext

    fun handleJson(jsonText: String, isRunning: () -> Boolean) {
        val obj = try { JSONObject(jsonText) } catch (_: Exception) { return }
        val type = obj.optString("type", "").lowercase(Locale.ROOT)

        when (type) {
            "action" -> handleActionPayload(obj, isRunning)
            "notification" -> handleNotificationPayload(obj, isRunning)
            "tv_mode" -> AgentEventHub.tvModeListener?.invoke()
            else -> handleGreetingPayload(obj, isRunning)
        }
    }

    private fun handleGreetingPayload(obj: JSONObject, isRunning: () -> Boolean) {
        val rawGreeting = obj.optString("text", "").trim()
        if (rawGreeting.isEmpty()) return
        val moodName = obj.optString("mood", "happy")
        val moodValue = parseMood(moodName)
        val accepted = DuinchAiGreeting.pushGreeting(rawGreeting)
        if (accepted.isEmpty()) return

        if (!isRunning()) return
        if (MainActivity.isHomeResumed) {
            onGreetingReceived(accepted, moodValue)
        } else {
            val hudNotif = TvNotification(
                id = System.currentTimeMillis().toString(),
                source = "DUINCH BOT",
                title = "",
                message = accepted,
                severity = moodName
            )
            NotificationHudOverlay.show(appContext, hudNotif)
        }
    }

    private fun handleActionPayload(obj: JSONObject, isRunning: () -> Boolean) {
        val actionType = obj.optString("action_type", "").trim()
        if (actionType.isEmpty()) return
        val stepId = obj.optString("step_id", "ws-action")
        val feedbackText = obj.optString("feedback_text", "").trim()
        val params = parseJsonObject(obj.optJSONObject("parameters"))

        if (!isRunning()) return
        if (feedbackText.isNotEmpty()) {
            val accepted = DuinchAiGreeting.pushGreeting(feedbackText)
            if (accepted.isNotEmpty()) {
                onGreetingReceived(accepted, DuinchView.EXCITED)
            }
        }
        NotificationRepositoryImpl.getInstance().add(TvNotification(
            id = stepId,
            source = "Agent",
            title = "Thao tác được đề xuất",
            message = feedbackText.ifBlank { actionType },
            actionType = actionType,
            actionParams = params
        ))
    }

    private fun handleNotificationPayload(obj: JSONObject, isRunning: () -> Boolean) {
        val notifObj = obj.optJSONObject("notification") ?: obj
        val id = notifObj.optString("id", System.currentTimeMillis().toString())
        val source = notifObj.optString("source", "nas")
        val title = notifObj.optString("title", "Thông báo")
        val message = notifObj.optString("message", obj.optString("text", ""))
        val severity = notifObj.optString("severity", "info")
        val actionObj = notifObj.optJSONObject("action")
        val actionType = actionObj?.optString("action_type", "")?.ifEmpty { null }
        val actionParams = parseJsonObject(actionObj?.optJSONObject("parameters"))
        val timestamp = notifObj.optLong("timestamp", System.currentTimeMillis())
        val imageUrl = notifObj.optString("image_url", "")
            .ifEmpty { notifObj.optString("poster_url", "") }
            .ifEmpty { notifObj.optString("thumb", "") }
            .ifEmpty {
                (actionParams["image_url"] ?: actionParams["poster_url"])?.toString() ?: ""
            }

        val notif = TvNotification(
            id = id,
            source = source,
            title = title,
            message = message,
            severity = severity,
            actionType = actionType,
            actionParams = actionParams.ifEmpty { null },
            timestampEpochMs = timestamp,
            imageUrl = imageUrl.ifEmpty { null }
        )
        NotificationRepositoryImpl.getInstance().init(appContext)
        NotificationRepositoryImpl.getInstance().add(notif)

        val alertText = if (message.isNotEmpty()) message else title
        val accepted = DuinchAiGreeting.pushGreeting(alertText)
        if (!isRunning()) return
        if (MainActivity.isHomeResumed) {
            if (accepted.isNotEmpty()) {
                onGreetingReceived(accepted, DuinchView.SURPRISED)
            }
        } else {
            NotificationHudOverlay.show(appContext, notif)
        }
    }

    private fun parseJsonObject(json: JSONObject?): Map<String, Any?> {
        if (json == null) return emptyMap()
        val map = HashMap<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.opt(key)
        }
        return map
    }

    private fun parseMood(rawMood: String): Int =
        when (rawMood.lowercase(Locale.ROOT)) {
            "wink" -> DuinchView.WINK
            "excited" -> DuinchView.EXCITED
            "thinking" -> DuinchView.THINKING
            "love" -> DuinchView.LOVE
            "surprised" -> DuinchView.SURPRISED
            "silly" -> DuinchView.SILLY
            "sad" -> DuinchView.SAD
            "waiting" -> DuinchView.WAITING
            "idle" -> DuinchView.IDLE
            else -> DuinchView.HAPPY
        }
}
