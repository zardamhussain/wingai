package com.wingai.keyboard.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.wingai.keyboard.settings.WingAiAppearance
import com.wingai.keyboard.settings.WingAiKeyboardAppearance
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WingAiKeyboardColorsTest {
    private lateinit var context: Context

    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.prefs().edit().clear().commit()
        SettingsActivity.forceTheme = null
        SettingsActivity.forceNight = null
    }

    @After fun reset() {
        context.prefs().edit().clear().commit()
        SettingsActivity.forceTheme = null
        SettingsActivity.forceNight = null
    }

    @Test fun everyNativeColorRoleUsesTheSelectedPalette() {
        WingAiAppearance.entries.filter { it != WingAiAppearance.KEYBOARD }.forEach { appearance ->
            WingAiKeyboardAppearance.synchronize(context, appearance)
            val palette = wingAiPresetPalette(appearance)!!
            val map = wingAiKeyboardColorMap(appearance)!!
            assertEquals(ColorType.entries.toSet(), map.keys)
            val colors = KeyboardTheme.getColorsForCurrentTheme(context)
            assertEquals(palette.bgCanvas.toArgb(), colors.get(ColorType.MAIN_BACKGROUND))
            assertEquals(palette.surface1.toArgb(), colors.get(ColorType.KEY_BACKGROUND))
            assertEquals(palette.surface2.toArgb(), colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND))
            assertEquals(palette.accentPrimaryContainer.toArgb(), colors.get(ColorType.SPACE_BAR_BACKGROUND))
            assertEquals(palette.accentPrimary.toArgb(), colors.get(ColorType.ACTION_KEY_BACKGROUND))
            assertEquals(palette.onAccentPrimary.toArgb(), colors.get(ColorType.ACTION_KEY_ICON))
            assertEquals(palette.textPrimary.toArgb(), colors.get(ColorType.KEY_TEXT))
            assertEquals(palette.textPrimary.toArgb(), colors.get(ColorType.POPUP_KEY_TEXT))
            assertEquals(KeyboardTheme.STYLE_ROUNDED, colors.themeStyle)
            assertTrue(colors.hasKeyBorders)
        }
    }

    @Test fun keyLabelsAndIconsHaveReadableContrast() {
        val pairs = listOf(
            ColorType.KEY_TEXT to ColorType.KEY_BACKGROUND,
            ColorType.KEY_HINT_TEXT to ColorType.KEY_BACKGROUND,
            ColorType.FUNCTIONAL_KEY_TEXT to ColorType.FUNCTIONAL_KEY_BACKGROUND,
            ColorType.SPACE_BAR_TEXT to ColorType.SPACE_BAR_BACKGROUND,
            ColorType.ACTION_KEY_ICON to ColorType.ACTION_KEY_BACKGROUND,
            ColorType.POPUP_KEY_TEXT to ColorType.POPUP_KEYS_BACKGROUND
        )
        WingAiAppearance.entries.filter { it != WingAiAppearance.KEYBOARD }.forEach { appearance ->
            val colors = wingAiKeyboardColors(appearance)!!
            pairs.forEach { (text, background) ->
                assertTrue("$appearance $text/$background", ColorUtils.calculateContrast(colors.get(text), colors.get(background)) >= 4.5)
            }
        }
    }

    // Pixel checks require real Skia; Robolectric's legacy canvas ignores drawable tint lists.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun pressedKeyDrawableRetainsFeedbackAndReturnsToNormal() {
        val colors = wingAiKeyboardColors(WingAiAppearance.AURORA)!!
        val drawable = ColorDrawable(Color.WHITE)
        drawable.setBounds(0, 0, 16, 16)
        colors.setColor(drawable, ColorType.KEY_BACKGROUND)
        fun pixel(): Int {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            drawable.draw(Canvas(bitmap))
            return bitmap.getPixel(8, 8)
        }
        val normal = pixel()
        drawable.state = intArrayOf(android.R.attr.state_pressed)
        val pressed = pixel()
        drawable.state = intArrayOf()
        assertNotEquals(normal, pressed)
        assertEquals(normal, pixel())
    }

    @Test fun matchKeyboardRestoresPreviouslySavedHeliBoardSettings() {
        val prefs = context.prefs()
        prefs.edit().putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
            .putBoolean(Settings.PREF_THEME_KEY_BORDERS, false)
            .putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_LIGHT)
            .putString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARK)
            .putBoolean(Settings.PREF_THEME_DAY_NIGHT, false).commit()
        val originalPrefs = prefs.all.toMap()
        val originalStyle = KeyboardTheme.getKeyboardTheme(context)
        val originalColors = KeyboardTheme.getColorsForCurrentTheme(context)
        WingAiKeyboardAppearance.synchronize(context, WingAiAppearance.ROSE)
        assertNotEquals(originalStyle, KeyboardTheme.getKeyboardTheme(context))
        assertEquals(WingAiAppearance.ROSE, WingAiKeyboardAppearance.current(context))
        val preset = KeyboardTheme.getColorsForCurrentTheme(context)
        assertFalse(preset.haveColorsChanged(context))
        // Compose's Match keyboard path must bypass an old snapshot during a theme transition.
        val baseWhilePresetActive = KeyboardTheme.getHeliBoardColors(context)
        ColorType.entries.forEach { assertEquals(originalColors.get(it), baseWhilePresetActive.get(it)) }
        WingAiKeyboardAppearance.synchronize(context, WingAiAppearance.KEYBOARD)
        assertTrue(preset.haveColorsChanged(context))
        assertEquals(originalStyle, KeyboardTheme.getKeyboardTheme(context))
        val restored = KeyboardTheme.getColorsForCurrentTheme(context)
        ColorType.entries.forEach { assertEquals(originalColors.get(it), restored.get(it)) }
        originalPrefs.forEach { (key, value) -> assertEquals(value, prefs.all[key]) }
        assertNull(wingAiKeyboardColors(WingAiAppearance.KEYBOARD))
    }

    @Test fun savedPresetAndUnknownValuesAreSafeAcrossRendererRecreation() {
        WingAiKeyboardAppearance.synchronize(context, WingAiAppearance.OCEAN)
        assertEquals(WingAiAppearance.OCEAN, WingAiKeyboardAppearance.current(context.applicationContext))
        val first = KeyboardTheme.getColorsForCurrentTheme(context)
        val second = KeyboardTheme.getColorsForCurrentTheme(context.applicationContext)
        ColorType.entries.forEach { assertEquals(first.get(it), second.get(it)) }
        context.prefs().edit().putString(WingAiKeyboardAppearance.SNAPSHOT_KEY, "future-theme").commit()
        assertEquals(WingAiAppearance.KEYBOARD, WingAiKeyboardAppearance.current(context))
        assertTrue(first.haveColorsChanged(context))
    }

    @Test fun nativeAppearancePreviewCanStillShowTheOriginalTheme() {
        WingAiKeyboardAppearance.synchronize(context, WingAiAppearance.AURORA)
        SettingsActivity.forceTheme = KeyboardTheme.THEME_LIGHT
        SettingsActivity.forceNight = false
        assertNotEquals(wingAiPresetPalette(WingAiAppearance.AURORA)!!.bgCanvas.toArgb(),
            KeyboardTheme.getColorsForCurrentTheme(context).get(ColorType.MAIN_BACKGROUND))
    }
}
