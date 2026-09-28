#!/usr/bin/env python3
import hashlib
import json
import os
import shutil
import struct
import subprocess
import zipfile
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa, padding
import datetime

ROOT = Path(__file__).resolve().parents[1]
BASE_APK = ROOT / "releases/OceanStudio-latest-debug.apk"
BUILD_DIR = ROOT / "build/release-staging"
UNALIGNED_APK = BUILD_DIR / "unaligned.apk"
ALIGNED_APK = BUILD_DIR / "aligned.apk"
FINAL_VERSION = "1.2.6"
FINAL_APK = ROOT / f"releases/OceanStudio-{FINAL_VERSION}-arm64-debug.apk"
LATEST_APK = ROOT / "releases/OceanStudio-latest-debug.apk"

STORE_EXTENSIONS = (".so", ".zst", ".gz", ".bin")

def lp(data: bytes) -> bytes:
    return struct.pack('<I', len(data)) + data

def compute_chunk_hashes(sections):
    chunk_hashes = []
    CHUNK_SIZE = 1048576  # 1MB
    for data in sections:
        offset = 0
        while offset < len(data):
            chunk = data[offset:offset + CHUNK_SIZE]
            h = hashlib.sha256(b'\x5a' + struct.pack('<I', len(chunk)) + chunk).digest()
            chunk_hashes.append(h)
            offset += len(chunk)
    total_chunks = len(chunk_hashes)
    top_hash = hashlib.sha256(b'\x5a' + struct.pack('<I', total_chunks) + b''.join(chunk_hashes)).digest()
    return top_hash

def sign_apk_v2(input_apk_path, output_apk_path, privkey, cert):
    with open(input_apk_path, 'rb') as f:
        apk_data = f.read()

    eocd_idx = apk_data.rfind(b'PK\x05\x06')
    if eocd_idx == -1:
        raise ValueError("Not a valid ZIP/APK")

    cd_size = struct.unpack('<I', apk_data[eocd_idx + 12:eocd_idx + 16])[0]
    cd_offset = struct.unpack('<I', apk_data[eocd_idx + 16:eocd_idx + 20])[0]

    section1 = apk_data[:cd_offset]
    section3 = apk_data[cd_offset:cd_offset + cd_size]
    
    eocd_orig = bytearray(apk_data[eocd_idx:])
    eocd_orig[16:20] = struct.pack('<I', len(section1))
    section4 = bytes(eocd_orig)

    top_hash = compute_chunk_hashes([section1, section3, section4])

    ALG_RSA_PKCS1_SHA256 = 0x0101
    digest_entry = struct.pack('<I', ALG_RSA_PKCS1_SHA256) + lp(top_hash)
    digests = lp(lp(digest_entry))

    cert_der = cert.public_bytes(serialization.Encoding.DER)
    certs = lp(lp(cert_der))
    attributes = lp(b'')

    signed_data = digests + certs + attributes

    sig_bytes = privkey.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    sig_entry = struct.pack('<I', ALG_RSA_PKCS1_SHA256) + lp(sig_bytes)
    signatures = lp(lp(sig_entry))

    pub_der = privkey.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo
    )
    public_key = lp(pub_der)

    signer = lp(signed_data) + signatures + public_key
    signers = lp(signer)

    v2_pair = struct.pack('<I', 0x7109871a) + signers
    v2_pair_with_size = struct.pack('<Q', len(v2_pair)) + v2_pair

    block_content_size = len(v2_pair_with_size) + 8 + 16
    signing_block = (
        struct.pack('<Q', block_content_size) +
        v2_pair_with_size +
        struct.pack('<Q', block_content_size) +
        b'APK Sig Block 42'
    )

    new_cd_offset = len(section1) + len(signing_block)
    new_eocd = bytearray(apk_data[eocd_idx:])
    new_eocd[16:20] = struct.pack('<I', new_cd_offset)

    with open(output_apk_path, 'wb') as f:
        f.write(section1)
        f.write(signing_block)
        f.write(section3)
        f.write(new_eocd)

