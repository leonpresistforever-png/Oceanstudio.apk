# Oceanstudio.apk

Android APK for the Ocean coding agent workspace — unified mobile build with all Electron/web features adapted for Android.

## Stack

| Layer | Technology |
|-------|------------|
| Mobile shell | Capacitor 7 (Android, target SDK 28) |
| UI | React 19 + TypeScript + Vite — modern white aesthetic, mobile-first nav |
| Terminal | `ocean-native-terminal` — **real PTY via JNI openpty**, proot Linux prefix (Termux bootstrap + busybox fallback) |
| Plugins | DexClassLoader marketplace (`plugin.json` manifest) |
| Auth | Firebase (redirect flow on Android WebView, popup on web) |
| Agent | Web agent + provider inference + playground |

## Features (APK-compatible)

All desktop Electron features are available on mobile:

- **Workspace** — Agent panel, code editor, file tree, native terminal, preview
- **Playground** — Active bot, screen narrator, recording, 3D studio, media jobs
- **Multi Agent** — Team orchestration, fusion, mission control
- **Providers & MCP** — Cloud SSE connectors (stdio MCP requires desktop)
- **Skills, Extensions, Plugins** — Marketplace catalogs
- **Integrations Hub** — Service connections
- **Native Terminal** — PTY-backed shell surpassing pipe-only wrappers; full proot/pkg/apt when bootstrap downloads

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

First launch downloads the Termux bootstrap for full `pkg` / `apt` / `proot` / `bash`. Run `npm run busybox:download` before release builds for offline fallback.

## Native Terminal Architecture

The terminal uses JNI `openpty()` for real pseudoterminal allocation (interactive bash, ssh, htop, nano). Falls back to ProcessBuilder pipes if native lib unavailable. Linux environment via proot with Termux-compatible prefix path.

## What belongs in this repo

- `src/` — React UI (mobile-adapted)
- `android/` — Capacitor Android project
- `plugins/ocean-native-terminal/` — PTY terminal + proot + DexClassLoader plugins
- `capacitor.config.ts`, `vite.config.ts`, etc.
