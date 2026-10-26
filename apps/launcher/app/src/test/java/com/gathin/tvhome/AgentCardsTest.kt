package com.gathin.tvhome

import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.core.view.AgentCards
import com.gathin.tvhome.feature.agent.data.AgentClient
import com.gathin.tvhome.feature.agent.data.PresentationResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCardsTest {

    private val presentationLine = """
        {"type":"presentation","payload":{"speech_text":"Có 1 kết quả","cards":[
          {"id":"tmdb:603","title":"The Matrix","year":"1999","media_type":"movie",
           "poster_url":"http://nas/p.jpg","status":"requestable",
           "actions":[{"id":"request","label":"Thêm vào NAS","type":"server",
             "action_id":"media.request","confirm":"Thêm?",
             "parameters":{"tmdb_id":603,"media_type":"movie"}},
             {"id":"open","label":"Mở","type":"client","action_type":"plex.open",
             "parameters":{"uri":"plex://x"}}]},
          {"title":""}]}}
    """.trimIndent().replace("\n", "")

    private fun cardsFrom(line: String): List<AgentCardData> {
        var received: PresentationResponse? = null
        AgentClient.parseAndDispatchV2Event(line, object : AgentClient.V2StreamListener {
            override fun onPresentation(presentation: PresentationResponse) {
                received = presentation
            }
        })
        return AgentCards.fromMaps(received!!.cards)
    }

    @Test
    fun presentationCardsKeepIdentityAndActions() {
        val cards = cardsFrom(presentationLine)
        assertEquals(1, cards.size)
        val card = cards[0]
        assertEquals("tmdb:603", card.id)
        assertEquals("movie", card.type)
        assertEquals(2, card.actions.size)
        val request = card.actions[0]
        assertTrue(request.isServer)
        assertEquals("media.request", request.name)
        assertEquals(603, request.parameters["tmdb_id"])
        assertEquals("Thêm?", request.confirm)
        assertFalse(card.actions[1].isServer)
        assertEquals("plex.open", card.actions[1].name)
    }

    @Test
    fun cardWithoutTitleIsDropped() {
        assertNull(AgentCards.fromMap(mapOf("status" to "plex")))
    }

    @Test
    fun actionWithoutNameIsDropped() {
        val card = AgentCards.fromMap(
            mapOf("title" to "A", "actions" to listOf(mapOf("label" to "Go")))
        )
        assertTrue(card!!.actions.isEmpty())
    }

    @Test
    fun legacyPlexCardOpensPlex() {
        val card = AgentCardData("plex", "Dune", "Movie", "2021", "plex://dune")
        val action = card.resolvedActions().single()
        assertEquals("plex.open", action.name)
        assertEquals("plex://dune", action.parameters["uri"])
    }

    @Test
    fun legacyUnknownCardRequestsMedia() {
        val card = AgentCardData("", "Severance", "Series", "2022", "")
        val action = card.resolvedActions().single()
        assertTrue(action.isServer)
        assertEquals("media.request", action.name)
        assertEquals("series", action.parameters["media_type"])
        assertTrue(action.confirm.contains("Severance"))
    }

    @Test
    fun downloadingAndRequestedCardsHaveNoAction() {
        assertTrue(AgentCardData("downloading", "A", "", "", "").resolvedActions().isEmpty())
        assertTrue(AgentCardData("requested", "A", "", "", "").resolvedActions().isEmpty())
    }
}
