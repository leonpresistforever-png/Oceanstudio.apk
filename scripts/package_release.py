#!/usr/bin/env python3
"""Hermetic production APK packager and signer for OceanStudio.

Generates full dual-scheme Android signatures:
- V1: Standard JAR signing (META-INF/MANIFEST.MF, META-INF/OCEANSTU.SF, META-INF/OCEANSTU.RSA)
- V2: APK Signature Scheme v2 (0x7109871a block)
- V3: APK Signature Scheme v3 (0xf05368c0 block)
Patches AXML manifest versionCode to 16 and versionName to 1.2.6.
Ensures 4-byte zip alignment and uncompressed shared libraries.
"""
from __future__ import annotations

import base64
import datetime
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
from cryptography.hazmat.primitives.serialization import pkcs7

ROOT = Path(__file__).resolve().parents[1]
BASE_APK = ROOT / "releases/OceanStudio-1.2.1-arm64-debug.apk"
BUILD_DIR = ROOT / "build/release-staging"
UNALIGNED_APK = BUILD_DIR / "unaligned.apk"
ALIGNED_APK = BUILD_DIR / "aligned.apk"
FINAL_VERSION = "1.2.6"
FINAL_CODE = 16
FINAL_APK = ROOT / f"releases/OceanStudio-{FINAL_VERSION}-arm64-debug.apk"
LATEST_APK = ROOT / "releases/OceanStudio-latest-debug.apk"

KEY_FILE = ROOT / "releases/oceanstudio-signing-key.pem"
CERT_FILE = ROOT / "releases/oceanstudio-signing-cert.pem"

STORE_EXTENSIONS = (".so", ".zst", ".gz", ".bin")

def get_or_create_keypair() -> tuple[rsa.RSAPrivateKey, x509.Certificate]:
    if KEY_FILE.exists() and CERT_FILE.exists():
        key = serialization.load_pem_private_key(KEY_FILE.read_bytes(), password=None)
        cert = x509.load_pem_x509_certificate(CERT_FILE.read_bytes())
        return key, cert

    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = issuer = x509.Name([
        x509.NameAttribute(x509.oid.NameOID.COMMON_NAME, "OceanStudio"),
        x509.NameAttribute(x509.oid.NameOID.ORGANIZATION_NAME, "OceanStudio"),
        x509.NameAttribute(x509.oid.NameOID.ORGANIZATIONAL_UNIT_NAME, "Engineering"),
        x509.NameAttribute(x509.oid.NameOID.LOCALITY_NAME, "San Francisco"),
        x509.NameAttribute(x509.oid.NameOID.STATE_OR_PROVINCE_NAME, "CA"),
        x509.NameAttribute(x509.oid.NameOID.COUNTRY_NAME, "US"),
    ])
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(8516051234191387503)
        .not_valid_before(datetime.datetime(2024, 1, 1, tzinfo=datetime.timezone.utc))
        .not_valid_after(datetime.datetime(2054, 1, 1, tzinfo=datetime.timezone.utc))
        .sign(key, hashes.SHA256())
    )
    KEY_FILE.write_bytes(key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption()
    ))
    CERT_FILE.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    return key, cert

def b64(b: bytes) -> str:
    return base64.b64encode(b).decode('ascii')

def format_manifest_entry(name: str, digest_b64: str) -> bytes:
    lines = [f"Name: {name}", f"SHA-256-Digest: {digest_b64}"]
    formatted = []
    for line in lines:
        if len(line) <= 70:
            formatted.append(line)
        else:
            formatted.append(line[:70])
            rem = line[70:]
            while len(rem) > 69:
                formatted.append(" " + rem[:69])
                rem = rem[69:]
            if rem:
                formatted.append(" " + rem)
    return "\r\n".join(formatted).encode('utf-8') + b"\r\n\r\n"

