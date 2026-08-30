#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
source "$ROOT/ocean-packages/config.env"
WORK=${OCEAN_BUILD_WORK:-$ROOT/ocean-packages/build}
UPSTREAM=$WORK/package-builder
OUT=$WORK/out
rm -rf "$WORK"; mkdir -p "$OUT/debs" "$OUT/repository/pool/main" "$OUT/bootstrap/root/usr"
git clone https://github.com/termux/termux-packages.git "$UPSTREAM"
git -C "$UPSTREAM" checkout --detach "$UPSTREAM_PACKAGES_COMMIT"
# The audited upstream GPL package build framework is used only as a compiler.
# These substitutions make packages Ocean-native at configure/compile time.
python3 - "$UPSTREAM/scripts/properties.sh" <<'PY'
import pathlib,sys
p=pathlib.Path(sys.argv[1]); s=p.read_text()
s=s.replace('TERMUX__NAME="Termux"','TERMUX__NAME="Ocean"')
s=s.replace('TERMUX__INTERNAL_NAME="termux"','TERMUX__INTERNAL_NAME="ocean"')
s=s.replace('TERMUX_APP__PACKAGE_NAME="com.termux"','TERMUX_APP__PACKAGE_NAME="studio.ocean.app"')
p.write_text(s)
PY
cat > "$UPSTREAM/repo.json" <<JSON
{"pkg_format":"debian","packages":{"name":"ocean-main","distribution":"stable","component":"main","url":"$OCEAN_REPOSITORY_URL"}}
JSON
# Ocean owns its bootstrap helpers and keyring.  Do not pull the upstream
# Android app bridge (`termux-tools` -> `termux-am`) or upstream repository
# identity into the native Ocean distribution.  These dependencies are shell
# integration/data packages, not ELF link dependencies of bash or apt.
python3 - "$UPSTREAM/packages/bash/build.sh" "$UPSTREAM/packages/apt/build.sh" <<'PY'
import pathlib, sys
for filename, removed in ((sys.argv[1], ("termux-tools",)),
                          (sys.argv[2], ("termux-keyring", "termux-licenses"))):
    path = pathlib.Path(filename)
    text = path.read_text()
    for package in removed:
        text = text.replace(", " + package, "").replace(package + ", ", "")
    path.write_text(text)
