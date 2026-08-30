#!/usr/bin/env bash
set -euo pipefail
ROOT=$(mktemp -d)
trap 'rm -rf "$ROOT"' EXIT
export TERMUX_SCRIPTDIR="$ROOT"
export TERMUX_PKG_TMPDIR="$ROOT/tmp"
mkdir -p "$TERMUX_PKG_TMPDIR"
printf 'wrong archive' > "$ROOT/wrong.tar"
printf 'verified archive' > "$ROOT/right.tar"
SHA=$(sha256sum "$ROOT/right.tar" | cut -d' ' -f1)

"$(dirname "$0")/ocean-download.sh" \
  "file://$ROOT/wrong.tar|file://$ROOT/right.tar" "$ROOT/result.tar" "$SHA"
test "$(sha256sum "$ROOT/result.tar" | cut -d' ' -f1)" = "$SHA"
# The second retrieval must reuse the checksum-verified destination without
# contacting either now-missing origin candidate.
rm "$ROOT/wrong.tar" "$ROOT/right.tar"
"$(dirname "$0")/ocean-download.sh" \
  "file://$ROOT/wrong.tar|file://$ROOT/right.tar" "$ROOT/result.tar" "$SHA"
test "$(sha256sum "$ROOT/result.tar" | cut -d' ' -f1)" = "$SHA"

# Unverified source inputs must never enter either a recipe cache or a build.
if "$(dirname "$0")/ocean-download.sh" \
  "file://$ROOT/result.tar" "$ROOT/unverified.tar" SKIP_CHECKSUM; then
  echo "Downloader accepted an unverified source" >&2
  exit 1
fi
echo "Ocean source download fallback and verified cache: PASS"
