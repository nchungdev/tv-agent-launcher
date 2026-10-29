package com.gathin.tvhome.feature.agent.data

class FlowSearchResults {
    private val results = LinkedHashMap<String, FlowResult>()

    fun update(result: FlowResult) {
        results[result.taskId] = result
    }

    fun cards(source: String) = FlowCards.from(results.values.filter { it.source == source }
        .map { result -> result.copy(items = result.items.filter { it.kind != "answer" }) })
        .filter { it.type != "status" }

    fun answer(): String = results.values.flatMap { it.items }
        .filter { it.kind == "answer" && it.content.isNotBlank() }
        .joinToString("\n") { it.content }
}
