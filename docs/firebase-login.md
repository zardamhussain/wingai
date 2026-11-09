# Android login addition

## Scope and design intent

Requested: add an attractive modern login system to the original Android app, without replacing its UI or changing current functionality. All source changes and deliverables are inside this checkout; the other WingAI project is untouched.

Entry: **WingAI Board Settings > WingAI Account & Plan > Sign in / Create account**.

The only addition to the existing Account & Plan screen is an account-entry card. The separate Compose activity provides autofill semantics, password visibility controls, inline validation, loading/error states, scroll/IME handling and a guest exit. Version 3815's login/signup/reset pages use the user's reference-style turquoise/white palette; signed-in account management retains its existing theme. Existing plan cards, device IDs, keyboard layouts, settings, capture, AI requests and Worker endpoints are not replaced or migrated.

Login is deliberately **optional** to preserve current behavior. It is not an authorization retrofit for the existing Cloudflare API. Firebase identity is not sent as a replacement for the current `wing_*` device ID and does not grant a paid plan, reset a quota, or transfer a subscription between devices.

### Compact input refinement (version 3813)

The user's follow-up reference shows slim, softly filled pill fields with small icons, rather than large outlined/floating-label boxes. [CompactAuthField](../android/app/src/main/java/com/wingai/keyboard/auth/CompactAuthField.kt) applies that treatment to name, email, password, confirmation, reset and deletion-password inputs. Fields use 14 sp entered text, 13 sp placeholders, a 24 dp corner radius and a 48 dp minimum height; the height expands for larger text rather than clipping it. Form gaps are 12 dp and inner padding is 16 dp. Password visibility uses an eye button with a 48 dp touch target and an explicit accessibility label. Autofill types, validation and all account actions are retained.

The signing certificate is unchanged. [Copyable SHA-1/SHA-256 fingerprints and Google provider setup instructions](firebase-signing-fingerprints.md) apply to both the email/password and Google-enabled builds.

### Google sign-in (version 3814)

**Continue with Google** is now wired on both sign-in and create-account pages. It launches Android Credential Manager's explicit `GetSignInWithGoogleOption`, using the generated web OAuth client ID from the user's updated configuration. A fresh random nonce is included on each request. The returned Google ID token is passed directly to Firebase `signInWithCredential(GoogleAuthProvider.getCredential(...))`; Firebase validates identity, not client-decoded token claims. No token is saved to Compose state, preferences, logs or Firestore by this code.

The Activity lifecycle owns the chooser; closing it is not an error, and destroying the Activity cancels pending chooser work and releases the busy state. Duplicate taps and simultaneous email submission are blocked. Version 3816 adds a 120-second chooser watchdog and a visible cancellation action; network/authentication work after selection has a separate 40-second bound. Missing accounts, unsupported devices, provider failures and account-collision errors receive safe messages; email login remains available. No custom silent account-linking/merge logic was added; Firebase's own provider policy governs identity matching.

Google accounts use the same minimal cloud profile and Firebase session persistence. Sign-out clears Firebase first and requests `CredentialManager.clearCredentialState`; a missing provider cannot prevent local sign-out. Google-linked users confirm deletion by selecting the same Google identity and calling `reauthenticate` on the captured current Firebase user, never by signing in as a different user. UID checks and Firebase's user-mismatch rejection precede profile deletion. The device plan and Google account itself are not deleted.

