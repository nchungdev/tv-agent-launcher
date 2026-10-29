package com.gathin.tvhome.core.util

import java.util.concurrent.Executors

class LatestWorkQueue(private val capacity: Int = 32) : java.io.Closeable {
    private val pending = LinkedHashMap<String, () -> Unit>()
    private val worker = Executors.newSingleThreadExecutor()
    private var draining = false

    @Synchronized
    fun submit(key: String, task: () -> Unit) {
        pending.remove(key)
        if (pending.size >= capacity) pending.remove(pending.keys.first())
        pending[key] = task
        if (draining) return
        draining = true
        worker.execute { drain() }
    }

    @Synchronized
    override fun close() {
        pending.clear()
        worker.shutdownNow()
    }

    private fun drain() {
        while (true) {
            val task = synchronized(this) {
                if (pending.isEmpty()) {
                    draining = false
                    return
                }
                pending.remove(pending.keys.first())!!
            }
            runCatching { task() }
        }
    }
}
