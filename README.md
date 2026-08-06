# Oceanstudio.apk

Android APK for the Ocean coding agent workspace. This repository is **dedicated to the mobile app only**.

The desktop Electron app lives in a separate **Ocean.studio** repository — do not mix or commit Electron code here.

## Stack

| Layer | Technology |
|-------|------------|
| Mobile shell | Capacitor 7 (Android, target SDK 28) |
| UI | React 19 + TypeScript + Vite |
| Terminal | `ocean-native-terminal` — proot Linux prefix (Termux bootstrap + busybox fallback) |
| Auth | Firebase (web SDK in WebView) |
| Agent | Web agent + provider inference |

## Prerequisites

- Node.js 20+
- Android Studio + SDK (`ANDROID_HOME`)
- JDK 17+

## Development

```bash
npm install
npm run plugin:build

# Web UI preview in browser (terminal requires device/emulator APK)
npm run dev

# Debug APK
npm run android:build
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`

First launch on device downloads the Termux bootstrap for full `pkg` / `apt` / `proot` / `bash`. Run `npm run busybox:download` before release builds for offline fallback.

## What belongs in this repo

- `src/` — React UI (mobile-adapted)
- `android/` — Capacitor Android project
- `plugins/ocean-native-terminal/` — native Linux terminal
- `capacitor.config.ts`, `vite.config.ts`, etc.

## What does NOT belong here

- `electron/` — desktop only (separate Ocean.studio repo)
- `node-pty`, `electron-builder`, `server/terminal-ws.mjs` — desktop dev server
