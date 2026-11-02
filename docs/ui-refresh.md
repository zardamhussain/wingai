# WingAI: Android UI refresh and preview

## What you can view now

**[Native Android debug APK](../artifacts/apk/WingAI-modern-ui-debug-arm64.apk)** is available, with [installation notes](../artifacts/apk/INSTALL.md). Revision **3.8-wingai-ui2 (3802)** adds full keyboard themes and the **Analyze chat** label. ARM64, Android 8.0/API 26 or newer. Build/signature checks and nine focused tests passed; not yet device-tested.

The following preview assets remain browser mockups, separate from the APK:

- [Interactive browser preview](../preview/index.html): open in a browser; no build, API key, network assets or Node installation required.
- [Full design preview screenshot](../preview/screenshots/aurora-desktop.png)
- [Full keyboard theme preview](../preview/screenshots/full-keyboard-desktop.png) and [Daylight keys](../preview/screenshots/daylight-keyboard-mockup.png).
- Theme screenshots: [Aurora](../preview/screenshots/aurora-reply-mockup.png), [Daylight](../preview/screenshots/daylight-reply-mockup.png), [Ocean](../preview/screenshots/ocean-reply-mockup.png), [Rose](../preview/screenshots/rose-reply-mockup.png).
- [Setup screen mockup](../preview/screenshots/setup-mockup.png) and [account screen mockup](../preview/screenshots/account-mockup.png).

**These are browser-rendered design mockups, not Android screenshots or a running APK.** They demonstrate the visual direction using synthetic conversations and quota data. They are not pixel-identical Compose renders. The neutral chat belongs to the imagined host app, not WingAI. The demo keyboard is not HeliBoard's actual key renderer.

For this session a local-only server was started at <http://127.0.0.1:52361/preview/> (PID 16279). It lasts only while that process is running. To restart on a stable port, run from this project's root:

```sh
python3 -m http.server 8765 --bind 127.0.0.1
```

Then visit <http://127.0.0.1:8765/preview/>. The HTML can also be opened directly from disk. No files are uploaded by the preview.

## Project review

This is primarily a native Android **input method/keyboard**, not a standalone messenger:

- Android: HeliBoard/LatinIME key engine with Jetpack Compose AI, setup and account surfaces.
- Backend: a Hono Cloudflare Worker, D1 usage storage, and Gemini integration.
- Shared: API contract definitions.
- iOS: the later iPhone 16 Pro request added a native SwiftUI app/keyboard project and theme collection. See the [iOS delivery notes](../ios/README.md). Static project/grammar checks passed, but no native iOS build, signing or runtime verification is available. Unsupported broadcast/shortcut experiments were replaced with explicit host-app photo selection and keyboard paste flows.

The Android build uses Kotlin 2.2.21, Android Gradle Plugin 8.13.2, Gradle 8.14.3, compile/target SDK 36, minimum SDK 26, Java target 17, NDK 27.1.12297006, and arm64-v8a native libraries. A debug build has now succeeded using Temurin JDK 17.0.21, SDK 36, AGP-selected Build Tools 35 and the specified NDK. Build Tools 36 were used for final APK verification.

The original download was left untouched. Edits are in the Designer workspace copy because this IDE's file-edit tools cannot modify files outside the workspace.

## Native Android changes

### Themes

Four named presets plus a backwards-compatible **Match keyboard** setting:

- **Aurora**: violet accents on warm dark surfaces.
- **Daylight**: light surfaces with an indigo accent.
- **Ocean**: deep blue with mint highlights.
- **Rose**: light blush with a berry accent.

Find the new selector at **WingAI Board Settings > Your WingAI**. Its selection is stored in the existing DataStore and observed by WingAI's Compose theme. The default remains Match keyboard, so existing users are not silently moved to a different palette. Unknown saved theme IDs also fall back to Match keyboard.

