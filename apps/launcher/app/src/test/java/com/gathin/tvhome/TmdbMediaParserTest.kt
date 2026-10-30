package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.data.CatalogParser
import com.gathin.tvhome.feature.agent.data.TmdbMediaParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMediaParserTest {
    private fun response(mode: Int = 2) = JSONObject("""
        {
          "media":{"id":"tmdb:tv/84958","kind":"show","title":"Loki","year":2021,
            "overview":"God of mischief","poster":"https://cdn/poster.jpg"},
          "sources":[
            {"source_id":"phimapi","status":"ok","playback_mode":$mode,"items":[
              {"id":"phimapi:loki-1","title":"Loki (Phần 1)","kind":"show","season":1,
                "url":"https://provider/loki-1","servers":[
                  {"server_name":"Vietsub #1","episodes":[
                    {"id":"ep-1","label":"Tập 01","group":"Vietsub","order":1,
                      "playable":true,"url":"https://cdn/1.m3u8"},
                    {"id":"ep-2","playable":false,"url":"https://cdn/2.m3u8"},
                    {"id":"ep-3","playable":true},
                    {"id":"","playable":true,"url":"https://cdn/invalid.m3u8"}]},
                  {"server_name":"Thuyết Minh #1","episodes":[
                    {"id":"dub-1","label":"Tập 01","url":"/streams/dub.mp4"}]}]},
              {"id":"phimapi:loki-2","title":"Loki (Phần 2)","season":2,
                "servers":[{"server_name":"Vietsub #1","episodes":[
                  {"id":"s2-1","url":"https://cdn/s2.m3u8"}]}]}
            ]},
            {"source_id":"plex","status":"skipped","playback_mode":2,"items":[]}
          ]
        }
    """.trimIndent())

    @Test
    fun parsesMediaAndAllSourceSeasonsWithoutActions() {
        val detail = CatalogParser.parseTmdbMediaDetail(response())
        assertEquals("Loki", detail.title.name)
        assertEquals("God of mischief", detail.title.overview)
        assertEquals("https://cdn/poster.jpg", detail.title.poster)
        assertEquals("show", detail.title.kind)
        assertEquals(2021, detail.title.year)
        assertEquals(2, detail.sources.size)
        val source = detail.sources.first()
        assertEquals(2, source.playbackMode)
        assertEquals(listOf(1, 2), source.items.map { it.season })
        assertEquals("https://provider/loki-1", source.items.first().url)
        assertTrue(source.items.first().actions.isEmpty())
        assertEquals("skipped", detail.sources.last().status)
        assertTrue(detail.sources.last().items.isEmpty())
    }

    @Test
    fun preservesServersEpisodeIdentityAndDirectUrls() {
        val source = CatalogParser.parseTmdbMediaDetail(response()).sources.first()
        val item = source.items.first()
        assertEquals(listOf("Vietsub #1", "Thuyết Minh #1"), item.servers.map { it.name })
        val title = TmdbMediaParser.title(source, item)
        assertEquals(5, title.episodes.size)
        val episode = title.episodes.first()
        assertEquals("phimapi:loki-1", episode.titleId)
        assertEquals("ep-1", episode.id)
        assertEquals("https://cdn/1.m3u8", episode.url)
        assertEquals("Vietsub #1", episode.group)
        assertEquals(1, episode.order)
        assertTrue(episode.playable)
        assertEquals("Thuyết Minh #1", title.episodes.last().group)
        assertEquals("/streams/dub.mp4", title.episodes.last().url)
        assertEquals("s2-1", TmdbMediaParser.title(source, source.items[1]).episodes.single().id)
    }

    @Test
    fun disablesUnavailableEpisodesAndMissingStreamsOrIds() {
        val source = CatalogParser.parseTmdbMediaDetail(response()).sources.first()
        val episodes = TmdbMediaParser.title(source, source.items.first()).episodes
        assertFalse(episodes[1].playable)
        assertFalse(episodes[2].playable)
        assertFalse(episodes[3].playable)
    }

    @Test
    fun onlyDirectPlaybackModesExposeEpisodes() {
        for (mode in listOf(0, 1, 2, 3)) {
            val source = CatalogParser.parseTmdbMediaDetail(response(mode)).sources.first()
            val title = TmdbMediaParser.title(source, source.items.first())
            assertEquals(mode in 1..2, title.episodes.isNotEmpty())
        }
    }

    @Test
    fun toleratesEmptySourcesAndServers() {
        val detail = CatalogParser.parseTmdbMediaDetail(JSONObject("""
            {"media":{"title":"Empty"},"sources":[
              {"source_id":"web","playback_mode":0,"items":[{"id":"web:1"}]}]}
        """.trimIndent()))
        val source = detail.sources.single()
        assertTrue(source.items.single().servers.isEmpty())
        assertTrue(TmdbMediaParser.title(source, source.items.single()).episodes.isEmpty())
        assertTrue(CatalogParser.parseTmdbMediaDetail(JSONObject()).sources.isEmpty())
    }

    @Test
    fun retainsLegacyTitleAndActionsWhenPlaybackModeIsAbsent() {
        val detail = CatalogParser.parseTmdbMediaDetail(JSONObject("""
            {"title":{"title":"Legacy"},"sources":[{"source_id":"plex","items":[
              {"id":"plex:1","actions":[{"type":"catalog_detail","item_id":"plex:1"}]}]}]}
        """.trimIndent()))
        assertEquals("Legacy", detail.title.name)
        assertEquals(-1, detail.sources.single().playbackMode)
        assertEquals("catalog_detail", detail.sources.single().items.single().actions.single().name)
    }
}
