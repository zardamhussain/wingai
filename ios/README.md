# WingAI for iPhone

Native SwiftUI app and custom keyboard extension targeting iOS 16 or later, with adaptive iPhone layouts. The requested device is **iPhone 16 Pro**. This is separate native source, not an APK conversion.

## Current delivery status

- [Xcode project](WingAI.xcodeproj/project.pbxproj): app, embedded keyboard extension and focused unit-test target. Shared scheme: **WingAI**.
- [Downloadable iOS source package](../artifacts/ios/WingAI-iOS-source.zip).
- [Static validation results](artifacts/validation/static-checks.txt).

**This is a source project, NOT an installable IPA.** Full Xcode and an iOS SDK are unavailable on the current Mac. No Swift/SwiftUI type-check, Xcode build, XCTest execution, simulator run, signing or iPhone installation has been completed. The project needs native build and device validation before it can be described as working on a phone.

50 static checks passed: Swift grammar parsing, Xcode object references and target/source membership, app-extension embedding, plist structure, scheme references, icon format and checks for the intended permission guards. These do not substitute for Apple's compiler, accessibility testing or runtime checks. An original 1024 px opaque app icon was also visually inspected.

## Implemented in source

### Host app

- **Replies:** paste/type a chat or explicitly select a screenshot with PhotosPicker; choose one of seven tones; add an optional instruction; explicitly generate replies; edit and copy a reply.
- **Your WingAI:** Aurora, Daylight, Ocean, Rose and Follow iPhone appearance; on-demand usage lookup; device ID; configurable HTTPS endpoint and consent.
- **Setup:** honest keyboard-installation instructions and iOS-specific limitations. No private settings URLs, no KVC keyboard detection and no fake permission-completion states.
- "Try sample" loads prewritten replies without a backend call. Account usage is not fabricated.
- The app uses safe areas rather than hard-coded Dynamic Island offsets. Light/dark appearance, scalable app typography and scrolling layouts are included; visual verification remains pending.
- The action is now **Analyze chat** in both the host app and keyboard. Screenshot selection is local until that action. Images are orientation-corrected, downsampled to 1600 px maximum dimension and re-encoded as JPEG, limited to 2 MB. Selection accepts inputs up to 20 MB. Crop sensitive details in Photos first; there is no in-app cropper in this version.

### Keyboard extension

- Basic English QWERTY, shift/caps lock, two symbol pages, space, return and delete connected to `textDocumentProxy`.
- UIKit globe key supports switching keyboards, including the system's hold-to-list behavior. It remains reachable from replies and themes.
- Theme picker, user-initiated paste, tone choice, context review and explicit **Analyze chat / Insert** actions.
- Themes color letter keys, function keys, the accent-container spacebar and accent return button as well as AI surfaces. This is source-verified only, not an iOS runtime claim.
- Normal typing needs no network. Clipboard/network actions require **Allow Full Access**, and AI requests also require consent to the displayed endpoint.
- No automatic clipboard reads, no automatic request when changing tone, and no sending host-app messages.
- Keyboard contexts/replies are cleared when the extension view disappears. Requests are cancelled on disappearance or consent revocation; cancellation cannot undo a request already received by a server.

**Not feature-parity with Android:** HeliBoard's swipe engine, autocorrect, multilingual dictionaries, dictation, emoji picker, clipboard history and rewrite actions have not been ported. The native iOS keyboard is a first-version implementation, not the Android engine.

### What iOS does differently

A third-party keyboard cannot automatically capture another app's screen or read an entire conversation. Use copied text in the keyboard, or select a screenshot in the host app. iOS substitutes its own keyboard for secure/password and some phone-pad fields; host apps can also disallow custom keyboards.

The downloaded single-frame ReplayKit/Back-Tap experiments were removed from the working iOS implementation. No screen-recording extension or screenshot shortcut is shipped. The original download outside this workspace remains unchanged.

## Install on your iPhone 16 Pro with Xcode

1. On a compatible Mac, install **full Xcode** with an iOS SDK/device support suitable for the iOS version installed on your phone. Launch Xcode once to complete its setup.
2. Open the **WingAI.xcodeproj** project in this folder. No CocoaPods or third-party Swift packages are needed.
3. In [Common.xcconfig](Config/Common.xcconfig), change `PRODUCT_BUNDLE_PREFIX` from `com.example.wingai` to a unique identifier you control. The extension automatically appends `.keyboard`.
4. In Xcode Settings > Accounts, sign in to your Apple account. For both **WingAI** and **WingAIKeyboard** targets, select your Team under Signing & Capabilities and leave automatic signing enabled. The test target may also need your Team for on-device tests.
5. Connect and trust your iPhone, enable Developer Mode if Xcode/iOS requests it, select the **WingAI** scheme and your iPhone as the destination, then choose Run.
6. After installation, go to iPhone **Settings > General > Keyboard > Keyboards > Add New Keyboard > WingAI**.
7. Switch to WingAI with the globe key in a text field. For keyboard AI/paste, turn on **Allow Full Access** in WingAI's keyboard settings, then review the service consent inside its reply panel.

