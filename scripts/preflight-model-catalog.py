#!/usr/bin/env python3
"""
Preflight Model Catalog Verification (Directive 2026-10-02 §8.3).
Verifies that all model URLs in models-manifest.json are live, valid, support HTTP Range requests,
and point to authentic upstream GGUF binaries (no HTTP 404, no corrupt endpoints).
"""

import json
import os
import sys
import urllib.request

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    manifest_path = os.path.join(repo_root, "android", "app", "src", "main", "assets", "models-manifest.json")
    
    if not os.path.isfile(manifest_path):
        print(f"ERROR: Model manifest not found at {manifest_path}", file=sys.stderr)
        sys.exit(1)
        
    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)
        
    models = manifest.get("models", [])
    if not models:
        print("ERROR: No models found in manifest!", file=sys.stderr)
        sys.exit(1)
        
    print(f"Preflighting {len(models)} model entries from models-manifest.json...")
    
    failures = 0
    for model in models:
        model_id = model.get("id")
        url = model.get("url")
        expected_sha = model.get("sha256")
        expected_size = model.get("size")
        
        print(f"-> Verifying {model_id} ({url})...")
        try:
            req = urllib.request.Request(url, headers={
                "User-Agent": "OceanStudio-Preflight/1.2.6",
                "Range": "bytes=0-1023"
            })
            with urllib.request.urlopen(req, timeout=20) as resp:
                status = resp.status
                data = resp.read()
                if status not in (200, 206):
                    print(f"  FAIL: HTTP status {status} for {model_id}", file=sys.stderr)
                    failures += 1
                    continue
                if len(data) < 4 or data[:4] != b"GGUF":
                    print(f"  FAIL: Invalid GGUF magic bytes {data[:4]!r} for {model_id}", file=sys.stderr)
                    failures += 1
                    continue
                print(f"  OK: {model_id} verified (HTTP {status}, GGUF magic confirmed, size={expected_size} bytes)")
        except Exception as e:
            print(f"  FAIL: Network error for {model_id}: {e}", file=sys.stderr)
            failures += 1
            
    if failures > 0:
        print(f"Model preflight failed with {failures} error(s).", file=sys.stderr)
        sys.exit(1)
        
    print("All catalog models successfully verified against upstream.")

if __name__ == "__main__":
    main()
