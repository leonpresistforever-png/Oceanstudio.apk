# OceanStudio 1.1.5 progress

Status: source saved to main; local build and signing complete. Release publication is the next step.

Implemented: main-thread agent terminal output fix; Runtime Ports discovery, browser and image/interaction tools; opt-in device Accessibility controls with stop notification and permission dashboard; launchable-app inventory; BYOK typography/spacing. These are real native APIs, not simulated tool results.

Verified: 49 unit tests pass; APK assembly and Android instrumentation-source compilation succeed; signature matches v1.1.4; embedded bootstrap/native binaries match. No physical Android device is connected, so live-device crash/gesture/WebView acceptance remains unverified.

Android limits: shared storage grants do not expose private app data. Secure screens remain protected. General Android apps do not run headlessly through Accessibility. Desktop apps, Docker, decompilation/re-sign tools require separately available compatible runtimes and packages; this release does not install or certify them.

Previous successful local build was lost when workspace reverted. Treat only current test/build artifacts as release evidence. Save all source checkpoints to main with skip-ci; do not use GitHub Actions quota. Signing keys and tokens must remain private.

Separate unfinished request: package expansion. Last audited pool 1036 distinct names / 1046 records. Do not claim 2200 packages, 1000 newly compiled packages, or official-source-only provenance without an audited build manifest.

Candidate: OceanStudio-1.1.5-arm64-debug.apk, SHA-256 bcb218d63f0395d4c26b85536b98582bb5e72ec58365a48df0c7523b8c9f683d. Build source commit 5b8643f83086dbbf5a224c89ef1595cea16ca973. Check GitHub Releases for publication status before retrying any upload.
