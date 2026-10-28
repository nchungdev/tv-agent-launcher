package com.gathin.tvhome

import com.gathin.tvhome.feature.runningapps.data.StuckLayers
import org.junit.Assert.assertEquals
import org.junit.Test

class StuckLayersTest {

    private val candidates = setOf("com.google.android.youtube.tv", "com.plexapp.android")

    @Test
    fun findsAppLayersComposedOverHome() {
        val sample = """
Display 0 HWC layers:
 Layer name
 com.gathin.tvhome/com.gathin.tvhome.feature.main.ui.MainActivity#0
  rel      0 |            1 |     DEVICE |          0 |    0    0 1920 1080 |
 SurfaceView - com.google.android.you[...]s.youtube.tv.activity.MainActivity#0
  rel     -2 |            0 |     DEVICE |          0 |  156  243  876  648 |
 com.google.android.youtube.tv/com.go[...]s.youtube.tv.activity.MainActivity#0
  rel      0 |            1 |     DEVICE |          0 |    0    0 1920 1080 |
h/w composer state:
        """.trimIndent()

        assertEquals(setOf("com.google.android.youtube.tv"), StuckLayers.parse(sample, candidates))
    }

    @Test
    fun ignoresHomeAndSystemLayers() {
        val sample = """
Display 0 HWC layers:
 com.gathin.tvhome/com.gathin.tvhome.feature.main.ui.MainActivity#0
 com.android.systemui.ImageWallpaper#0
 Toast#0
h/w composer state:
        """.trimIndent()

        assertEquals(emptySet<String>(), StuckLayers.parse(sample, candidates))
    }
}