Presets now style **native letter/number/symbol keys, function keys, spacebar, action/toolbar buttons, popups, AI panels, setup and the account screen**. Named presets use rounded bordered keys and their own canvas; saved HeliBoard style/color/background-image preferences are not overwritten. **Match keyboard** restores those original settings. Other apps' chat screens are not themed.

DataStore remains authoritative; a derived SharedPreferences snapshot lets HeliBoard's synchronous renderer read the selected preset without blocking. Startup reconciles presets selected in the previous APK; a change requests HeliBoard's existing theme reload. **Hide and reopen the keyboard after switching themes.** Full IME lifecycle behavior still needs device testing. The browser preference is separate from Android.

### Layout and copy

- Softer card corners, rounded tone chips, stronger hierarchy and theme-aware Material 3 settings surfaces.
- Account screen now includes the theme picker, plan/usage card, readable error state, privacy copy and copyable user ID.
- Empty state: "Find your next words." and an explanation of chat selection/pasting.
- Panel heading: "Your reply, your way"; tone mix labeled "Mix it up".
- All former **Read chat** actions are now **Analyze chat**, with the same action handlers. The iOS app/keyboard source uses the same label; its spacebar and other keys share the selected palette.
- Setup heading: "Good conversations start here." with clearer permission language.
- Reply insertion requires the explicit Insert action; scrolling/tapping the reply body no longer inserts accidentally.
- Reply actions scroll horizontally on narrow keyboards. The fixed-height IME panel and vertically scrolling results remain.
- Account and onboarding actions use 48 dp controls; compact IME controls remain smaller and need device/TalkBack checks.
- Existing online service, quota policy, model prompts, wire-format tone names, capture logic and key engine are unchanged.
- Removed the account screen's duplicate initial refresh trigger.

New English copy follows the existing WingAI Kotlin convention. Localization of these strings remains follow-up work.

### Source map

- [Appearance IDs and fallback](../android/app/settings/WingAiAppearance.kt)
- [DataStore settings](../android/app/settings/SettingsRepository.kt)
- [Color presets](../android/app/ui/WingAiThemePresets.kt)
- [Native keyboard color-role mapping](../android/app/ui/WingAiKeyboardColors.kt) and [synchronous appearance snapshot](../android/app/settings/WingAiKeyboardAppearance.kt).
- [Theme and common components](../android/app/ui/WingAiDesignSystem.kt)
- [Theme picker](../android/app/ui/WingAiThemePicker.kt)
- [Account screen](../android/app/ui/UserAccountScreen.kt)
- [Reply panel](../android/app/ui/AiControlPanel.kt)
- [Keyboard AI toolbar](../android/app/ui/WingAiStripView.kt)
- [Setup screen](../android/app/src/main/java/helium314/keyboard/settings/WelcomeWizard.kt)
- [Settings entry points](../android/app/src/main/java/helium314/keyboard/settings/screens/MainSettingsScreen.kt)

## Verification and limitations

### Passed

The [Chrome smoke test](../preview/test_preview.py) exercised:

- Four theme choices on both phones and persistence after reload.
- Distinct themed letter/function/space/action keys in all four themes, and Analyze chat label/action regression checks.
- Mixed replies and all seven individual tone choices.
- Insert, sample shorten/expand, draft typing and backspace.
- Simulated empty/loading/error states and retry.
- Setup simulation, reset, and no-send behavior.
- No horizontal page overflow and phone-frame containment at 320, 360, 390, 720, 768, 1024, 1200 and 1440 px.
- No browser JavaScript exceptions or external HTTP/API requests during the test.

The desktop and Daylight screenshots were visually inspected. Numeric checks of the new native palette constants show primary/secondary text on canvas, surface and raised surface, and button text on accent, all above 4.5:1 contrast. This is not a complete accessibility audit.

### Native APK verification

The initial missing-Java blocker is resolved. JDK, Android SDK and NDK tools were installed in a dedicated user cache without a system-wide installation. The source is staged into a path without spaces to avoid Android SDK/NDK path-handling problems. Use the [build helper](../tools/build-debug-apk.sh) to repeat the build.

