# WingAI Board

WingAI Board (`com.wingai.keyboard`) is an Android AI keyboard built on HeliBoard v3.8 (`LatinIME`) paired with a Cloudflare Worker + D1 + Google Gemini backend (`worker/`).

## Modern UI preview

This is an edited workspace copy; the original GitHub download is unchanged.

- [Interactive Android-sized browser mockup](preview/index.html)
- [Desktop preview image](preview/screenshots/aurora-desktop.png)
- [Design changes, checks, source map and Android build instructions](docs/ui-refresh.md)
- [Built Android APK](artifacts/apk/WingAI-modern-ui-debug-arm64.apk) and [installation notes](artifacts/apk/INSTALL.md)

The refresh adds Aurora, Daylight, Ocean and Rose presets to the **entire keyboard: keys, buttons, popups and AI**, plus setup/account screens. The action is now **Analyze chat**. Choose a preset under **Settings > Your WingAI**, then hide/reopen the keyboard. **Match keyboard** restores your original HeliBoard appearance. See the [full-keyboard preview](preview/screenshots/full-keyboard-desktop.png).

The browser preview uses sample data and sends no messages or AI requests; it remains a browser mockup. **Native debug APK 3.8-wingai-ui2 (3802)** is built for ARM64 Android 8.0+: compilation, signature/integrity/alignment checks and all nine focused theme tests passed. The build uses an isolated Java/Android toolchain cache. Device installation/runtime remain unverified. Review the configured backend before using personal chat content.

## iPhone 16 Pro / iOS version

A native SwiftUI app and custom keyboard extension are prepared in the
[iOS project](ios/README.md), with the four themes, user-selected screenshot input,
paste/review/generate/copy replies, and a basic offline English keyboard.
Download the [iOS source package](artifacts/ios/WingAI-iOS-source.zip).
All 50 static project/Swift grammar checks passed, but **no iOS build or IPA has
been produced**: full Xcode and Apple signing are required. The included XCTest
tests have not run. The iOS keyboard is not a full HeliBoard port and cannot
automatically capture other apps. The latest iOS source also uses **Analyze chat** and themed keys/buttons; it is still source-only.

## Android features
1. **Full Android Keyboard (`WingAI Board`)**: Native QWERTY, swipe/gesture typing, autocorrect, dictionaries, numbers, symbols, emoji picker, and clipboard history. Works offline for normal typing.
2. **Integrated AI Toolbar & Reply Panel**:
   - **Analyze chat**: Starts the existing user-initiated chat-region analysis flow.
   - **✨ Ask**: Type custom instructions on the keyboard (e.g., *"Tell them I am busy today. Suggest tomorrow."*) to guide reply generation.
   - **Paste Chat**: Reads copied conversation text from the clipboard, displays the pasted context preview, and generates replies when screen capture is not used.
   - **🪽 AI / ⌨️ Keys**: Switches seamlessly between the keyboard keys and the AI reply cards while preserving exact keyboard height.
3. **Tone & Rewrite Controls**: Supports `Casual`, `Friendly`, `Professional`, `Funny`, `Flirty`, `Short`, and `Detailed` tones, inline editing, and `Rewrite`, `Shorten`, and `Expand` actions.
4. **User Plans & Daily Quota (`Cloudflare D1`)**:
   - **Free Plan**: 5 AI requests per UTC day (auto-resets daily).
   - **Paid Plan**: Unlimited AI requests.
   - **Your WingAI**: Choose a theme and view your User ID, current plan (`Free` or `Paid`), and remaining daily usage inside **WingAI Board Settings** or from the keyboard AI panel.
5. **Privacy & Safety**: Never reads password fields, never monitors the screen or clipboard in the background, and never sends messages automatically.

## Cloudflare Worker Backend (`worker/`)
```bash
cd worker
npm install
npm test
npm run deploy
```

### Manually Updating a User to Paid in Cloudflare D1
```bash
cd worker
npx wrangler d1 execute wingai --remote --command "UPDATE users SET tier = 'paid' WHERE user_id = '<USER_ID>';"
```

## Building & Installing the Android App (`android/`)
```bash
cd android
bash gradlew :app:assembleDebug
find app/build/outputs/apk/debug -name '*.apk'
# Install the actual APK path printed above with: adb install -r <apk-path>
```
