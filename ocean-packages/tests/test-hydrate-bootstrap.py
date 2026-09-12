#!/usr/bin/env python3
import importlib.util
import gzip
import hashlib
from pathlib import Path

path = Path(__file__).parents[1] / "scripts/hydrate-signed-bootstrap.py"
spec = importlib.util.spec_from_file_location("hydrate", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

entries = [
    {"Package": "shell", "Depends": "libc (>= 1), virtual-tls | tls"},
    {"Package": "libc"},
    {"Package": "tls-provider", "Provides": "virtual-tls"},
]
resolved = {x["Package"] for x in module.resolve(entries, ("shell",))}
assert resolved == {"shell", "libc", "tls-provider"}, resolved

try:
    module.resolve([{"Package": "broken", "Depends": "missing"}], ("broken",))
except RuntimeError as error:
    assert "unresolved dependency" in str(error)
else:
    raise AssertionError("unresolved dependency was accepted")

print("Signed bootstrap dependency resolver tests passed.")

raw = b"Package: real-package\nVersion: 1.0\n"
compressed = gzip.compress(raw)
release = f"SHA256:\n {hashlib.sha256(compressed).hexdigest()} {len(compressed)} main/binary-aarch64/Packages.gz\n"
assert module.verify_catalog_index(release, compressed) == raw
for tampered in (compressed + b"tampered", gzip.compress(b"Package: injected\n")):
    try:
        module.verify_catalog_index(release, tampered)
    except RuntimeError:
        pass
    else:
        raise AssertionError("unauthenticated index was accepted")
print("Signed package index integrity tests passed.")
