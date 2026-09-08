#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
ANDROID_HOME=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
JAVA_HOME=${JAVA_HOME:-}
[[ -n "$ANDROID_HOME" && -x "$ANDROID_HOME/build-tools/35.0.0/apksigner" ]] || {
  echo 'Set ANDROID_HOME to an SDK containing Android 35 and build-tools 35.0.0.' >&2; exit 1;
}
[[ -n "$JAVA_HOME" && -x "$JAVA_HOME/bin/java" ]] || {
  echo 'Set JAVA_HOME to a working JDK 17 installation.' >&2; exit 1;
}
for command in python3 gpg gpgv dpkg-deb zstd tar unzip sha256sum; do
  command -v "$command" >/dev/null || { echo "Required command is missing: $command" >&2; exit 1; }
done

ASSETS="$ROOT/android/app/src/main/assets/ocean/bootstrap/aarch64"
mkdir -p "$ASSETS"
python3 "$ROOT/ocean-packages/scripts/hydrate-signed-bootstrap.py" --output "$ASSETS"
python3 "$ROOT/ocean-packages/scripts/verify-bootstrap.py" \
  "$ASSETS/ocean-aarch64.manifest.json" "$ASSETS/ocean-aarch64.tar.zst"
SHA=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["archiveSha256"])' "$ASSETS/ocean-aarch64.manifest.json")
VERSION=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["bootstrapVersion"])' "$ASSETS/ocean-aarch64.manifest.json")
COMMIT=$(git -C "$ROOT" rev-parse HEAD)

(cd "$ROOT/android" && \
  ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_HOME" JAVA_HOME="$JAVA_HOME" \
  PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH" \
  OCEAN_BUILD_COMMIT="$COMMIT" OCEAN_BOOTSTRAP_BUILD_COMMIT="$COMMIT" \
  OCEAN_BOOTSTRAP_VERSION="$VERSION" OCEAN_BOOTSTRAP_SHA256="$SHA" \
  ./gradlew clean test assembleDebug assembleDebugAndroidTest --no-daemon --stacktrace)

APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
unzip -p "$APK" assets/ocean/bootstrap/aarch64/ocean-aarch64.tar.zst > "$TMP/ocean-aarch64.tar.zst"
unzip -p "$APK" assets/ocean/bootstrap/aarch64/ocean-aarch64.manifest.json > "$TMP/ocean-aarch64.manifest.json"
cmp "$ASSETS/ocean-aarch64.tar.zst" "$TMP/ocean-aarch64.tar.zst"
cmp "$ASSETS/ocean-aarch64.manifest.json" "$TMP/ocean-aarch64.manifest.json"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose "$APK"
printf 'APK: %s\nSHA256: %s\n' "$APK" "$(sha256sum "$APK" | cut -d' ' -f1)"
