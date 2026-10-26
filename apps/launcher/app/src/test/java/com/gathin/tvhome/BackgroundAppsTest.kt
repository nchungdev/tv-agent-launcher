package com.gathin.tvhome

import com.gathin.tvhome.feature.runningapps.data.BackgroundApps
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.function.Predicate

class BackgroundAppsTest {

    @Test
    fun parsesCpuAndMemoryAndRecency() {
        val sample = """
%CPU    RSS NAME
 3.1 194288 com.google.android.youtube.tv
 1.7 142272 com.android.vending
 0.5 126796 com.android.vending:background
 0.0  45888 com.google.android.ext.services
--recents--
  * Recent #0: Task{1 #1 visible=false type=standard A=10061:com.android.vending sz=1}
  * Recent #1: Task{2 #2 visible=false type=standard A=10066:com.google.android.youtube.tv sz=1}
        """.trimIndent()

        val allowed = Predicate<String> { pkg ->
            pkg == "com.google.android.youtube.tv" || pkg == "com.android.vending"
        }

        val apps = BackgroundApps.parse(sample, allowed)
        assertEquals(2, apps.size)

        assertEquals("com.android.vending", apps[0].pkg)
        assertEquals(269068L, apps[0].memoryKb)
        assertEquals(2.2f, apps[0].cpuPercent, 0.01f)
        assertEquals(0, apps[0].recency)

        assertEquals("com.google.android.youtube.tv", apps[1].pkg)
        assertEquals(194288L, apps[1].memoryKb)
        assertEquals(3.1f, apps[1].cpuPercent, 0.01f)
        assertEquals(1, apps[1].recency)
    }

    @Test
    fun handlesLegacyTwoColumnOutputWithoutCpu() {
        val sample = """
  194288 com.google.android.youtube.tv
--recents--
  * Recent #0: Task{1 #1 visible=false type=standard A=10066:com.google.android.youtube.tv sz=1}
        """.trimIndent()

        val apps = BackgroundApps.parse(sample) { true }
        assertEquals(1, apps.size)
        assertEquals("com.google.android.youtube.tv", apps[0].pkg)
        assertEquals(194288L, apps[0].memoryKb)
        assertEquals(0.0f, apps[0].cpuPercent, 0.01f)
    }
}
