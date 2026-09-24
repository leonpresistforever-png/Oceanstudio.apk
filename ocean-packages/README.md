# Ocean aarch64 package distribution

Ocean's native Android prefix is `/data/data/studio.ocean.app/files/usr`.
The isolated glibc prefix is `/data/data/studio.ocean.app/files/glibc`.
Neither is a Debian system root. Ocean-distro and proot-distro keep independent
guest directories and do not start each other.

## Source builds and current limitations

The legacy framework/cache distribution builder is retired. Its previous claim
that every runtime package was built from official source is not established by
the current forensic evidence. Existing published binaries must be verified or
rebuilt individually; changing branding, maintainer text or prefixes is not proof
of a source build.

`ocean-packages/scripts/build-source-packages.py` uses pinned official upstream
source commits, SHA256 checksums and Android NDK r27. It preserves upstream license
files, installs to the Ocean prefix and records toolchain/source/artifact hashes.
Its manifest is `ocean-packages/sources/recipes.json`. The current recipe set does
not rebuild the entire existing collection. Output is quarantined pending
runtime, dependency and repository publication checks.

```bash
python3 ocean-packages/scripts/build-source-packages.py --plan
python3 ocean-packages/scripts/build-source-packages.py \
  --ndk /path/to/android-ndk-r27 --resume
```

The independent `ocean-download.sh` source-cache helper requires a SHA256 for all
sources and mirrors. A corrupt resumed download is discarded and retried from
byte zero. Unchecked downloads are rejected. It does not replace native APT's
package downloader.

## Signed APT publication

The public `Oceanstudio-packages` repository owns the canonical complete-pool
publisher, `scripts/index_all_staged.py`. It reconciles pool and staging, checks
actual archive/control data and regenerates Packages, Packages.gz, Release,
Release.gpg and InRelease as one verified publication. Old pool files and valid
packages are preserved; exact duplicates do not create duplicate index entries.

The existing trusted archive identity is:

```text
Ocean Package Archive <archive@ocean.studio>
09D45DD2CDC37BD4F9BC2C458EC15431CA5542E2
```

Publication requires the corresponding original private key in the publisher's
`OCEAN_REPOSITORY_SIGNING_KEY` Actions secret, as a complete ASCII-armored block.
A GitHub access token, deploy key or different newly generated GPG key cannot sign
for that existing identity. Do not change the trusted key or disable verification
to make stale metadata pass.

The forensic reports and `REPAIR_PROGRESS.md` in the public repository record
what was actually checked. Indexed package counts do not establish executable
functionality, official-source provenance or successful phone installation.

## APK and device verification

Use `scripts/build_source_apk.py` / the production release
workflow with the original APK signing material. Publication remains gated on a
consistent signed catalogue, clean verified inputs, production configuration,
source compilation and smoke tests. Do not substitute old DEX files or a debug
signing key for a production build.

`ocean-package-smoke-test` resolves commands beneath `$PREFIX` and exercises
actual pkg/APT/dpkg. Phone acceptance must additionally cover representative
native packages, large downloads, dependency upgrades and independent guest
logins. Host-side source or metadata checks are not physical-device smoke tests.
