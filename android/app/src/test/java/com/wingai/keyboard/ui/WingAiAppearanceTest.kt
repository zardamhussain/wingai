package com.wingai.keyboard.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.wingai.keyboard.settings.WingAiAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WingAiAppearanceTest {
    @Test fun unknownPreferencesPreserveKeyboardAppearance() {
        assertEquals(WingAiAppearance.KEYBOARD, WingAiAppearance.fromId(null))
        assertEquals(WingAiAppearance.KEYBOARD, WingAiAppearance.fromId("future-theme"))
        assertNull(wingAiPresetPalette(WingAiAppearance.KEYBOARD))
    }

    @Test fun everyPresetHasAStableUniquePreferenceId() {
        assertEquals(WingAiAppearance.entries.size, WingAiAppearance.entries.map { it.id }.distinct().size)
        WingAiAppearance.entries.forEach { assertEquals(it, WingAiAppearance.fromId(it.id)) }
    }

    @Test fun presetTextHasReadableContrast() {
        WingAiAppearance.entries.mapNotNull(::wingAiPresetPalette).forEach { palette ->
            listOf(palette.bgCanvas, palette.surface1, palette.surface2).forEach { background ->
                assertTrue("Primary text contrast", contrast(palette.textPrimary, background) >= 4.5f)
                assertTrue("Secondary text contrast", contrast(palette.textSecondary, background) >= 4.5f)
            }
            assertTrue("Action label contrast", contrast(palette.onAccentPrimary, palette.accentPrimary) >= 4.5f)
            assertTrue("Error text contrast", contrast(palette.textPrimary, palette.accentRoseContainer) >= 4.5f)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}
