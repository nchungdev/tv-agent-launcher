package com.gathin.tvhome.feature.agent.ui
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.coordinator.*
import com.gathin.tvhome.feature.agent.widget.*
import com.gathin.tvhome.feature.agent.adapter.*

import android.content.Context
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.data.AgentClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class AgentStatus(context: Context) {

    fun interface Listener {
        fun onStatusChanged()
    }

    private val context: Context = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main)
    private var state = UNKNOWN
    private var checkedAt = 0L
    private var checking = false

    fun isOnline(): Boolean = state == AgentClient.ONLINE

    fun labelRes(): Int {
        return when (state) {
            AgentClient.ONLINE -> R.string.agent_status_online
            AgentClient.REFUSED -> R.string.agent_status_refused
            AgentClient.NO_TOKEN -> R.string.agent_status_no_token
            AgentClient.OFFLINE -> R.string.agent_status_offline
            else -> R.string.agent_status_checking
        }
    }

    fun refresh(force: Boolean, listener: Listener) {
        val now = System.currentTimeMillis()
        val fresh = now - checkedAt < CHECK_INTERVAL_MS
        if (checking || (!force && fresh)) return

        checking = true
        scope.launch {
            val found = withContext(Dispatchers.IO) { AgentClient.ping(context) }
            checking = false
            checkedAt = System.currentTimeMillis()
            state = found
            listener.onStatusChanged()
        }
    }

    companion object {
        private const val UNKNOWN = -1
        private const val CHECK_INTERVAL_MS = 30_000L
    }
}
