# Firebase (Android)

## Package name

The APK `applicationId` is **`studio.ocean.app`** (product name: Ocean.studio). Runtime paths under `ocean-packages/` assume this package.

Firebase project **`oceanstudio-ef4c5`** currently includes:

| Console / JSON `package_name` | `mobilesdk_app_id` | Used by this repo |
|------------------------------|--------------------|-------------------|
| **`studio.ocean.app`** | `1:2746278315:android:418e0c18a550b83475b2ee` | **Yes** — primary client in `android/app/google-services.json` |
| `Ocean.studio` | `1:2746278315:android:418e0c18a550b83475b2ee` | Legacy console name; same Firebase Android app as above until you add a separate registration |
| `oceanstudio.ai` | `1:2746278315:android:071675f462ed2c3175b2ee` | Other product; kept in the shared JSON |

If the console only lists **`Ocean.studio`**, SHA fingerprints you add there apply to that registration. For Google Sign-In and some console views to match the shipped APK exactly, add a **second** Android app with package **`studio.ocean.app`**, register the same debug/release SHA-1 and SHA-256, download an updated `google-services.json`, and replace `android/app/google-services.json` (you can remove the duplicate `Ocean.studio` client once `studio.ocean.app` has its own `mobilesdk_app_id`).

## Console setup (fingerprints)

1. [Firebase console](https://console.firebase.google.com/) → project **oceanstudio-ef4c5** → **Project settings** → **Your apps**.
2. Select the Android app that matches your package (`Ocean.studio` and/or `studio.ocean.app`).
3. **Add fingerprint** → paste **SHA-1** and **SHA-256** from your keystore:
   - Debug: `./gradlew :app:signingReport` (or `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`).
   - Release / Play: use the upload key or Play App Signing certificates from Play Console.
4. Download **google-services.json** and merge into git if it adds a new `studio.ocean.app` client (keep the client whose `package_name` matches `applicationId`).

## Gradle / SDK

- Root `android/build.gradle`: `com.google.gms:google-services:4.4.2`
- `android/app/build.gradle`: `com.google.gms.google-services` plugin, Firebase BOM `33.7.0`, `firebase-analytics`
- Analytics initializes automatically via the Google Services plugin (no manual `FirebaseApp.initializeApp` required).

## `google-services.json` in git

`google-services.json` is **safe to commit** for mobile apps: it contains project identifiers and API keys restricted by package name and SHA fingerprints, not server secrets. Do not commit service account JSON or release keystore material.

## Auth API key

Email/password auth via REST uses `OCEAN_FIREBASE_API_KEY` at build time (`AuthClient`), or the `google_api_key` entry from the committed `google-services.json` when the build flag is empty. Google and GitHub buttons use Firebase Auth (Play services + Identity Toolkit `signInWithIdp` for Google, Firebase OAuth provider for GitHub). Enable GitHub in Firebase Authentication before testing GitHub sign-in.

```bash
export OCEAN_FIREBASE_API_KEY='…'   # same project as google-services.json
./gradlew :app:assembleDebug
```

Analytics uses the Firebase SDK and the committed `google-services.json` entry for `studio.ocean.app`.

## Device smoke test

1. Install debug APK (`./gradlew :app:installDebug` or CI artifact).
2. Open the app and sign in with email/password (requires `OCEAN_FIREBASE_API_KEY` in the build that produced the APK).
3. In Firebase console → **Analytics** → **DebugView**, enable debug mode on device:  
   `adb shell setprop debug.firebase.analytics.app studio.ocean.app`  
   Confirm events appear within a few minutes.
