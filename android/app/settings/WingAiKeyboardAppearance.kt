package com.wingai.keyboard.settings

import android.content.Context
import androidx.annotation.MainThread
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.utils.prefs

/** DataStore stays authoritative. HeliBoard's synchronous drawing path reads this derived cache.
 * App startup reconciles old installations/restores; updateAppearance refreshes it before returning.
 * The cache uses a separate key, leaving all saved HeliBoard appearance preferences intact.
 */
object WingAiKeyboardAppearance {
    const val SNAPSHOT_KEY = "wingai_keyboard_appearance_snapshot"

    fun current(context: Context): WingAiAppearance =
        WingAiAppearance.fromId(context.prefs().getString(SNAPSHOT_KEY, null))

    @MainThread
    fun synchronize(context: Context, appearance: WingAiAppearance) {
        if (current(context) == appearance) return
        context.prefs().edit { putString(SNAPSHOT_KEY, appearance.id) }
        // Uses the same native reload path as HeliBoard's Appearance screen.
        // Recreates keys/popups on the next input-view presentation, not during a key gesture.
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }
}
