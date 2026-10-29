package com.gathin.tvhome.feature.watching.data

object WatchMerge {
    const val LIMIT = 8
    const val COMPLETED = 0.95f
    const val LOCAL_KEEP_MS = 14L * 24 * 60 * 60_000
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    fun validPackage(value: String): Boolean = packagePattern.matches(value)

    fun progress(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    fun timestamp(value: Long, now: Long): Long {
        val millis = if (value in 1..9_999_999_999L) value * 1000 else value
        return if (millis > 0 && millis <= now) millis else 0
    }

    fun merge(
        candidates: List<WatchItem>, now: Long,
        hidden: Set<String> = emptySet(), hiddenApps: Set<String> = emptySet()
    ): List<WatchItem> {
        val merged = ArrayList<WatchItem>()
        for (candidate in candidates) {
            if (!validPackage(candidate.packageName) || candidate.title.isBlank()) continue
            val item = candidate.copy(
                progress = progress(candidate.progress),
                updatedAt = timestamp(candidate.updatedAt, now)
            )
            val matches = merged.filter { existing -> existing.matchKeys().any { it in item.matchKeys() } }
            merged.removeAll(matches.toSet())
            merged.add(matches.fold(item, ::combine))
        }
        return merged.filter { item ->
            item.packageName !in hiddenApps && item.keys().none { it in hidden } &&
                item.progress < COMPLETED && (item.source != WatchSource.LOCAL ||
                (item.updatedAt > 0 && now - item.updatedAt < LOCAL_KEEP_MS &&
                    (item.durationMs <= 0 || item.progress >= 0.03f)))
        }.sortedWith(compareByDescending<WatchItem> { it.updatedAt }
            .thenBy { it.source.ordinal }.thenBy { it.keys().sorted().first() }).take(LIMIT)
    }

    private fun combine(first: WatchItem, second: WatchItem): WatchItem {
        val newer = if (second.updatedAt > first.updatedAt ||
            (second.updatedAt == first.updatedAt && second.source.ordinal < first.source.ordinal)) {
            second
        } else first
        val other = if (newer === first) second else first
        val artwork = listOf(first, second).sortedBy { it.source.ordinal }
            .flatMap { it.posterCandidates() }.distinct()
        val watched = if (newer.progress <= 0f && other.progress > 0f) other else newer
        return newer.copy(
            progress = watched.progress,
            remainingMs = watched.remainingMs,
            durationMs = maxOf(newer.durationMs, other.durationMs),
            deepLink = newer.deepLink.ifBlank { other.deepLink },
            posterUri = artwork.firstOrNull(),
            posterAlternatives = artwork.drop(1),
            identityKeys = first.keys() + second.keys()
        )
    }
}
