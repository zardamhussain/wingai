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
adb install -r app/build/outputs/apk/debug/HeliBoard_3.8-wingai-debug.apk
```
