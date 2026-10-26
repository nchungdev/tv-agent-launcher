package com.gathin.tvhome.feature.runningapps.data

import android.graphics.drawable.Drawable


data class RunningApp(
    val pkg: String,
    val label: CharSequence,
    val icon: Drawable?,
    val memoryKb: Long,
    val cpuPercent: Float,
    val recency: Int,
    val isLauncher: Boolean
)
