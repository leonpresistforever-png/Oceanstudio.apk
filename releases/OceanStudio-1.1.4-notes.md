## OceanStudio 1.1.4 — Agent terminal tools

ARM64 debug/testing APK, version code 6. Signed with the same key as 1.1.3, so it supports updating that installation in place.

### Changes

- Connect Gemini, Claude and OpenAI-compatible structured tool calls to Ocean's native terminal service. The model receives actual command output and exit codes without requiring the terminal screen to be open.
- Keep command sessions separate from interactive terminal sessions; add cancellation, bounded execution time and output capture. Fix output being dropped when a command exits quickly.
- Make “Open terminal” open the terminal directly. Validate model IDs, honor custom provider endpoints and show clearer connection errors. A provider name such as `google` is not a model ID.
- Preserve the existing layout while adding grey Ocean Agent labels, outlined vector icons, fine separators, readable message formatting and expandable command output rows.

### Validation

- 42 Gradle unit tests passed, including provider tool round trips, argument validation, execution limits and package/bootstrap regressions.
- Host command lifecycle check passed: 20 fast processes preserved exact UTF-8 output and exit code 7; cancellation reaped the child. This check uses host process adapters, not Android JNI.
- Android headless-service test sources compiled. No Android device was attached, and no live BYOK model API key was available; live provider/device acceptance is still pending.
- APK signature, ZIP alignment, version and packaged catalogue verified. Bootstrap assets and the unchanged Ocean PTY native library match 1.1.3 byte for byte.

The local build reused the verified, unchanged native library to reduce build cost. The repository retains its regular CMake/NDK source build. This testing APK retains the visibly labelled testing entry; production authentication configuration is still pending.

Package availability is unchanged at 1,036 distinct indexed names. This release does not claim completion of the separate package-expansion or source-provenance work.

Built from `fad6505bcf061e199d8aeff78fc3894bc7ae4843`.

APK SHA-256: `bbaa174a9c2fa36a55a80432f9bad16e25c91442409b837536aa6829ca8cc4aa`
