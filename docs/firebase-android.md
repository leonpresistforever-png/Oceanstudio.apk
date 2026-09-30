# Firebase (Android)

## Package name

The APK `applicationId` is **`studio.ocean.app`** (product name: Ocean.studio). Runtime paths under `ocean-packages/` assume this package.

If the Firebase console lists **`Ocean.studio`**, that registration does not match the shipped app. Either:

1. Add a second Android app in the same Firebase project with package **`studio.ocean.app`**, download a fresh `google-services.json`, and replace `android/app/google-services.json`, or
2. Rename/remove the `Ocean.studio` registration and use `studio.ocean.app` only.

The committed `google-services.json` maps the primary client to `studio.ocean.app` so the Google Services Gradle plugin matches the build.

## `google-services.json` in git

`google-services.json` is **safe to commit** for mobile apps: it contains project identifiers and API keys restricted by package name and SHA fingerprints, not server secrets. Do not commit service account JSON or release keystore material.

## Auth API key

Email/password auth via REST still uses `OCEAN_FIREBASE_API_KEY` at build time (`AuthClient`). Analytics uses the Firebase SDK from this file.

## SHA fingerprints

Register **debug** SHA-1/SHA-256 for local builds. For Play Store releases, add the **upload/release** keystore SHA in Firebase console (Project settings → Your apps → Add fingerprint).
