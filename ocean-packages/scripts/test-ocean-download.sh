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

bash "$(dirname "$0")/ocean-download.sh" \
  "file://$ROOT/wrong.tar|file://$ROOT/right.tar" "$ROOT/result.tar" "$SHA"
test "$(sha256sum "$ROOT/result.tar" | cut -d' ' -f1)" = "$SHA"
# The second retrieval must reuse the checksum-verified destination without
# contacting either now-missing origin candidate.
rm "$ROOT/wrong.tar" "$ROOT/right.tar"
bash "$(dirname "$0")/ocean-download.sh" \
  "file://$ROOT/wrong.tar|file://$ROOT/right.tar" "$ROOT/result.tar" "$SHA"
test "$(sha256sum "$ROOT/result.tar" | cut -d' ' -f1)" = "$SHA"

echo "Ocean source download fallback and verified cache: PASS"
