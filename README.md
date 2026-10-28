# WingAI Keyboard & Floating Emoji MVP

WingAI is an Android AI keyboard (`InputMethodService`) and draggable Floating Emoji overlay (`SYSTEM_ALERT_WINDOW`) paired with a Python FastAPI + Google Gemini backend.

## Features
1. **Android AI Keyboard**: Standard QWERTY, numbers, symbols, emoji picker, cursor keys, and an upper AI toolbar. Works offline for normal typing.
2. **Draggable Floating Emoji**: Place the floating emoji anywhere on the screen.
   - **Double-tap** (default): Reads the visible screen with user permission and generates 3 contextual replies.
   - **Single-tap** (default): Opens or closes the floating AI control panel.
   - **Long-press** (default): Pastes conversation context from the clipboard.
   - All three gestures are configurable in the app settings.
3. **Single-Frame Screen Capture**: Uses `MediaProjection` to capture one frame, crops system bars, allows user inspection/cancellation before upload, and immediately releases capture resources.
4. **Tone & Rewrite Controls**: Supports `Casual`, `Friendly`, `Professional`, `Funny`, `Flirty`, `Short`, and `Detailed` tones, custom instructions, and `Rewrite`, `Shorten`, and `Expand` actions.
5. **Privacy & Safety**: Never reads password fields, never monitors the screen or clipboard in the background, and never sends messages automatically.

## Running the Backend
```bash
cd backend
export GEMINI_API_KEY="your-gemini-api-key"
.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Running Backend Tests
```bash
PYTHONPATH=backend backend/.venv/bin/python -m unittest discover -s backend/tests -v
```

## Building & Testing the Android App
```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```
