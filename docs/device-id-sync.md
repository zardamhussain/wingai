# WingAI device-ID sync and Google sign-in troubleshooting (3816)

## What changed

The user reported that the app closes or freezes around Google sign-in and requested saving the existing WingAI device user ID to the database. The signing certificate, Android OAuth certificate entry and generated web client ID were checked and match. `adb devices -l` reported no connected Android device, so the specific on-device crash/ANR has **not been reproduced or conclusively diagnosed**.

Version 3816 adds targeted safeguards rather than claiming a confirmed root-cause fix:

- Credential Manager is constructed lazily inside a guarded request, not during the auth Activity's creation. Unexpected provider runtime exceptions become safe UI errors.
- The asynchronous chooser wait is capped at 120 seconds and has a **Cancel Google sign-in** action. Firebase authentication after selection retains a separate 40-second bound.
- Google progress identifies choosing an account versus authenticating. Window icon flags update only on palette/screen changes, not every recomposition.
- Successful authentication releases the login controls immediately. Profile/device-ID saving runs as a separate job; the Firestore/device-store adapter runs on an IO dispatcher. Sign-out/back remain available during a slow database call.
- Profile failures are separate from Google login failures. Permission errors identify the rules problem without exposing raw SDK exception text, account credentials or tokens.
- A restored authenticated session also syncs its device ID, so upgrading does not require creating another account.

Fatal VM/native crashes and OEM/Play-services ANRs cannot be ruled out by mocked provider tests. A device crash trace is still required if the original problem persists.

## Database location and exact meaning

In Firebase project **keyboard-f9ed8**, open **Firestore Database > Data > users > the Firebase Authentication UID**.

The document now contains:

```json
{
  "displayName": "Example User",
  "email": "example@example.com",
  "wingaiDeviceId": "wing_a1b2c3d4e5",
  "createdAt": "server timestamp",
  "updatedAt": "server timestamp"
}
```

The values above are illustrative, not an actual user's record. `wingaiDeviceId` is read using the existing `SettingsRepository.getOrCreateUserId()` and the original application-context DataStore. It is the same value shown under **WingAI Account & Plan > WingAI Device User ID**, not the Firebase UID, Android hardware ID, advertising ID, or a new ID generated for each login. An already saved ID is not replaced.

**Multiple devices:** this single root field records the most recently synced installation. Signing into the same account on another device updates the field; this version does not keep a device history. Different users using the same installation can report the same device ID. This is deliberately **client-reported informational metadata**, not a uniqueness/ownership assertion. Never use it alone to assign paid tiers, reset quotas, transfer subscriptions or authorize the legacy Worker API. Those flows are unchanged.

The app displays **Device ID saved to your private cloud profile** only after a server-acknowledged transaction. If the UI times out, it says the save is not confirmed; an SDK task may still complete later. Retry **Sync account profile** to confirm. Signing out clears the account display, not the installation's saved ID. Deleting the Firebase profile removes this cloud copy but does not erase the local/Worker device identity.

## Required live Firestore rule update

**The rules bundled with earlier builds reject the new field.** Review and publish [the updated rules](../firebase/firestore.rules) before expecting secure device-ID writes to succeed.

1. Firebase Console > project **keyboard-f9ed8** > Firestore Database > **Rules**.
2. Merge the updated `users/{uid}` policy into any existing production rules. If this project only has the WingAI profile collection, the provided file is a complete policy.
3. Publish. Do not enable unrestricted/test-mode writes as a workaround.
4. Install [the updated APK](../artifacts/apk/WingAI-login-debug-arm64.apk) over the existing app using the same signing certificate. **Do not uninstall**, because that can erase the local device ID and settings.
5. Sign in or reopen the login account screen for an existing session. Use **Sync account profile** if needed. Compare the saved field with the original Account & Plan device ID.

Rules permit only the authenticated owner to read/write their profile, allow the new field only in the existing `wing_` plus 10 lowercase hexadecimal format, and still reject roles/tiers/quotas/verification fields. The device field is optional in the rules for older-client compatibility; the new app always sends it. Creation timestamps remain immutable on update. All 17 emulator rule tests passed locally.

No live Firebase rules were deployed by this session: no authenticated administrator connection or default Firebase CLI login file was available. The supplied mobile configuration is not an admin credential. Do not embed or share service-account keys.

## Safe error codes

- `GOOGLE_START`: provider startup/response exception; update Google Play services and retry, or use email login.
- `GOOGLE_PROVIDER`: Credential Manager request failure; check network, provider enablement and signing configuration.
- `GOOGLE_CHOOSER_TIMEOUT`: chooser did not return within 120 seconds.
- `GOOGLE_AUTH_TIMEOUT`: Firebase authentication did not finish within its timeout.
- `PROFILE_PERMISSION_DENIED`: login succeeded, but Firestore rules rejected the profile/device-ID write. Publish the updated policy.
- `PROFILE_UNAUTHENTICATED`: refresh the login session.
- `PROFILE_UNAVAILABLE` / `PROFILE_TIMEOUT`: database/network delay; login remains valid and save is not yet confirmed.
- `PROFILE_DEVICE_ID`: an existing nonstandard local ID failed validation; it is not silently changed.

If the app truly exits again, reopen WingAI settings and use the existing **Crash report files found > get** export flow. That built-in report contains version/device information and the stack trace; review and redact private data before sharing it. Do not post passwords, Google ID tokens, Firebase refresh tokens or administrator keys. A screenshot of a safe error code is preferable when the app remains open.
