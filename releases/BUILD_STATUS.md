# OceanStudio 1.2.0 build status

Signed arm64 debug APK repackaged, zipaligned, and verified with APK Signature Scheme v2:
https://github.com/leonpresistforever-png/Oceanstudio.apk/releases/tag/v1.2.0

- Source commit: 12c4ffd.
- Version: 1.2.0, versionCode 9.
- Native architecture verified: `scripts/verify-native-only.sh` passed.
- APK assembly succeeded with versionCode 9 and versionName 1.2.0.
- Prefix-native curl bootstrap hydrated and verified (`bootstrapVersion 1.0.4`, 56 packages).
- Zip alignment: 4-byte aligned and 4KB page aligned for shared libraries (`zipalign -c -v 4` passed).
- Signature scheme: APK Signature Scheme v2 verified (`APK Sig Block 42` present).
- Certificate DN: `CN=OceanStudio, O=OceanStudio` (`9896a7fb0e7c198dc46f729ae485d061049ccb0a6cf11eef5dbe3a36e7844501`).
- APK SHA-256: `f20107eb48c49ed2d0807e96051d0ffa9a5d96907923db9f62ee9ce628ce2991`.
- Size: 29,487,466 bytes.
- Companion package repository `Oceanstudio-packages` indexed and updated to 1,508 unique packages across official shards.

See OceanStudio-1.2.0-validation.json and OceanStudio-1.2.0-notes.md for detailed evidence, changes, and verification.
