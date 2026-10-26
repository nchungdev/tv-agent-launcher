package com.gathin.tvhome.feature.agent.data

import org.json.JSONArray
import org.json.JSONObject


enum class ActionStatus(val value: String) {
    ACCEPTED("accepted"),
    RUNNING("running"),
    NEEDS_INPUT("needs_input"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    UNKNOWN("unknown");

    companion object {
        fun fromValue(rawValue: String?): ActionStatus {
            if (rawValue == null) return UNKNOWN
            val normalized = rawValue.trim().lowercase()
            return values().firstOrNull { it.value == normalized } ?: UNKNOWN
        }
    }
}

object AgentContractJsonHelper {

    fun mapToJsonObject(mapData: Map<String, Any?>): JSONObject {
        val resultJson = JSONObject()
        for ((mapKey, mapValue) in mapData) {
            resultJson.put(mapKey, mapValue ?: JSONObject.NULL)
        }
        return resultJson
    }

    fun jsonObjectToMap(payload: JSONObject?): Map<String, Any?> {
        if (payload == null) return emptyMap()
        val resultMap = mutableMapOf<String, Any?>()
        val keyIterator = payload.keys()
        while (keyIterator.hasNext()) {
            val currentKey = keyIterator.next()
            val currentValue = payload.opt(currentKey)
            resultMap[currentKey] = if (currentValue == JSONObject.NULL) null else currentValue
        }
        return resultMap
    }

    fun jsonArrayToStringList(payload: JSONArray?): List<String> {
        if (payload == null) return emptyList()
        val stringList = mutableListOf<String>()
        for (itemIndex in 0 until payload.length()) {
            stringList.add(payload.optString(itemIndex))
        }
        return stringList
    }

    fun jsonArrayToMapList(payload: JSONArray?): List<Map<String, Any?>> {
        if (payload == null) return emptyList()
        val mapList = mutableListOf<Map<String, Any?>>()
        for (itemIndex in 0 until payload.length()) {
            val itemObject = payload.optJSONObject(itemIndex)
            if (itemObject != null) {
                mapList.add(jsonObjectToMap(itemObject))
            }
        }
        return mapList
    }

    fun stringListToJsonArray(strings: List<String>): JSONArray {
        val jsonArray = JSONArray()
        for (stringValue in strings) {
            jsonArray.put(stringValue)
        }
        return jsonArray
    }

