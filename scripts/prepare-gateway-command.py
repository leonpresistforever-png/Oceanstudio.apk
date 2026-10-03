#!/usr/bin/env python3
"""Consume the pinned command from the canonical package repository, without editing its snapshot."""
import hashlib
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
COMMIT = 'd19e68695f56542f4b625618aa579d9021c381f8'
FILES = {
    'ocean-gateway': 'da7c61876a8e5f2c0520edea7c7b60f6ddcc6d8bf11e2ef13541ddf07f54e51e',
    'prepare-runtime.mjs': 'fabde3226316a25d0fc47349fa7d2e4f352ec4199eb6ca1d23fbf11c5cd3210d',
}
for name, expected in FILES.items():
    url = f'https://raw.githubusercontent.com/leonpresistforever-png/Oceanstudio-packages/{COMMIT}/packages/ocean-gateway/{name}'
    target = ROOT / 'android/app/src/main/assets/ocean/gateway' / name
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != expected:
        with urllib.request.urlopen(url, timeout=60) as response:
            payload = response.read()
        if hashlib.sha256(payload).hexdigest() != expected:
            raise SystemExit('Canonical Ocean gateway integrity check failed: ' + name)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(payload)
print(f'Verified canonical Ocean gateway command: {COMMIT}')
