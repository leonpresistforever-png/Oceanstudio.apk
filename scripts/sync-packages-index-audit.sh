#!/usr/bin/env bash
# Refresh audits/packages-index-issues-plain.txt from a sparse Oceanstudio-packages checkout.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${OCEAN_PACKAGES_SPARSE_DIR:-/tmp/ocean-pkg-sparse}"
if [[ ! -f "$WORK/apt/dists/stable/main/binary-aarch64/Packages.gz" ]]; then
  rm -rf "$WORK"
  mkdir -p "$WORK"
  git -C "$WORK" init
  git -C "$WORK" remote add origin https://github.com/leonpresistforever-png/Oceanstudio-packages.git
  git -C "$WORK" config core.sparseCheckout true
  cat >"$WORK/.git/info/sparse-checkout" <<'EOF'
apt/dists/stable/
apt/ocean.gpg
sources/expansion-1000/
EOF
  git -C "$WORK" pull --depth 1 origin main
fi
PKG_DIR="$WORK/apt/dists/stable/main/binary-aarch64"
if [[ -f "$PKG_DIR/Packages.gz" && ! -f "$PKG_DIR/Packages" ]]; then
  gzip -dc "$PKG_DIR/Packages.gz" >"$PKG_DIR/Packages"
fi
if [[ ! -f "$WORK/apt/dists/stable/InRelease" ]]; then
  echo "Sparse checkout missing signed APT metadata; delete $WORK and retry." >&2
  exit 1
fi
export OCEAN_PACKAGES_ROOT="$WORK"
python3 "$ROOT/ocean-packages/scripts/scan-package-index-issues.py" \
  --output "$ROOT/audits/packages-index-issues-plain.txt" \
  --json "$ROOT/audits/packages-index-issues.json"
python3 "$ROOT/ocean-packages/scripts/plan-expansion-packages.py" \
  --root "$WORK" \
  --write \
  --targets "$ROOT/ocean-packages/sources/expansion-1000/target-packages.json"
