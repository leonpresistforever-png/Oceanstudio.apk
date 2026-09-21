# OceanStudio 1.1.6 — Ocean Forge checkpoint

Ocean Forge turns OceanStudio into a local, agent-operable development workspace for modifying and rebuilding OceanStudio itself.

## Android baseline

The application intentionally remains:

- minSdk 28
- targetSdk 28
- compileSdk 35
- applicationId studio.ocean.app

The existing architecture verifier continues to enforce minSdk/targetSdk 28.

## Forge runtime

The executable `ocean-forge` command is available in the Ocean tools package and is also bundled directly inside the APK at:

`assets/ocean/forge/ocean-forge`

`OceanForgeInstaller` installs or refreshes that bundled command into Ocean's private runtime prefix using SHA-256 comparison and an atomic replacement.

Supported Forge commands:

- tools
- bootstrap
- configure-sdk
- detect-sdk
- sdk-status
- clone
- init
- status
- diff
- test
- build
- build-signed
- verify
- install
- checkpoint
- rollback

Forge state is kept under `$HOME/ocean-forge`.

## Internal Forge engine

Forge is an agent-facing self-development capability, not a normal user workspace or package-manager UI. The main navigation and Agent Controls do not expose a Forge setup page. `OceanForgeActivity` remains bundled only as dormant legacy/diagnostic code while the supported path is the structured agent tool surface below.

The legacy activity contains infrastructure for:

- local toolchain bootstrap
- Android SDK / ARM64 aapt2 auto-detection
- explicit SDK adapter configuration
- local source import
- credential-safe Git clone
- checkpoint / diff / rollback
- tests
- candidate APK build
- candidate verification
- signed candidate build
- visible user-approved update handoff
- streamed Forge command output

Git clone rejects access tokens embedded in repository URLs and expects Git credentials to be configured separately.

## Agent self-development tools

Ocean Agent has two structured Forge tools. They are available internally to the agent and are not controlled by the user-facing Plugins connection list.

### ocean_forge

Bounded development actions:

- tools
- status
- checkpoint
- diff
- test
- build
- verify

The model-facing Forge tool does not expose install.

### ocean_forge_workspace

Confined source operations:

- list
- read
- search
- write
- replace
- move
- delete

Every path is canonicalized beneath Ocean's private Forge workspace.

Credential, signing, Git-internal and local-secret paths are excluded.

Reads return SHA-256. Existing-file writes and refactors use stale-edit SHA guards. Replace/move/delete require a recent SHA, and every structured source mutation requires a Forge checkpoint first.

The workspace tool never accesses files outside the Forge source workspace.

## On-device build compatibility

The project Gradle wrapper remains pinned to Gradle 8.11.1 with Android Gradle Plugin 8.7.2.

Forge does not force the independently packaged Gradle 9.x runtime onto this project.

Forge supports a pluggable Android SDK adapter:

- Android 35 platform / android.jar
- executable ARM64 aapt2
- AGP override through `android.aapt2FromMavenOverride`

Forge can auto-detect compatible SDK/aapt2 installations or accept explicit paths.

The current Ocean package catalogue contains `aapt`, but not an ARM64 `aapt2` package, so a complete SDK adapter still needs to be supplied by a compatible provider until Ocean publishes one.

## Native C fast path

Ordinary Java/XML/UI/agent/plugin changes do not require rebuilding Ocean's PTY native library.

When `OCEAN_FORGE_REUSE_NATIVE=true`:

- Gradle disables the CMake native rebuild.
- Forge verifies that `android/app/src/main/cpp` is unchanged from the Forge baseline.
- The working `liboceanpty.so` is extracted from the currently installed Ocean APK.
- It is packaged through the Forge-only JNI source directory.

If native C source changed, Forge refuses the reuse path rather than silently packaging stale native code.

## Candidate versions and signing

Normal project metadata remains parseable:

- versionCode 8
- versionName 1.1.6

Forge supplies candidate version overrides through Gradle project properties and automatically chooses a versionCode that is not below the installed Ocean version.

Candidate verification checks:

- APK signature validity
- package name = studio.ocean.app
- versionCode compatibility where aapt is available
- candidate signing certificate against the currently installed Ocean certificate

Forge only reports `UPDATE_COMPATIBLE=true` when the certificate actually matches.

For signed candidates, the user can provide the trusted keystore and alias. Passwords are passed using short-lived files in Ocean private storage and removed after the build. Signing secrets are not stored in Git or exposed through Forge workspace tools.

## Persistent builds

Headless terminal and Forge jobs support bounded command sessions up to 1800 seconds.

While a long command is active:

- Ocean starts a visible foreground service.
- A low-importance ongoing notification reports that a local task is active.
- A bounded partial wake lock keeps the CPU available while the screen sleeps.
- cancellation, timeout and process cleanup remain active.
- foreground state and wake lock are released on success, failure, cancellation, timeout or service shutdown.

## Validation checkpoint

Source consistency audit completed after the Forge changes:

- Java delimiter balance passed after stripping comments/string/character literals.
- Ocean Forge, Main and Agent Settings XML are well formed.
- no duplicate Android IDs were found in the audited layouts.
- AndroidManifest.xml is well formed.
- Gradle Forge blocks are structurally balanced.
- normal 1.1.6 version literals remain intact for existing release scripts.
- Forge native-reuse, imported-signing and version-property hooks are present.
- package `ocean-forge` has Git mode 100755.
- package Forge script and APK-bundled Forge asset are byte-identical.
- unit/source tests cover Forge tool boundaries, workspace validation, foreground durability, reasoning/session settings and extended timeout limits.

A physical APK has not been produced from this checkpoint because the repository GitHub Actions quota is unavailable and this chat execution environment has no Android build SDK/network. The source state is committed on main for the next local/device build.
