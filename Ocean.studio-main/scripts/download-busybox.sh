#!/usr/bin/env bash
set -euo pipefail

# Downloads a static busybox arm64 binary for the Ocean native terminal plugin.
# Uses Alpine Linux busybox-static package (reliable mirror).

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSET_DIR="$ROOT/plugins/ocean-native-terminal/android/src/main/assets"
TARGET="$ASSET_DIR/busybox-arm64"
ALPINE_VERSION="1.38.0-r4"
APK_URL="https://dl-cdn.alpinelinux.org/alpine/edge/main/aarch64/busybox-static-${ALPINE_VERSION}.apk"

mkdir -p "$ASSET_DIR"

if [[ -f "$TARGET" ]]; then
  echo "busybox-arm64 already exists at $TARGET ($(wc -c < "$TARGET") bytes)"
  exit 0
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "Downloading busybox-static ${ALPINE_VERSION} for aarch64..."
if command -v curl >/dev/null 2>&1; then
  curl -fsSL "$APK_URL" -o "$TMP/busybox.apk"
elif command -v wget >/dev/null 2>&1; then
  wget -q "$APK_URL" -O "$TMP/busybox.apk"
else
  echo "Error: curl or wget required" >&2
  exit 1
fi

tar -xzf "$TMP/busybox.apk" -C "$TMP"
cp "$TMP/bin/busybox.static" "$TARGET"
chmod +x "$TARGET"
echo "Saved busybox to $TARGET ($(wc -c < "$TARGET") bytes)"
