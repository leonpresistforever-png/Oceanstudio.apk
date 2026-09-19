# OceanStudio 1.2.0 (versionCode 9) — Official Core Suite & Prefix-Native Curl Bootstrap

OceanStudio 1.2.0 delivers the prefix-native curl bootstrap fix, version bump to 1.2.0 (versionCode 9), native architecture verification, and integration with the verified official-source package expansion.

## Highlights
- **Prefix-Native Curl Bootstrap Fix**: Restored `/data/data/studio.ocean.app/files/usr/bin/curl` alongside native `bash` and `apt` directly in the verified minimal runtime bootstrap (`ocean-aarch64.tar.zst`, bootstrapVersion `1.0.4`, 56 packages). Physical-device tests no longer fall through to `/system/bin/curl`.
- **Version Bump**: Bumped to `versionName "1.2.0"` and `versionCode 9` with `applicationId "studio.ocean.app"` targeting Android API 28.
- **Native Architecture Verified**: All native architecture rules and Runtime Ports in-app preview checks pass cleanly via `scripts/verify-native-only.sh`.
- **Official Package Expansion Integration**: The companion package repository (`leonpresistforever-png/Oceanstudio-packages`) has been indexed and promoted with 166 verified official-source packages:
  - Official Toybox 0.8.14 isolated multicall expansion (153 packages namespaced as `ocean-toybox-*` / `tb-*`).
  - Isolated glibc 2.44 foundation runtime (7 packages under `/data/data/studio.ocean.app/files/glibc`).
  - Nix 2.34.8 isolated static package manager runtime (`nix-ocean`).
  - Official Core Suite multicall utilities: BusyBox 1.38.0, suckless sbase, suckless ubase, sinit, and official Buildroot 2026.08 framework (5 packages).
  - All built from official upstream git/source distributions without copying Termux binaries or recipes.

## Verification
- Local bootstrap verification passed cleanly: `verified Ocean bootstrap 1.0.4 (17846609 bytes)`.
- AndroidManifest.xml verified via `aapt dump badging`:
  `package: name='studio.ocean.app' versionCode='9' versionName='1.2.0' compileSdkVersion='35'`
- APK signing certificate SHA-256: `b12468091b50e6fb94f815d82a3f685d57b452b1f2275ce534def7c55bc7e387`
- No Termux namespace, binary, or configuration collisions.
- Physical device execution status: UNTESTED on hardware in this environment (verified via headless local build).

## Artifacts & Checksums
- `OceanStudio-1.2.0-arm64-debug.apk` (29,590,440 bytes)
  SHA-256: `33084842c7fbd7ea9b019fd4607c062ae07009a9e03555543545743e46a61cc9`