Dependencies added: Credential Manager and its Play Services adapter **1.5.0**, Google ID **1.1.1**, compatible with this existing Kotlin toolchain. The Google button uses the official multicolor mark from [Google's branding assets](https://developers.google.com/identity/branding-guidelines), with no extra OAuth scopes for contacts, files or other Google data.

**Live Google authorization still requires a real supported Android device, Google Play services, an account, network access and an enabled Firebase Google provider.** Local tests substitute the credential/Firebase SDK endpoints; they do not claim a real Google login was performed. No user account was accessed and no production Firebase settings were modified by the agent.

### Full reference-style UI (version 3815)

The user explicitly requested the complete login/signup composition from the supplied image, not only smaller fields. [WingAiLoginVisuals](../android/app/src/main/java/com/wingai/keyboard/auth/WingAiLoginVisuals.kt) provides a turquoise illustrated header and a full-width white sheet with 32 dp top corner radii. Original vector-drawn WingAI chat bubbles and a phone keyboard replace the reference's third-party product artwork. A centered **Login**/**Sign up** title and a real text-link mode switch replace the former tab chips. Inputs remain pale filled 48 dp-minimum pills; the main action is teal and the neutral Google button sits under **Or continue with**.

The fixed light palette applies only to login/signup/reset, even when the keyboard uses a dark theme. Status/navigation icon contrast is set for this palette and restored for the signed-in theme. Text fields can grow with font scale, the mode-switch row wraps, and the natural-height form scrolls in small/landscape/IME-reduced viewports instead of squeezing or clipping its children. The large white header uses a slightly deeper teal than the reference for readability; normal-sized primary button text has approximately 5.1:1 white/teal contrast.

The login form keeps name only on registration, retains confirmation, password reset, Google authentication, guest exit and every existing validation/error action. No fake Apple provider, fake checkbox or new compulsory onboarding was added. Firebase repositories, OAuth configuration, device IDs, plans, quotas and keyboard behavior were not changed for this UI refinement.

Current native Compose test renders: [login](../artifacts/auth-ui/sign-in-reference.png), [signup](../artifacts/auth-ui/signup-reference.png) and [password reset](../artifacts/auth-ui/reset-password-reference.png). Older render files remain historical and do not represent the current login/signup styling.

### Google stability safeguards and device-ID saving (version 3816)

The user reported that Google sign-in closes or freezes the app. No Android device or crash trace was available, so the exact failure is **not conclusively reproduced or diagnosed**. Provider initialization now happens inside a guarded, lazy request; unexpected SDK runtime exceptions are contained, chooser waiting is cancellable/bounded, and window flag updates are keyed to actual palette changes. Firestore synchronization no longer holds the global authentication busy state: it runs separately, with the repository on an IO dispatcher. Tests cover the live login-to-account UI transition and slow/failed database cases; they do not prove an OEM/native crash is fixed.

At the user's explicit request, the profile now saves `wingaiDeviceId`, using the **existing** keyboard `SettingsRepository` DataStore ID. It never substitutes the Firebase UID or regenerates an existing installation ID. Sync runs after Google/email login or registration and for restored sessions when the account screen opens. A success label appears only after the server transaction is acknowledged; failed/slow writes show a separate profile status and remain retryable without logging out.

The root field represents the most recently synced installation, not a complete multi-device history or proof of ownership. Do not use this client-reported field to grant tiers, transfer subscriptions, reset quotas or authorize Worker requests. Updated owner-only Firestore rules allow and validate the field while retaining legacy four-field-profile compatibility. **The updated rules must be reviewed/published; no live Firebase rules were deployed by this session.** See [device-ID setup and troubleshooting](device-id-sync.md).

## Included

- Email/password sign-in and registration, Firebase-managed persistent sessions.
- Google sign-in/sign-up through Credential Manager, with cancellation/error handling and Firebase token exchange.
- Name, email, password and confirmation validation; existing shorter passwords are accepted on sign-in.
- Password reset with an account-existence-neutral success message.
- Verification email, resend cooldown and explicit verified-status refresh.
- Signed-in profile, sign-out confirmation, provider-state clearing, and password or Google reauthentication for deletion.
- Minimal Firestore profile at `users/{firebaseUid}` containing `displayName`, `email`, `wingaiDeviceId`, `createdAt`, and `updatedAt`.
- Owner-only Firestore rules with schema restrictions. Clients cannot store roles, tiers, quotas, verification state or another user's email.
- Firestore failure does not roll back a successful login or disable the keyboard. A visible retry action is provided.

## Supplied Firebase configuration

Both supplied configuration copies identify Firebase project **keyboard-f9ed8** and Android package **com.wingai.keyboard**. The Android build consumes [the app-module configuration](../android/app/google-services.json). During the compact-field refinement, the user updated that copy to include an Android OAuth client matching the delivered debug APK certificate and a web OAuth client. The older root copy has no OAuth clients. The agent preserved both files without editing them.

The generated Android resources contain OAuth `default_web_client_id`, consumed by the Google credential request in version 3814. The app-side flow and Google account-management support are implemented. Live provider/device verification remains a release gate.

The existing `debugNoMinify` variant uses package `com.wingai.keyboard.debug`; it needs a separately registered matching Firebase client/configuration before that variant can be built with Google Services. Debug and release use the supplied package name.

## Required Firebase console steps (not performed by this session)

A mobile `google-services.json` identifies an app; it is **not** an administrator credential. No authenticated Firebase CLI session, service account or Firebase admin connector was available here. No production rules, users, auth providers, database contents or billing settings were changed.

1. Open project **keyboard-f9ed8** in the [Firebase console](https://console.firebase.google.com/project/keyboard-f9ed8/overview).
2. **Authentication > Sign-in method > Email/Password**: enable the password provider. Email-link sign-in is not needed.
   Also ensure **Google** is enabled, a support email is selected, and the Android app's SHA-1 matches the installed APK's signing certificate. The supplied OAuth config matches this debug certificate.
3. In Authentication settings, use a minimum password length of at least 8; enable email-enumeration protection. The app also displays server-side stronger-password-policy errors.
4. Review the sender name and verification/reset email templates. Test delivery to a real inbox.
5. Review [the Firestore rules](../firebase/firestore.rules) against any existing collections and publish the `users/{uid}` policy. Do not keep Firestore in open test mode. If the project already serves another application, merge the policy into its reviewed rules rather than blindly replacing all rules.
6. Test registration, verification, reset, sign-out, session restoration and deletion on an Android device before production release.

Optional command-line deployment, **only after authenticating as the project owner and reviewing current remote rules**:

```sh
cd firebase
npm ci
npx firebase login
npx firebase deploy --only firestore:rules --project keyboard-f9ed8
```

No service account belongs in the Android app, APK, repository or chat. Do not send private Firebase administrator keys.

## Privacy and direct boot

- No analytics dependency was added. No typed text, screenshots, clipboard content, AI prompts, passwords, tokens, tiers or quotas are written to the login profile. The existing pseudonymous WingAI device ID is now saved alongside name/email at the user's request; this association links the installation identity to the signed-in account.
- Firebase Auth owns the password/token protocol. The app does not manually store passwords or tokens; passwords are not saved in Compose instance state and raw SDK error details are not shown.
- The auth activity is non-exported, not direct-boot-aware, and uses `FLAG_SECURE` to protect its screen and recents thumbnail.
- The keyboard retains its existing device-protected default and direct-boot behavior. Firebase's automatic initialization provider is removed, and a named account Firebase app is initialized only through the account UI after unlock.
- Because this legacy app defaults to device-protected storage, the account context uses Android ContextImpl's credential-storage flag (`0x10`) through `createPackageContext`; this flag is not a named public SDK constant. The result is checked at runtime and sign-in fails closed if credential storage is not obtained. A ContextWrapper prevents Firebase's `getApplicationContext()` call from reverting to keyboard storage. API-level storage tests accompany this compatibility path; an actual locked-boot device smoke test remains required.
- Backup rules exclude this named Firebase Auth preference file from both cloud backup and device transfer, without disabling existing keyboard backups. **If the Firebase app ID or named app changes, regenerate the encoded filename in both backup XML files.**
- Firestore uses an in-memory cache and server transactions rather than persistent offline profile writes. Outstanding profile-write tasks are tracked so deletion waits for them even if their UI coroutine timed out.

## Account deletion semantics

Deletion confirms the password or Google identity using Firebase reauthentication, removes the profile, then deletes the Auth user. It does **not** erase local dictionaries, preferences or the legacy D1 device record, cancel any device-based payment plan, or delete the user's Google account. The dialog explicitly says this.

Firebase Auth and Firestore do not offer a cross-service client transaction. If the profile delete succeeds but the Auth delete fails, the account can remain valid without its profile; the UI reports failure and the user can retry deletion. For production account-lifecycle guarantees across admin-initiated deletion, process death or multiple devices, add an authenticated server-side cleanup/trigger after a separate backend review. No server credentials or undeployed Cloud Function were invented here.

## Build and test

The Firebase BoM is pinned to **34.5.0** (Auth 24.0.1), compatible with the existing Kotlin 2.2.21 toolchain. BoM 35.1.0 was tested and rejected because its Auth artifact uses Kotlin 2.4 metadata; upgrading the existing app's compiler is outside this non-regression change.

Normal checkout without spaces:

```sh
cd android
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --tests 'com.wingai.keyboard.auth.*' --tests 'com.wingai.keyboard.data.WingAiBridgeTest'
```

For this checkout's path containing spaces, GNU Make/NDK cannot consume the original path directly. [The build helper](../android/tools/build-login-apk.sh) builds the same current JNI sources through a temporary short symlink and passes the result to Gradle. Source and build outputs stay in this checkout; the temporary symlink is removed on exit. There is no substituted native library or modified keyboard implementation.

```sh
# Requires JDK 17, SDK 36 and NDK 27.1.12297006; set JAVA_HOME and ANDROID_HOME.
bash android/tools/build-login-apk.sh :app:assembleDebug :app:testDebugUnitTest \
  --tests 'com.wingai.keyboard.auth.*' --tests 'com.wingai.keyboard.data.WingAiBridgeTest'
```

Local Firestore rule tests (demo project, never production):

```sh
cd firebase
npm ci
npm test
```

The test suite covers validation, session state, duplicate submissions, errors/timeouts, cloud-profile retry, password reset, verification, sign-out/deletion, Compose interactions and storage isolation. Google tests cover the actual request type/web client ID, nonce uniqueness, malformed/unsupported credentials, cancellation, provider errors, token exchange, provider detection, sign-out ordering and wrong-account reauthentication rejection. Rules tests cover owner access, anonymous/cross-account denials, schema/timestamp/email enforcement, privileged-field injection, enumeration and subcollection denial.

[UI test renders](../artifacts/auth-ui/sign-in-reference.png) come from the actual Compose layout under Robolectric. They are not photographs or screenshots of an installed device. Login/signup/reset deliberately use the reference's light palette, while signed-in management and the rest of the app retain the selected theme. Device testing, real email delivery and production Firebase setup are separate release gates.
