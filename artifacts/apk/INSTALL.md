# WingAI login APK - installation and verification

## Download/build identity

- [WingAI login ARM64 debug APK](WingAI-login-debug-arm64.apk)
- Package: `com.wingai.keyboard`
- Version code: **3816**; version name: **3.8-wingai**
- Minimum Android API: **26**; target API: **36**
- Architecture: **arm64-v8a** (not 32-bit ARM or x86)
- Signed with the available **Android Debug** certificate, not a production release key.

APK SHA-256:

```text
2092ee37cd3cbcff52a27b367a5b94e46d4e8f48ed6882e1c572e26d4adf526e
```

## Before testing live login

For Firebase Google provider setup, use [the verified signing-certificate fingerprints](../../docs/firebase-signing-fingerprints.md), **not the APK file checksum above**. Version 3816 uses the same debug signing certificate as versions 3812-3815.

In Firebase project **keyboard-f9ed8**, ensure **Authentication > Sign-in method > Google** is enabled with a support email, and retain **Email/Password** for email login. Enabling Cloud Firestore alone does not enable authentication. Review and publish [the owner-only profile rules](../../firebase/firestore.rules), merging them with any existing production policies. These cloud changes were **not deployed** in this session because no Firebase administrator login was available.

See [the complete setup/security notes](../../docs/firebase-login.md). The user's updated app-module configuration includes matching Android and web OAuth clients and was preserved without editing it. **Continue with Google** opens Android Credential Manager and exchanges the Google ID token with Firebase. Both login/signup pages support Google; email/password and compact input fields are retained. Google sign-out clears provider state, and Google account deletion uses same-account Google reauthentication rather than a password.

Version 3815 restyles only login/signup/reset to the supplied reference: turquoise illustrated header, original WingAI artwork, rounded white form sheet, centered titles, pale compact fields, teal buttons and the Google button below the form. These pages use a fixed light palette, independently of the existing keyboard and signed-in account themes. No fake Apple sign-in or new authentication behavior was added.

Version 3816 adds the requested device association at `users/{firebaseUid}.wingaiDeviceId`, using the original keyboard DataStore ID. **Review and publish the updated Firestore rules; earlier rules reject this field.** Saves are non-blocking and only acknowledged saves show a confirmed ID. Restored sessions backfill on account-screen entry. [Database setup and troubleshooting](../../docs/device-id-sync.md).

Google provider startup is now lazy/guarded, the chooser wait has a cancel action and 120-second watchdog, and database errors are distinct from Google authentication errors. **The user's specific app-closing/freezing incident has not been reproduced on a device:** no device/crash trace was connected, so these safeguards are not a conclusive root-cause fix. Re-test on the affected phone; the troubleshooting notes explain the existing crash-report export flow if it persists.

## Install

Transfer the APK to an ARM64 Android device and open it, or run from the project root:

```sh
adb install -r artifacts/apk/WingAI-login-debug-arm64.apk
```

**Preserve your existing data:** an in-place update is possible only if the installed app uses the same signing certificate. If Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, rebuild using the original app's signing key. Do not uninstall the existing app just to bypass that error unless you have exported your data and accept losing local settings/dictionaries/device identity. No device installation or uninstallation was performed here.

Open **WingAI Board Settings > WingAI Account & Plan > Sign in / Create account > Continue with Google**. Choose your Google account and complete any consent prompt. The login screen is optional; **Continue without signing in** returns to the original application.

Google sign-in requires Google Play services and a Google account on a supported Android device. Cancellation returns to the form without an error. If no account/provider is available, the screen explains the fallback to email login. For configuration errors, verify the Google provider, package name, installed APK certificate and generated web client ID; do not use the Android OAuth client ID as the web client ID.

This is a debug/testing APK, not a Play Store release artifact. Sign a release build with your existing release keystore after completing device tests and Firebase setup.

## Verification performed

