# Ocean aarch64 package distribution

This pipeline builds Android/bionic packages for `studio.ocean.app` at the
Ocean prefix `/data/data/studio.ocean.app/files/usr`. It pins the audited
`termux-packages` build framework solely as GPL-licensed cross-build
infrastructure, changes the application identity and prefix before configure
or compilation, and builds every runtime package from source. It never copies
prebuilt Termux packages.

The GitHub workflow requires `OCEAN_REPOSITORY_SIGNING_KEY`, an ASCII-armored
private development key stored as a repository secret. Only its public key is
placed in the bootstrap. GitHub Pages hosts the signed static APT repository.

The pinned development signing identity is:

```text
Ocean Development Repository <repository@ocean.studio>
DF7857C7D40149151DEAB8E98FBEEFDC907346A0
```

Add the full, unmodified ASCII-armored private-key block as the single GitHub
Actions repository secret `OCEAN_REPOSITORY_SIGNING_KEY` under **Settings →
Secrets and variables → Actions**. It is not base64 encoded and requires no
companion secret. CI rejects any secret whose fingerprint differs from the
committed public key.

Run `OCEAN_REPO_SIGNING_KEY=<fingerprint> ./ocean-packages/scripts/build-ocean-distribution.sh`
on a Docker-capable Linux host with that secret key already imported.

## Catalogue phases

`catalog.json` is the Ocean-owned catalogue boundary. The pinned upstream
recipes provide versioned source URLs, SHA-256 checksums, licenses, patches,
dependency declarations and build-system instructions; Ocean changes the app
identity/prefix before configuration and compilation and never imports their
prebuilt binary repository.

The default build stops at `foundation`: Bash/APT/libcurl plus coreutils,
findutils, grep, sed, gawk, diffutils, tar, gzip, zstd, less, which, patch,
util-linux, wget and OpenSSL. Network and source-control roots are declared as
later phases but are not reported as published until their CI artifacts pass.

Select a phase explicitly with `OCEAN_PACKAGE_PHASE=foundation` (or a later
declared phase). The builder checks its package-output cache per root and sends
only missing roots into the dependency-aware upstream compiler. Expansion
packages are repository-only: `bootstrap-closure.py` computes the actual
dependency closure of Bash/APT/libcurl/Ocean pkg so catalogue growth does not
inflate the APK bootstrap.

`ocean-package-smoke-test` is delivered by the real `ocean-tools` package. It
resolves every command beneath `$PREFIX`, then uses actual pkg/APT/dpkg to
install and validate `ocean-hello`. It is intended for arm64 physical-device
acceptance; compilation alone is not considered a runtime pass.
