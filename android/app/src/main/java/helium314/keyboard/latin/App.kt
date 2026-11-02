// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin

import android.app.Application
import com.wingai.keyboard.settings.SettingsRepository
import com.wingai.keyboard.settings.WingAiKeyboardAppearance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings

class App : Application() {
    private val appearanceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        DebugFlags.init(this)
        Settings.init(this)
        SubtypeSettings.init(this)
        RichInputMethodManager.init(this)

        AppUpgrade.checkVersionUpgrade(this)
        AppUpgrade.transferOldPinnedClips(this) // todo: remove in a few months, maybe mid 2026
        app = this
        Defaults.initDynamicDefaults(this)
        LayoutUtilsCustom.removeMissingLayouts(this) // only after version upgrade
        SupportedEmojis.load(this)

        // Reconcile presets selected before full-keyboard theming was added, and backup restores.
        appearanceScope.launch {
            SettingsRepository(this@App).settingsFlow
                .map { it.appearance }
                .distinctUntilChanged()
                .catch { Log.w("WingAI", "Unable to refresh keyboard appearance", it) }
                .collect { WingAiKeyboardAppearance.synchronize(this@App, it) }
        }

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        Log.i(
            "startup", "Starting ${applicationInfo.processName} version ${packageInfo.versionName} (${
                packageInfo.versionCode
            }) on Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
        )
    }

    companion object {
        // used so JniUtils can access application once
        private var app: App? = null
        fun getApp(): App? {
            val application = app
            app = null
            return application
        }
    }
}
