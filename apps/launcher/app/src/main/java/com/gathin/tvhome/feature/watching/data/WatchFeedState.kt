package com.gathin.tvhome.feature.watching.data

enum class WatchAvailability { READY, EMPTY, PERMISSION_REQUIRED, UNSUPPORTED, ERROR }

data class WatchResult(
    val availability: WatchAvailability,
    val items: List<WatchItem> = emptyList()
) {
    companion object {
        fun loaded(items: List<WatchItem>) = WatchResult(
            if (items.isEmpty()) WatchAvailability.EMPTY else WatchAvailability.READY, items
        )
    }
}

class WatchFeedState {
    private val snapshots = HashMap<WatchSource, List<WatchItem>>()
    private val status = HashMap<WatchSource, WatchAvailability>()

    fun update(source: WatchSource, result: WatchResult) {
        status[source] = result.availability
        if (result.availability == WatchAvailability.PERMISSION_REQUIRED ||
            result.availability == WatchAvailability.UNSUPPORTED) {
            snapshots.remove(source)
        } else if (result.availability != WatchAvailability.ERROR) {
            snapshots[source] = result.items
        }
    }

    fun state(source: WatchSource) = status[source] ?: WatchAvailability.EMPTY

    fun count(source: WatchSource) = snapshots[source]?.size ?: 0

    fun items(now: Long, hidden: Set<String>, hiddenApps: Set<String>): List<WatchItem> =
        WatchMerge.merge(WatchSource.entries.flatMap { snapshots[it].orEmpty() },
            now, hidden, hiddenApps)
}
