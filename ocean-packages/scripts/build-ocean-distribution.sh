#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
source "$ROOT/ocean-packages/config.env"
WORK=${OCEAN_BUILD_WORK:-$ROOT/ocean-packages/build}
UPSTREAM=$WORK/package-builder
OUT=$WORK/out
mkdir -p "$WORK" "$ROOT/ocean-packages/.cache/sources"
rm -rf "$OUT"; mkdir -p "$OUT/debs" "$OUT/repository/pool/main" "$OUT/bootstrap/root/usr"
if [[ ! -d "$UPSTREAM/.git" ]]; then
  # actions/cache may restore output/ before the pinned source checkout exists.
  # Preserve those completed packages while replacing the cache-created shell
  # directory with the real Git checkout.
  CACHED_OUTPUT="$WORK/restored-package-output"
  rm -rf "$CACHED_OUTPUT"
  if [[ -d "$UPSTREAM/output" ]]; then mv "$UPSTREAM/output" "$CACHED_OUTPUT"; fi
  rm -rf "$UPSTREAM"
  git clone https://github.com/termux/termux-packages.git "$UPSTREAM"
  if [[ -d "$CACHED_OUTPUT" ]]; then mv "$CACHED_OUTPUT" "$UPSTREAM/output"; fi
fi
git -C "$UPSTREAM" fetch --no-tags origin "$UPSTREAM_PACKAGES_COMMIT"
git -C "$UPSTREAM" reset --hard "$UPSTREAM_PACKAGES_COMMIT"
git -C "$UPSTREAM" clean -fd -e output -e .ocean-cache
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
# Install Ocean's checksum-addressed resilient downloader into the audited
# builder. Its persistent cache is inside the checkout so Actions can restore
# it without exposing unverified files as package inputs.
install -m755 "$ROOT/ocean-packages/scripts/ocean-download.sh" \
  "$UPSTREAM/scripts/build/termux_download.sh"
mkdir -p "$UPSTREAM/.ocean-cache/sources"
cp -a "$ROOT/ocean-packages/.cache/sources/." "$UPSTREAM/.ocean-cache/sources/"
# GitHub's host runner and the package-builder image use different numeric
# UIDs. This cache contains only public, checksum-verified source archives, so
# grant the isolated builder write access without changing package integrity.
chmod -R a+rwX "$UPSTREAM/.ocean-cache/sources"
sync_source_cache() {
  mkdir -p "$ROOT/ocean-packages/.cache/sources"
  cp -a "$UPSTREAM/.ocean-cache/sources/." "$ROOT/ocean-packages/.cache/sources/" 2>/dev/null || true
}
trap sync_source_cache EXIT
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
# Savannah's plain-HTTP endpoint intermittently resets long-running GitHub
# builds. Use its canonical TLS endpoint without changing the pinned source
# archive or checksum.
grep -rl 'http://download.savannah.gnu.org/' "$UPSTREAM/packages" \
  | xargs -r sed -i 's#http://download\.savannah\.gnu\.org/#https://download.savannah.gnu.org/#g'
# attr's recipe provides a checksum-verified GNU mirror fallback. The custom
# downloader tries it only after the canonical Savannah endpoint fails.
python3 - "$UPSTREAM/packages/attr/build.sh" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1])
s = p.read_text()
needle = 'TERMUX_PKG_SRCURL="https://download.savannah.gnu.org/releases/attr/attr-${TERMUX_PKG_VERSION}.tar.gz"'
replacement = needle[:-1] + '|https://ftpmirror.gnu.org/attr/attr-${TERMUX_PKG_VERSION}.tar.gz"'
if needle not in s:
    raise SystemExit("Pinned attr source recipe changed unexpectedly")
p.write_text(s.replace(needle, replacement))
PY
# Each result is built from upstream source by Android NDK for the Ocean prefix.
ROOT_PACKAGES=(bash apt libcurl)
for package in "${ROOT_PACKAGES[@]}"; do
  test -f "$UPSTREAM/packages/$package/build.sh" || {
    echo "Ocean package recipe does not exist at pinned upstream commit: $package" >&2
    exit 1
  }
