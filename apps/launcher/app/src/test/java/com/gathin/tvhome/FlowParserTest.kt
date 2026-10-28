package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.data.FlowParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FlowParserTest {

    @Test
    fun parsesPlanReadyEvent() {
        val json = JSONObject("""
            {
                "version": 1,
                "request_id": "req-123",
                "sequence": 1,
                "type": "plan_ready",
                "plan": {
                    "understanding": {
                        "intent": "media_search",
                        "query": "Dune",
                        "needs_input": false
                    },
                    "tasks": [
                        {"id": "t1", "source": "plex", "query": "Dune"}
                    ]
                }
            }
        """.trimIndent())

        val event = FlowParser.parseEvent(json)
        assertEquals("plan_ready", event.type)
        assertEquals("req-123", event.requestId)
        assertNotNull(event.plan)
        assertEquals("Dune", event.plan?.understanding?.query)
        assertEquals("media_search", event.plan?.understanding?.intent)
        assertEquals(1, event.plan?.tasks?.size)
        assertEquals("plex", event.plan?.tasks?.get(0)?.source)
    }

    @Test
    fun parsesTaskResultEvent() {
        val json = JSONObject("""
            {
                "version": 1,
                "request_id": "req-123",
                "sequence": 2,
                "type": "task_result",
                "result": {
                    "task_id": "t1",
                    "source": "plex",
                    "status": "success",
                    "items": [
                        {
                            "id": "item-999",
                            "kind": "movie",
                            "title": "Dune: Part Two",
                            "url": "https://media.home/stream.m3u8",
                            "content": "Phim khoa học viễn tưởng",
                            "actions": [
                                {
                                    "type": "catalog_playback",
                                    "label": "Phát ngay",
                                    "url": "https://media.home/stream.m3u8",
                                    "item_id": "item-999"
                                }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent())

        val event = FlowParser.parseEvent(json)
        assertEquals("task_result", event.type)
        val result = event.result
        assertNotNull(result)
        assertEquals("t1", result?.taskId)
        assertEquals("plex", result?.source)
        assertEquals("success", result?.status)
        assertEquals(1, result?.items?.size)

        val item = result?.items?.get(0)
        assertEquals("item-999", item?.id)
        assertEquals("movie", item?.kind)
        assertEquals("Dune: Part Two", item?.title)
        assertEquals("https://media.home/stream.m3u8", item?.url)
        assertEquals("Phim khoa học viễn tưởng", item?.content)
        assertEquals(1, item?.actions?.size)
        assertEquals("catalog_playback", item?.actions?.get(0)?.type)
    }

    @Test
    fun parsesResponseCompletedEvent() {
        val json = JSONObject("""
            {
                "version": 1,
                "request_id": "req-123",
                "sequence": 3,
                "type": "response_completed",
                "status": "success"
            }
        """.trimIndent())

        val event = FlowParser.parseEvent(json)
        assertEquals("response_completed", event.type)
        assertEquals("success", event.status)
        assertEquals("req-123", event.requestId)
    }
}
