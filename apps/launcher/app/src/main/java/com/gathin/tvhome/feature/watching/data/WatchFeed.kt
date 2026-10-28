package com.gathin.tvhome.feature.watching.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class WatchFeed(
    private val scope: CoroutineScope,
    private val sources: Map<WatchSource, () -> Load>,
    private val filters: () -> Pair<Set<String>, Set<String>>,
    private val changed: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    interface Load {
        fun fetch(): WatchResult
        fun cancel()
    }

    private val snapshots = WatchFeedState()
    private val requests = ArrayList<Load>()
    private var active = false
    private var pending = false
    private var generation = 0
    private var job: Job? = null
    private var hidden: Set<String> = emptySet()
    private var hiddenApps: Set<String> = emptySet()
    var items: List<WatchItem> = emptyList()
        private set

    fun state(source: WatchSource) = snapshots.state(source)
    fun count(source: WatchSource) = snapshots.count(source)

    fun start() {
        if (active) return
        active = true
        refresh()
    }

    fun stop() {
        active = false
        generation++
        pending = false
        requests.forEach { it.cancel() }
        requests.clear()
        job?.cancel()
        job = null
    }

    fun refresh() {
        if (!active) return
        if (job != null) {
            pending = true
            return
        }
        val ticket = ++generation
        job = scope.launch {
            try {
                val selection = withContext(Dispatchers.IO) { filters() }
                if (!accepts(ticket)) return@launch
                hidden = selection.first
                hiddenApps = selection.second
                supervisorScope {
                    for ((source, factory) in sources) launch {
                        val request = factory()
                        requests.add(request)
                        try {
                            val result = withContext(Dispatchers.IO) { request.fetch() }
                            if (accepts(ticket)) {
                                snapshots.update(source, result)
                                publish()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            if (accepts(ticket)) {
                                snapshots.update(source, WatchResult(WatchAvailability.ERROR))
                                publish()
                            }
                        } finally {
                            request.cancel()
                            requests.remove(request)
                        }
                    }
                }
            } finally {
                if (ticket == generation) {
                    job = null
                    if (active && pending) {
                        pending = false
                        refresh()
                    }
                }
            }
        }
    }

    fun hide(keys: Set<String>) {
        hidden = hidden + keys
        publish()
    }

    private fun accepts(ticket: Int) = active && generation == ticket

    private fun publish() {
        items = snapshots.items(now(), hidden, hiddenApps)
        changed()
    }
}
