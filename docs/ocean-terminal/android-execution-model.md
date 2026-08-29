# Ocean Terminal Android execution model

OceanStudio is currently a single, sideload-first APK with application ID
`studio.ocean.app`. The build deliberately uses a modern `compileSdk` while
retaining `minSdk 28` and `targetSdk 28`.

`compileSdk` selects APIs available to source code. `minSdk` selects the oldest
supported Android release. `targetSdk` selects compatibility behavior for the
installed application. Target 28 is intentional because Ocean Terminal's
planned package model executes binaries installed after APK installation into
the writable private prefix. Android 10 introduced a target-dependent execute
restriction for applications targeting API 29 and later.

This is not a security bypass. The Android sandbox, SELinux, storage and
permission controls, lifecycle limits, background execution rules, and OEM
process management still apply. Ocean Terminal neither claims root nor accesses
other applications' private data.

## Path contract

Android code resolves `filesDir` at runtime. The resulting layout is:

* `OCEAN_ROOT=<filesDir>`
* `OCEAN_HOME=<filesDir>/home`
* `OCEAN_PREFIX=<filesDir>/usr`
* `OCEAN_TMP=<filesDir>/usr/tmp`

The canonical build-time primary-user prefix is
`/data/data/studio.ocean.app/files/usr`, but application code must use
`OceanPaths`, not that literal path.

## Current verified boundary

The application contains a JNI PTY implementation and can launch a real child
process in that PTY. Until a signed Ocean-native bootstrap and repository exist,
the UI truthfully labels `/system/bin/sh` as a recovery shell. The presence of
directories is never treated as proof that the Ocean runtime is installed; a
validated `.ocean-runtime.json` marker and critical executable checks are
required before the UI may report the runtime as ready.

Moving the Full APK above target 28 requires a separately verified execution
architecture, such as a companion runtime APK, prepackaged native libraries,
an interpreter/WASM runtime, or remote execution. It must not be done as a
routine Gradle modernization.
