# WingAI Board

WingAI Board (`com.wingai.keyboard`) is a production Android AI keyboard built on HeliBoard v3.8 (`LatinIME`) paired with a Cloudflare Worker + D1 + Google Gemini backend (`worker/`).

## Features
1. **Full Android Keyboard (`WingAI Board`)**: Native QWERTY, swipe/gesture typing, autocorrect, dictionaries, numbers, symbols, emoji picker, and clipboard history. Works offline for normal typing.
2. **Integrated AI Toolbar & Reply Panel**:
   - **📸 Read**: Captures the visible chat screen with `MediaProjection` permission and generates 3 contextual replies.
   - **✨ Ask**: Type custom instructions on the keyboard (e.g., *"Tell them I am busy today. Suggest tomorrow."*) to guide reply generation.
   - **Paste Chat**: Reads copied conversation text from the clipboard, displays the pasted context preview, and generates replies when screen capture is not used.
   - **🪽 AI / ⌨️ Keys**: Switches seamlessly between the keyboard keys and the AI reply cards while preserving exact keyboard height.
3. **Tone & Rewrite Controls**: Supports `Casual`, `Friendly`, `Professional`, `Funny`, `Flirty`, `Short`, and `Detailed` tones, inline editing, and `Rewrite`, `Shorten`, and `Expand` actions.
4. **User Plans & Daily Quota (`Cloudflare D1`)**:
   - **Free Plan**: 5 AI requests per UTC day (auto-resets daily).
   - **Paid Plan**: Unlimited AI requests.
   - **WingAI Account & Plan**: View your User ID, current plan (`Free` or `Paid`), and remaining daily usage inside **WingAI Board Settings** or from the keyboard AI panel.
5. **Privacy & Safety**: Never reads password fields, never monitors the screen or clipboard in the background, and never sends messages automatically.
6. **Optional Firebase Login**: Google sign-in via Android Credential Manager, email/password sign-in, registration, password reset, verification and account controls, available from **WingAI Account & Plan**. Existing keyboard behavior, settings, device IDs, paid plans and AI quotas are unchanged. See [Firebase setup and implementation notes](docs/firebase-login.md).

## Android login build

- [ARM64 debug APK with device-ID sync and Google safeguards, version 3816](artifacts/apk/WingAI-login-debug-arm64.apk)
- [Installation and verification notes](artifacts/apk/INSTALL.md)
- [Verified Firebase SHA-1 and SHA-256 signing fingerprints](docs/firebase-signing-fingerprints.md)
- [Turquoise login screen render](artifacts/auth-ui/sign-in-reference.png)
- [Turquoise sign-up screen render](artifacts/auth-ui/signup-reference.png)
- [Firestore access rules](firebase/firestore.rules)
- [Device-ID database setup and Google sign-in troubleshooting](docs/device-id-sync.md)

The supplied Firebase configuration matches the app and its debug signing certificate. **Ensure Google and Email/Password are enabled in Firebase Authentication and review/publish the Firestore rules before live use.** No Firebase administrator connection was available in this session, so production settings were not changed. The screen images are native Compose test renders, not installed-device screenshots. Live Google account authorization still needs device testing.

Version 3815 applies the user's reference to the **whole login/signup UI**: a turquoise illustrated header, original WingAI chat/keyboard artwork, a full-width white sheet with rounded top corners, centered titles, pale compact inputs, teal action buttons and Google sign-in below the form. Only login/signup/reset use this fixed light style; the keyboard and signed-in account theme remain unchanged. Google/email authentication and account actions from version 3814 are retained. No Apple sign-in or decorative nonfunctional checkboxes were introduced.

Version 3816 retains that UI and saves the existing installation's WingAI ID as `users/{firebaseUid}.wingaiDeviceId`. The ID is informational metadata, not a replacement for the Firebase UID or a plan/ownership credential. Profile saves are non-blocking, restored sessions backfill the field, and the account screen confirms server-acknowledged saves. **Publish the updated Firestore rules; earlier rules reject the added field.** Google provider startup is guarded and chooser waiting is cancellable/bounded. The reported on-device closing/freezing issue still needs a device trace/retest for root-cause confirmation.

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
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/WingAI-Board-3.8-wingai-debug.apk
```
