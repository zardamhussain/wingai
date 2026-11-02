package com.wingai.keyboard.ui

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.wingai.keyboard.settings.WingAiAppearance
import com.wingai.keyboard.settings.WingAiKeyboardAppearance
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.AllColors
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import java.util.EnumMap

/** One palette for Compose and HeliBoard's real keys, symbols, popups and toolbar.
 * Named presets use rounded, bordered keys. Match keyboard restores HeliBoard preferences;
 * none of the user's original style, colors, borders or background-image settings are overwritten.
 */
fun wingAiKeyboardColorMap(appearance: WingAiAppearance): EnumMap<ColorType, Int>? {
    val palette = wingAiPresetPalette(appearance) ?: return null
    val map = EnumMap<ColorType, Int>(ColorType::class.java)
    ColorType.entries.forEach { role ->
        map[role] = when (role) {
            ColorType.MAIN_BACKGROUND, ColorType.NAVIGATION_BAR, ColorType.STRIP_BACKGROUND -> palette.bgCanvas
            ColorType.KEY_BACKGROUND, ColorType.MORE_SUGGESTIONS_WORD_BACKGROUND -> palette.surface1
            ColorType.FUNCTIONAL_KEY_BACKGROUND, ColorType.POPUP_KEYS_BACKGROUND,
            ColorType.MORE_SUGGESTIONS_BACKGROUND, ColorType.KEY_PREVIEW_BACKGROUND,
            ColorType.GESTURE_PREVIEW, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND,
            ColorType.AUTOFILL_BACKGROUND_CHIP -> palette.surface2
            ColorType.SPACE_BAR_BACKGROUND, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND -> palette.accentPrimaryContainer
            ColorType.ACTION_KEY_BACKGROUND, ColorType.ACTION_KEY_POPUP_KEYS_BACKGROUND,
            ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND, ColorType.GESTURE_TRAIL,
            ColorType.SHIFT_KEY_ICON, ColorType.CLIPBOARD_PIN, ColorType.EMOJI_CATEGORY_SELECTED -> palette.accentPrimary
            ColorType.ACTION_KEY_ICON -> palette.onAccentPrimary
            ColorType.KEY_HINT_TEXT, ColorType.MORE_SUGGESTIONS_HINT,
            ColorType.SUGGESTED_WORD, ColorType.SUGGESTION_TYPED_WORD, ColorType.SUGGESTION_VALID_WORD -> palette.textSecondary
            ColorType.KEY_ICON, ColorType.KEY_TEXT, ColorType.FUNCTIONAL_KEY_TEXT,
            ColorType.SPACE_BAR_TEXT, ColorType.POPUP_KEY_ICON, ColorType.POPUP_KEY_TEXT,
            ColorType.KEY_PREVIEW_TEXT, ColorType.EMOJI_KEY_TEXT, ColorType.EMOJI_CATEGORY,
            ColorType.TOOL_BAR_KEY, ColorType.TOOL_BAR_EXPAND_KEY, ColorType.ONE_HANDED_MODE_BUTTON,
            ColorType.REMOVE_SUGGESTION_ICON, ColorType.SUGGESTION_AUTO_CORRECT -> palette.textPrimary
        }.toArgb()
    }
    return map
}

fun wingAiKeyboardColors(appearance: WingAiAppearance): Colors? {
    val map = wingAiKeyboardColorMap(appearance) ?: return null
    val native = AllColors(map, KeyboardTheme.STYLE_ROUNDED, true, null)
    // Retain HeliBoard's native pressed-state drawables, icon tinting and popup rendering.
    return object : Colors by native {
        override fun haveColorsChanged(context: Context): Boolean =
            WingAiKeyboardAppearance.current(context) != appearance
    }
}