- **APK assembly: passed.** The current native keyboard sources were built, and the packaged APK contains `libjni_latinime.so` for ARM64. [Build evidence](../verification/apk-tests-success.log).
- **108 Android tests: passed**: 8 validation, 38 account-state, 23 Compose interaction/render, 13 Google credential-controller, 8 Firebase adapter, 4 credential-storage/direct-boot, 3 keyboard bridge, 6 profile schema/error, and 5 Firestore device-ID adapter tests. Coverage includes a live login/account UI transition, stalled chooser cancellation/timeouts, non-blocking profile writes, rules-denial handling and reusing the real SettingsRepository ID. Google/Firebase endpoints are mocked in adapter tests; no production login or database write is claimed. [UI results](../verification/android-tests/TEST-com.wingai.keyboard.auth.AuthContentTest.xml) and [device-ID adapter results](../verification/android-tests/TEST-com.wingai.keyboard.auth.FirestoreDeviceIdAdapterTest.xml).
- **17 Firestore emulator security-rule tests: passed.** These used the local demo project, not production. They include valid ID writes, backfills, malformed IDs, cross-user access and privilege-injection denial. [Rule test evidence](../verification/firestore-rules-tests.log).
- **APK signature: passed (v2)** and **ZIP alignment: passed**, including the 16 KiB alignment check. [Signature](../verification/apk-signature.txt) and [alignment](../verification/apk-alignment.txt) evidence.
- Manifest checks confirmed the non-exported/non-direct-boot auth activity, removal of Firebase's automatic provider, and preservation of the keyboard's device-protected default.
- Both auth-session backup-exclusion filenames were checked against the Firebase app ID, which is unchanged. The updated app-module configuration includes Google OAuth clients; the older root-level copy does not. Neither user file was edited by the agent.
- Existing keyboard, AI, capture, settings repository, data bridge, Worker and iOS source paths were checked for unintended changes; no changes were made there.
- Current [login](../auth-ui/sign-in-reference.png) and [signup](../auth-ui/signup-reference.png) renders were inspected. They are actual Compose test renders of the new reference palette, **not installed-device screenshots**. Older screenshots are historical.
- [Confirmed device-ID UI](../auth-ui/device-id-saved.png) is a native Compose test render using a synthetic test identity, not a live user's database record.

**Project-wide lint is not green:** [the report](../verification/lint-results-debug.txt) contains 65 errors in unchanged legacy files (64 missing-translation findings and one existing theme configuration-read finding). No auth-file lint findings remain. These unrelated issues were not hidden by a project-wide suppression or a new lint baseline.

## Still required on a real device

1. Upgrade with a matching signing key; confirm keyboard settings and device ID are retained.
2. Create an account, receive the verification email, follow the link and refresh verification.
3. Sign out/in, restart the app and confirm the session is restored.
4. Request a reset link and sign in with the changed password.
5. Test offline auth/profile errors; ensure the keyboard still types normally.
6. Check small screens, landscape, large text, TalkBack, autofill, soft-keyboard insets and your selected theme.
7. Reboot without unlocking and verify the existing keyboard still works; account access must wait for unlock.
8. Delete a disposable test account using its current password; confirm both Auth user and Firestore profile are removed. Device-plan data intentionally remains unchanged.
9. Tap **Continue with Google**, select an account and confirm the Firebase session/profile. Test chooser cancellation, no available Google account, sign-out and restart.
10. Delete a disposable Google-linked WingAI account: choose a different Google account first and verify deletion is rejected; then confirm with the correct account. Only the WingAI account/profile should be deleted, not the Google account or device plan.
11. After publishing the updated rules, compare Firestore `users/{uid}.wingaiDeviceId` with the existing WingAI Account & Plan device ID. It should match without changing local settings or quotas. Check restored-session backfill and offline/rules-denied retry states.

Real Google authorization/consent, email delivery, production Firebase rules/providers, OEM direct-boot behavior and on-device runtime behavior have **not** been verified by these local tests.
