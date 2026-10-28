package com.gathin.tvhome

import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot
import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteToneTest {

    @Test
    fun testTonesMatchMaterialDynamicColors() {
        val seed = -0x98af5c // BASELINE_SEED
        for (dark in listOf(true, false)) {
            val s = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
            val c = MaterialDynamicColors()

            assertEquals("primary dark=$dark", c.primary().getArgb(s), s.primaryPalette.tone(if (dark) 80 else 40))
            assertEquals("onPrimary dark=$dark", c.onPrimary().getArgb(s), s.primaryPalette.tone(if (dark) 20 else 100))
            assertEquals("primaryContainer dark=$dark", c.primaryContainer().getArgb(s), s.primaryPalette.tone(if (dark) 30 else 90))
            assertEquals("onPrimaryContainer dark=$dark", c.onPrimaryContainer().getArgb(s), s.primaryPalette.tone(if (dark) 90 else 10))

            assertEquals("secondary dark=$dark", c.secondary().getArgb(s), s.secondaryPalette.tone(if (dark) 80 else 40))
            assertEquals("secondaryContainer dark=$dark", c.secondaryContainer().getArgb(s), s.secondaryPalette.tone(if (dark) 30 else 90))
            assertEquals("onSecondaryContainer dark=$dark", c.onSecondaryContainer().getArgb(s), s.secondaryPalette.tone(if (dark) 90 else 10))

            assertEquals("tertiary dark=$dark", c.tertiary().getArgb(s), s.tertiaryPalette.tone(if (dark) 80 else 40))
            assertEquals("tertiaryContainer dark=$dark", c.tertiaryContainer().getArgb(s), s.tertiaryPalette.tone(if (dark) 30 else 90))

            assertEquals("surface dark=$dark", c.surface().getArgb(s), s.neutralPalette.tone(if (dark) 6 else 98))
            assertEquals("surfaceContainer dark=$dark", c.surfaceContainer().getArgb(s), s.neutralPalette.tone(if (dark) 12 else 94))
            assertEquals("surfaceContainerHigh dark=$dark", c.surfaceContainerHigh().getArgb(s), s.neutralPalette.tone(if (dark) 17 else 92))
            assertEquals("surfaceContainerHighest dark=$dark", c.surfaceContainerHighest().getArgb(s), s.neutralPalette.tone(if (dark) 22 else 90))

            assertEquals("onSurface dark=$dark", c.onSurface().getArgb(s), s.neutralPalette.tone(if (dark) 90 else 10))
            assertEquals("onSurfaceVariant dark=$dark", c.onSurfaceVariant().getArgb(s), s.neutralVariantPalette.tone(if (dark) 80 else 30))
            assertEquals("surfaceVariant dark=$dark", c.surfaceVariant().getArgb(s), s.neutralVariantPalette.tone(if (dark) 30 else 90))

            assertEquals("inverseSurface dark=$dark", c.inverseSurface().getArgb(s), s.neutralPalette.tone(if (dark) 90 else 20))
            assertEquals("inverseOnSurface dark=$dark", c.inverseOnSurface().getArgb(s), s.neutralPalette.tone(if (dark) 20 else 95))
            assertEquals("inversePrimary dark=$dark", c.inversePrimary().getArgb(s), s.primaryPalette.tone(if (dark) 40 else 80))

            assertEquals("outline dark=$dark", c.outline().getArgb(s), s.neutralVariantPalette.tone(if (dark) 60 else 50))
            assertEquals("outlineVariant dark=$dark", c.outlineVariant().getArgb(s), s.neutralVariantPalette.tone(if (dark) 30 else 80))
            assertEquals("error dark=$dark", c.error().getArgb(s), s.errorPalette.tone(if (dark) 80 else 40))
        }
    }
}
