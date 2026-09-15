> Historical snapshot: the signed APK has since been published and merged to main. See [the current build status](../../releases/BUILD_STATUS.md) for delivered files, signing compatibility and remaining package work.

# Source build checkpoint — 2026-09-15

This branch is work in progress, not a published release.

## Durable application fixes

Commit `051404d9711448c55147d3f9aa3ac2ba69cecfb6` adds automatic catalogue refresh, an existing-install migration for the byte-identical legacy Ocean pkg frontend, and signed-index hash verification. Seven catalogue behavior tests passed. Earlier branch commits contain dpkg file-list repair, bundled catalogue seeding, terminal styling and the keyboard button.

`apt update` downloads package metadata; it does not install command binaries. The screenshots' shared dpkg failure names malformed `resolv-conf.list` data. Screenshots showing `[Y/n] n` and `Abort` are declined installs, a separate case.

## Source-only build path

The old distribution script used the Termux build framework and is now retired. The shard entry point now invokes `build-source-packages.py`. This does not remove or certify the existing published binary pool.

Four official-upstream recipes are pinned by commit and archive SHA-256: cmark, kilo, libcjson and libtommath. Builds use Google NDK r27 (`27.0.12077973`), Android API 28, ARM64 Bionic and `/data/data/studio.ocean.app/files/usr`. The script records source/license/toolchain hashes, checks ELF architecture and interpreter, rejects foreign prefixes, produces compressed `.deb` candidates and supports disjoint shards and verified resume.

Example (Python 3.12+, dpkg-deb, GNU make, NDK and CMake required):

```sh
python3 ocean-packages/scripts/build-source-packages.py \
  --ndk /path/to/android-sdk/ndk/27.0.12077973 \
  --cmake /path/to/android-sdk/cmake/3.22.1/bin/cmake \
  --output /path/to/candidates --cache /path/to/source-cache \
  --workers 2 --jobs-per-package 2 --shard-count 1 --shard-index 0 --resume
```

Those four recipes compiled successfully in the preceding temporary workspace and passed static payload checks. Their compiled artifacts were lost when that workspace was reset; no durable artifact links are claimed. No Android device execution tests have passed. A librsync recipe was being built at reset; its completion is unconfirmed and it is not counted here. New candidate binaries must be regenerated and preserved before claiming delivery.

## Remaining delivery work and blockers

- The audited live pool contained 1,046 records, but only 1,036 distinct package names. No packages have been added to the live pool in this task. Reaching 2,200 unique packages requires 1,164 additions, plus provenance work for existing binaries.
- Existing published files include Termux-derived code; the whole runtime cannot be described as official-source-only. The replacement bootstrap/core distribution is unfinished.
- Before promotion, new candidates require complete dependency metadata/closure checks, Android installation and execution tests, and signing with the repository's existing trusted signing key.
- App version 1.1.3 is a source change, not a completed signed APK release. The compatible app signing key is not available in this environment.
- The latest GitHub release `oceanstudio-latest` was checked on September 15 and has zero attached assets. The connector provides source-control writes but no release asset upload action; there is no authenticated GitHub CLI in the workspace.
- No new APK has been published. A Gradle build alone would not prove a complete, compatible or source-only runtime.

The branch and PR preserve source changes. Save each subsequent source checkpoint to GitHub before long builds, and preserve build artifacts before ending a turn.