A Personal Team can be used for development testing subject to Apple's current provisioning limits. TestFlight distribution requires Apple Developer Program membership and an archive signed for that account. **An unsigned IPA or an Android APK cannot be installed on your iPhone by simply opening it.** No Apple credentials or signing certificates are included in this delivery.

For TestFlight: validate a release archive in Xcode Organizer, configure your App Store Connect app, review privacy/distribution requirements, and upload through your authorized account. This task has not created a TestFlight build or public download link.

## Optional App Groups

The default project intentionally requires **no App Group entitlement**, keeping basic local testing independent of that capability. Without it:

- App and keyboard have separate local theme selections, endpoint consent and device IDs/usage accounts.
- Select themes in the app or from the keyboard's palette button.
- Changing the host app's backend does not reconfigure the keyboard. For a custom keyboard endpoint without a group, change the shared default in source before building.

For a team that supports App Groups:

1. Register an App Group and add the **same** capability to both targets in Signing & Capabilities.
2. Set `WINGAI_APP_GROUP` in Common.xcconfig to that exact identifier.
3. Ensure the entitlements files generated by Xcode are selected for each target. [Example entitlements](Config/AppGroups.entitlements.example) show the required key; do not sign an unresolved placeholder.
4. Keyboard Full Access is needed for shared-container behavior. Reopen the keyboard after changing settings and verify that both targets actually share preferences.

The code checks group-container availability and reports whether shared storage is active. It does not pretend that temporary directories are shared between processes. Conversation text and screenshots are not stored in group preferences.

## Privacy and backend

The initial endpoint comes from the downloaded repository: `https://wingai-backend.zardamhussain.workers.dev`. Its ownership, availability, retention and model behavior are not validated here. The Worker source contains request logging/storage paths, so the app does **not** promise that uploaded content is never stored.

- Online AI starts disabled. No automatic account lookup is performed at launch.
- Generate sends only the explicitly selected text/image, instructions/tone and device identifier. HTTPS is required; URLs containing credentials, queries or fragments are rejected.
- Endpoint changes invalidate consent. Network tasks freeze the chosen endpoint and device ID for each request and discard cancelled/stale responses.
- Conversation limit: 12,000 UTF-16 units; instruction limit: 500, matching the Worker's JavaScript string limits. Image upload limit is intentionally below the server's total request budget.
- Ephemeral URLSession configuration avoids normal persistent HTTP cache/cookie storage. App drafts/context live in memory; explicit Copy puts the selected text on the clipboard.
- [Privacy manifest](Config/PrivacyInfo.xcprivacy) declares UserDefaults reasons and app-functionality content/device-ID processing. Review it, backend practices and App Store privacy answers before distribution; it is not a privacy/security certification.

No backend endpoint was contacted during this iOS implementation or static validation.

## Build and test

From this folder, after installing Xcode:

```sh
bash tools/build_ios.sh build
IOS_TEST_DESTINATION='platform=iOS Simulator,name=iPhone 16 Pro' bash tools/build_ios.sh test
```

Use an actually installed simulator name. The default build compiles an unsigned simulator app, not a phone-installable IPA. The [build helper](tools/build_ios.sh) also supports `archive` with `DEVELOPMENT_TEAM` set, leaving distribution/export to Xcode Organizer.

The Foundation model/API tests can also run with `swift test` using a compatible Swift toolchain. [Package.swift](Package.swift) deliberately excludes UIKit/SwiftUI app and extension code from that package; it does not build an installable application.

Before device delivery, validate:

- Xcode build and all five [unit tests](Tests/WingAITests.swift).
- Portrait/landscape on iPhone 16 Pro, safe areas, large text, VoiceOver and light/dark themes.
- Globe switching, return/delete behavior, secure-field fallback and Full Access toggling.
- Full Access off: typing works and clipboard/network actions are blocked.
- Consent off: no account or AI request; paste/image selection alone never uploads.
- Generating, cancellation, service errors, quota limits and backend changes using non-sensitive sample data.
- Image import/cancellation, correct image orientation and memory usage.
- Process relaunch with local preferences, and optional App Group sharing on a properly provisioned device.

For static checks only:

```sh
python3 tools/validate_project.py
# Optional grammar-only parser dependencies:
python3 -m pip install tree-sitter==0.26.0 tree-sitter-swift==0.7.4
python3 tools/validate_project.py --swift-grammar
```

The [project generator](tools/generate_xcode_project.py) is deterministic and refuses to replace different generated files, so it won't silently erase changes made in Xcode.

## Apple references

- [Custom Keyboard extension guide](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html)
- [Extension Info.plist keys](https://developer.apple.com/library/archive/documentation/General/Reference/InfoPlistKeyReference/Articles/AppExtensionKeys.html)
- [PhotoKit privacy and user-selected photos](https://developer.apple.com/documentation/PhotoKit/delivering_an_enhanced_privacy_experience_in_your_photos_app)
- [Required-reason API manifest guidance](https://developer.apple.com/documentation/technotes/tn3183-adding-required-reason-api-entries-to-your-privacy-manifest)
- [Apple Developer program terms](https://developer.apple.com/support/terms/apple-developer-program-license-agreement/)
