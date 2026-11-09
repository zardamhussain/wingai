# Firebase signing fingerprints

These are **signing-certificate fingerprints**, extracted with Android `apksigner verify --print-certs` from the delivered [WingAI ARM64 debug APK](../artifacts/apk/WingAI-login-debug-arm64.apk). They are not the APK file's SHA-256 checksum.

Android package: **com.wingai.keyboard**  
Firebase project: **keyboard-f9ed8**  
Signer: **Android Debug**

## SHA-1

```text
DD:30:F2:11:D1:20:C8:5E:A3:DA:2B:40:BD:8C:DE:32:93:AC:BB:D6
```

## SHA-256

```text
93:7B:35:38:BC:62:D8:82:6E:7E:E2:09:7C:F2:56:F8:F8:9F:01:96:D3:06:CF:15:CA:E7:23:94:9E:A1:E8:05
```

## Add them to Firebase

1. Open **Project settings > General > Your apps > com.wingai.keyboard** in the Firebase console.
2. Under **SHA certificate fingerprints**, add SHA-1 and SHA-256 above, then save.
3. Open **Authentication > Sign-in method > Google**, enable it, choose the project support email, and save.
4. Download the updated **google-services.json** for this Android app and replace [the app-module configuration](../android/app/google-services.json). This is the copy Gradle actually consumes.
5. Install version **3814** or later and tap **Continue with Google** on the login screen. This version includes Credential Manager/Google ID integration and Firebase token exchange. Its web client ID comes from the module configuration, not a hard-coded guess.

**Configuration update detected during this task:** the user replaced the app-module configuration with one containing an Android OAuth client whose certificate hash matches the SHA-1 above, plus a web OAuth client. The generated Android resources now contain `default_web_client_id`. The older root-level copy still has no OAuth clients; Gradle uses the app-module copy. Both user files were preserved without editing them.

Version **3814** includes the app-side Google button, credential chooser, Firebase sign-in, sign-out cleanup and Google reauthentication for deletion. This confirms implementation and local tests, not a live Google sign-in test or provider-console audit. No live Firebase settings were changed by the agent.

## Debug versus production

These values apply only to builds signed with this same debug certificate. For a release APK, also register the release signing certificate. For Google Play distribution, register the **Play App Signing** certificate shown in Play Console; it may differ from the upload key. A future developer-machine debug keystore can also have a different fingerprint.

Rebuilding UI code or changing the APK version does not change the signing certificate when the same keystore is used. The APK file checksum, by contrast, changes on a new build.

References: [Firebase Google sign-in for Android](https://firebase.google.com/docs/auth/android/google-signin), [Google client authentication](https://developers.google.com/android/guides/client-auth), and [Firebase setup](https://firebase.google.com/docs/android/setup/).