done
# The cache lives inside the checkout mounted by run-docker while compilation
# is active; the EXIT trap synchronizes it back to the Actions cache path even
# when a later source or package fails.
(cd "$UPSTREAM"; ./scripts/run-docker.sh ./build-package.sh -a aarch64 "${ROOT_PACKAGES[@]}")
# Runtime dependency closure contains both architecture-specific and
# Architecture: all data packages. Omitting the latter produces a bootstrap
# whose ELF files exist but whose certificates/configuration are incomplete.
find "$UPSTREAM/output" -type f \( -name '*_aarch64.deb' -o -name '*_all.deb' \) -exec cp -v {} "$OUT/debs/" \;
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
mkdir -p "$OUT/bootstrap/root$OCEAN_PREFIX/etc/apt/apt.conf.d" "$OUT/bootstrap/root$OCEAN_PREFIX/etc/apt/sources.list.d" "$OUT/bootstrap/root$OCEAN_PREFIX/etc/apt/keyrings" "$OUT/bootstrap/root$OCEAN_PREFIX/var/lib/dpkg"
printf 'deb [signed-by=%s/etc/apt/keyrings/ocean.gpg] %s stable main\n' "$OCEAN_PREFIX" "$OCEAN_REPOSITORY_URL" > "$OUT/bootstrap/root$OCEAN_PREFIX/etc/apt/sources.list.d/ocean.list"
cp "$OUT/ocean-repository.gpg" "$OUT/bootstrap/root$OCEAN_PREFIX/etc/apt/keyrings/ocean.gpg"
cat > "$OUT/bootstrap/root$OCEAN_PREFIX/etc/apt/apt.conf.d/00-ocean-paths" <<EOF
Dir "$OCEAN_PREFIX";
Dir::Etc "etc/apt";
Dir::State "var/lib/apt";
Dir::State::status "var/lib/dpkg/status";
Dir::Cache "var/cache/apt";
Dir::Log "var/log/apt";
EOF
# Register the packages whose payloads form the bootstrap. This is real dpkg
# state derived from each .deb control archive, not hand-written package data.
STATUS="$OUT/bootstrap/root$OCEAN_PREFIX/var/lib/dpkg/status"
: > "$STATUS"
for deb in "$OUT/debs"/*.deb; do
  dpkg-deb -f "$deb" Package Version Architecture Maintainer Depends Section Priority Description >> "$STATUS"
  printf 'Status: install ok installed\n\n' >> "$STATUS"
done
# APK extraction root is filesDir, therefore archive paths begin with usr/.
cd "$OUT/bootstrap/root/data/data/$OCEAN_APP_PACKAGE/files"
tar --sort=name --mtime='UTC 2026-01-01' --owner=0 --group=0 --numeric-owner -cf "$WORK/ocean-aarch64.tar" usr
COUNT=$(tar -tf "$WORK/ocean-aarch64.tar"|wc -l)
zstd -19 -T0 "$WORK/ocean-aarch64.tar" -o "$OUT/bootstrap/ocean-aarch64.tar.zst"
ARCHIVE=$OUT/bootstrap/ocean-aarch64.tar.zst
SHA=$(sha256sum "$ARCHIVE"|cut -d' ' -f1); SIZE=$(stat -c%s "$ARCHIVE"); FPR=$(gpg --with-colons --fingerprint "$OCEAN_REPO_SIGNING_KEY"|awk -F: '$1=="fpr"{print $10;exit}')
python3 - "$OUT/bootstrap/ocean-aarch64.manifest.json" "$SHA" "$SIZE" "$COUNT" "$FPR" "$OUT/debs" <<'PY'
import json,os,pathlib,subprocess,sys
p,sha,size,count,fpr,debs=sys.argv[1:]
packages=[]
for deb in sorted(pathlib.Path(debs).glob('*.deb')):
 def field(name): return subprocess.check_output(['dpkg-deb','-f',deb,name],text=True).strip()
 name,version,arch=field('Package'),field('Version'),field('Architecture')
 packages.append({'name':name,'version':version,'architecture':arch,'artifact':deb.name,'size':deb.stat().st_size})
m={"bootstrapVersion":"1.0.0","architecture":"aarch64","packageName":"studio.ocean.app","prefix":"/data/data/studio.ocean.app/files/usr","archive":"ocean-aarch64.tar.zst","archiveSha256":sha,"archiveSize":int(size),"entryCount":int(count),"packageList":[x['name'] for x in packages],"packages":packages,"buildCommit":os.getenv("GITHUB_SHA","local"),"repositoryUrl":"https://foxerdude90-source.github.io/Oceanstudio.apk/apt","repositoryKeyFingerprint":fpr}
open(p,'w').write(json.dumps(m,indent=2)+"\n")
PY
python3 "$ROOT/ocean-packages/scripts/verify-bootstrap.py" "$OUT/bootstrap/ocean-aarch64.manifest.json" "$ARCHIVE"
