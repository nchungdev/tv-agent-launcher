package com.gathin.tvhome

import com.gathin.tvhome.feature.settings.data.Config
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutModeTest {

    @Test
    fun contractKeyMatchesLayoutMode() {
        assertEquals("layout_mode", Contract.Key.LAYOUT_MODE)
    }

    @Test
    fun normalizeLayoutModeAcceptsDockAndDefaultsToGrid() {
        assertEquals(Config.LAYOUT_DOCK, Config.normalizeLayoutMode("dock"))
        assertEquals(Config.LAYOUT_DOCK, Config.normalizeLayoutMode("  DOCK "))
        assertEquals(Config.LAYOUT_GRID, Config.normalizeLayoutMode("grid"))
        assertEquals(Config.LAYOUT_GRID, Config.normalizeLayoutMode(""))
        assertEquals(Config.LAYOUT_GRID, Config.normalizeLayoutMode(null))
        assertEquals(Config.LAYOUT_GRID, Config.normalizeLayoutMode("unknown"))
    }
}