    fun mapListToJsonArray(maps: List<Map<String, Any?>>): JSONArray {
        val jsonArray = JSONArray()
        for (mapItem in maps) {
            jsonArray.put(mapToJsonObject(mapItem))
        }
        return jsonArray
    }
}

data class InputEvent(
    val requestId: String = "",
    val sessionId: String = "",
    val utteranceId: String = "",
    val text: String = "",
    val alternatives: List<String> = emptyList(),
    val locale: String = "vi-VN",
    val inputType: String = "voice"
) {
    val request_id: String get() = requestId
    val session_id: String get() = sessionId
    val utterance_id: String get() = utteranceId
    val input_type: String get() = inputType

    fun toJson(): JSONObject {
        val payload = JSONObject()
        payload.put("request_id", requestId)
        payload.put("session_id", sessionId)
        payload.put("utterance_id", utteranceId)
        payload.put("text", text)
        payload.put("alternatives", AgentContractJsonHelper.stringListToJsonArray(alternatives))
        payload.put("locale", locale)
        payload.put("input_type", inputType)
        return payload
    }

    companion object {
        fun fromJson(payload: JSONObject?): InputEvent {
            if (payload == null) return InputEvent()
            val requestIdentifier = payload.optString(
                "request_id",
                payload.optString("requestId", "")
            )
            val sessionIdentifier = payload.optString(
                "session_id",
                payload.optString("sessionId", "")
            )
            val utteranceIdentifier = payload.optString(
                "utterance_id",
                payload.optString("utteranceId", "")
            )
            val eventText = payload.optString("text", "")
            val rawAlternatives = payload.optJSONArray("alternatives")
            val alternativeList = AgentContractJsonHelper.jsonArrayToStringList(rawAlternatives)
            val eventLocale = payload.optString("locale", "vi-VN")
            val typeOfInput = payload.optString(
                "input_type",
                payload.optString("inputType", "voice")
            )
            return InputEvent(
                requestId = requestIdentifier,
                sessionId = sessionIdentifier,
                utteranceId = utteranceIdentifier,
                text = eventText,
                alternatives = alternativeList,
                locale = eventLocale,
                inputType = typeOfInput
            )
        }
    }
}

data class RequestContext(
    val clientId: String = "",
    val isAuthenticated: Boolean = false,
    val deadlineEpochMs: Long = 0L,
    val clientCapabilities: List<String> = emptyList(),
    val viewerProfile: String? = null
) {
    val client_id: String get() = clientId
    val is_authenticated: Boolean get() = isAuthenticated
    val deadline_epoch_ms: Long get() = deadlineEpochMs
    val client_capabilities: List<String> get() = clientCapabilities
    val viewer_profile: String? get() = viewerProfile

    fun toJson(): JSONObject {
        val payload = JSONObject()
        payload.put("client_id", clientId)
        payload.put("is_authenticated", isAuthenticated)
        payload.put("deadline_epoch_ms", deadlineEpochMs)
        val capabilitiesArray = AgentContractJsonHelper.stringListToJsonArray(clientCapabilities)
        payload.put("client_capabilities", capabilitiesArray)
        if (viewerProfile != null) {
            payload.put("viewer_profile", viewerProfile)
        }
        return payload
    }

    companion object {
        fun fromJson(payload: JSONObject?): RequestContext {
            if (payload == null) return RequestContext()
            val clientIdentifier = payload.optString(
                "client_id",
                payload.optString("clientId", "")
            )
            val authenticated = if (payload.has("is_authenticated")) {
                payload.optBoolean("is_authenticated", false)
            } else {
                payload.optBoolean("isAuthenticated", false)
            }
            val deadline = if (payload.has("deadline_epoch_ms")) {
                payload.optLong("deadline_epoch_ms", 0L)
            } else {
                payload.optLong("deadlineEpochMs", 0L)
            }
            val rawCapabilities = payload.optJSONArray("client_capabilities")
                ?: payload.optJSONArray("clientCapabilities")
            val capabilities = AgentContractJsonHelper.jsonArrayToStringList(rawCapabilities)
            val profile = if (payload.has("viewer_profile")) {
                payload.optString("viewer_profile")
            } else if (payload.has("viewerProfile")) {
                payload.optString("viewerProfile")
            } else {
                null
            }
            return RequestContext(
                clientId = clientIdentifier,
                isAuthenticated = authenticated,
                deadlineEpochMs = deadline,
                clientCapabilities = capabilities,
                viewerProfile = profile
            )
        }
    }
}

data class Intent(
    val name: String = "",
    val domain: String = "",
    val confidence: Double = 0.0,
    val slots: Map<String, Any?> = emptyMap(),
    val explicitSource: String = "",
    val missingSlots: List<String> = emptyList()
) {
    val explicit_source: String get() = explicitSource
    val missing_slots: List<String> get() = missingSlots

    fun toJson(): JSONObject {
        val payload = JSONObject()
        payload.put("name", name)
        payload.put("domain", domain)
        payload.put("confidence", confidence)
        payload.put("slots", AgentContractJsonHelper.mapToJsonObject(slots))
        payload.put("explicit_source", explicitSource)
        payload.put("missing_slots", AgentContractJsonHelper.stringListToJsonArray(missingSlots))
        return payload
    }

    companion object {
        fun fromJson(payload: JSONObject?): Intent {
            if (payload == null) return Intent()
            val intentName = payload.optString("name", "")
            val intentDomain = payload.optString("domain", "")
            val intentConfidence = payload.optDouble("confidence", 0.0)
            val rawSlots = payload.optJSONObject("slots")
            val parsedSlots = AgentContractJsonHelper.jsonObjectToMap(rawSlots)
            val source = payload.optString(
                "explicit_source",
                payload.optString("explicitSource", "")
            )
            val rawMissing = payload.optJSONArray("missing_slots")
                ?: payload.optJSONArray("missingSlots")
            val missingList = AgentContractJsonHelper.jsonArrayToStringList(rawMissing)
            return Intent(
                name = intentName,
                domain = intentDomain,
                confidence = intentConfidence,
                slots = parsedSlots,
                explicitSource = source,
                missingSlots = missingList
            )
        }
    }
}

data class ActionStep(
    val stepId: String = "",
    val actionType: String = "",
    val targetId: String = "",
    val parameters: Map<String, Any?> = emptyMap(),
    val runsOn: String = "backend",
    val timeoutMs: Long = 5000L,
    val isIdempotent: Boolean = false
) {
    val step_id: String get() = stepId
    val action_type: String get() = actionType
    val target_id: String get() = targetId
    val runs_on: String get() = runsOn
    val timeout_ms: Long get() = timeoutMs
    val is_idempotent: Boolean get() = isIdempotent

    fun toJson(): JSONObject {
        val payload = JSONObject()
        payload.put("step_id", stepId)
        payload.put("action_type", actionType)
        payload.put("target_id", targetId)
        payload.put("parameters", AgentContractJsonHelper.mapToJsonObject(parameters))
        payload.put("runs_on", runsOn)
        payload.put("timeout_ms", timeoutMs)
        payload.put("is_idempotent", isIdempotent)
        return payload
    }

    companion object {
        fun fromJson(payload: JSONObject?): ActionStep {
            if (payload == null) return ActionStep()
            val stepIdentifier = payload.optString(
                "step_id",
                payload.optString("stepId", "")
            )
            val actionName = payload.optString(
                "action_type",
                payload.optString("actionType", "")
            )
            val targetIdentifier = payload.optString(
                "target_id",
                payload.optString("targetId", "")
            )
            val rawParams = payload.optJSONObject("parameters")
            val parsedParams = AgentContractJsonHelper.jsonObjectToMap(rawParams)
            val targetRunner = payload.optString(
                "runs_on",
                payload.optString("runsOn", "backend")
            )
            val timeoutValue = if (payload.has("timeout_ms")) {
                payload.optLong("timeout_ms", 5000L)
            } else {
                payload.optLong("timeoutMs", 5000L)
            }
            val idempotentFlag = if (payload.has("is_idempotent")) {
                payload.optBoolean("is_idempotent", false)
            } else {
                payload.optBoolean("isIdempotent", false)
            }
            return ActionStep(
                stepId = stepIdentifier,
                actionType = actionName,
                targetId = targetIdentifier,
                parameters = parsedParams,
                runsOn = targetRunner,
                timeoutMs = timeoutValue,
                isIdempotent = idempotentFlag
            )
        }
    }
}

data class ActionPlan(
    val planId: String = "",
    val intentName: String = "",
    val steps: List<ActionStep> = emptyList(),
    val confirmationRequired: Boolean = false,
    val evidence: Map<String, Any?> = emptyMap()
) {
    val plan_id: String get() = planId
    val intent_name: String get() = intentName
    val confirmation_required: Boolean get() = confirmationRequired

    fun toJson(): JSONObject {
        val payload = JSONObject()
        payload.put("plan_id", planId)
        payload.put("intent_name", intentName)
        val stepsArray = JSONArray()
        for (stepItem in steps) {
            stepsArray.put(stepItem.toJson())
        }
        payload.put("steps", stepsArray)
        payload.put("confirmation_required", confirmationRequired)
        payload.put("evidence", AgentContractJsonHelper.mapToJsonObject(evidence))
        return payload
    }

    companion object {
        fun fromJson(payload: JSONObject?): ActionPlan {
            if (payload == null) return ActionPlan()
            val planIdentifier = payload.optString(
                "plan_id",
                payload.optString("planId", "")
            )
            val targetIntent = payload.optString(
                "intent_name",
                payload.optString("intentName", "")
            )
            val rawSteps = payload.optJSONArray("steps")
            val parsedSteps = mutableListOf<ActionStep>()
            if (rawSteps != null) {
                for (stepIndex in 0 until rawSteps.length()) {
                    val stepObject = rawSteps.optJSONObject(stepIndex)
                    if (stepObject != null) {
                        parsedSteps.add(ActionStep.fromJson(stepObject))
                    }
                }
            }
            val confirm = if (payload.has("confirmation_required")) {
                payload.optBoolean("confirmation_required", false)
            } else {
                payload.optBoolean("confirmationRequired", false)
            }
            val rawEvidence = payload.optJSONObject("evidence")
            val parsedEvidence = AgentContractJsonHelper.jsonObjectToMap(rawEvidence)
            return ActionPlan(
                planId = planIdentifier,
                intentName = targetIntent,
                steps = parsedSteps,
                confirmationRequired = confirm,
                evidence = parsedEvidence
            )
        }
    }
}

data class ExecutionResult(
    val stepId: String = "",
    val status: ActionStatus = ActionStatus.UNKNOWN,
    val errorCode: String? = null,
    val detail: String? = null,
    val actualState: Map<String, Any?> = emptyMap(),
    val canRetry: Boolean = false
) {
    val step_id: String get() = stepId
    val error_code: String? get() = errorCode
    val actual_state: Map<String, Any?> get() = actualState
    val can_retry: Boolean get() = canRetry

    fun toJson(): JSONObject {
        val payload = JSONObject()
        payload.put("step_id", stepId)
        payload.put("status", status.value)
        if (errorCode != null) {
            payload.put("error_code", errorCode)
        }
        if (detail != null) {
            payload.put("detail", detail)
        }
        payload.put("actual_state", AgentContractJsonHelper.mapToJsonObject(actualState))
        payload.put("can_retry", canRetry)
        return payload
    }

    companion object {
        fun fromJson(payload: JSONObject?): ExecutionResult {
            if (payload == null) return ExecutionResult()
            val stepIdentifier = payload.optString(
                "step_id",
                payload.optString("stepId", "")
            )
            val rawStatus = payload.optString("status", "")
            val resolvedStatus = ActionStatus.fromValue(rawStatus)
            val error = if (payload.has("error_code")) {
                payload.optString("error_code")
            } else if (payload.has("errorCode")) {
                payload.optString("errorCode")
            } else {
                null
            }
            val detailMessage = if (payload.has("detail")) {
                payload.optString("detail")
            } else {
                null
            }
            val rawState = payload.optJSONObject("actual_state")
                ?: payload.optJSONObject("actualState")
            val parsedState = AgentContractJsonHelper.jsonObjectToMap(rawState)
            val retryable = if (payload.has("can_retry")) {
                payload.optBoolean("can_retry", false)
            } else {
                payload.optBoolean("canRetry", false)
            }
            return ExecutionResult(
                stepId = stepIdentifier,
                status = resolvedStatus,
                errorCode = error,
                detail = detailMessage,
                actualState = parsedState,
                canRetry = retryable
            )
        }
    }
}

data class PresentationResponse(
    val speechText: String = "",
    val cards: List<Map<String, Any?>> = emptyList(),
    val quickChips: List<String> = emptyList(),
    val clientActions: List<Map<String, Any?>> = emptyList(),
    val mood: String = "neutral"
) {
    val speech_text: String get() = speechText
    val quick_chips: List<String> get() = quickChips
    val client_actions: List<Map<String, Any?>> get() = clientActions

    fun toJson(): JSONObject {
        val payload = JSONObject()
        payload.put("speech_text", speechText)
        payload.put("cards", AgentContractJsonHelper.mapListToJsonArray(cards))
        val chipsArray = AgentContractJsonHelper.stringListToJsonArray(quickChips)
        payload.put("quick_chips", chipsArray)
        val actionsArray = AgentContractJsonHelper.mapListToJsonArray(clientActions)
        payload.put("client_actions", actionsArray)
        payload.put("mood", mood)
        return payload
    }

    companion object {
        fun fromJson(payload: JSONObject?): PresentationResponse {
            if (payload == null) return PresentationResponse()
            val speech = payload.optString(
                "speech_text",
                payload.optString("speechText", "")
            )
            val rawCards = payload.optJSONArray("cards")
            val parsedCards = AgentContractJsonHelper.jsonArrayToMapList(rawCards)
            val rawChips = payload.optJSONArray("quick_chips")
                ?: payload.optJSONArray("quickChips")
            val parsedChips = AgentContractJsonHelper.jsonArrayToStringList(rawChips)
            val rawActions = payload.optJSONArray("client_actions")
                ?: payload.optJSONArray("clientActions")
            val parsedActions = AgentContractJsonHelper.jsonArrayToMapList(rawActions)
            val moodTone = payload.optString("mood", "neutral")
            return PresentationResponse(
                speechText = speech,
                cards = parsedCards,
                quickChips = parsedChips,
                clientActions = parsedActions,
                mood = moodTone
            )
        }
    }
}

data class Suggestion(
    val id: String = "",
    val kind: String = "",
    val title: String = "",
    val message: String = "",
    val targetId: String = "",
    val action: ActionStep = ActionStep(),
    val evidence: Map<String, Any?> = emptyMap(),
    val generatedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val dedupeKey: String = ""
) {
    val target_id: String get() = targetId
    val generated_at: Long get() = generatedAt
    val expires_at: Long get() = expiresAt
    val dedupe_key: String get() = dedupeKey

    fun toJson(): JSONObject {
        val payload = JSONObject()
        payload.put("id", id)
        payload.put("kind", kind)
        payload.put("title", title)
        payload.put("message", message)
        payload.put("target_id", targetId)
        payload.put("action", action.toJson())
        payload.put("evidence", AgentContractJsonHelper.mapToJsonObject(evidence))
        payload.put("generated_at", generatedAt)
        payload.put("expires_at", expiresAt)
        payload.put("dedupe_key", dedupeKey)
        return payload
    }

    companion object {
        fun fromJson(payload: JSONObject?): Suggestion {
            if (payload == null) return Suggestion()
            val identifier = payload.optString("id", "")
            val suggestionKind = payload.optString("kind", "")
            val suggestionTitle = payload.optString("title", "")
            val suggestionMessage = payload.optString("message", "")
            val targetIdentifier = payload.optString(
                "target_id",
                payload.optString("targetId", "")
            )
            val actionObject = payload.optJSONObject("action")
            val resolvedAction = ActionStep.fromJson(actionObject)
            val rawEvidence = payload.optJSONObject("evidence")
            val parsedEvidence = AgentContractJsonHelper.jsonObjectToMap(rawEvidence)
            val generatedTimestamp = if (payload.has("generated_at")) {
                payload.optLong("generated_at", 0L)
            } else {
                payload.optLong("generatedAt", 0L)
            }
            val expirationTimestamp = if (payload.has("expires_at")) {
                payload.optLong("expires_at", 0L)
            } else {
                payload.optLong("expiresAt", 0L)
            }
            val deduplicationKey = payload.optString(
                "dedupe_key",
                payload.optString("dedupeKey", "")
            )
            return Suggestion(
                id = identifier,
                kind = suggestionKind,
                title = suggestionTitle,
                message = suggestionMessage,
                targetId = targetIdentifier,
                action = resolvedAction,
                evidence = parsedEvidence,
                generatedAt = generatedTimestamp,
                expiresAt = expirationTimestamp,
                dedupeKey = deduplicationKey
            )
        }
    }
}

data class ContextSnapshot(
    val timestampEpochMs: Long = 0L,
    val timeOfDay: String = "",
    val viewerProfile: String? = null,
    val environment: Map<String, Any?> = emptyMap(),
    val recentMedia: List<Map<String, Any?>> = emptyList()
) {
    val timestamp_epoch_ms: Long get() = timestampEpochMs
    val time_of_day: String get() = timeOfDay
    val viewer_profile: String? get() = viewerProfile
    val recent_media: List<Map<String, Any?>> get() = recentMedia

    fun toJson(): JSONObject {
        val payload = JSONObject()
        payload.put("timestamp_epoch_ms", timestampEpochMs)
        payload.put("time_of_day", timeOfDay)
        if (viewerProfile != null) {
            payload.put("viewer_profile", viewerProfile)
        }
        payload.put("environment", AgentContractJsonHelper.mapToJsonObject(environment))
        payload.put("recent_media", AgentContractJsonHelper.mapListToJsonArray(recentMedia))
        return payload
    }

    companion object {
        fun fromJson(payload: JSONObject?): ContextSnapshot {
            if (payload == null) return ContextSnapshot()
            val timestamp = if (payload.has("timestamp_epoch_ms")) {
                payload.optLong("timestamp_epoch_ms", 0L)
            } else {
                payload.optLong("timestampEpochMs", 0L)
            }
            val daytime = payload.optString(
                "time_of_day",
                payload.optString("timeOfDay", "")
            )
            val profile = if (payload.has("viewer_profile")) {
                payload.optString("viewer_profile")
            } else if (payload.has("viewerProfile")) {
                payload.optString("viewerProfile")
            } else {
                null
            }
            val rawEnv = payload.optJSONObject("environment")
            val parsedEnv = AgentContractJsonHelper.jsonObjectToMap(rawEnv)
            val rawMedia = payload.optJSONArray("recent_media")
                ?: payload.optJSONArray("recentMedia")
            val parsedMedia = AgentContractJsonHelper.jsonArrayToMapList(rawMedia)
            return ContextSnapshot(
                timestampEpochMs = timestamp,
                timeOfDay = daytime,
                viewerProfile = profile,
                environment = parsedEnv,
                recentMedia = parsedMedia
            )
        }
    }
}

data class ProactiveChip(
    val chipId: String = "",
    val label: String = "",
    val iconKey: String = "",
    val actionPayload: Map<String, Any?> = emptyMap()
) {
    val chip_id: String get() = chipId
    val icon_key: String get() = iconKey
    val action_payload: Map<String, Any?> get() = actionPayload

    fun toJson(): JSONObject {
        val payload = JSONObject()
        payload.put("chip_id", chipId)
        payload.put("label", label)
        payload.put("icon_key", iconKey)
        payload.put("action_payload", AgentContractJsonHelper.mapToJsonObject(actionPayload))
        return payload
    }

    companion object {
        fun fromJson(payload: JSONObject?): ProactiveChip {
            if (payload == null) return ProactiveChip()
            val id = payload.optString(
                "chip_id",
                payload.optString("chipId", "")
            )
            val chipLabel = payload.optString("label", "")
            val icon = payload.optString(
                "icon_key",
                payload.optString("iconKey", "")
            )
            val rawPayload = payload.optJSONObject("action_payload")
                ?: payload.optJSONObject("actionPayload")
            val parsedPayload = AgentContractJsonHelper.jsonObjectToMap(rawPayload)
            return ProactiveChip(
                chipId = id,
                label = chipLabel,
                iconKey = icon,
                actionPayload = parsedPayload
            )
        }
    }
}
