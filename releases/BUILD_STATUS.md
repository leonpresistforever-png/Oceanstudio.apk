# OceanStudio 1.2.0 build status

Signed arm64 debug APK built locally, verified, and published:
https://github.com/leonpresistforever-png/Oceanstudio.apk/releases/tag/v1.2.0

- Source commit: 12c4ffd.
- Version: 1.2.0, versionCode 9.
- Native architecture verified: `scripts/verify-native-only.sh` passed.
- APK assembly succeeded with versionCode 9 and versionName 1.2.0.
- Prefix-native curl bootstrap hydrated and verified (`bootstrapVersion 1.0.4`, 56 packages).
- Signing certificate matches v1.1.4 and v1.1.5 (`b12468091b50e6fb94f815d82a3f685d57b452b1f2275ce534def7c55bc7e387`).
- APK SHA-256: `33084842c7fbd7ea9b019fd4607c062ae07009a9e03555543545743e46a61cc9`.
- Size: 29,590,440 bytes.
- Companion package repository `Oceanstudio-packages` indexed and updated with 166 verified official-source packages (Toybox, glibc, nix, core-suite).

See OceanStudio-1.2.0-validation.json and OceanStudio-1.2.0-notes.md for detailed evidence, changes, and verification.
