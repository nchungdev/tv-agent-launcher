package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FlowSearchStateTest {
    @Test fun keepsIndependentSourcesAndRejectsOldEvents() {
        val state = FlowSearchState()
        val plan = FlowPlan(tasks = listOf(FlowTask("p", source = "plex"),
            FlowTask("j", source = "jellyfin")))
        assertTrue(state.accept(FlowEvent(requestId = "r", sequence = 1,
            type = "plan_ready", plan = plan)))
        val plex = FlowResult("p", "plex", "ok", items = listOf(FlowItem("1", "movie", "Film")))
        assertTrue(state.accept(FlowEvent(requestId = "r", sequence = 2,
            type = "task_result", result = plex)))
        assertFalse(state.accept(FlowEvent(requestId = "r", sequence = 2,
            type = "task_result", result = plex.copy(items = emptyList()))))
        assertFalse(state.accept(FlowEvent(requestId = "other", sequence = 9)))
        assertTrue(state.accept(FlowEvent(requestId = "r", sequence = 3,
            type = "task_result", result = FlowResult("j", "jellyfin", "error", "timeout"))))
        assertEquals(plex, state.results().first())
        assertEquals("timeout", state.results().last().errorCode)
        assertTrue(state.accept(FlowEvent(requestId = "r", sequence = 4,
            type = "response_completed")))
        assertFalse(state.accept(FlowEvent(requestId = "r", sequence = 5)))
    }

    @Test fun retainsMetadataIdentityAndExplicitActions() {
        val item = FlowParser.parseItem(JSONObject("""{
          "id":"phimapi:film","kind":"series","title":"Film","poster":"https://cdn/a.jpg",
          "year":2026,"season":2,"episode":3,
          "actions":[{"type":"catalog_detail","item_id":"title-id","source_id":"phimapi"}]
        }"""))
        val card = FlowCards.card("phimapi", item)
        assertEquals("2026", card.year)
        assertEquals("https://cdn/a.jpg", card.posterUrl)
        assertEquals("phimapi", card.sourceId)
        assertEquals("title-id", card.actions.single().parameters["title_id"])
        assertEquals(2, card.season)
        assertEquals(3, card.episode)
        assertFalse(card.isPlex())
    }

    @Test fun unknownActionAndMetadataNeverCreatePlaybackOrDownloads() {
        val metadata = FlowCards.card("tmdb", FlowItem("id", "movie", "Film"))
        assertTrue(metadata.resolvedActions().isEmpty())
        val unknown = FlowCards.card("source", FlowItem("id", "web_link", "Link",
            "https://example.com", actions = listOf(FlowAction("unknown", url = "file:///x"))))
        assertTrue(unknown.resolvedActions().isEmpty())
        val disabled = FlowParser.parseItem(JSONObject("""{
          "id":"id","kind":"movie","title":"Film",
          "actions":[{"type":"catalog_detail","enabled":false,"reason":"offline"}]
        }"""))
        assertTrue(FlowCards.card("source", disabled).resolvedActions().isEmpty())
    }

    @Test fun fourContentTypesAndNoCrossSourceIdDeduplication() {
        assertEquals("Movie", FlowCards.contentType("movie"))
        for (type in listOf("show", "series", "season", "episode")) {
            assertEquals("Series", FlowCards.contentType(type))
        }
        for (type in listOf("video", "web_link", "media_title", "future")) {
            assertEquals("Other", FlowCards.contentType(type))
        }
        val results = listOf("plex", "jellyfin").map {
            FlowResult(it, it, "ok", items = listOf(FlowItem("1", "movie", "Film")))
        }
        assertEquals(2, FlowCards.from(results).size)
    }
}