def patch_manifest_axml(manifest_bytes: bytearray) -> bytearray:
    # 1. Update versionName to UTF-16 "1.2.6"
    pos = manifest_bytes.find(b"1\x00.\x002\x00.\x001\x00")
    if pos != -1:
        manifest_bytes[pos:pos+10] = b"1\x00.\x002\x00.\x006\x00"

    # 2. Update versionCode to 16 (0x00000010)
    pos_code = manifest_bytes.find(b"\x08\x00\x00\x10\x0a\x00\x00\x00")
    if pos_code != -1:
        manifest_bytes[pos_code+4:pos_code+8] = struct.pack("<I", FINAL_CODE)
    return manifest_bytes

def lp(data: bytes) -> bytes:
    return struct.pack('<I', len(data)) + data

def compute_chunk_hashes(sections: list[bytes]) -> bytes:
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

def sign_apk_v2_v3(input_apk: Path, output_apk: Path, privkey: rsa.RSAPrivateKey, cert: x509.Certificate):
    apk_data = input_apk.read_bytes()

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

    # V2 stripping protection attribute: 0xbeeff00d -> 3 (signals V3 present)
    attr_stripping = struct.pack('<I', 0xbeeff00d) + struct.pack('<I', 3)
    attrs_v2 = lp(lp(attr_stripping))

    # V2 signer
    signed_data_v2 = digests + certs + attrs_v2
    sig_bytes_v2 = privkey.sign(signed_data_v2, padding.PKCS1v15(), hashes.SHA256())
    sig_entry_v2 = struct.pack('<I', ALG_RSA_PKCS1_SHA256) + lp(sig_bytes_v2)
    signatures_v2 = lp(lp(sig_entry_v2))

    pub_der = privkey.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo
    )
    public_key = lp(pub_der)

    signer_v2 = lp(signed_data_v2) + signatures_v2 + public_key
    signers_v2 = lp(signer_v2)

    v2_pair = struct.pack('<I', 0x7109871a) + signers_v2
    v2_pair_with_size = struct.pack('<Q', len(v2_pair)) + v2_pair

    # V3 signer:
    # In V3, minSdk and maxSdk appear:
    # 1. Inside signed_data_v3: digests + certs + minSdk (uint32) + maxSdk (uint32) + attributes
    # 2. Inside signer_v3: lp(signed_data_v3) + minSdk (uint32) + maxSdk (uint32) + signatures + public_key
    min_sdk = 24
    max_sdk = 0x7fffffff
    sdk_bounds = struct.pack('<II', min_sdk, max_sdk)
    attrs_v3 = lp(b'')

    signed_data_v3 = digests + certs + sdk_bounds + attrs_v3
    sig_bytes_v3 = privkey.sign(signed_data_v3, padding.PKCS1v15(), hashes.SHA256())
    sig_entry_v3 = struct.pack('<I', ALG_RSA_PKCS1_SHA256) + lp(sig_bytes_v3)
    signatures_v3 = lp(lp(sig_entry_v3))

    signer_v3 = lp(signed_data_v3) + sdk_bounds + signatures_v3 + public_key
    signers_v3 = lp(signer_v3)

    v3_pair = struct.pack('<I', 0xf05368c0) + signers_v3
    v3_pair_with_size = struct.pack('<Q', len(v3_pair)) + v3_pair

    base_pairs = v2_pair_with_size + v3_pair_with_size

    # Android requires Central Directory offset (len(section1) + len(signing_block))
    # to be 4096-byte page aligned for mmap loading.
    # Total signing block size = 8 (header size) + len(pairs) + 8 (footer size) + 16 (magic)
    #                          = len(pairs) + 32
    # So cd_offset = len(section1) + len(pairs) + 32
    current_offset = len(section1) + 32 + len(base_pairs)
    rem = current_offset % 4096
    if rem != 0:
        needed_padding = 4096 - rem
        if needed_padding < 12:
            needed_padding += 4096
        padding_payload = b'\x00' * (needed_padding - 12)
        padding_pair = struct.pack('<I', 0x42726577) + padding_payload
        padding_pair_with_size = struct.pack('<Q', len(padding_pair)) + padding_pair
        pairs_data = base_pairs + padding_pair_with_size
    else:
        pairs_data = base_pairs

    block_content_size = len(pairs_data) + 8 + 16
    signing_block = (
        struct.pack('<Q', block_content_size) +
        pairs_data +
        struct.pack('<Q', block_content_size) +
        b'APK Sig Block 42'
    )

    new_cd_offset = len(section1) + len(signing_block)
    assert new_cd_offset % 4096 == 0, f"Central directory offset {new_cd_offset} must be 4096-aligned"

    new_eocd = bytearray(apk_data[eocd_idx:])
    new_eocd[16:20] = struct.pack('<I', new_cd_offset)

    with open(output_apk, 'wb') as f:
        f.write(section1)
        f.write(signing_block)
        f.write(section3)
        f.write(new_eocd)

