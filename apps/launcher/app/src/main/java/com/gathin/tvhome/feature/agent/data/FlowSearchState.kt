package com.gathin.tvhome.feature.agent.data

class FlowSearchState {
    private var requestId = ""
    private var sequence = 0
    private val tasks = LinkedHashMap<String, FlowResult>()
    var finished = false
        private set

    fun accept(event: FlowEvent): Boolean {
        if (finished || event.version != 1 || event.requestId.isBlank()) return false
        if (requestId.isNotEmpty() && requestId != event.requestId) return false
        if (event.sequence <= sequence) return false
        requestId = event.requestId
        sequence = event.sequence
        event.plan?.tasks?.forEach { task ->
            tasks.putIfAbsent(task.id, FlowResult(task.id, task.source, "running"))
        }
        event.task?.let { task ->
            tasks.putIfAbsent(task.id, FlowResult(task.id, task.source, "running"))
        }
        event.result?.let { tasks[it.taskId] = it }
        if (event.type == "response_completed") finished = true
        return true
    }

    fun results(): List<FlowResult> = tasks.values.toList()
}
