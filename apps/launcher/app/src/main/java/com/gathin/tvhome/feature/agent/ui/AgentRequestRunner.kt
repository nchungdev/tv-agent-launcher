package com.gathin.tvhome.feature.agent.ui

import com.gathin.tvhome.R
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.gathin.tvhome.core.view.AgentCards
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.feature.agent.data.AgentClient
import com.gathin.tvhome.feature.agent.data.FlowClient
import com.gathin.tvhome.feature.agent.data.FlowInput
import com.gathin.tvhome.feature.agent.data.FlowSearchResults
import com.gathin.tvhome.feature.agent.data.FlowInputContext
import com.gathin.tvhome.feature.agent.data.SearchSelection
import com.gathin.tvhome.feature.agent.data.FlowPlan
import com.gathin.tvhome.feature.agent.data.FlowResult
import com.gathin.tvhome.feature.agent.data.PresentationResponse
import com.gathin.tvhome.feature.agent.data.SearchTab
import com.gathin.tvhome.feature.agent.data.SearchTabs
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AgentRequestRunner(context: Context, private val callbacks: Callbacks) {

    interface Callbacks {
        fun onPartialAnswer(soFar: String)
        fun onCards(cards: List<AgentCardData>)
        fun onFinished(error: String?, answer: String)
        fun onSearchResults(tabs: List<SearchTab>) {}
    }

    private val appContext = context.applicationContext
    private val activity = context as? Activity
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

        val selection = SearchSelection(appContext)
        val input = FlowInput(request, FlowInputContext(selection.title(), selection.sources()))
        val results = FlowSearchResults()
        var inputQuestion = ""
        val tabs = SearchTabs()
        FlowClient.execute(appContext, input, object : FlowClient.FlowListener {
            override fun onPlanReady(plan: FlowPlan) {
                if (requestGeneration != generation) return
                tabs.plan(plan.tasks.map { it.source }.distinct())
                deliverTabs(tabs, requestGeneration)
            }

            override fun onTaskResult(result: FlowResult) {
                if (requestGeneration != generation) return
                results.update(result)
                tabs.result(result.source, result.status, results.cards(result.source),
                    result.errorCode)
                received.clear()
                appendBounded(received, listOf(results.answer(), inputQuestion)
                    .filter { it.isNotBlank() }.joinToString("\n"), connection)
                deliverSnapshot(received, lastDelivery, requestGeneration)
                deliverTabs(tabs, requestGeneration)
            }

            override fun onNeedsInput(question: String) {
                if (requestGeneration != generation) return
                inputQuestion = question
                received.clear()
                appendBounded(received, listOf(results.answer(), inputQuestion)
                    .filter { it.isNotBlank() }.joinToString("\n"), connection)
                deliverSnapshot(received, lastDelivery, requestGeneration)
            }

            override fun onCompleted(status: String) {
                if (requestGeneration != generation) return
                val finalAnswer = received.toString().trim().ifEmpty { summary(tabs) }
                main.post {
                    if (requestGeneration != generation) return@post
                    main.removeCallbacks(renderPending)
                    pendingAnswer = null
                    val failure = if (status in setOf("completed", "needs_input")) null
                        else status
                    callbacks.onFinished(failure, finalAnswer)
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
                    if (code == UNAUTHORIZED) requestLogin()
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
            if (e.messageRes == R.string.agent_unauthorised) main.post { requestLogin() }
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

    private fun requestLogin() {
        activity?.let { AgentLogin.request(it) }
    }

    private fun deliverTabs(tabs: SearchTabs, requestGeneration: Int) {
        val snapshot = tabs.snapshot()
        main.post {
            if (requestGeneration == generation) callbacks.onSearchResults(snapshot)
        }
    }

    private fun summary(tabs: SearchTabs): String {
        val total = tabs.total
        return if (total > 0) "" else appContext.getString(R.string.agent_search_nothing)
    }

    private fun appendBounded(
        target: StringBuilder, text: String, request: AgentClient.Request
    ) {
        if (target.length + text.length > MAX_ANSWER_CHARS) {
            request.cancel()
            throw IllegalStateException(ANSWER_TOO_LONG)
        }
        target.append(text)
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
        private const val UNAUTHORIZED = "unauthorized"
    }
}
