package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.data.ActionPlan
import com.gathin.tvhome.feature.agent.data.ActionStatus
import com.gathin.tvhome.feature.agent.data.ActionStep
import com.gathin.tvhome.feature.agent.data.ContextSnapshot
import com.gathin.tvhome.feature.agent.data.ExecutionResult
import com.gathin.tvhome.feature.agent.data.InputEvent
import com.gathin.tvhome.feature.agent.data.Intent
import com.gathin.tvhome.feature.agent.data.PresentationResponse
import com.gathin.tvhome.feature.agent.data.ProactiveChip
import com.gathin.tvhome.feature.agent.data.RequestContext
import com.gathin.tvhome.feature.agent.data.Suggestion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentContractsTest {

    @Test
    fun actionStatusEnumMapping() {
        assertEquals("accepted", ActionStatus.ACCEPTED.value)
        assertEquals("running", ActionStatus.RUNNING.value)
        assertEquals("needs_input", ActionStatus.NEEDS_INPUT.value)
        assertEquals("succeeded", ActionStatus.SUCCEEDED.value)
        assertEquals("failed", ActionStatus.FAILED.value)
        assertEquals("cancelled", ActionStatus.CANCELLED.value)
        assertEquals("unknown", ActionStatus.UNKNOWN.value)
        assertEquals(ActionStatus.SUCCEEDED, ActionStatus.fromValue("succeeded"))
        assertEquals(ActionStatus.UNKNOWN, ActionStatus.fromValue("invalid"))
        assertEquals(ActionStatus.UNKNOWN, ActionStatus.fromValue(null))
    }

    @Test
    fun inputEventRoundtrip() {
        val defaultEvent = InputEvent()
        assertEquals("", defaultEvent.requestId)
        assertEquals("vi-VN", defaultEvent.locale)
        assertEquals("voice", defaultEvent.inputType)

        val customEvent = InputEvent(
            requestId = "req-1",
            sessionId = "sess-1",
            utteranceId = "utt-1",
            text = "mở quạt",
            alternatives = listOf("bật quạt"),
            locale = "vi-VN",
            inputType = "voice"
        )
        val serializedJson = customEvent.toJson()
        val deserializedEvent = InputEvent.fromJson(serializedJson)
        assertEquals("req-1", deserializedEvent.requestId)
        assertEquals("sess-1", deserializedEvent.sessionId)
        assertEquals("mở quạt", deserializedEvent.text)
        assertEquals(1, deserializedEvent.alternatives.size)
        assertEquals("bật quạt", deserializedEvent.alternatives[0])
    }

    @Test
    fun requestContextRoundtrip() {
        val defaultContext = RequestContext()
        assertFalse(defaultContext.isAuthenticated)
        assertNull(defaultContext.viewerProfile)

        val customContext = RequestContext(
            clientId = "tv-living-room",
            isAuthenticated = true,
            deadlineEpochMs = 1729850000000L,
            clientCapabilities = listOf("voice", "cards"),
            viewerProfile = "husband"
        )
        val serializedJson = customContext.toJson()
        val deserializedContext = RequestContext.fromJson(serializedJson)
        assertEquals("tv-living-room", deserializedContext.clientId)
        assertTrue(deserializedContext.isAuthenticated)
        assertEquals(1729850000000L, deserializedContext.deadlineEpochMs)
        assertEquals("husband", deserializedContext.viewerProfile)
        assertEquals(2, deserializedContext.clientCapabilities.size)
    }

    @Test
    fun intentRoundtrip() {
        val defaultIntent = Intent()
        assertEquals(0.0, defaultIntent.confidence, 0.001)

        val customIntent = Intent(
            name = "turn_on",
            domain = "home_assistant",
            confidence = 0.95,
            slots = mapOf("device" to "fan"),
            explicitSource = "rule",
            missingSlots = emptyList()
        )
        val serializedJson = customIntent.toJson()
        val deserializedIntent = Intent.fromJson(serializedJson)
        assertEquals("turn_on", deserializedIntent.name)
        assertEquals(0.95, deserializedIntent.confidence, 0.001)
        assertEquals("fan", deserializedIntent.slots["device"])
    }

    @Test
    fun actionStepAndPlanRoundtrip() {
        val customStep = ActionStep(
            stepId = "step-101",
            actionType = "call_service",
            targetId = "switch.turn_on",
            parameters = mapOf("entity_id" to "switch.fan"),
            runsOn = "backend",
            timeoutMs = 4000L,
            isIdempotent = true
        )
        val customPlan = ActionPlan(
            planId = "plan-202",
            intentName = "turn_on_fan",
            steps = listOf(customStep),
            confirmationRequired = false,
            evidence = mapOf("reason" to "user voice command")
        )
        val serializedJson = customPlan.toJson()
        val deserializedPlan = ActionPlan.fromJson(serializedJson)
        assertEquals("plan-202", deserializedPlan.planId)
        assertEquals("turn_on_fan", deserializedPlan.intentName)
        assertEquals(1, deserializedPlan.steps.size)
        assertEquals("step-101", deserializedPlan.steps[0].stepId)
        assertTrue(deserializedPlan.steps[0].isIdempotent)
    }

    @Test
    fun executionResultRoundtrip() {
        val customResult = ExecutionResult(
            stepId = "step-101",
            status = ActionStatus.SUCCEEDED,
            errorCode = null,
            detail = "Fan switched on",
            actualState = mapOf("state" to "on"),
            canRetry = false
        )
        val serializedJson = customResult.toJson()
        val deserializedResult = ExecutionResult.fromJson(serializedJson)
        assertEquals("step-101", deserializedResult.stepId)
        assertEquals(ActionStatus.SUCCEEDED, deserializedResult.status)
        assertNull(deserializedResult.errorCode)
        assertEquals("Fan switched on", deserializedResult.detail)
        assertEquals("on", deserializedResult.actualState["state"])
        assertFalse(deserializedResult.canRetry)
    }

    @Test
    fun presentationResponseRoundtrip() {
        val customResponse = PresentationResponse(
            speechText = "Đã bật quạt",
            cards = listOf(mapOf("title" to "Quạt phòng khách")),
            quickChips = listOf("Tắt quạt", "Hẹn giờ"),
            clientActions = emptyList(),
            mood = "calm"
        )
        val serializedJson = customResponse.toJson()
        val deserializedResponse = PresentationResponse.fromJson(serializedJson)
        assertEquals("Đã bật quạt", deserializedResponse.speechText)
        assertEquals(1, deserializedResponse.cards.size)
        assertEquals("Quạt phòng khách", deserializedResponse.cards[0]["title"])
        assertEquals(2, deserializedResponse.quickChips.size)
        assertEquals("calm", deserializedResponse.mood)
    }

    @Test
    fun suggestionRoundtrip() {
        val customAction = ActionStep(
            stepId = "step-play",
            actionType = "play_media",
            targetId = "plex.play",
            parameters = mapOf("media_id" to "12345"),
            runsOn = "client",
            timeoutMs = 5000L,
            isIdempotent = true
        )
        val customSuggestion = Suggestion(
            id = "sug-101",
            kind = "media_playback",
            title = "Xem tiếp tập mới",
            message = "Tập mới đã sẵn sàng",
            targetId = "plex.tv",
            action = customAction,
            evidence = mapOf("source" to "radarr"),
            generatedAt = 1729850100000L,
            expiresAt = 1729853700000L,
            dedupeKey = "media_breaking_bad_s05e16"
        )
        val serializedJson = customSuggestion.toJson()
        val deserializedSuggestion = Suggestion.fromJson(serializedJson)
        assertEquals("sug-101", deserializedSuggestion.id)
        assertEquals("media_playback", deserializedSuggestion.kind)
        assertEquals("Xem tiếp tập mới", deserializedSuggestion.title)
        assertEquals("Tập mới đã sẵn sàng", deserializedSuggestion.message)
        assertEquals("plex.tv", deserializedSuggestion.targetId)
        assertEquals("plex.play", deserializedSuggestion.action.targetId)
        assertEquals("12345", deserializedSuggestion.action.parameters["media_id"])
        assertEquals("radarr", deserializedSuggestion.evidence["source"])
        assertEquals(1729850100000L, deserializedSuggestion.generatedAt)
        assertEquals(1729853700000L, deserializedSuggestion.expiresAt)
        assertEquals("media_breaking_bad_s05e16", deserializedSuggestion.dedupeKey)
    }

    @Test
    fun contextSnapshotRoundtrip() {
        val customSnapshot = ContextSnapshot(
            timestampEpochMs = 1729850000000L,
            timeOfDay = "evening",
            viewerProfile = "husband",
            environment = mapOf("temperature" to 26.5),
            recentMedia = listOf(mapOf("title" to "Breaking Bad"))
        )
        val serializedJson = customSnapshot.toJson()
        val deserializedSnapshot = ContextSnapshot.fromJson(serializedJson)
        assertEquals(1729850000000L, deserializedSnapshot.timestampEpochMs)
        assertEquals("evening", deserializedSnapshot.timeOfDay)
        assertEquals("husband", deserializedSnapshot.viewerProfile)
        assertEquals(26.5, deserializedSnapshot.environment["temperature"])
        assertEquals(1, deserializedSnapshot.recentMedia.size)
        assertEquals("Breaking Bad", deserializedSnapshot.recentMedia[0]["title"])
    }

    @Test
    fun proactiveChipRoundtrip() {
        val customChip = ProactiveChip(
            chipId = "chip-coffee",
            label = "Pha cà phê sáng",
            iconKey = "coffee",
            actionPayload = mapOf("entity_id" to "switch.coffee_maker")
        )
        val serializedJson = customChip.toJson()
        val deserializedChip = ProactiveChip.fromJson(serializedJson)
        assertEquals("chip-coffee", deserializedChip.chipId)
        assertEquals("Pha cà phê sáng", deserializedChip.label)
        assertEquals("coffee", deserializedChip.iconKey)
        assertEquals("switch.coffee_maker", deserializedChip.actionPayload["entity_id"])
    }

    private fun loadFixture(fixtureName: String): String {
        val stream = javaClass.getResourceAsStream("/fixtures/contracts/$fixtureName")
            ?: error("Fixture not found: $fixtureName")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun parseInputEventFixtures() {
        val validJson = JSONObject(loadFixture("input_event_valid.json"))
        val validEvent = InputEvent.fromJson(validJson)
        assertEquals("req-1001", validEvent.requestId)
        assertEquals("sess-2001", validEvent.sessionId)
        assertEquals("utt-3001", validEvent.utteranceId)
        assertEquals("bật quạt phòng khách", validEvent.text)
        assertEquals(2, validEvent.alternatives.size)
        assertEquals("vi-VN", validEvent.locale)
        assertEquals("voice", validEvent.inputType)

        val invalidJson = JSONObject(loadFixture("input_event_invalid.json"))
        val invalidEvent = InputEvent.fromJson(invalidJson)
        assertEquals("", invalidEvent.requestId)
        assertEquals("", invalidEvent.text)
    }

    @Test
    fun parseIntentFixture() {
        val json = JSONObject(loadFixture("intent_valid.json"))
        val intent = Intent.fromJson(json)
        assertEquals("turn_on", intent.name)
        assertEquals("home_assistant", intent.domain)
        assertEquals(0.95, intent.confidence, 0.001)
        assertEquals("fan", intent.slots["device"])
        assertEquals("living_room", intent.slots["room"])
        assertEquals("voice_rule", intent.explicitSource)
    }

    @Test
    fun parseActionPlanFixture() {
        val json = JSONObject(loadFixture("action_plan_valid.json"))
        val plan = ActionPlan.fromJson(json)
        assertEquals("plan-101", plan.planId)
        assertEquals("turn_on", plan.intentName)
        assertEquals(1, plan.steps.size)
        val step = plan.steps[0]
        assertEquals("step-1", step.stepId)
        assertEquals("service_call", step.actionType)
        assertEquals("fan.living_room", step.targetId)
        assertEquals("backend", step.runsOn)
        assertEquals(3000, step.timeoutMs)
        assertTrue(step.isIdempotent)
        assertFalse(plan.confirmationRequired)
    }

    @Test
    fun parsePresentationResponseFixture() {
        val json = JSONObject(loadFixture("presentation_response_valid.json"))
        val response = PresentationResponse.fromJson(json)
        assertEquals("Đã bật quạt phòng khách", response.speechText)
        assertEquals(1, response.cards.size)
        assertEquals(2, response.quickChips.size)
        assertEquals("Tắt quạt", response.quickChips[0])
        assertEquals("Hẹn giờ 30 phút", response.quickChips[1])
        assertEquals(1, response.clientActions.size)
        assertEquals("friendly", response.mood)
    }

    @Test
    fun parseSuggestionFixture() {
        val json = JSONObject(loadFixture("suggestion_valid.json"))
        val suggestion = Suggestion.fromJson(json)
        assertEquals("sug-901", suggestion.id)
        assertEquals("turn_off_reminder", suggestion.kind)
        assertEquals("Tắt quạt", suggestion.title)
        assertEquals("step-sug-1", suggestion.action.stepId)
        assertEquals("fan.living_room", suggestion.action.targetId)
        assertEquals(1729850000000L, suggestion.generatedAt)
        assertEquals(1729853600000L, suggestion.expiresAt)
        assertEquals("sug-living-room-fan-empty", suggestion.dedupeKey)
    }

    @Test
    fun parseContextSnapshotFixture() {
        val json = JSONObject(loadFixture("context_snapshot_valid.json"))
        val snapshot = ContextSnapshot.fromJson(json)
        assertEquals(1729850000000L, snapshot.timestampEpochMs)
        assertEquals("evening", snapshot.timeOfDay)
        assertEquals("husband", snapshot.viewerProfile)
        val temp = (snapshot.environment["temperature"] as? Number)?.toDouble() ?: 0.0
        assertEquals(27.5, temp, 0.001)
        assertEquals(1, snapshot.recentMedia.size)
    }

    @Test
    fun parseProactiveChipFixture() {
        val json = JSONObject(loadFixture("proactive_chip_valid.json"))
        val chip = ProactiveChip.fromJson(json)
        assertEquals("chip-quick-1", chip.chipId)
        assertEquals("Tắt đèn phòng khách", chip.label)
        assertEquals("lightbulb", chip.iconKey)
        assertEquals("light.living_room", chip.actionPayload["entity_id"])
    }
}
