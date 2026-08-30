#!/usr/bin/env bash
set -euo pipefail
ROOT=$(mktemp -d)
trap 'rm -rf "$ROOT"' EXIT
export TERMUX_SCRIPTDIR="$ROOT"
export TERMUX_PKG_TMPDIR="$ROOT/tmp"
export OCEAN_SOURCE_CACHE="$ROOT/cache"
mkdir -p "$TERMUX_PKG_TMPDIR"
printf 'wrong archive' > "$ROOT/wrong.tar"
printf 'verified archive' > "$ROOT/right.tar"
SHA=$(sha256sum "$ROOT/right.tar" | cut -d' ' -f1)

"$(dirname "$0")/ocean-download.sh" \
  "file://$ROOT/wrong.tar|file://$ROOT/right.tar" "$ROOT/result.tar" "$SHA"
test "$(sha256sum "$ROOT/result.tar" | cut -d' ' -f1)" = "$SHA"
test -s "$OCEAN_SOURCE_CACHE/$SHA"

# The second retrieval must come from the checksum-addressed cache even after
# both origin candidates disappear.
rm "$ROOT/wrong.tar" "$ROOT/right.tar" "$ROOT/result.tar"
"$(dirname "$0")/ocean-download.sh" \
  "file://$ROOT/wrong.tar|file://$ROOT/right.tar" "$ROOT/result.tar" "$SHA"
test "$(sha256sum "$ROOT/result.tar" | cut -d' ' -f1)" = "$SHA"
echo "Ocean source download fallback and verified cache: PASS"
