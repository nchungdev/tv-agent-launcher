package com.gathin.tvhome.feature.agent.data

import kotlinx.coroutines.flow.Flow


interface AgentEventRepository {
    fun observeEvents(): Flow<String>
}
