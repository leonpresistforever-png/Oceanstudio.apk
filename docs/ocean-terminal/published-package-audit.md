# Published Ocean package repository audit

Audit date: 2026-09-07

Repositories inspected:

- `leonpresistforever-png/Oceanstudio.apk` at `e4f7218`
- `leonpresistforever-png/Oceanstudio-packages` at `817e20a`

## Verdict

The public package repository must not currently be described as a verified,
Ocean-source-built catalogue. Its signed index is readable, but the repository
fails provenance, identity, dependency, duplication, and payload checks.
Existing artifacts require quarantine and package-by-package rebuilds from
pinned upstream source before they can be promoted as supported.

## Measured repository state

- 1,046 indexed package records represent 1,036 unique package names.
- Ten names have multiple indexed versions: `caddy`, `crane`, `gdb`, `luajit`,
  `ninja`, `strace`, `swig`, `texinfo`, `traceroute`, and `wbt`.
- Twenty packages have no payload entries. Most declare transition/meta
  dependencies, but `natscli_0.4.0_aarch64.deb` and
  `vulkan-icd_0.1-1_all.deb` have neither payload nor dependencies and are
  operationally empty.
- `duplicity` depends on unavailable `librsync`.
- Seven packages contain Termux-named payload paths, including
  `termux-chroot` in `proot` and Termux service paths in OpenSSH and Dropbear.
- The repository is signed by fingerprint
  `09D45DD2CDC37BD4F9BC2C458EC15431CA5542E2`, while the Android repository pins
  `DF7857C7D40149151DEAB8E98FBEEFDC907346A0`. A client trusting the application
  key cannot authenticate the published metadata.
- The configured GitHub Pages URL returns HTTP 404. The raw GitHub content URL
  responds, but it is not the URL configured in `config.env`.
- The package repository contains only `.deb` artifacts and APT metadata. It
  has no source manifests, recipes, patches, source checksums, build logs,
  SBOMs, or provenance attestations from which the artifacts can be reproduced.

## Proven binary-copy path

The Android repository contained a local shard builder that downloaded
precompiled `.deb` files from the Termux package CDN, moved the installed tree
from `com.termux` to `studio.ocean.app`, rewrote text and RPATH strings, and
repacked the result. This is binary relocation, not compilation from official
upstream source. The workflow and builder have been removed.

The published Maven package corroborates that path: its
`maven-core-3.9.16.jar` has the same SHA-256 as the file inside the Termux CDN
package. The published OpenJDK release metadata says `IMPLEMENTOR="Termux"`.
These artifacts cannot be represented as independently built Ocean packages.

## Distro findings

`proot` ships `termux-chroot`, and `proot-distro` is the upstream Termux script
with Termux-specific messages and options. Separately, the Ocean-owned
`ocean-distro` registry routes most distributions through Termux
`proot-distro` release archives. This violates the requested independence
boundary. The two managers may coexist only after licensing, naming, source
provenance, rootfs sources, and checksums are audited; neither should currently
be called verified.

## Required continuation order

1. Freeze promotion of the current pool and label it experimental.
2. Align the repository signing key and reachable HTTPS URL with the Android
   client's pinned configuration.
3. Remove operationally empty packages, resolve duplicate versions, and repair
   dependency closure.
4. Establish per-package source recipes containing official source URL,
   checksum, license, patches, toolchain identity, and reproducible build.
5. Rebuild bootstrap/core first. Promote a package only after archive, AArch64
   ELF, dependency, prefix, installed-command, and physical-device tests pass.
6. Rebuild JDK, Maven, Gradle, Rust, Android tools, PRoot, and distro managers
   from official sources rather than relocated terminal packages.
7. Keep `proot-distro` and `ocean-distro` distinct, with the latter using
   independently verified official rootfs sources.

Run the repeatable audit with:

```bash
python3 ocean-packages/scripts/audit-published-repository.py \
  /path/to/Oceanstudio-packages \
  --expected-fingerprint "$(cat ocean-packages/keys/ocean-development-repository.fingerprint)"
```
