package com.gathin.tvhome.feature.agent.data

import com.gathin.tvhome.core.view.AgentCardData

data class SearchTab(val source: String, val state: State, val cards: List<AgentCardData>) {
    enum class State { LOADING, READY, EMPTY, FAILED }
}

class SearchTabs {
    private val tabs = LinkedHashMap<String, SearchTab>()

    val total: Int get() = tabs.values.sumOf { it.cards.size }

    @Synchronized
    fun plan(sources: List<String>) {
        for (source in sources) {
            if (source.isNotBlank() && source !in tabs) {
                tabs[source] = SearchTab(source, SearchTab.State.LOADING, emptyList())
            }
        }
    }

    @Synchronized
    fun result(source: String, status: String, cards: List<AgentCardData>) {
        if (source.isBlank()) return
        val state = when {
            cards.isNotEmpty() -> SearchTab.State.READY
            status == "error" || status == "timeout" -> SearchTab.State.FAILED
            else -> SearchTab.State.EMPTY
        }
        tabs[source] = SearchTab(source, state, cards)
    }

    @Synchronized
    fun snapshot(): List<SearchTab> {
        val ordered = tabs.values.sortedBy { ORDER.indexOf(it.source).let { i -> if (i < 0) ORDER.size else i } }
        return ordered.filter { it.state != SearchTab.State.EMPTY && it.state != SearchTab.State.FAILED } +
            ordered.filter { it.state == SearchTab.State.EMPTY || it.state == SearchTab.State.FAILED }
    }

    private companion object {
        val ORDER = listOf("plex", "jellyfin", "phimapi", "radarr", "sonarr", "tmdb", "internet")
    }
}
