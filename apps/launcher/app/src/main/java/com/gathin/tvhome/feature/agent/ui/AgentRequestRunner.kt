package com.gathin.tvhome.feature.agent.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.gathin.tvhome.feature.agent.data.AgentClient
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AgentRequestRunner(context: Context, private val callbacks: Callbacks) {

    interface Callbacks {
        fun onPartialAnswer(soFar: String)
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
        var failure: String? = null
        val lastDelivery = longArrayOf(0)
        try {
            AgentClient.ask(appContext, session, request, { piece ->
                if (received.length + piece.length > MAX_ANSWER_CHARS) {
                    connection.cancel()
                    throw IllegalStateException(ANSWER_TOO_LONG)
                }
                received.append(piece)
                deliverSnapshot(received, lastDelivery, requestGeneration)
            }, connection)
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
