# OceanStudio 1.1.6 — Ocean Forge self-development checkpoint

Ocean Forge turns OceanStudio into a local Android development/rebuild environment for OceanStudio itself.

## Android baseline

Forge preserves the application compatibility baseline:

- minSdk: 28
- targetSdk: 28
- compileSdk: 35
- Android Gradle Plugin: 8.7.2
- Gradle wrapper: 8.11.1
- application id: studio.ocean.app

Forge-only builds select ARM64 Build Tools 34.0.4 while continuing to compile against API 35. Normal CI configuration remains separate.

## Self-source

Every Ocean APK now carries a generated `ocean/forge/source.zip`.

The bundle contains:

- the Android project required to rebuild Ocean
- Gradle wrapper/project files
- native PTY source
- `scripts/verify-native-only.sh`
- `packages/ocean-prefix.env`
- Ocean tool-package source
- selected Ocean package build/validation scripts

Build outputs, IDE caches, local.properties and common signing-key file types are excluded.

The ZIP is stored uncompressed in the APK so Android AssetManager can stream the large source snapshot reliably.

`OceanForgeInstaller.ensureSourceBundle()` installs the snapshot into Ocean private state. `ocean-forge seed` initializes a private Forge workspace and local Git baseline from that snapshot. A self-built Ocean therefore carries forward the source that produced it.

## Canonical Forge command and tool overlay

The canonical Forge command lives at:

`ocean-packages/packages/ocean-tools/data/data/studio.ocean.app/files/usr/bin/ocean-forge`

The previous duplicate committed APK asset was removed.

Gradle generates the APK's `ocean/forge/ocean-forge` asset from the canonical package source at build time.

Gradle also generates a signed tool overlay containing Ocean's tool scripts. On app startup, `OceanForgeInstaller.ensureToolOverlay()` copies those scripts into:

`files/forge-tools/bin`

Ocean Terminal prepends that directory to PATH before the APT-managed prefix. This lets a self-built Ocean activate updated shell tooling without overwriting dpkg/APT-managed files.

## On-device build-environment bootstrap

`ocean-forge bootstrap` installs the Ocean packages required for development, including:

- OpenJDK 21
- Git
- Kotlin
- ECJ
- Python
- clang / LLVM
- CMake
- Ninja
- Android signing/resource helpers
- curl / archive tools

`ocean-forge bootstrap-sdk`:

1. downloads Google's Android SDK repository metadata,
2. resolves the current archive for `platforms;android-35`,
3. checks the platform archive size and repository checksum,
4. installs API 35 `android.jar`,
5. downloads the AndroidIDE ARM64 Build Tools 34.0.4 archive,
6. verifies its pinned archive size,
7. installs the ARM64 build-tools directory,
8. verifies that ARM64 `aapt2` executes,
9. configures Forge SDK/aapt2 state.

Forge continues to use Ocean's Gradle 8.11.1 wrapper and passes `android.aapt2FromMavenOverride` during on-device builds.

## Native-core self-rebuild

Forge no longer stops when `android/app/src/main/cpp` changes.

`prepare_native_library` works as follows:

- if native source is unchanged from the Forge baseline, reuse the installed verified `liboceanpty.so`;
- if native source changed, compile `ocean_pty.c` locally with Ocean's ARM64 clang/NDK sysroot;
- link a fresh `liboceanpty.so`;
- verify that the result is an AArch64 ELF using `llvm-readelf`;
- package it through the existing `src/forgeNative/arm64-v8a` Forge compatibility path.

The native library digest/provenance is reported by `ocean-forge status`.

## Forge Doctor

`ocean-forge doctor` reports:

- Java/Git/Python/archive/native compiler availability
- source-workspace readiness
- Gradle wrapper presence
- SDK 28/28/35 baseline
- API 35 platform availability
- ARM64 aapt2 availability
- Forge Git baseline
- whether native source will be reused or rebuilt
- current candidate state

It returns a non-zero result when required pieces are missing, allowing the agent to repair its environment before another build.

## Agent self-development loop

Ocean Agent has bounded Forge tools for:

- bootstrap
- bootstrap_sdk
- doctor
- seed
- status
- checkpoint
- diff
- test
- build
- verify

It also has a confined Forge workspace API for:

- list
- read
- search
- write
- replace
- move
- delete

Mutating existing files uses expected SHA-256 checks, sensitive/signing/Git-internal paths are rejected, and the agent is instructed to checkpoint before edits.

The agent is instructed to use real compiler/test output to patch and retry failed self-builds rather than claiming success.

Agent Forge actions intentionally do not include APK installation or direct access to signing secrets.

## Long-running builds

Headless Forge tasks run through OceanTerminalRuntimeService as visible foreground work.

- command ceiling: 3600 seconds
- foreground notification while active
- partial wake lock remains held until the task exits/cancels
- agent Stop cancels the active CommandHandle
- Forge page includes an explicit "Stop active Forge task" control

## Private Forge signing vault

Ocean Forge can optionally save the user's signing identity for repeat self-builds.

- the keystore copy lives under `getNoBackupFilesDir()`
- store/key passwords are encrypted with AES/GCM
- the encryption key lives in Android Keystore
- the agent runner has no API for retrieving these credentials
- the user can forget/delete the saved identity from Forge
- signing credentials are decrypted only by the user-facing Forge signing flow and written to short-lived private password files for the build
- candidate verification still compares the resulting APK identity against the installed Ocean identity

The existing Ocean signing key is still required for an in-place update of an already installed Ocean build.

## Dynamic plugin tool layer

Terminal plugins can be registered persistently with `ocean-plugin`.

New commands include:

`ocean-plugin scaffold <id> <name> [bash|python] [description]`

The scaffold command creates a private executable handler without overwriting an existing handler and registers it in the Plugins page.

Ocean Agent exposes:

- `list_ocean_plugins`
- `run_ocean_plugin`

Registered plugin IDs/commands are validated. Commands must resolve inside the signed Forge overlay or Ocean's private prefix. Connected/disconnected state is honored.

Plugin input is written to a private temporary file and redirected to stdin; payload content is not embedded in shell command text. Input is bounded to 32 KiB. Output is treated as untrusted tool data.

## Validation status

Source-level integration/regression tests now cover:

- Forge tool and workspace boundaries
- SDK bootstrap/self-source integration
- native-rebuild architecture
- foreground long-task execution
- private signing-vault boundary
- signed tool overlay
- dynamic plugin confinement and input bounds
- rejection of agent install/build-signed actions

Source audits after this checkpoint confirmed the canonical Forge and ocean-plugin files are executable (100755), the duplicate Forge APK source asset is removed, and the modified production Java/XML files have balanced structure/no detected accidental patch escapes.

A real on-device Gradle/JNI self-build has not yet been physically executed in this environment. GitHub Actions quota is exhausted, so no new 1.1.6 APK is claimed from this checkpoint until an actual build is run and verified.
