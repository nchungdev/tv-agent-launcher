package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.data.*
import org.junit.Assert.*
import org.junit.Test

class FlowSearchResultsTest {
    @Test fun keepsTasksWithinSourceAndReplacesTaskSnapshots() {
        val results = FlowSearchResults()
        results.update(FlowResult("one", "plex", "ok",
            items = listOf(FlowItem("a", "movie", "First"))))
        results.update(FlowResult("two", "plex", "ok",
            items = listOf(FlowItem("b", "series", "Second"))))
        results.update(FlowResult("three", "jellyfin", "ok",
            items = listOf(FlowItem("a", "movie", "Other source"))))
        assertEquals(listOf("First", "Second"), results.cards("plex").map { it.title })
        assertEquals("Other source", results.cards("jellyfin").single().title)
        results.update(FlowResult("one", "plex", "ok",
            items = listOf(FlowItem("c", "movie", "Replacement"))))
        assertEquals(listOf("Replacement", "Second"), results.cards("plex").map { it.title })
    }

    @Test fun answersStayTextAndActionsKeepIdentityAndEnabledPolicy() {
        val results = FlowSearchResults()
        val enabled = FlowAction("catalog_playback", sourceId = "provider",
            itemId = "episode", url = "/stream")
        results.update(FlowResult("one", "phimapi", "ok", items = listOf(
            FlowItem("answer", "answer", content = "Explanation"),
            FlowItem("film", "series", "Film", poster = "https://example.com/poster",
                actions = listOf(enabled, enabled.copy(enabled = false))))))
        assertEquals("Explanation", results.answer())
        val card = results.cards("phimapi").single()
        assertEquals("phimapi", card.sourceId)
        assertEquals("phimapi", card.source)
        assertFalse(card.automaticActions)
        val action = card.actions.single()
        assertEquals("provider", action.parameters["source_id"])
        assertEquals("episode", action.parameters["episode_id"])
        assertEquals(card.posterUrl, action.parameters["poster"])
        results.update(FlowResult("one", "phimapi", "ok", items = listOf(
            FlowItem("answer", "answer", content = "Updated explanation"))))
        assertEquals("Updated explanation", results.answer())
        assertTrue(results.cards("phimapi").isEmpty())
    }

    @Test fun disabledAndUnknownActionsNeverBecomeImplicitPlayback() {
        val results = FlowSearchResults()
        for (action in listOf(FlowAction("catalog_detail", enabled = false),
            FlowAction("future_action"))) {
            results.update(FlowResult("one", "tmdb", "ok", items = listOf(
                FlowItem("film", "movie", "Film", actions = listOf(action)))))
            assertTrue(results.cards("tmdb").single().resolvedActions().isEmpty())
        }
    }
}
