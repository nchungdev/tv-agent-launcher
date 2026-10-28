package com.gathin.tvhome.feature.agent.data

import android.content.Context
import com.gathin.tvhome.feature.notification.data.NotificationRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

object AgentEventHub {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    @Volatile
    var greetingListener: ((String, Int) -> Unit)? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun ensureStarted(context: Context) {
        if (isRunning) return

        val appContext = context.applicationContext
        if (AgentClient.baseUrl(appContext).isBlank()) return

        NotificationRepositoryImpl.getInstance().init(appContext)
        val repository: AgentEventRepository =
            AgentEventRepositoryImpl(AgentWebSocketDataSource(appContext))
        val router = DuinchPayloadRouter(appContext) { text, mood ->
            greetingListener?.invoke(text, mood)
        }
        job = scope.launch(Dispatchers.IO) {
            repository.observeEvents().collect { payload ->
                withContext(Dispatchers.Main) {
                    router.handleJson(payload) { isRunning }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
