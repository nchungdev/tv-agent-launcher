package com.gathin.tvhome.feature.watching.data

data class WatchItem(
    val title: String,
    val subtitle: String,
    val progress: Float,
    val posterUri: String?,
    val appLabel: String,
    val packageName: String,
    val actionType: String,
    val parameters: Map<String, Any?>,
    val remainingMs: Long = 0L,
    val source: WatchSource = WatchSource.LOCAL,
    val contentId: String = "",
    val updatedAt: Long = 0,
    val deepLink: String = "",
    val identityKeys: Set<String> = emptySet(),
    val durationMs: Long = 0,
    val posterAlternatives: List<String> = emptyList()
) {
    fun matchKeys(): Set<String> = if (contentId.isBlank() && deepLink.isBlank()) identityKeys
    else keys()

    fun posterCandidates(): List<String> = (listOfNotNull(posterUri) + posterAlternatives)
        .filter { it.isNotBlank() }.distinct()

    fun keys(): Set<String> = identityKeys + buildSet {
        if (deepLink.isNotBlank()) add("$packageName|uri:$deepLink")
        if (contentId.isNotBlank()) add("$packageName|${source.name}:$contentId")
        if (isEmpty()) add("$packageName|${source.name}:$title|$subtitle")
    }
}

enum class WatchSource { TV, SERVER, LOCAL }
