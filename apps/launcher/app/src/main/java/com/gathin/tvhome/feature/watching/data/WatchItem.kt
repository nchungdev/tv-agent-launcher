package com.gathin.tvhome.feature.watching.data

import android.content.Intent

class WatchItem(
    val title: String,
    val subtitle: String,
    val progress: Float,
    val posterUri: String?,
    val appLabel: String,
    val packageName: String,
    val launch: Intent?
)
