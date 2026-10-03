#!/usr/bin/env python3
"""Consume the pinned command from the canonical package repository, without editing its snapshot."""
import hashlib
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
COMMIT = '9780660ffb0c7ed09d86c52c8cee4f90d1ee18a3'
SHA256 = 'b0ed2b76e3985278976a248011e96b0db2879be3ca066347727b811fbc8aec54'
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
