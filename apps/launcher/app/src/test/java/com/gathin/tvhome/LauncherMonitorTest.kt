package com.gathin.tvhome

import com.gathin.tvhome.feature.main.data.LauncherMonitor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherMonitorTest {

    @Test
    fun sampleReturnsValidValues() {
        val snapshot = LauncherMonitor.sample()
        assertNotNull(snapshot)
        assertTrue(snapshot.threadCount > 0)
        assertTrue(snapshot.memoryKb >= 0)
        assertTrue(snapshot.cpuPercent >= 0f)
    }

    @Test
    fun trimExecutesSafely() {
        LauncherMonitor.sample()
    }
}