def main():
    BUILD_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Building updated OceanStudio v{FINAL_VERSION} release APK...")

    # Load existing APK entries
    with zipfile.ZipFile(BASE_APK, 'r') as base_z, zipfile.ZipFile(UNALIGNED_APK, 'w') as out_z:
        existing = set()
        
        # 1. Add all updated assets
        assets_dir = ROOT / "android/app/src/main/assets"
        for p in assets_dir.rglob("*"):
            if p.is_file():
                rel = p.relative_to(assets_dir)
                arcname = f"assets/{rel.as_posix()}"
                compress = zipfile.ZIP_STORED if arcname.endswith(STORE_EXTENSIONS) else zipfile.ZIP_DEFLATED
                out_z.write(p, arcname, compress_type=compress)
                existing.add(arcname)
                print(f"  Added asset: {arcname}")

        # 2. Add base APK files that were not overridden (and strip old META-INF signatures)
        for item in base_z.infolist():
            if item.filename in existing:
                continue
            if item.filename.startswith("META-INF/OCEANSTU.") or item.filename == "META-INF/MANIFEST.MF":
                continue
            data = base_z.read(item.filename)
            compress = zipfile.ZIP_STORED if item.filename.endswith(STORE_EXTENSIONS) or item.filename.startswith("lib/") else zipfile.ZIP_DEFLATED
            out_z.writestr(item.filename, data, compress_type=compress)

    print(f"Unaligned APK created: {UNALIGNED_APK.stat().st_size} bytes")

    # 3. Zipalign
    env = os.environ.copy()
    env["LD_LIBRARY_PATH"] = f"{ROOT}/build/tmp-tools/data/data/studio.ocean.app/files/usr/lib:/data/data/com.termux/files/usr/lib:/system/lib64"
    zipalign_bin = ROOT / "build/tmp-tools/data/data/studio.ocean.app/files/usr/bin/zipalign"
    subprocess.run([str(zipalign_bin), "-f", "-p", "4", str(UNALIGNED_APK), str(ALIGNED_APK)], env=env, check=True)
    print(f"Aligned APK created: {ALIGNED_APK.stat().st_size} bytes")

    # 4. Sign with RSA-2048 key
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = issuer = x509.Name([
        x509.NameAttribute(x509.oid.NameOID.COMMON_NAME, "OceanStudio"),
        x509.NameAttribute(x509.oid.NameOID.ORGANIZATION_NAME, "OceanStudio"),
    ])
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(1)
        .not_valid_before(datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=1))
        .not_valid_after(datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=10000))
        .sign(key, hashes.SHA256())
    )

    sign_apk_v2(str(ALIGNED_APK), str(FINAL_APK), key, cert)
    print(f"Signed release APK generated: {FINAL_APK.stat().st_size} bytes")

    # Verify signature
    apksigner_bin = ROOT / "build/tmp-tools/data/data/studio.ocean.app/files/usr/bin/ocean-apksigner"
    verify_res = subprocess.run([str(apksigner_bin), "verify", "-v", str(FINAL_APK)], env=env, capture_output=True, text=True)
    print("apksigner verification result:")
    print(verify_res.stdout)
    if "Verified using APK Signature Scheme v2: true" not in verify_res.stdout:
        raise RuntimeError("Signature verification failed!")

    # Copy to latest
    shutil.copyfile(FINAL_APK, LATEST_APK)
    
    # Compute SHA-256
    digest = hashlib.sha256(FINAL_APK.read_bytes()).hexdigest()
    (FINAL_APK.with_suffix(".apk.sha256")).write_text(f"{digest}  {FINAL_APK.name}\n")
    (LATEST_APK.with_suffix(".apk.sha256")).write_text(f"{digest}  {LATEST_APK.name}\n")

    # Update SHA256SUMS
    sha_file = ROOT / "releases/SHA256SUMS"
    sha_lines = [
        f"{digest}  {LATEST_APK.name}\n",
        f"{digest}  {FINAL_APK.name}\n"
    ]
    sha_file.write_text("".join(sha_lines))

    print(f"All releases updated successfully! SHA256: {digest}")

if __name__ == "__main__":
    main()
