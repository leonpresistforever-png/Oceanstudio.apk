#!/usr/bin/env python3
"""Verify the executable actually shipped in the APK, rather than only its build output."""
import argparse
import hashlib
import json
import pathlib
import struct
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=pathlib.Path)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
with zipfile.ZipFile(args.apk) as archive:
    for entry in ['lib/arm64-v8a/liboceanstudio_ext.so', 'lib/arm64-v8a/liboceanpty.so',
                  'lib/arm64-v8a/libllama-server.so', 'lib/arm64-v8a/libollama.so',
                  'lib/arm64-v8a/libollama-llama-server.so', 'assets/ocean/gateway/ocean-gateway',
                  'assets/ocean/gateway/prepare-runtime.mjs']:
        if entry not in archive.namelist(): raise SystemExit('APK is missing ' + entry)
        print('Packaged:', entry)
    entry = 'lib/arm64-v8a/libllama-server.so'
    binary = archive.read(entry)
    original = root / 'android/app/src/main/jniLibs/arm64-v8a/libllama-server.so'
    packaged_hash = hashlib.sha256(binary).hexdigest()
    original_hash = hashlib.sha256(original.read_bytes()).hexdigest()
    print('Packaged llama SHA-256:', packaged_hash)
    print('Built llama SHA-256:', original_hash)
    if packaged_hash != original_hash: raise SystemExit('APK changed the verified llama-server executable')
    receipt = json.loads(archive.read('assets/ocean/native/ollama-runtime.json'))
    if receipt['commit'] != 'cc4069396f3ad2c370c53eed2e4a42ac13adab84':
        raise SystemExit('Unexpected Ollama source revision')
    for executable in ['libollama.so', 'libollama-llama-server.so']:
        payload = archive.read('lib/arm64-v8a/' + executable)
        built = root / 'android/app/src/main/jniLibs/arm64-v8a' / executable
        digest = hashlib.sha256(payload).hexdigest()
        if digest != receipt['executables'][executable] or payload != built.read_bytes():
            raise SystemExit('APK changed the verified Ollama executable: ' + executable)
        print('Packaged Ollama SHA-256:', executable, digest)
    for name in archive.namelist():
        if not name.startswith('lib/arm64-v8a/') or not name.endswith('.so'): continue
        elf = archive.read(name)
        if elf[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', elf, 18)[0] != 183:
            raise SystemExit('Not Android arm64 ELF: ' + name)
        offset = struct.unpack_from('<Q', elf, 32)[0]
        size, count = struct.unpack_from('<HH', elf, 54)
        alignments = [struct.unpack_from('<Q', elf, offset + i * size + 48)[0] for i in range(count)
                      if struct.unpack_from('<I', elf, offset + i * size)[0] == 1]
        if not alignments or min(alignments) < 16384: raise SystemExit('ELF lacks 16 KB page support: ' + name)
        print('16 KB ELF verified:', name)
    for name in ['ocean-gateway', 'prepare-runtime.mjs']:
        command = root / 'android/app/src/main/assets/ocean/gateway' / name
        if archive.read('assets/ocean/gateway/' + name) != command.read_bytes():
            raise SystemExit('APK changed the verified canonical gateway file: ' + name)
print('PASS: packaged arm64 executables, page alignment, native checksum and gateway command')
