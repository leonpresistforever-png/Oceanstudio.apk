# OceanStudio 1.1.5 progress

Status: source integration completed; fresh local build and signing pending. No v1.1.5 release published yet.

Implemented: main-thread agent terminal output fix; Runtime Ports discovery, browser and image/interaction tools; opt-in device Accessibility controls with stop notification and permission dashboard; launchable-app inventory; BYOK typography/spacing. These are real native APIs, not simulated tool results.

Acceptance pending: fresh unit tests, APK assembly, signature compatibility and embedded bootstrap/native checks. No physical Android device is connected, so live-device crash/gesture/WebView acceptance remains unverified.

Android limits: shared storage grants do not expose private app data. Secure screens remain protected. General Android apps do not run headlessly through Accessibility. Desktop apps, Docker, decompilation/re-sign tools require separately available compatible runtimes and packages; this release does not install or certify them.

Previous successful local build was lost when workspace reverted. Treat only current test/build artifacts as release evidence. Save all source checkpoints to main with skip-ci; do not use GitHub Actions quota. Signing keys and tokens must remain private.

Separate unfinished request: package expansion. Last audited pool 1036 distinct names / 1046 records. Do not claim 2200 packages, 1000 newly compiled packages, or official-source-only provenance without an audited build manifest.
