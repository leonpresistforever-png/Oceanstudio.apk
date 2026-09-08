#!/usr/bin/env python3
import json, pathlib, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
subprocess.run(['python3',str(ROOT/'ocean-packages/scripts/catalog.py'),'validate'],check=True)
roots=subprocess.check_output(['python3',str(ROOT/'ocean-packages/scripts/catalog.py'),'roots','--through','foundation'],text=True).splitlines()
assert roots[:3]==['bash','apt','libcurl']
assert {'coreutils','findutils','wget','openssl','util-linux'} <= set(roots)
assert 'python' not in roots and 'nodejs' not in roots
print('Ocean package catalogue tests passed.')
