package com.gathin.tvhome.feature.agent.data

import com.gathin.tvhome.feature.agent.data.AgentWebSocketDataSource
import com.gathin.tvhome.feature.agent.data.AgentEventRepository
import kotlinx.coroutines.flow.Flow


class AgentEventRepositoryImpl(
    private val webSocketDataSource: AgentWebSocketDataSource
) : AgentEventRepository {

    override fun observeEvents(): Flow<String> = webSocketDataSource.observeEvents()
}
