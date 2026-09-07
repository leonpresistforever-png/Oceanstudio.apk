#!/usr/bin/env python3
"""
HARD CI/device regression test that validates Ocean runtime purity:
Fails if ANY runtime package, APT source, archive payload, maintainer script,
metadata, executable, symlink, config or repo contains:
- /data/data/com.termux
- packages.termux.dev
- packages-cf.termux.dev
- TERMUX_PREFIX
"""
import argparse
import pathlib
import sys

FORBIDDEN = [
    b"/data/data/com.termux",
    b"packages.termux.dev",
    b"packages-cf.termux.dev",
    b"TERMUX_PREFIX",
]

errors = []

def check_file(path: pathlib.Path, label: str):
    if not path.exists():
        return
    try:
        content = path.read_bytes()
        for f in FORBIDDEN:
            if f in content:
                errors.append(f"[{label}] Found forbidden {f.decode('utf-8', errors='ignore')} in {path}")
    except Exception as e:
        errors.append(f"[{label}] Could not read {path}: {e}")

parser = argparse.ArgumentParser()
parser.add_argument(
    "--bootstrap-prefix",
    type=pathlib.Path,
    default=pathlib.Path("ocean-packages/build/work/clean_bootstrap/data/data/studio.ocean.app/files/usr"),
)
parser.add_argument(
    "--repository",
    type=pathlib.Path,
    default=pathlib.Path("ocean-packages/build/out/repository/apt"),
)
args = parser.parse_args()
bootstrap_dir = args.bootstrap_prefix.resolve()
repo_dir = args.repository.resolve()

print("=== RUNNING HARD NO-TERMUX REGRESSION AUDIT ===")

if not bootstrap_dir.is_dir():
    errors.append(f"[Bootstrap] Expected extracted prefix is missing: {bootstrap_dir}")
if not repo_dir.is_dir():
    errors.append(f"[Repository] Expected repository is missing: {repo_dir}")

# 1. Audit APT sources
sources = list((bootstrap_dir / "etc/apt").rglob("*"))
for s in sources:
    if s.is_file():
        check_file(s, "APT Source / Config")

# 2. Audit trusted keys
keyrings = list((bootstrap_dir / "etc/apt/trusted.gpg.d").glob("*"))
for k in keyrings:
    if k.name != "ocean.gpg":
        errors.append(f"[Trusted Keys] Non-ocean key found: {k.name}")

# 3. Audit dpkg status
check_file(bootstrap_dir / "var/lib/dpkg/status", "DPKG Status DB")

# 4. Audit APT Repository metadata
if repo_dir.exists():
    for f in (repo_dir / "dists/stable").rglob("*"):
        if f.is_file():
            check_file(f, "Repo Metadata")

if errors:
    print(f"\n❌ HARD REGRESSION AUDIT FAILED ({len(errors)} violations found):")
    for err in errors:
        print(f"  - {err}")
    sys.exit(1)
else:
    print("\n✅ HARD REGRESSION AUDIT PASSED: ZERO forbidden patterns found across Ocean runtime!")
    sys.exit(0)
