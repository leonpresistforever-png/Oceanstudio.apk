#!/usr/bin/env python3
"""Hydrate Ocean's minimal runtime from its signed APT repository.

This is a local-build fallback for environments without the package compiler
cache. It never invents packages: repository metadata is signature checked,
every downloaded .deb is SHA-256 checked, and the complete dependency closure
is installed into an Android-prefix staging tree.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import re
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PREFIX = Path("data/data/studio.ocean.app/files/usr")
DEFAULT_SEEDS = ("bash", "apt", "libcurl", "ocean-pkg", "ocean-hello")


def paragraphs(text: str) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for block in text.strip().split("\n\n"):
        item: dict[str, str] = {}
        key = ""
        for line in block.splitlines():
            if line[:1].isspace() and key:
                item[key] += "\n" + line
            elif ": " in line:
                key, value = line.split(": ", 1)
                item[key] = value
        if item:
            result.append(item)
    return result


def dependency_groups(value: str) -> list[list[str]]:
    groups: list[list[str]] = []
    for group in re.split(r",\s*", value):
        alternatives = []
        for choice in group.split("|"):
            name = re.split(r"[\s(]", choice.strip(), maxsplit=1)[0].split(":", 1)[0]
            if name:
                alternatives.append(name)
        if alternatives:
            groups.append(alternatives)
    return groups


def resolve(entries: list[dict[str, str]], seeds: tuple[str, ...]) -> list[dict[str, str]]:
    by_name: dict[str, dict[str, str]] = {}
    providers: dict[str, str] = {}
    for entry in entries:
        name = entry.get("Package", "")
        if name and name not in by_name:
            by_name[name] = entry
        for provided in dependency_groups(entry.get("Provides", "")):
            for virtual in provided:
                providers.setdefault(virtual, name)
    selected: dict[str, dict[str, str]] = {}
    pending = list(seeds)
    while pending:
        requested = pending.pop(0)
        actual = requested if requested in by_name else providers.get(requested, "")
        if not actual:
            raise RuntimeError(f"repository cannot satisfy dependency: {requested}")
        if actual in selected:
            continue
        entry = by_name[actual]
        selected[actual] = entry
        for field in ("Pre-Depends", "Depends"):
            for alternatives in dependency_groups(entry.get(field, "")):
                candidate = next((x for x in alternatives if x in by_name or x in providers), "")
                if not candidate:
                    raise RuntimeError(f"{actual} has unresolved dependency: {' | '.join(alternatives)}")
                pending.append(candidate)
    return [selected[name] for name in sorted(selected)]


def download(url: str, destination: Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "OceanStudio-bootstrap-builder/1"})
    with urllib.request.urlopen(request, timeout=120) as response, destination.open("wb") as out:
        shutil.copyfileobj(response, out)


def run(*args: object, **kwargs: object) -> subprocess.CompletedProcess[str]:
    return subprocess.run([str(x) for x in args], check=True, text=True, **kwargs)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository-url", default="https://raw.githubusercontent.com/leonpresistforever-png/Oceanstudio-packages/main/apt")
    parser.add_argument("--seed", action="append", default=[])
    args = parser.parse_args()
    seeds = tuple(args.seed) or DEFAULT_SEEDS
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="ocean-bootstrap-") as temporary:
        work = Path(temporary)
        inrelease, compressed, key = work / "InRelease", work / "Packages.gz", work / "ocean.gpg"
        download(f"{args.repository_url}/dists/stable/InRelease", inrelease)
        download(f"{args.repository_url}/dists/stable/main/binary-aarch64/Packages.gz", compressed)
        download(f"{args.repository_url}/ocean.gpg", key)
        run("gpgv", "--keyring", key, inrelease, stdout=subprocess.DEVNULL)
        expected = (ROOT / "ocean-packages/keys/ocean-development-repository.fingerprint").read_text().strip()
        shown = run("gpg", "--batch", "--show-keys", "--with-colons", key, stdout=subprocess.PIPE).stdout
        actual = next(line.split(":")[9] for line in shown.splitlines() if line.startswith("fpr:"))
        if actual != expected:
            raise RuntimeError(f"repository key mismatch: expected={expected} actual={actual}")
        entries = paragraphs(gzip.decompress(compressed.read_bytes()).decode())
        closure = resolve(entries, seeds)

        root = work / "root"
        prefix = root / PREFIX
        debs = work / "debs"
        info = prefix / "var/lib/dpkg/info"
        debs.mkdir(); info.mkdir(parents=True)
        status_blocks: list[str] = []
        installed: list[dict[str, object]] = []
        for entry in closure:
            for field in ("Package", "Version", "Architecture", "Filename", "SHA256"):
                if not entry.get(field):
                    raise RuntimeError(f"signed index entry lacks {field}: {entry.get('Package', '<unknown>')}")
            deb = debs / Path(entry["Filename"]).name
            download(f"{args.repository_url}/{entry['Filename']}", deb)
            digest = hashlib.sha256(deb.read_bytes()).hexdigest()
            if digest != entry["SHA256"]:
                raise RuntimeError(f"package checksum mismatch: {deb.name}")
            run("dpkg-deb", "-x", deb, root)
            control = work / "control" / entry["Package"]
            control.mkdir(parents=True)
            run("dpkg-deb", "-e", deb, control)
            control_fields = (control / "control").read_text().rstrip()
            status_blocks.append(control_fields + "\nStatus: install ok installed\n")
            listing = run("dpkg-deb", "-c", deb, stdout=subprocess.PIPE).stdout
            paths = []
            for line in listing.splitlines():
                parts = line.split(maxsplit=5)
                if len(parts) == 6:
                    paths.append("/" + parts[5].removeprefix("./"))
            (info / f"{entry['Package']}.list").write_text("\n".join(paths) + "\n")
            for name in ("md5sums", "conffiles", "preinst", "postinst", "prerm", "postrm", "config", "triggers"):
                source = control / name
                if source.is_file():
                    target = info / f"{entry['Package']}.{name}"
                    shutil.copy2(source, target)
                    if name in {"preinst", "postinst", "prerm", "postrm", "config"}:
                        target.chmod(0o755)
            installed.append({"name": entry["Package"], "version": entry["Version"], "sha256": digest, "artifact": deb.name})

        (prefix / "var/lib/dpkg/status").write_text("\n".join(status_blocks))
        (prefix / "var/lib/dpkg/updates").mkdir(parents=True, exist_ok=True)
        (prefix / "tmp").mkdir(parents=True, exist_ok=True)
        (prefix / "tmp").chmod(0o700)
        (prefix / "var/run").mkdir(parents=True, exist_ok=True)
        (prefix / "var/lib/apt/lists/partial").mkdir(parents=True, exist_ok=True)
        (prefix / "var/cache/apt/archives/partial").mkdir(parents=True, exist_ok=True)
        (prefix / "var/log/apt").mkdir(parents=True, exist_ok=True)
        (prefix / "etc/apt/keyrings").mkdir(parents=True, exist_ok=True)
        shutil.copy2(key, prefix / "etc/apt/keyrings/ocean.gpg")
        sources = prefix / "etc/apt/sources.list.d"
        sources.mkdir(parents=True, exist_ok=True)
        (sources / "ocean.list").write_text(
            f"deb [signed-by=/data/data/studio.ocean.app/files/usr/etc/apt/keyrings/ocean.gpg] {args.repository_url} stable main\n"
        )
        shell = prefix / "bin/sh"
        if not shell.exists():
            shell.symlink_to("bash")

        # Build-prefix absolute symlinks are valid inside packages but Android
        # may expose filesDir as /data/user/0. Store relative links so the same
        # archive works through either canonical path spelling.
        canonical_prefix = "/data/data/studio.ocean.app/files/usr"
        for path in prefix.rglob("*"):
            if not path.is_symlink():
                continue
            target = os.readlink(path)
            if target == canonical_prefix or target.startswith(canonical_prefix + "/"):
                logical_target = prefix / target[len(canonical_prefix) + 1:]
                path.unlink()
                path.symlink_to(os.path.relpath(logical_target, path.parent))

        archive = output / "ocean-aarch64.tar.zst"
        tar_path = work / "ocean-aarch64.tar"
        def reproducible(member: tarfile.TarInfo) -> tarfile.TarInfo:
            member.uid = member.gid = 0
            member.uname = member.gname = ""
            member.mtime = 0
            return member
        with tarfile.open(tar_path, "w") as tar:
            tar.add(prefix, arcname="usr", recursive=True, filter=reproducible)
        with tarfile.open(tar_path) as tar:
            archive_entries = len(set(tar.getnames()))
        run("zstd", "-19", "-T0", "-f", tar_path, "-o", archive)
        manifest = {
            "bootstrapVersion": "1.0.2",
            "architecture": "aarch64",
            "packageName": "studio.ocean.app",
            "prefix": "/data/data/studio.ocean.app/files/usr",
            "archive": archive.name,
            "archiveSha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
            "archiveSize": archive.stat().st_size,
            "entryCount": archive_entries,
            "packageList": [x["name"] for x in installed],
            "packages": installed,
            "buildCommit": os.environ.get("GITHUB_SHA", "local-signed-repository-hydration"),
            "repositoryUrl": args.repository_url,
            "repositoryKeyFingerprint": actual,
        }
        (output / "ocean-aarch64.manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        print(f"Hydrated {len(installed)} signed packages into {archive} ({archive.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
