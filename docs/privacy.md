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
- Only anonymous quota metadata (`user_id`, `tier`, `daily_used`, `usage_date`) is stored in Cloudflare D1.
- The Android client does not contain the AI provider API key.
