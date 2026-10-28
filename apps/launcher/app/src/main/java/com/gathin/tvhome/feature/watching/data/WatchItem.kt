package com.gathin.tvhome.feature.watching.data

class WatchItem(
    val title: String,
    val subtitle: String,
    val progress: Float,
    val posterUri: String?,
    val appLabel: String,
    val packageName: String,
    val actionType: String,
    val parameters: Map<String, Any?>,
    val remainingMs: Long = 0L
)