- `:app:assembleDebug` succeeded, including Kotlin, Java and C++ compilation.
- All three [Kotlin palette/preference-ID tests](../android/app/src/test/java/com/wingai/keyboard/ui/WingAiAppearanceTest.kt) passed; see [test results](../artifacts/test-results/TEST-com.wingai.keyboard.ui.WingAiAppearanceTest.xml).
- Six [native theme tests](../android/app/src/test/java/com/wingai/keyboard/ui/WingAiKeyboardColorsTest.kt) passed: complete role mapping, key contrast, native Skia pressed-state rendering, original-preference restoration, snapshot fallback/recreation and native Appearance preview behavior. See [results](../artifacts/test-results/TEST-com.wingai.keyboard.ui.WingAiKeyboardColorsTest.xml). These are host-side Robolectric tests, not an emulator session.
- APK v2 debug signature, ZIP integrity and ZIP alignment checks passed.
- Enabled NDK r27 flexible page-size support in [Application.mk](../android/app/src/main/jni/Application.mk). Verified all three bundled native libraries have 16 KB-aligned ELF load segments.
- The APK is 34,875,256 bytes, package `com.wingai.keyboard`, version `3.8-wingai-ui2` (3802), ARM64, minimum API 26 and target API 36. It retains the prior debug certificate. See the [verification record](../artifacts/build-logs/apk-verification.json).

### Not yet verified

The APK has **not been installed or run on an Android device/emulator**. There are no native app screenshots yet. No emulator or Android Studio was installed, and no device settings were changed. Only the nine focused theme tests were run, not the entire inherited HeliBoard test suite. Browser checks and compilation do not establish native layout, IME interaction or backend correctness.

Before treating this as release-ready, test DataStore persistence across process restart, theme switching with an open IME, 180-350 dp panel heights, large font sizes, TalkBack, landscape, keyboard switching, password fields, actual capture permissions and offline/error behavior on Android. The existing upstream API and worker behavior were not tested in this task.

## Getting a real Android preview

1. Install a compatible Android Studio and select a JDK compatible with this Gradle/AGP configuration (Java 17 minimum).
2. Open the Android project folder in Android Studio, not the workspace starter root.
3. In SDK Manager, install Android SDK Platform 36, platform/build tools and NDK **27.1.12297006**. Let Gradle request any additional build components.
4. Create an **ARM64** Android emulator (the project only builds arm64-v8a) or connect an ARM64 Android phone running Android 8.0/API 26 or newer.
5. From the Android folder, run:

```sh
bash gradlew :app:assembleDebug
bash gradlew :app:testDebugUnitTest
find app/build/outputs/apk/debug -name '*.apk'
```

Install the APK path produced by the build with `adb install -r <actual-apk-path>`, or use Android Studio's Run action. The inherited README's fixed APK filename is stale; use the build output rather than guessing a filename.

6. Enable WingAI in Android's keyboard settings, choose it as the active keyboard, and test it in a text field.
7. Open **WingAI Board Settings > Your WingAI** to select themes. The Compose preview functions also support previewing injected sample profiles without calling the account service.

**Privacy:** the project ships with a default public backend URL controlled by the downloaded repository's configuration. Review or replace that endpoint before sending personal conversations or screen captures. No AI backend was contacted during this work. The inherited HeliBoard README's claim of no internet permission does not apply to this WingAI fork: its manifest requests Internet access.

## Re-running the browser checks

The preview itself has no dependencies. Automated testing requires installed Chrome (or Chromium) and Python with websocket-client:

```sh
python3 -m venv /tmp/wingai-preview-venv
/tmp/wingai-preview-venv/bin/pip install -r preview/requirements-dev.txt
/tmp/wingai-preview-venv/bin/python preview/test_preview.py
```

On systems other than this Mac, set `CHROME_BIN` to the browser executable if it is not found automatically. Tests use a temporary browser profile, regenerate the mockup screenshots, and close that browser when finished.
