#!/usr/bin/env python3
"""Consume the pinned command from the canonical package repository, without editing its snapshot."""
import hashlib
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
COMMIT = 'fe2dd0de5e10a9fe5fac1f2c2862d7c0b108c4fe'
SHA256 = '939159d38f5cd98eb7452d8da08353a3148fb0652109ad3313427b4176cd03c3'
URL = f'https://raw.githubusercontent.com/leonpresistforever-png/Oceanstudio-packages/{COMMIT}/packages/ocean-gateway/ocean-gateway'
target = ROOT / 'android/app/src/main/assets/ocean/gateway/ocean-gateway'
if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != SHA256:
    with urllib.request.urlopen(URL, timeout=60) as response:
        payload = response.read()
    if hashlib.sha256(payload).hexdigest() != SHA256:
        raise SystemExit('Canonical Ocean gateway command integrity check failed')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(payload)
print(f'Verified canonical Ocean gateway command: {COMMIT}')