def main():
    BUILD_DIR.mkdir(parents=True, exist_ok=True)
    print(f"=== PACKAGING OCEANSTUDIO v{FINAL_VERSION} (code {FINAL_CODE}) ===")
    key, cert = get_or_create_keypair()
    print("Signing Certificate:", cert.subject)
    cert_sha = hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest()
    print("Certificate SHA-256:", cert_sha)

    # Collect and update files
    files_map: dict[str, tuple[bytes, int]] = {}

    # 1. Base APK files
    with zipfile.ZipFile(BASE_APK, 'r') as base_z:
        for item in base_z.infolist():
            if item.filename.startswith("META-INF/"):
                continue
            data = base_z.read(item.filename)
            ctype = zipfile.ZIP_STORED if item.filename.endswith(STORE_EXTENSIONS) or item.filename.startswith("lib/") else zipfile.ZIP_DEFLATED
            files_map[item.filename] = (data, ctype)

    # 2. Patch AndroidManifest.xml
    manifest_bytes = patch_manifest_axml(bytearray(files_map["AndroidManifest.xml"][0]))
    files_map["AndroidManifest.xml"] = (bytes(manifest_bytes), zipfile.ZIP_DEFLATED)

    # 3. Source assets overlay
    assets_dir = ROOT / "android/app/src/main/assets"
    for p in assets_dir.rglob("*"):
        if p.is_file():
            rel = p.relative_to(assets_dir)
            arcname = f"assets/{rel.as_posix()}"
            ctype = zipfile.ZIP_STORED if arcname.endswith(STORE_EXTENSIONS) else zipfile.ZIP_DEFLATED
            files_map[arcname] = (p.read_bytes(), ctype)
            print(f"  Injected asset: {arcname}")

    # Build V1 JAR Manifest & Signature files
    # Order: AndroidManifest.xml, classes.dex, ...
    def sort_order(name: str) -> tuple[int, str]:
        if name == "AndroidManifest.xml": return (0, name)
        if name.startswith("classes"): return (1, name)
        if name.startswith("res/"): return (2, name)
        return (3, name)

    sorted_names = sorted(files_map.keys(), key=sort_order)

    manifest_header = (
        "Manifest-Version: 1.0\r\n"
        "Built-By: Generated-by-OceanStudio\r\n"
        "Created-By: OceanStudio\r\n"
        "\r\n"
    ).encode('utf-8')

    manifest_body = bytearray()
    sf_body = bytearray()

    for name in sorted_names:
        data, _ = files_map[name]
        h = hashlib.sha256(data).digest()
        entry_bytes = format_manifest_entry(name, b64(h))
        manifest_body.extend(entry_bytes)
        sf_entry_digest = hashlib.sha256(entry_bytes).digest()
        sf_body.extend(format_manifest_entry(name, b64(sf_entry_digest)))

    manifest_bytes = manifest_header + bytes(manifest_body)
    manifest_digest = hashlib.sha256(manifest_bytes).digest()

    sf_header = (
        "Signature-Version: 1.0\r\n"
        "Created-By: 1.0 (Android)\r\n"
        f"SHA-256-Digest-Manifest: {b64(manifest_digest)}\r\n"
        "X-Android-APK-Signed: 2, 3\r\n"
        "\r\n"
    ).encode('utf-8')
    sf_bytes = sf_header + bytes(sf_body)

    # Detached PKCS#7 signature for OCEANSTU.RSA
    builder = pkcs7.PKCS7SignatureBuilder().set_data(sf_bytes)
    builder = builder.add_signer(cert, key, hashes.SHA256())
    rsa_bytes = builder.sign(serialization.Encoding.DER, options=[pkcs7.PKCS7Options.DetachedSignature])

    # Write unaligned APK with META-INF placed after package resources
    print("Writing unaligned APK with complete V1 JAR signature...")
    with zipfile.ZipFile(UNALIGNED_APK, "w") as out_z:
        for name in sorted_names:
            data, ctype = files_map[name]
            out_z.writestr(name, data, compress_type=ctype)
        out_z.writestr("META-INF/MANIFEST.MF", manifest_bytes, compress_type=zipfile.ZIP_DEFLATED)
        out_z.writestr("META-INF/OCEANSTU.SF", sf_bytes, compress_type=zipfile.ZIP_DEFLATED)
        out_z.writestr("META-INF/OCEANSTU.RSA", rsa_bytes, compress_type=zipfile.ZIP_DEFLATED)

    print(f"Unaligned APK: {UNALIGNED_APK.stat().st_size} bytes")

    # Zipalign
    env = os.environ.copy()
    env["LD_LIBRARY_PATH"] = f"{ROOT}/build/tmp-tools/data/data/studio.ocean.app/files/usr/lib:/data/data/com.termux/files/usr/lib:/system/lib64"
    zipalign_bin = ROOT / "build/tmp-tools/data/data/studio.ocean.app/files/usr/bin/zipalign"
    subprocess.run([str(zipalign_bin), "-f", "-p", "4", str(UNALIGNED_APK), str(ALIGNED_APK)], env=env, check=True)
    print(f"Aligned APK: {ALIGNED_APK.stat().st_size} bytes")

    # Sign with V2 + V3
    sign_apk_v2_v3(ALIGNED_APK, FINAL_APK, key, cert)
    print(f"Dual-Scheme Signed APK: {FINAL_APK.stat().st_size} bytes")

    # Verify with ocean-apksigner
    apksigner_bin = ROOT / "build/tmp-tools/data/data/studio.ocean.app/files/usr/bin/ocean-apksigner"
    verify_res = subprocess.run([str(apksigner_bin), "verify", "-v", str(FINAL_APK)], env=env, capture_output=True, text=True)
    print("ocean-apksigner output:\n" + verify_res.stdout)
    if "Verifies" not in verify_res.stdout:
        raise RuntimeError("Signature verification failed!")

    # Verify badging
    aapt_bin = ROOT / "build/tmp-tools/data/data/studio.ocean.app/files/usr/bin/aapt"
    badging = subprocess.run([str(aapt_bin), "dump", "badging", str(FINAL_APK)], env=env, capture_output=True, text=True).stdout
    print("AAPT badging:\n" + "\n".join(badging.splitlines()[:5]))

    # Copy to latest
    shutil.copyfile(FINAL_APK, LATEST_APK)

    # Compute SHA-256
    digest = hashlib.sha256(FINAL_APK.read_bytes()).hexdigest()
    FINAL_APK.with_suffix(".apk.sha256").write_text(f"{digest}  {FINAL_APK.name}\n")
    LATEST_APK.with_suffix(".apk.sha256").write_text(f"{digest}  {LATEST_APK.name}\n")

    sha_file = ROOT / "releases/SHA256SUMS"
    sha_file.write_text(f"{digest}  {LATEST_APK.name}\n{digest}  {FINAL_APK.name}\n")

    print(f"SUCCESS! New release APK generated. SHA256: {digest}")

if __name__ == "__main__":
    main()
