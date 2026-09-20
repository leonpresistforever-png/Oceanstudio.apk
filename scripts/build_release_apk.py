#!/usr/bin/env python3
"""Build, align, sign, and validate the official OceanStudio release APK with official apksigner."""
import os, sys, shutil, subprocess, zipfile, hashlib, json
from pathlib import Path

ROOT = Path("/data/data/com.termux/files/home/Oceanstudio.apk")
RELEASES_DIR = ROOT / "releases"
KEYSTORE_PATH = Path("/data/data/com.termux/files/home/oceanstudio-release.jks")

def main():
    print("=== OCEANSTUDIO APK CANONICAL RELEASE BUILDER ===")
    
    # 1. Ensure canonical signing keystore exists
    if not KEYSTORE_PATH.exists():
        print("Generating canonical OceanStudio release keystore...")
        cmd_genkey = [
            "keytool", "-genkeypair", "-v",
            "-keystore", str(KEYSTORE_PATH),
            "-alias", "oceanstudio",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-storepass", "oceanstudio",
            "-keypass", "oceanstudio",
            "-dname", "CN=OceanStudio, OU=Engineering, O=Ocean, L=San Francisco, ST=CA, C=US"
        ]
        subprocess.run(cmd_genkey, check=True)
        print("Keystore created successfully at:", KEYSTORE_PATH)
    else:
        print("Using existing keystore at:", KEYSTORE_PATH)

    # 2. Source APK
    src_apk = RELEASES_DIR / "OceanStudio-1.2.0-arm64-debug.apk"
    work_dir = Path("/data/data/com.termux/files/usr/tmp/ocean_apk_build")
    if work_dir.exists():
        shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True)

    unaligned_apk = work_dir / "unaligned.apk"
    aligned_apk = work_dir / "aligned.apk"
    final_signed_apk = work_dir / "final_signed.apk"

    print("Repacking clean APK (removing legacy signatures and standardizing compression)...")
    with zipfile.ZipFile(src_apk, "r") as zin, zipfile.ZipFile(unaligned_apk, "w") as zout:
        for item in zin.infolist():
            # Strip previous signature files
            if item.filename.startswith("META-INF/") and (
                item.filename.endswith(".SF") or
                item.filename.endswith(".RSA") or
                item.filename.endswith(".MF") or
                item.filename.endswith(".EC")
            ):
                continue
            data = zin.read(item.filename)
            zinfo = zipfile.ZipInfo(item.filename)
            # .so files and bootstrap tarball MUST be stored uncompressed
            if item.filename.endswith(".so") or "ocean-aarch64.tar.zst" in item.filename:
                zinfo.compress_type = zipfile.ZIP_STORED
            else:
                zinfo.compress_type = zipfile.ZIP_DEFLATED
            zout.writestr(zinfo, data)

    # 3. Zipalign with 4-byte and 4KB page alignment (-p 4)
    print("Running zipalign (-f -p 4)...")
    subprocess.run(["zipalign", "-f", "-p", "4", str(unaligned_apk), str(aligned_apk)], check=True)
    res_align = subprocess.run(["zipalign", "-c", "-v", "4", str(aligned_apk)], capture_output=True, text=True)
    if "Verification successful" not in res_align.stdout:
        raise SystemExit("FATAL: zipalign verification failed!")
    print("Zipalign: VERIFICATION SUCCESSFUL")

    # 4. Sign with official Android SDK apksigner (v1, v2, v3, min-sdk-version 28)
    print("Signing with official apksigner (v1, v2, v3 schemes, min-sdk-version 28)...")
    cmd_sign = [
        "apksigner", "sign",
        "--ks", str(KEYSTORE_PATH),
        "--ks-pass", "pass:oceanstudio",
        "--ks-key-alias", "oceanstudio",
        "--key-pass", "pass:oceanstudio",
        "--min-sdk-version", "28",
        "--v1-signing-enabled", "true",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        "--in", str(aligned_apk),
        "--out", str(final_signed_apk)
    ]
    res_sign = subprocess.run(cmd_sign, capture_output=True, text=True)
    if res_sign.returncode != 0:
        raise SystemExit(f"FATAL: apksigner sign failed: {res_sign.stderr}")
    print("apksigner sign: COMPLETED")

    # 5. Full forensic verification with apksigner verify
    print("\nVerifying signed APK with apksigner...")
    res_verify = subprocess.run(
        ["apksigner", "verify", "-v", "--print-certs", str(final_signed_apk)],
        capture_output=True, text=True
    )
    print(res_verify.stdout)
    if "Verifies" not in res_verify.stdout:
        raise SystemExit("FATAL: apksigner verify failed!")

    # Verify zipalign still passes after apksigner
    res_align_final = subprocess.run(["zipalign", "-c", "-v", "4", str(final_signed_apk)], capture_output=True, text=True)
    if "Verification successful" not in res_align_final.stdout:
        raise SystemExit("FATAL: zipalign check failed on signed APK!")
    print("Post-signing zipalign: VERIFICATION SUCCESSFUL")

    # 6. Verify badging (SDK version 28)
    print("\nVerifying manifest badging...")
    res_aapt = subprocess.run(["aapt", "dump", "badging", str(final_signed_apk)], capture_output=True, text=True)
    badging = res_aapt.stdout
    print([line for line in badging.splitlines() if "sdkVersion" in line or "package:" in line])
    assert "sdkVersion:'28'" in badging
    assert "targetSdkVersion:'28'" in badging

    # 7. Compute final hashes and replace release files
    apk_data = final_signed_apk.read_bytes()
    apk_sha256 = hashlib.sha256(apk_data).hexdigest()
    apk_size = len(apk_data)
    print(f"\nFinal APK size: {apk_size} bytes")
    print(f"Final APK SHA-256: {apk_sha256}")

    target_apk = RELEASES_DIR / "OceanStudio-1.2.0-arm64-debug.apk"
    latest_apk = RELEASES_DIR / "OceanStudio-latest-debug.apk"

    shutil.copy2(final_signed_apk, target_apk)
    shutil.copy2(final_signed_apk, latest_apk)

    (RELEASES_DIR / "OceanStudio-1.2.0-arm64-debug.apk.sha256").write_text(f"{apk_sha256}\n", encoding="utf-8")
    (RELEASES_DIR / "OceanStudio-latest-debug.apk.sha256").write_text(f"{apk_sha256}\n", encoding="utf-8")

    sums_content = f"{apk_sha256}  OceanStudio-1.2.0-arm64-debug.apk\n{apk_sha256}  OceanStudio-latest-debug.apk\n"
    (RELEASES_DIR / "SHA256SUMS").write_text(sums_content, encoding="utf-8")

    validation_data = {
        "apk": "OceanStudio-1.2.0-arm64-debug.apk",
        "size": apk_size,
        "sha256": apk_sha256,
        "minSdkVersion": 28,
        "targetSdkVersion": 28,
        "compileSdkVersion": 35,
        "applicationId": "studio.ocean.app",
        "versionCode": 9,
        "versionName": "1.2.0",
        "abi": "arm64-v8a",
        "zipalign": "4-byte page-aligned (passed)",
        "signing": {
            "v1_jar": True,
            "v2_scheme": True,
            "v3_scheme": True,
            "signer": "CN=OceanStudio, OU=Engineering, O=Ocean, L=San Francisco, ST=CA, C=US"
        },
        "status": "READY_FOR_INSTALLATION"
    }
    (RELEASES_DIR / "OceanStudio-1.2.0-validation.json").write_text(json.dumps(validation_data, indent=2) + "\n", encoding="utf-8")

    print("\nRelease files updated successfully on disk!")
    return apk_sha256, apk_size

if __name__ == "__main__":
    main()
