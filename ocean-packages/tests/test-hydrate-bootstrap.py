#!/usr/bin/env python3
import importlib.util
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
