# Ocean Terminal Android execution model

OceanStudio is currently one sideload-first APK with application ID `studio.ocean.app`.
It compiles against the repository's current Android SDK while deliberately using
`minSdk 28` and `targetSdk 28`. These values are not interchangeable: `compileSdk`
controls available compile-time APIs, `minSdk` is the oldest supported device, and
`targetSdk` selects compatibility behavior for the installed application.

The target remains 28 because Ocean's intended package model installs executables
after APK installation into the writable app-private prefix. Android 10 introduced
a target-29 execution restriction for writable application data. Raising the target
without replacing that architecture would make a displayed `pkg install` facility
unable to execute what it installs.

This is not a security bypass. SELinux, the Android application sandbox, lifecycle
and background limits, storage rules, permissions, OEM process policies, and all
device-wide restrictions still apply. Ocean Terminal does not provide root access
or access to another application's private files.

## Path contract

Runtime code derives paths from `Context.getFilesDir()`:

* `OCEAN_ROOT=<filesDir>`
* `OCEAN_HOME=<filesDir>/home`
* `OCEAN_PREFIX=<filesDir>/usr`
* `OCEAN_TMP=<filesDir>/usr/tmp`

The canonical build prefix for packages tied to the current application ID is
`/data/data/studio.ocean.app/files/usr`, but Android runtime code must not assume
that primary-user spelling and always uses `filesDir`.

## Current verified boundary

The APK includes a native `arm64-v8a` PTY implementation and a service-owned Android
recovery shell. It does **not** contain a signed Ocean bootstrap, APT/dpkg database,
Ocean repository keyring, or package repository. The UI labels this state explicitly;
it must not call the recovery shell an installed Ocean runtime and must not expose a
fake `pkg` command.

A future modern-target build requires a different, separately validated architecture,
such as a companion runtime, APK-packaged native executables, an interpreter/WASM
runtime, or remote execution. It must not silently retain the writable-prefix design.