PY
# Each result is built from upstream source by Android NDK for the Ocean prefix.
CORE=(bash apt dpkg coreutils grep sed tar gzip curl findutils procps util-linux zlib xz-utils zstd openssl ca-certificates ncurses readline)
(cd "$UPSTREAM"; ./scripts/run-docker.sh ./build-package.sh -a aarch64 "${CORE[@]}")
find "$UPSTREAM/output" -type f -name '*_aarch64.deb' -exec cp -v {} "$OUT/debs/" \;
test -n "$(find "$OUT/debs" -name 'bash_*_aarch64.deb' -print -quit)"
test -n "$(find "$OUT/debs" -name 'apt_*_aarch64.deb' -print -quit)"
test -n "$(find "$OUT/debs" -name 'dpkg_*_aarch64.deb' -print -quit)"
# Build a genuine test .deb rather than writing repository metadata by hand.
HELLO=$WORK/ocean-hello; mkdir -p "$HELLO/DEBIAN" "$HELLO$OCEAN_PREFIX/bin"
cp "$ROOT/ocean-packages/packages/ocean-hello/control" "$HELLO/DEBIAN/control"
install -m755 "$ROOT/ocean-packages/packages/ocean-hello/ocean-hello" "$HELLO$OCEAN_PREFIX/bin/ocean-hello"
dpkg-deb --root-owner-group --build "$HELLO" "$OUT/debs/ocean-hello_1.0.0_aarch64.deb"
PKGROOT=$WORK/ocean-pkg; mkdir -p "$PKGROOT/DEBIAN" "$PKGROOT$OCEAN_PREFIX/bin"
cp "$ROOT/ocean-packages/packages/ocean-pkg/control" "$PKGROOT/DEBIAN/control"
install -m755 "$ROOT/ocean-packages/packages/ocean-pkg/pkg" "$PKGROOT$OCEAN_PREFIX/bin/pkg"
dpkg-deb --root-owner-group --build "$PKGROOT" "$OUT/debs/ocean-pkg_1.0.0_all.deb"
cp "$OUT/debs"/*.deb "$OUT/repository/pool/main/"
cd "$OUT/repository"; mkdir -p dists/stable/main/binary-aarch64
apt-ftparchive packages pool/main > dists/stable/main/binary-aarch64/Packages
gzip -9nc dists/stable/main/binary-aarch64/Packages > dists/stable/main/binary-aarch64/Packages.gz
apt-ftparchive -o APT::FTPArchive::Release::Origin=OceanStudio -o APT::FTPArchive::Release::Label=Ocean -o APT::FTPArchive::Release::Architectures=aarch64 -o APT::FTPArchive::Release::Components=main release dists/stable > dists/stable/Release
: "${OCEAN_REPO_SIGNING_KEY:?Set OCEAN_REPO_SIGNING_KEY to a CI-only GPG key fingerprint}"
gpg --batch --yes --local-user "$OCEAN_REPO_SIGNING_KEY" --clearsign -o dists/stable/InRelease dists/stable/Release
gpg --batch --yes --local-user "$OCEAN_REPO_SIGNING_KEY" --detach-sign -o dists/stable/Release.gpg dists/stable/Release
gpg --batch --export "$OCEAN_REPO_SIGNING_KEY" > "$OUT/ocean-repository.gpg"
# Install actual deb payloads into the bootstrap root and initialize dpkg state.
for deb in "$OUT/debs"/*.deb; do dpkg-deb -x "$deb" "$OUT/bootstrap/root"; done
mkdir -p "$OUT/bootstrap/root$OCEAN_PREFIX/etc/apt/sources.list.d" "$OUT/bootstrap/root$OCEAN_PREFIX/etc/apt/trusted.gpg.d" "$OUT/bootstrap/root$OCEAN_PREFIX/var/lib/dpkg"
printf 'deb %s stable main\n' "$OCEAN_REPOSITORY_URL" > "$OUT/bootstrap/root$OCEAN_PREFIX/etc/apt/sources.list.d/ocean.list"
cp "$OUT/ocean-repository.gpg" "$OUT/bootstrap/root$OCEAN_PREFIX/etc/apt/trusted.gpg.d/ocean.gpg"
: > "$OUT/bootstrap/root$OCEAN_PREFIX/var/lib/dpkg/status"
# APK extraction root is filesDir, therefore archive paths begin with usr/.
cd "$OUT/bootstrap/root/data/data/$OCEAN_APP_PACKAGE/files"
tar --sort=name --mtime='UTC 2026-01-01' --owner=0 --group=0 --numeric-owner -cf "$WORK/ocean-aarch64.tar" usr
COUNT=$(tar -tf "$WORK/ocean-aarch64.tar"|wc -l)
zstd -19 -T0 "$WORK/ocean-aarch64.tar" -o "$OUT/bootstrap/ocean-aarch64.tar.zst"
ARCHIVE=$OUT/bootstrap/ocean-aarch64.tar.zst
SHA=$(sha256sum "$ARCHIVE"|cut -d' ' -f1); SIZE=$(stat -c%s "$ARCHIVE"); FPR=$(gpg --with-colons --fingerprint "$OCEAN_REPO_SIGNING_KEY"|awk -F: '$1=="fpr"{print $10;exit}')
python3 - "$OUT/bootstrap/ocean-aarch64.manifest.json" "$SHA" "$SIZE" "$COUNT" "$FPR" "${CORE[*]} ocean-pkg ocean-hello" <<'PY'
import json,os,sys
p,sha,size,count,fpr,packages=sys.argv[1:]
m={"bootstrapVersion":"1.0.0","architecture":"aarch64","packageName":"studio.ocean.app","prefix":"/data/data/studio.ocean.app/files/usr","archive":"ocean-aarch64.tar.zst","archiveSha256":sha,"archiveSize":int(size),"entryCount":int(count),"packageList":packages.split(),"buildCommit":os.getenv("GITHUB_SHA","local"),"repositoryUrl":"https://foxerdude90-source.github.io/Oceanstudio.apk/apt","repositoryKeyFingerprint":fpr}
open(p,'w').write(json.dumps(m,indent=2)+"\n")
PY
python3 "$ROOT/ocean-packages/scripts/verify-bootstrap.py" "$OUT/bootstrap/ocean-aarch64.manifest.json" "$ARCHIVE"
