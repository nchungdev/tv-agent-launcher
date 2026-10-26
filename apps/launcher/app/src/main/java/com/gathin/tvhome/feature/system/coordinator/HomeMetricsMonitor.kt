package com.gathin.tvhome.feature.system.coordinator
import com.gathin.tvhome.feature.system.data.*
import com.gathin.tvhome.feature.system.widget.*

import android.content.Context
import com.gathin.tvhome.feature.system.data.CpuMeter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class HomeMetricsMonitor(
    context: Context,
    private val scope: CoroutineScope,
    private val onMinuteTick: () -> Unit,
    private val onSysInfoSample: (cpuPercent: Int) -> Unit
) {
    private val cpuMeter = CpuMeter(context)
    private var minuteJob: Job? = null
    private var sysInfoJob: Job? = null

    fun start() {
        stop()
        minuteJob = scope.launch {
            while (isActive) {
                delay(60_000L)
                onMinuteTick()
            }
        }
        sysInfoJob = scope.launch {
            while (isActive) {
                val sample = withContext(Dispatchers.IO) {
                    cpuMeter.sample()
                }
                onSysInfoSample(sample)
                delay(2000L)
            }
        }
    }

    fun stop() {
        minuteJob?.cancel()
        minuteJob = null
        sysInfoJob?.cancel()
        sysInfoJob = null
        cpuMeter.stop()
    }
}
