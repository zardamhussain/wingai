# Architecture Specification

## 1. System Overview
WingAI Board consists of an Android keyboard client (`android/`, built on HeliBoard v3.8) and a serverless Cloudflare Worker + D1 backend (`worker/`).

## 2. Android Client Components (`android/app/`)
- **IME Service (`helium314.keyboard.latin.LatinIME`)**: Full HeliBoard input method service providing layouts, dictionaries, gesture typing, and `InputConnection` management.
- **WingAI Toolbar & Panel (`WingAiStripView`, `AiControlPanel`)**: Embedded directly into `suggestions_strip.xml` and `main_keyboard_frame.xml`. Provides `[📸 Read]`, `[✨ Ask]`, `[Paste Chat]`, tone chips, and 3 reply cards locked to the keyboard's exact height.
- **Screen Capture (`ScreenCaptureActivity`, `ScreenCaptureService`, `ScreenCaptureManager`)**: Requests `MediaProjection` permission on user action, captures the visible screen frame, crops system bars, and sends the frame to the backend.
- **Input Bridge (`InputConnectionBridge`)**: Connects `LatinIME` and `AiSessionController` to insert selected replies or route typed custom instructions without ever sending messages automatically.
- **Account & Settings (`SettingsRepository`, `UserAccountScreen`)**: Persists the device's unique `user_id` (`wing_...`) and preferences in Jetpack `DataStore` and displays plan tier (`Free` 5/day vs `Paid` unlimited) and usage in **WingAI Board Settings**.

## 3. Cloudflare Worker & D1 Backend (`worker/`)
- **API Worker (`worker/src/index.ts`)**: Hono-based Cloudflare Worker deployed at `https://wingai-backend.zardamhussain.workers.dev` exposing `GET /v1/health`, `GET /v1/user`, `POST /v1/analyze`, `POST /v1/replies`, and `POST /v1/rewrite`.
- **User Quota Store (`Cloudflare D1 - wingai`)**: Stores `users (user_id, tier, daily_used, usage_date)`. Automatically resets `daily_used` on a new UTC date and enforces 5 requests/day for `free` users while allowing unlimited requests for `paid` users.
- **AI Service**: Calls Google Gemini (`gemini-3.8-flash`) with structured JSON output schema to return 3 contextual replies.
- **Security Controls**: Enforces payload size limits, per-IP rate limits, and keeps `GEMINI_API_KEY` in Cloudflare Worker secrets.
