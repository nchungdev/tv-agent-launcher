package com.gathin.tvhome.feature.agent.data

import com.gathin.tvhome.core.view.AgentCardData

data class SearchTab(val source: String, val state: State, val cards: List<AgentCardData>,
    val detail: String = "") {
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
    fun result(source: String, status: String, cards: List<AgentCardData>, detail: String = "") {
        if (source.isBlank()) return
        val state = when {
            cards.isNotEmpty() -> SearchTab.State.READY
            status == "running" -> SearchTab.State.LOADING
            status == "error" || status == "timeout" -> SearchTab.State.FAILED
            else -> SearchTab.State.EMPTY
        }
        tabs[source] = SearchTab(source, state, cards, detail)
    }

    @Synchronized
    fun snapshot(): List<SearchTab> {
        val ordered = tabs.values.sortedBy {
            ORDER.indexOf(it.source).let { index -> if (index < 0) ORDER.size else index }
        }
        val (ready, inactive) = ordered.partition {
            it.state != SearchTab.State.EMPTY && it.state != SearchTab.State.FAILED
        }
        return ready + inactive
    }

    private companion object {
        val ORDER = listOf("plex", "jellyfin", "phimapi", "radarr", "sonarr", "tmdb", "internet")
    }
}
