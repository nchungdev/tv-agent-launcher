package com.gathin.tvhome.feature.agent.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.gathin.tvhome.core.view.AgentCards
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.core.view.CardAction
import com.gathin.tvhome.feature.agent.data.AgentClient
import com.gathin.tvhome.feature.agent.data.FlowClient
import com.gathin.tvhome.feature.agent.data.FlowInput
import com.gathin.tvhome.feature.agent.data.FlowItem
import com.gathin.tvhome.feature.agent.data.FlowResult
import com.gathin.tvhome.feature.agent.data.PresentationResponse
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AgentRequestRunner(context: Context, private val callbacks: Callbacks) {

    interface Callbacks {
        fun onPartialAnswer(soFar: String)
        fun onCards(cards: List<AgentCardData>)
        fun onFinished(error: String?, answer: String)
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val session = UUID.randomUUID().toString()
    private var generation = 0
    private var activeRequest: AgentClient.Request? = null
    private var pendingAnswer: String? = null

    private val renderPending = Runnable {
        pendingAnswer?.let { callbacks.onPartialAnswer(it) }
        pendingAnswer = null
    }

    fun send(request: String) {
        cancel()
        val received = StringBuilder()
        val requestGeneration = ++generation
        val connection = AgentClient.Request()
        activeRequest = connection
        worker.execute { ask(request, received, requestGeneration, connection) }
    }

    fun cancel() {
        generation++
        activeRequest?.cancel()
        activeRequest = null
        main.removeCallbacks(renderPending)
        pendingAnswer = null
    }

    fun destroy() {
        cancel()
        worker.shutdownNow()
    }

    private fun ask(
        request: String,
        received: StringBuilder,
        requestGeneration: Int,
        connection: AgentClient.Request
    ) {
        val lastDelivery = longArrayOf(0)
        var fallbackToLegacy = false

        val input = FlowInput(text = request)
        FlowClient.execute(appContext, input, object : FlowClient.FlowListener {
            override fun onTaskResult(result: FlowResult) {
                if (requestGeneration != generation) return
                val cards = result.items.mapNotNull { item -> mapFlowItemToCard(item) }
                if (cards.isNotEmpty()) {
                    main.post {
                        if (requestGeneration == generation) callbacks.onCards(cards)
                    }
                }
                for (item in result.items) {
                    if (item.content.isNotBlank()) {
                        received.append(item.content).append("\n")
                        deliverSnapshot(received, lastDelivery, requestGeneration)
                    }
                }
            }

            override fun onNeedsInput(question: String) {
                if (requestGeneration != generation) return
                received.append(question)
                deliverSnapshot(received, lastDelivery, requestGeneration)
            }

            override fun onCompleted(status: String) {
                if (requestGeneration != generation) return
                val finalAnswer = received.toString().trim()
                main.post {
                    if (requestGeneration != generation) return@post
                    main.removeCallbacks(renderPending)
                    pendingAnswer = null
                    callbacks.onFinished(null, finalAnswer)
                }
            }

            override fun onError(code: String, message: String) {
                if (requestGeneration != generation) return
                val finalAnswer = received.toString().trim()
                main.post {
                    if (requestGeneration != generation) return@post
                    main.removeCallbacks(renderPending)
                    pendingAnswer = null
                    callbacks.onFinished(message, finalAnswer)
                }
            }

            override fun onLegacyFallback() {
                fallbackToLegacy = true
            }
        }, connection)

        if (fallbackToLegacy) {
            askLegacy(request, received, requestGeneration, connection, lastDelivery)
        }
    }

    private fun askLegacy(
        request: String,
        received: StringBuilder,
        requestGeneration: Int,
        connection: AgentClient.Request,
        lastDelivery: LongArray
    ) {
        var failure: String? = null
        try {
            AgentClient.ask(appContext, session, request, { piece ->
                if (received.length + piece.length > MAX_ANSWER_CHARS) {
                    connection.cancel()
                    throw IllegalStateException(ANSWER_TOO_LONG)
                }
                received.append(piece)
                deliverSnapshot(received, lastDelivery, requestGeneration)
            }, connection) { presentation -> deliverCards(presentation, requestGeneration) }
        } catch (e: AgentClient.AgentException) {
            failure = appContext.getString(e.messageRes)
        } catch (e: Exception) {
            failure = e.message
        }

        val finalAnswer = received.toString()
        main.post {
            if (requestGeneration != generation) return@post
            main.removeCallbacks(renderPending)
            pendingAnswer = null
            callbacks.onFinished(failure, finalAnswer)
        }
    }

    private fun mapFlowItemToCard(item: FlowItem): AgentCardData? {
        val title = item.title.ifBlank { item.url }
        if (title.isBlank()) return null
        val actions = ArrayList<CardAction>()

        for (action in item.actions) {
            when (action.type) {
                "catalog_detail" -> {
                    val params = mapOf<String, Any?>("title_id" to action.itemId.ifBlank { item.id })
                    actions.add(CardAction("detail", action.label.ifBlank { "Xem chi tiết" }, CardAction.KIND_CLIENT, "catalog_detail", params))
                }
                "catalog_playback" -> {
                    val params = mapOf<String, Any?>("url" to action.url, "id" to action.itemId)
                    actions.add(CardAction("play", action.label.ifBlank { "Phát ngay" }, CardAction.KIND_CLIENT, "catalog_playback", params))
                }
                else -> {
                    val url = action.url.ifBlank { item.url }
                    if (url.isNotBlank()) {
                        actions.add(CardAction("open", action.label.ifBlank { "Mở" }, CardAction.KIND_CLIENT, "web.open", mapOf("url" to url)))
                    }
                }
            }
        }

        if (actions.isEmpty() && item.url.isNotBlank()) {
            actions.add(CardAction("open_web", "Mở liên kết", CardAction.KIND_CLIENT, "web.open", mapOf("url" to item.url)))
        }

        return AgentCardData(
            status = "available",
            title = title,
            type = item.kind,
            year = "",
            link = item.url,
            posterUrl = "",
            id = item.id,
            actions = actions
        )
    }

    private fun deliverCards(presentation: PresentationResponse, requestGeneration: Int) {
        if (presentation.cards.isEmpty()) return

        val cards = AgentCards.fromMaps(presentation.cards)
        main.post {
            if (requestGeneration == generation) callbacks.onCards(cards)
        }
    }

    private fun deliverSnapshot(
        received: StringBuilder,
        lastDelivery: LongArray,
        requestGeneration: Int
    ) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDelivery[0] < RENDER_INTERVAL_MS) return

        lastDelivery[0] = now
        val snapshot = received.toString()
        main.post {
            if (requestGeneration != generation) return@post
            val scheduled = pendingAnswer != null
            pendingAnswer = snapshot
            if (!scheduled) main.postDelayed(renderPending, RENDER_INTERVAL_MS)
        }
    }

    companion object {
        private const val MAX_ANSWER_CHARS = 32_768
        private const val RENDER_INTERVAL_MS = 150L
        private const val ANSWER_TOO_LONG = "Câu trả lời vượt giới hạn hiển thị"
    }
}
