# Ocean.studio for Android

Ocean.studio is now a standalone native Android application. The repository intentionally contains no Electron, website, React, Vite, Capacitor, WebView, Node.js, or browser-bridge application code.

## Current milestone

The first native shell includes:

- a native Android activity and XML layout;
- Agent, Editor, Terminal, Files, and Tools navigation;
- a model picker, agent empty state, and prompt field matching the mobile product direction;
- a clean Gradle-only APK build targeting Android API 35.

The product specifications shared in Google Docs require access permission before their detailed requirements can be implemented. This milestone establishes the native-only foundation without attempting to preserve the old web implementation.

## Build

Requirements: JDK 17+ and Android SDK 35.

```bash
cd android
./gradlew assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.
