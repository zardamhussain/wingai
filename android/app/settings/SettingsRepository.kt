package com.wingai.keyboard.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wingai.keyboard.data.ShortcutAction
import helium314.keyboard.latin.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "wingai_settings")

data class WingAiSettings(
    val userId: String = "",
    val backendUrl: String = BuildConfig.DEFAULT_BACKEND_URL,
    val floatingEmoji: String = "\uD83E\uDEBD",
    val floatingEnabled: Boolean = true,
    val doubleTapAction: ShortcutAction = ShortcutAction.READ_SCREEN,
    val singleTapAction: ShortcutAction = ShortcutAction.OPEN_PANEL,
    val longPressAction: ShortcutAction = ShortcutAction.PASTE_CONTEXT,
    val inspectBeforeUpload: Boolean = false,
    val bubbleX: Int = 24,
    val bubbleY: Int = 360
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val FLOATING_EMOJI = stringPreferencesKey("floating_emoji")
        val FLOATING_ENABLED = booleanPreferencesKey("floating_enabled")
        val DOUBLE_TAP_ACTION = stringPreferencesKey("double_tap_action")
        val SINGLE_TAP_ACTION = stringPreferencesKey("single_tap_action")
        val LONG_PRESS_ACTION = stringPreferencesKey("long_press_action")
        val INSPECT_BEFORE_UPLOAD = booleanPreferencesKey("inspect_before_upload")
        val BUBBLE_X = intPreferencesKey("bubble_x")
        val BUBBLE_Y = intPreferencesKey("bubble_y")
    }

    val settingsFlow: Flow<WingAiSettings> = context.dataStore.data.map { prefs ->
        val savedUrl = prefs[Keys.BACKEND_URL]
        val resolvedUrl = if (
            savedUrl.isNullOrBlank() ||
            savedUrl.contains("127.0.0.1") ||
            savedUrl.contains("10.87.112.251") ||
            savedUrl.contains("example.com")
        ) {
            BuildConfig.DEFAULT_BACKEND_URL
        } else {
            savedUrl
        }
        WingAiSettings(
            userId = prefs[Keys.USER_ID].orEmpty(),
            backendUrl = resolvedUrl,
            floatingEmoji = prefs[Keys.FLOATING_EMOJI] ?: "\uD83E\uDEBD",
            floatingEnabled = prefs[Keys.FLOATING_ENABLED] ?: true,
            doubleTapAction = ShortcutAction.fromWireName(prefs[Keys.DOUBLE_TAP_ACTION]),
            singleTapAction = prefs[Keys.SINGLE_TAP_ACTION]?.let(ShortcutAction::fromWireName)
                ?: ShortcutAction.OPEN_PANEL,
            longPressAction = prefs[Keys.LONG_PRESS_ACTION]?.let(ShortcutAction::fromWireName)
                ?: ShortcutAction.PASTE_CONTEXT,
            inspectBeforeUpload = prefs[Keys.INSPECT_BEFORE_UPLOAD] ?: false,
            bubbleX = prefs[Keys.BUBBLE_X] ?: 24,
            bubbleY = prefs[Keys.BUBBLE_Y] ?: 360
        )
    }

    suspend fun getOrCreateUserId(): String {
        val current = settingsFlow.first().userId
        if (current.isNotBlank()) return current
        val generated = "wing_" + UUID.randomUUID().toString().replace("-", "").take(10)
        context.dataStore.edit { prefs ->
            if (prefs[Keys.USER_ID].isNullOrBlank()) {
                prefs[Keys.USER_ID] = generated
            }
        }
        return settingsFlow.first().userId.ifBlank { generated }
    }

    suspend fun updateBackendUrl(url: String) {
        context.dataStore.edit { it[Keys.BACKEND_URL] = url.trim().trimEnd('/') }
    }

    suspend fun updateInspectBeforeUpload(inspect: Boolean) {
        context.dataStore.edit { it[Keys.INSPECT_BEFORE_UPLOAD] = inspect }
    }
}
