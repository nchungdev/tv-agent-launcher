package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.data.AgentClient
import com.gathin.tvhome.feature.agent.data.ActionStatus
import com.gathin.tvhome.feature.agent.data.PresentationResponse
import com.gathin.tvhome.feature.agent.coordinator.AgentActionExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentClientV2Test {

    @Test
    fun parseAcceptedEvent() {
        val payload = """{"type":"accepted","payload":{"request_id":"req-1","session_id":"sess-1"}}"""
        var acceptedRequestId = ""
        var acceptedSessionId = ""
        val listener = object : AgentClient.V2StreamListener {
            override fun onAccepted(requestId: String, sessionId: String) {
                acceptedRequestId = requestId
                acceptedSessionId = sessionId
            }
        }
        AgentClient.parseAndDispatchV2Event(payload, listener)
        assertEquals("req-1", acceptedRequestId)
        assertEquals("sess-1", acceptedSessionId)
    }

    @Test
    fun parseProgressEvent() {
        val payload = """{"type":"progress","payload":{"stage":"resolution"}}"""
        var reportedStage = ""
        val listener = object : AgentClient.V2StreamListener {
            override fun onProgress(stage: String) {
                reportedStage = stage
            }
        }
        AgentClient.parseAndDispatchV2Event(payload, listener)
        assertEquals("resolution", reportedStage)
    }

    @Test
    fun parsePresentationEvent() {
        val payload = """{
            "type":"presentation",
            "payload":{
                "speech_text":"Đang mở Plex",
                "cards":[{"title":"Mai","year":2024}],
                "quick_chips":["Xem thêm"],
                "mood":"excited"
            }
        }"""
        var receivedPresentation: PresentationResponse? = null
        val listener = object : AgentClient.V2StreamListener {
            override fun onPresentation(presentation: PresentationResponse) {
                receivedPresentation = presentation
            }
        }
        AgentClient.parseAndDispatchV2Event(payload, listener)
        assertNotNull(receivedPresentation)
        assertEquals("Đang mở Plex", receivedPresentation?.speechText)
        assertEquals(1, receivedPresentation?.cards?.size)
        assertEquals("Mai", receivedPresentation?.cards?.get(0)?.get("title"))
        assertEquals("excited", receivedPresentation?.mood)
    }

    @Test
    fun parseClientActionEvent() {
        val payload = """{
            "type":"client_action",
            "payload":{
                "step_id":"step-plex-1",
                "action_type":"plex.open",
                "parameters":{"uri":"plex://movie/123"}
            }
        }"""
        var receivedAction: Map<String, Any?>? = null
        val listener = object : AgentClient.V2StreamListener {
            override fun onClientAction(action: Map<String, Any?>) {
                receivedAction = action
            }
        }
        AgentClient.parseAndDispatchV2Event(payload, listener)
        assertNotNull(receivedAction)
        assertEquals("step-plex-1", receivedAction?.get("step_id"))
        assertEquals("plex.open", receivedAction?.get("action_type"))
    }

    @Test
    fun parseCompletedEvent() {
        val payload = """{"type":"completed","payload":{"request_id":"req-9","status":"success"}}"""
        var completedId = ""
        var completedStatus = ""
        val listener = object : AgentClient.V2StreamListener {
            override fun onCompleted(requestId: String, status: String) {
                completedId = requestId
                completedStatus = status
            }
        }
        AgentClient.parseAndDispatchV2Event(payload, listener)
        assertEquals("req-9", completedId)
        assertEquals("success", completedStatus)
    }

    @Test
    fun parseErrorEvent() {
        val payload = """{"type":"error","payload":{"code":"timeout","message":"NAS busy"}}"""
        var errCode = ""
        var errMsg = ""
        val listener = object : AgentClient.V2StreamListener {
            override fun onError(code: String, message: String) {
                errCode = code
                errMsg = message
            }
        }
        AgentClient.parseAndDispatchV2Event(payload, listener)
        assertEquals("timeout", errCode)
        assertEquals("NAS busy", errMsg)
    }

    @Test
    fun actionExecutorNullContext() {
        val result = AgentActionExecutor.execute(
            null,
            "step-x",
            "plex.open",
            emptyMap()
        )
        assertEquals(ActionStatus.FAILED, result.status)
        assertEquals("NO_CONTEXT", result.errorCode)
    }

    @Test
    fun actionExecutorUnsupportedAction() {
        val result = AgentActionExecutor.execute(
            null,
            "step-x",
            "unknown.action",
            emptyMap()
        )
        assertEquals(ActionStatus.FAILED, result.status)
        assertEquals("NO_CONTEXT", result.errorCode)
    }
}
