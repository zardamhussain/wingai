# Privacy and Security Policy

## 1. User Control Over Screen Capture
- The app does not request screen capture permission during initial setup.
- The app captures the screen only when the user explicitly taps **📸 Read** on the WingAI keyboard bar.
- The app does not store captured images in the photo gallery or in application logs.
- Optional capture preview before upload can be enabled in settings.

## 2. Sensitive Input Protection
- `PrivacyGuard` inspects `EditorInfo.inputType` before any AI operation.
- When a password field is active, the app disables AI screen analysis and text insertion.
- The app reads the clipboard only when the user explicitly taps **Paste Chat** and displays the pasted text inside the AI panel so the user can review or clear it.
- The app does not use `AccessibilityService`, floating overlays, or background clipboard listeners.

## 3. Backend Data Handling
- All requests use HTTPS (`https://wingai-backend.zardamhussain.workers.dev`).
- The Cloudflare Worker processes conversation text and images in memory only and discards them immediately after generating replies.
- Only pseudonymous device quota metadata (`user_id`, `tier`, `daily_used`, `usage_date`) is stored in Cloudflare D1. Optional Firebase login can associate that device ID with a named account, so the identifier should not be described as anonymous.
- The Android client does not contain the AI provider API key.

## 4. Optional Firebase Login and Device Association
- Google/email login uses Firebase Authentication. Google credentials are exchanged with Firebase, not saved to profile documents or logged by the app.
- From Android build 3816, the private Firestore profile stores the user's name, email, existing WingAI device ID (`wingaiDeviceId`) and server timestamps. The device ID is an app-generated installation identifier, not a hardware/advertising identifier.
- The field records the most recently synced installation; the app does not maintain a device history in this version. The same installation can be associated with different accounts when users switch accounts.
- Owner-only rules are provided and must be published in the live Firebase project. This metadata must not be trusted as device ownership or an entitlement to a paid plan.
- Login does not upload typed text, screenshots, chats or clipboard data. Existing user-triggered AI requests are a separate flow.
- Signing out clears the displayed account association, not the local device ID. Deleting the Firebase account removes the cloud profile/device-ID field but does not erase local keyboard data or the existing Cloudflare device record.
- See [device-ID sync and troubleshooting](device-id-sync.md) for exact behavior and deployment requirements.
