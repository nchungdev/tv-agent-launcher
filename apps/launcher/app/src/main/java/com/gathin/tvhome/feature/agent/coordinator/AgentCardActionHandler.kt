package com.gathin.tvhome.feature.agent.coordinator

import android.content.Context
import android.widget.Toast
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.core.view.CardAction
import com.gathin.tvhome.feature.agent.data.AgentActionClient
import com.gathin.tvhome.feature.agent.data.AgentClient
import com.gathin.tvhome.feature.agent.data.PresentationResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AgentCardActionHandler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun handleCardAction(context: Context, card: AgentCardData) {
        if (card.sourceId.isNotBlank() && card.type in setOf("movie", "show", "series")) {
            com.gathin.tvhome.feature.agent.data.SearchSelection(context).title(card.title)
        }
        if (card.sourceId.isNotBlank() && card.type !in setOf("web_link", "answer", "status")) {
            MediaDetailSheet.show(context, card)
            return
        }
        val actions = card.resolvedActions()
        when {
            actions.isEmpty() -> if (card.automaticActions) showStatus(context, card)
            actions.size == 1 -> perform(context, card, actions[0])
            else -> chooseAction(context, card, actions)
        }
    }

    private fun showStatus(context: Context, card: AgentCardData) {
        val requested = card.status.equals(AgentCardData.STATUS_REQUESTED, ignoreCase = true)
        val message = if (requested) MESSAGE_REQUESTED else MESSAGE_DOWNLOADING
        val close = AgentCardDialog.Button(LABEL_CLOSE) {}
        AgentCardDialog.show(context, card.title, message, listOf(close))
    }

    private fun chooseAction(context: Context, card: AgentCardData, actions: List<CardAction>) {
        val buttons = actions.map { action ->
            AgentCardDialog.Button(action.label) { perform(context, card, action) }
        }
        val close = AgentCardDialog.Button(LABEL_CLOSE) {}
        AgentCardDialog.show(context, card.title, subtitle(card), listOf(close) + buttons)
    }

    private fun subtitle(card: AgentCardData): String {
        return listOf(card.year, card.type).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    fun perform(context: Context, card: AgentCardData, action: CardAction) {
        if (action.confirm.isEmpty()) {
            execute(context, card, action)
            return
        }
        val cancel = AgentCardDialog.Button(LABEL_CANCEL) {}
        val confirm = AgentCardDialog.Button(action.label) { execute(context, card, action) }
        AgentCardDialog.show(context, action.label, action.confirm, listOf(cancel, confirm))
    }

    private fun execute(context: Context, card: AgentCardData, action: CardAction) {
        if (action.isServer) {
            sendToServer(context, card, action)
        } else {
            AgentActionExecutor.execute(context, action.id, action.name, action.parameters)
        }
    }

    private fun sendToServer(context: Context, card: AgentCardData, action: CardAction) {
        toast(context, MESSAGE_SENDING)
        val parameters = action.parameters.toMutableMap()
        if (card.id.isNotEmpty()) parameters.putIfAbsent(PARAMETER_CARD_ID, card.id)

        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                post(context, action.name, parameters)
            }
            if (failure != null) toast(context, failure)
        }
    }

    private fun post(context: Context, actionId: String, parameters: Map<String, Any?>): String? {
        return try {
            AgentActionClient.send(context, SESSION, actionId, parameters, listener(context))
            null
        } catch (error: Exception) {
            "$MESSAGE_CONNECTION: ${error.message}"
        }
    }

    private fun listener(context: Context) = object : AgentClient.V2StreamListener {
        override fun onPresentation(presentation: PresentationResponse) {
            val speech = presentation.speechText.ifEmpty { MESSAGE_SENT }
            scope.launch { toast(context, speech) }
        }

        override fun onError(code: String, message: String) {
            scope.launch { toast(context, "$MESSAGE_SERVER ($code)") }
        }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private const val SESSION = "card_action"
    private const val PARAMETER_CARD_ID = "card_id"
    private const val LABEL_CLOSE = "Đóng"
    private const val LABEL_CANCEL = "Hủy"
    private const val MESSAGE_SENDING = "Đang gửi yêu cầu..."
    private const val MESSAGE_SENT = "Đã gửi yêu cầu tới NAS."
    private const val MESSAGE_SERVER = "Lỗi máy chủ"
    private const val MESSAGE_CONNECTION = "Lỗi kết nối"
    private const val MESSAGE_DOWNLOADING = "Phim/series này đang trong hàng đợi tải về trên NAS."
    private const val MESSAGE_REQUESTED = "Yêu cầu đã được gửi, NAS sẽ báo khi có phim."
}
