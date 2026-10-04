#!/usr/bin/env python3
"""Consume the pinned command from the canonical package repository, without editing its snapshot."""
import hashlib
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
COMMIT = 'e33099c39380d9b78cad0181114ce7fb6ba6150e'
FILES = {'ocean-gateway': 'cd1bc91dd0086224ed23653c77561851b874760f32db7359849aa8b639220d8a', 'connect.mjs': '5cfb2a3248bbd5ed363244fcdd81c6c227ff0fd7da8404158c583e9f5dedf235', 'oauth.mjs': 'eb0593d8033e40d7964d7fd59ed1047ed51595953733716149f6423554fec9ba', 'prepare-runtime.mjs': 'b2f1cb59d52cabaa27cd4d515f4eeb56092fb2a358a4edf7565b3f70534ea95f', 'providers.mjs': '7accfa83b5f9b0f1b664c127a0f8f7fb1841c1c8f947d707da48b6936e6719fb', 'registrations.mjs': '55e654d35edcdee3fa212e85d605a486a057f416ed09f94385ccc27aae1dd9de', 'server.mjs': 'a42e76e878299e3116c88b830fe5adcd1089138f4aecbe69d2b96472e43f8a56', 'store.mjs': '5223bd718c50679585b3195fc3d917a149b3f53d7e34751d117ffe5137813b54', 'transport.mjs': 'c2ea4de2ece0273b131a1f7795d2fd195d27b857fdfc3cc363e477d22b4b7d84'}
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
