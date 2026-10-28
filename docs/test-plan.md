# Acceptance Test Plan

| ID | Test Case | Verification Method |
|---|---|---|
| AT-01 | Keyboard activation | Enable WingAI Board in Android Settings, focus a text field, and verify the keyboard opens. |
| AT-02 | Normal typing offline | Disconnect network, type letters/numbers/punctuation/emoji, and verify text commits normally. |
| AT-03 | Screen permission | Tap `📸 Read` on the keyboard bar and verify Android shows the `MediaProjection` permission dialog on first use. |
| AT-04 | Permission denial | Deny the dialog and verify no capture occurs and `Paste Chat` remains available. |
| AT-05 | Context analysis | Approve capture or tap `Paste Chat` and verify `/v1/analyze` or `/v1/replies` receives only the user-approved context. |
| AT-06 | Reply generation | Verify the backend returns three replies (`casual`, `friendly`, `professional`). |
| AT-07 | Tone modification | Select a tone chip (`Funny`, `Flirty`, `Short`, `Detailed`) and verify new replies match the selected tone. |
| AT-08 | Text insertion | Tap `Insert` on a reply card and verify the text appears in the active field. |
| AT-09 | No automatic send | Verify `InputConnection.commitText` inserts the draft without submitting or sending the message. |
| AT-10 | Password privacy | Focus a password field and verify `PrivacyGuard` blocks AI screen analysis and insertion. |
| AT-11 | User quota & tiers | Verify `free` users are limited to 5 requests per UTC day (HTTP `429` on request 6) and `paid` users have unlimited requests. |
| AT-12 | Protected content | Capture a `FLAG_SECURE` window and verify the app handles blocked capture gracefully. |
