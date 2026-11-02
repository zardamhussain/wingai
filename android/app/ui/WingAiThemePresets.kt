package com.wingai.keyboard.ui

import androidx.compose.ui.graphics.Color
import com.wingai.keyboard.settings.WingAiAppearance

/** Shared colors for onboarding, AI, account settings and the native HeliBoard renderer.
 * WingAiKeyboardColors maps these same tokens to keys, symbols, popups and buttons.
 */
fun wingAiPresetPalette(appearance: WingAiAppearance): WingAiPalette? = when (appearance) {
    WingAiAppearance.KEYBOARD -> null
    WingAiAppearance.AURORA -> preset(
        light = false, canvas = 0xFF11101B, surface = 0xFF1D1B2E,
        raised = 0xFF29253E, border = 0xFF3B3554, accent = 0xFFC4B5FD,
        onAccent = 0xFF21143D, accentContainer = 0xFF342650,
        primary = 0xFFF5F2FF, secondary = 0xFFBEB7D4, cyan = 0xFF81DDD0
    )
    WingAiAppearance.DAYLIGHT -> preset(
        light = true, canvas = 0xFFF5F6FB, surface = 0xFFFFFFFF,
        raised = 0xFFEAEDF6, border = 0xFFD2D7E7, accent = 0xFF5145B8,
        onAccent = 0xFFFFFFFF, accentContainer = 0xFFE9E5FC,
        primary = 0xFF202139, secondary = 0xFF5D6179, cyan = 0xFF087F79
    )
    WingAiAppearance.OCEAN -> preset(
        light = false, canvas = 0xFF0B1923, surface = 0xFF132A37,
        raised = 0xFF1C3947, border = 0xFF305362, accent = 0xFF7DDED0,
        onAccent = 0xFF082F30, accentContainer = 0xFF164942,
        primary = 0xFFEAF8FA, secondary = 0xFFA8C3CE, cyan = 0xFF9ECFFF
    )
    WingAiAppearance.ROSE -> preset(
        light = true, canvas = 0xFFFCF4F3, surface = 0xFFFFFAF9,
        raised = 0xFFF4E6E7, border = 0xFFE5CBCD, accent = 0xFFA33357,
        onAccent = 0xFFFFFFFF, accentContainer = 0xFFF8DDE5,
        primary = 0xFF39222D, secondary = 0xFF795A65, cyan = 0xFF5364A1
    )
}

private fun preset(
    light: Boolean, canvas: Long, surface: Long, raised: Long, border: Long,
    accent: Long, onAccent: Long, accentContainer: Long, primary: Long,
    secondary: Long, cyan: Long
) = WingAiPalette(
    isLight = light,
    bgCanvas = Color(canvas), surface0 = Color(canvas), surface1 = Color(surface),
    surface2 = Color(raised), surface3 = Color(border),
    borderSubtle = Color(border), borderStrong = Color(secondary), borderAccent = Color(accent),
    accentPrimary = Color(accent), onAccentPrimary = Color(onAccent),
    accentPrimaryContainer = Color(accentContainer), accentCyan = Color(cyan),
    accentEmerald = if (light) Color(0xFF13734C) else Color(0xFF86D9AC),
    accentEmeraldContainer = if (light) Color(0xFFDCF4E4) else Color(0xFF163C30),
    accentAmber = if (light) Color(0xFF8C5C08) else Color(0xFFF0CF8B),
    accentRose = if (light) Color(0xFFAC284B) else Color(0xFFFFA4B9),
    accentRoseContainer = if (light) Color(0xFFFBE2E9) else Color(0xFF482335),
    textPrimary = Color(primary), textSecondary = Color(secondary), textMuted = Color(secondary)
)
