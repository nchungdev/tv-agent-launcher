package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.data.CatalogClient
import com.gathin.tvhome.feature.agent.data.CatalogParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogParserTest {

    @Test
    fun parsesTitleDetailResponse() {
        val json = JSONObject("""
            {
                "title": {
                    "id": "tt-12345",
                    "name": "Breaking Bad",
                    "year": 2008,
                    "kind": "series",
                    "episodes": [
                        {
                            "id": "ep-101",
                            "title_id": "tt-12345",
                            "label": "Tập 1: Pilot",
                            "order": 1,
                            "group": "Season 1",
                            "playable": true
                        }
                    ]
                }
            }
        """.trimIndent())

        val title = CatalogParser.parseTitleResponse(json)
        assertEquals("tt-12345", title.id)
        assertEquals("Breaking Bad", title.name)
        assertEquals(2008, title.year)
        assertEquals("series", title.kind)
        assertEquals(1, title.episodes.size)

        val ep = title.episodes[0]
        assertEquals("ep-101", ep.id)
        assertEquals("tt-12345", ep.titleId)
        assertEquals("Tập 1: Pilot", ep.label)
        assertEquals(1, ep.order)
        assertEquals("Season 1", ep.group)
        assertEquals(true, ep.playable)
    }

    @Test
    fun parsesPlaybackResponse() {
        val json = JSONObject("""
            {
                "playback": {
                    "url": "/media/proxy/hls/ep-101.m3u8",
                    "mime_type": "application/x-mpegURL",
                    "title": "Breaking Bad S01E01"
                }
            }
        """.trimIndent())

        val response = CatalogParser.parsePlaybackResponse(json)
        assertEquals("/media/proxy/hls/ep-101.m3u8", response.playback.url)
        assertEquals("application/x-mpegURL", response.playback.mimeType)
        assertEquals("Breaking Bad S01E01", response.playback.title)
    }

    @Test
    fun resolvesRelativeAndAbsoluteStreamUrls() {
        assertEquals(
            "http://home-nas:8080/media/proxy/hls/stream.m3u8",
            CatalogClient.resolveStreamUrl("http://home-nas:8080", "/media/proxy/hls/stream.m3u8")
        )
        assertEquals(
            "http://home-nas:8080/media/proxy/hls/stream.m3u8",
            CatalogClient.resolveStreamUrl("http://home-nas:8080/", "media/proxy/hls/stream.m3u8")
        )
        assertEquals(
            "https://cdn.example.com/stream.m3u8",
            CatalogClient.resolveStreamUrl("http://home-nas:8080", "https://cdn.example.com/stream.m3u8")
        )
    }
}
