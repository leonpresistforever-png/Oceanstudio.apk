# OceanStudio 1.1.5 build status

Signed arm64 debug APK built locally and verified. Published as the latest GitHub release: https://github.com/leonpresistforever-png/Oceanstudio.apk/releases/tag/v1.1.5.

- Source commit: 5b8643f83086dbbf5a224c89ef1595cea16ca973.
- Version: 1.1.5, versionCode 7.
- Unit tests: 49 passed, zero failures/errors/skips.
- APK assembly and Android instrumentation-source compilation succeeded. No connected-device test was run.
- Signing certificate matches v1.1.4; install as an update to preserve app data.
- Bootstrap and native arm64 PTY binary match v1.1.4; no terminal runtime replacement.
- APK SHA-256: bcb218d63f0395d4c26b85536b98582bb5e72ec58365a48df0c7523b8c9f683d.
- No GitHub Actions quota used. APK is already a ZIP container; the bootstrap stays stored uncompressed inside it for extraction compatibility.

See OceanStudio-1.1.5-validation.json and OceanStudio-1.1.5-notes.md for evidence, changes and limits. Device acceptance and the separate 1000-package expansion remain open.
