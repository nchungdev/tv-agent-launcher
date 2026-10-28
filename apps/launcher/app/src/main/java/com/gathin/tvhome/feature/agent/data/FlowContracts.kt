package com.gathin.tvhome.feature.agent.data

import org.json.JSONArray
import org.json.JSONObject

data class FlowInput(
    val text: String,
    val context: FlowInputContext = FlowInputContext()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("text", text)
        if (context.title.isNotBlank() || context.sources.isNotEmpty()) {
            put("context", JSONObject().apply {
                if (context.title.isNotBlank()) put("title", context.title)
                if (context.sources.isNotEmpty()) put("sources", JSONArray(context.sources))
            })
        }
    }
}

data class FlowInputContext(
    val title: String = "",
    val sources: List<String> = emptyList()
)

data class FlowUnderstanding(
    val domain: String = "",
    val intent: String = "",
    val query: String = "",
    val entities: Map<String, String> = emptyMap(),
    val needsInput: Boolean = false,
    val question: String = "",
    val method: String = "",
    val sources: List<String> = emptyList()
)

data class FlowTask(
    val id: String = "",
    val skill: String = "",
    val source: String = "",
    val query: String = "",
    val strategy: String = ""
)

data class FlowPlan(
    val understanding: FlowUnderstanding = FlowUnderstanding(),
    val tasks: List<FlowTask> = emptyList()
)

data class FlowAction(
    val type: String = "",
    val label: String = "",
    val url: String = "",
    val sourceId: String = "",
    val itemId: String = ""
)

data class FlowItem(
    val id: String = "",
    val kind: String = "",
    val title: String = "",
    val url: String = "",
    val content: String = "",
    val poster: String = "",
    val year: Int = 0,
    val season: Int = 0,
    val episode: Int = 0,
    val actions: List<FlowAction> = emptyList()
)

data class FlowResult(
    val taskId: String = "",
    val source: String = "",
    val status: String = "",
    val errorCode: String = "",
    val cache: String = "",
    val items: List<FlowItem> = emptyList()
)

data class FlowEvent(
    val version: Int = 1,
    val requestId: String = "",
    val sequence: Int = 0,
    val type: String = "",
    val status: String = "",
    val plan: FlowPlan? = null,
    val task: FlowTask? = null,
    val result: FlowResult? = null
)

object FlowParser {
    fun parseEvent(json: JSONObject): FlowEvent {
        val version = json.optInt("version", 1)
        val requestId = json.optString("request_id", "")
        val sequence = json.optInt("sequence", 0)
        val type = json.optString("type", "")
        val status = json.optString("status", "")

        val planObj = json.optJSONObject("plan")
        val plan = planObj?.let { parsePlan(it) }

        val taskObj = json.optJSONObject("task")
        val task = taskObj?.let { parseTask(it) }

        val resultObj = json.optJSONObject("result")
        val result = resultObj?.let { parseResult(it) }

        return FlowEvent(version, requestId, sequence, type, status, plan, task, result)
    }

    fun parsePlan(json: JSONObject): FlowPlan {
        val uObj = json.optJSONObject("understanding")
        val understanding = if (uObj != null) {
            val entitiesObj = uObj.optJSONObject("entities")
            val entities = HashMap<String, String>()
            if (entitiesObj != null) {
                for (key in entitiesObj.keys()) {
                    entities[key] = entitiesObj.optString(key, "")
                }
            }
            val sourcesArray = uObj.optJSONArray("sources")
            val sources = ArrayList<String>()
            if (sourcesArray != null) {
                for (i in 0 until sourcesArray.length()) {
                    sources.add(sourcesArray.optString(i))
                }
            }
            FlowUnderstanding(
                domain = uObj.optString("domain", ""),
                intent = uObj.optString("intent", ""),
                query = uObj.optString("query", ""),
                entities = entities,
                needsInput = uObj.optBoolean("needs_input", false),
                question = uObj.optString("question", ""),
                method = uObj.optString("method", ""),
                sources = sources
            )
        } else FlowUnderstanding()

        val tasksArray = json.optJSONArray("tasks")
        val tasks = ArrayList<FlowTask>()
        if (tasksArray != null) {
            for (i in 0 until tasksArray.length()) {
                tasksArray.optJSONObject(i)?.let { tasks.add(parseTask(it)) }
            }
        }
        return FlowPlan(understanding, tasks)
    }

    fun parseTask(json: JSONObject): FlowTask = FlowTask(
        id = json.optString("id", ""),
        skill = json.optString("skill", ""),
        source = json.optString("source", ""),
        query = json.optString("query", ""),
        strategy = json.optString("strategy", "")
    )

    fun parseResult(json: JSONObject): FlowResult {
        val itemsArray = json.optJSONArray("items")
        val items = ArrayList<FlowItem>()
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                itemsArray.optJSONObject(i)?.let { items.add(parseItem(it)) }
            }
        }
        return FlowResult(
            taskId = json.optString("task_id", ""),
            source = json.optString("source", ""),
            status = json.optString("status", ""),
            errorCode = json.optString("error_code", ""),
            cache = json.optString("cache", ""),
            items = items
        )
    }

    fun parseItem(json: JSONObject): FlowItem {
        val actionsArray = json.optJSONArray("actions")
        val actions = ArrayList<FlowAction>()
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                actionsArray.optJSONObject(i)?.let { a ->
                    actions.add(FlowAction(
                        type = a.optString("type", ""),
                        label = a.optString("label", ""),
                        url = a.optString("url", ""),
                        sourceId = a.optString("source_id", ""),
                        itemId = a.optString("item_id", "")
                    ))
                }
            }
        }
        return FlowItem(
            id = json.optString("id", ""),
            kind = json.optString("kind", ""),
            title = json.optString("title", ""),
            url = json.optString("url", ""),
            content = json.optString("content", ""),
            poster = json.optString("poster", ""),
            year = json.optInt("year"),
            season = json.optInt("season"),
            episode = json.optInt("episode"),
            actions = actions
        )
    }
}
