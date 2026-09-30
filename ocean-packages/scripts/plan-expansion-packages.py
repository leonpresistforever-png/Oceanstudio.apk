#!/usr/bin/env python3
"""List candidate package names for expansion that are not already in the live index."""
from __future__ import annotations

import argparse
import gzip
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_TARGETS = ROOT / "sources/expansion-1000/target-packages.json"

PRIORITY = [
    "alsa-utils",
    "libnotify-bin",
    "libnotify",
    "x11-apps",
    "xeyes",
    "xauth",
    "xset",
    "espeak-ng",
    "espeak",
    "festival",
    "flite",
    "paplay",
    "pactl",
    "ffprobe",
    "imagemagick",
    "graphicsmagick",
    "ghostscript",
    "poppler-utils",
    "qpdf",
    "ripgrep",
    "fd",
    "fzf",
    "bat",
    "eza",
    "zoxide",
    "htop",
    "btop",
    "ncdu",
    "jq",
    "yq",
    "curl",
    "wget",
    "httpie",
    "bind9-dnsutils",
    "dnsutils",
    "git-lfs",
    "lazygit",
    "tig",
    "tmux",
    "neovim",
    "helix",
    "micro",
    "go",
    "rustc",
    "cargo",
    "deno",
    "bun",
    "php",
    "ruby",
    "perl",
    "postgresql",
    "sqlite3",
    "redis",
    "nginx",
    "apache2",
    "caddy",
    "terraform",
    "kubectl",
    "helm",
    "awscli",
    "gh",
    "docker-cli",
    "binwalk",
    "yara",
    "radare2",
    "nmap",
    "masscan",
    "trivy",
    "grype",
    "openblas",
    "fftw3",
    "gmp",
    "mpfr",
    "mpc",
    "gcc",
    "gfortran",
    "meson",
    "ninja",
    "bazel",
    "ccache",
    "sccache",
    "pkg-config",
    "pandoc",
    "texlive-binaries",
    "tesseract-ocr",
    "ocrmypdf",
    "opencv",
    "ffmpeg",
    "gstreamer1.0-tools",
    "v4l-utils",
]


def resolve_repo_root(explicit: Path | None) -> Path:
    if explicit is not None:
        return explicit.resolve()
    env = Path(os.environ.get("OCEAN_PACKAGES_ROOT", ""))
    if env.is_dir() and (env / "apt").is_dir():
        return env.resolve()
    if (ROOT / "apt").is_dir():
        return ROOT.resolve()
    sibling = ROOT.parent / "Oceanstudio-packages"
    if sibling.is_dir() and (sibling / "apt").is_dir():
        return sibling.resolve()
    raise SystemExit(
        "No APT tree found. Set --root or OCEAN_PACKAGES_ROOT to Oceanstudio-packages."
    )


def indexed_names(repo: Path) -> set[str]:
    index = repo / "apt/dists/stable/main/binary-aarch64/Packages.gz"
    if not index.is_file():
        raise SystemExit(f"Missing index: {index}")
    text = gzip.open(index, "rt", encoding="utf-8", errors="replace").read()
    names: set[str] = set()
    for block in text.split("\n\n"):
        if not block.startswith("Package:"):
            continue
        for line in block.splitlines():
            if line.startswith("Package:"):
                names.add(line.split(":", 1)[1].strip())
    return names


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, help="Oceanstudio-packages checkout")
    parser.add_argument("--write", action="store_true", help="Write target-packages.json")
    parser.add_argument("--targets", type=Path, default=DEFAULT_TARGETS)
    args = parser.parse_args()

    repo = resolve_repo_root(args.root)
    live = indexed_names(repo)
    missing_priority = [name for name in PRIORITY if name not in live]
    payload = {
        "schemaVersion": 1,
        "indexedCount": len(live),
        "priorityMissing": missing_priority,
        "notes": "Names here are install targets only; each requires an official Ocean source build before publication.",
    }
    if args.write:
        out = args.targets
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
