#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "==> OceanStudio Local / Self-Hosted Shard Builder"
echo "Target Root: $ROOT_DIR"

python3 "$SCRIPT_DIR/build-local-shards.py"

echo "==> Validating generated artifacts..."
python3 - <<'PY'
import glob, subprocess, sys
debs = glob.glob(f"{subprocess.check_output(['git', 'rev-parse', '--show-toplevel']).decode().strip()}/ocean-packages/build/shard-debs/**/*.deb", recursive=True)
print(f"Found {len(debs)} shard packages.")
for deb in sorted(debs):
    info = subprocess.check_output(["dpkg-deb", "-I", deb]).decode()
    pkg = [line for line in info.splitlines() if "Package:" in line][0].split(":")[1].strip()
    arch = [line for line in info.splitlines() if "Architecture:" in line][0].split(":")[1].strip()
    print(f"  ✓ {pkg} ({arch})")
PY
echo "==> Build and validation complete."
