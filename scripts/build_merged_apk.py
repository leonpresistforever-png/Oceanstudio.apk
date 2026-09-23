#!/usr/bin/env python3
"""Builds merged APK with secondary DEX files, native libraries, and release signing."""
import hashlib
import os
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEW_APK = ROOT / "android/app/build/outputs/apk/debug/app-debug.apk"
OLD_APK = ROOT / "releases/OceanStudio-1.2.0-arm64-debug.apk"
BUILD_DIR = ROOT / "build"
UNALIGNED_APK = BUILD_DIR / "merged-unaligned.apk"
ALIGNED_APK = BUILD_DIR / "merged-aligned.apk"
OUTPUT_APK = ROOT / "releases/OceanStudio-1.2.1-arm64-debug.apk"

KEYSTORE = Path(os.path.expanduser("~/oceanstudio-release.jks"))
STORE_PASS = "oceanstudio"
KEY_ALIAS = "oceanstudio"
KEY_PASS = "oceanstudio"

STORE_EXTENSIONS = (".so", ".zst", ".gz")

def build_merged_apk():
    BUILD_DIR.mkdir(parents=True, exist_ok=True)
    if not NEW_APK.is_file():
        raise FileNotFoundError(f"Source APK not found: {NEW_APK}")
    if not OLD_APK.is_file():
        raise FileNotFoundError(f"Base 1.2.0 APK not found: {OLD_APK}")

    added = set()
    with zipfile.ZipFile(UNALIGNED_APK, "w") as out_zip:
        with zipfile.ZipFile(NEW_APK, "r") as new_z:
            for item in new_z.infolist():
                if item.filename.startswith("META-INF/"):
                    continue
                data = new_z.read(item.filename)
                compress = zipfile.ZIP_STORED if item.filename.endswith(STORE_EXTENSIONS) else zipfile.ZIP_DEFLATED
                out_zip.writestr(item.filename, data, compress_type=compress)
                added.add(item.filename)

        with zipfile.ZipFile(OLD_APK, "r") as old_z:
            for item in old_z.infolist():
                if item.filename.startswith("META-INF/") or item.filename in added:
                    continue
                if item.filename.startswith("classes") and item.filename != "classes.dex":
                    data = old_z.read(item.filename)
                    out_zip.writestr(item.filename, data, compress_type=zipfile.ZIP_DEFLATED)
                    added.add(item.filename)
                elif item.filename.startswith("lib/"):
                    data = old_z.read(item.filename)
                    out_zip.writestr(item.filename, data, compress_type=zipfile.ZIP_STORED)
                    added.add(item.filename)
                elif item.filename == "DebugProbesKt.bin":
                    data = old_z.read(item.filename)
                    out_zip.writestr(item.filename, data, compress_type=zipfile.ZIP_DEFLATED)
                    added.add(item.filename)

    print(f"Merged unaligned APK: {UNALIGNED_APK.stat().st_size} bytes")

    # Zipalign
    subprocess.run(["zipalign", "-f", "-p", "4", str(UNALIGNED_APK), str(ALIGNED_APK)], check=True)
    print(f"Aligned APK: {ALIGNED_APK.stat().st_size} bytes")

    # Sign
    env = os.environ.copy()
    env["SP"] = STORE_PASS
    env["KP"] = KEY_PASS

    cmd_sign = [
        "apksigner", "sign",
        "--ks", str(KEYSTORE),
        "--ks-pass", "env:SP",
        "--ks-key-alias", KEY_ALIAS,
        "--key-pass", "env:KP",
        "--min-sdk-version", "28",
        "--v1-signing-enabled", "true",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        "--out", str(OUTPUT_APK),
        str(ALIGNED_APK)
    ]
    subprocess.run(cmd_sign, check=True, env=env)
    print(f"Signed APK: {OUTPUT_APK.stat().st_size} bytes")

    # Verify
    verify_output = subprocess.check_output(
        ["apksigner", "verify", "--verbose", "--print-certs", str(OUTPUT_APK)],
        text=True
    )
    print("Verification:")
    for line in verify_output.splitlines():
        if "Verifies" in line or "Signer #1 certificate SHA-256" in line:
            print(" ", line)

if __name__ == "__main__":
    build_merged_apk()
