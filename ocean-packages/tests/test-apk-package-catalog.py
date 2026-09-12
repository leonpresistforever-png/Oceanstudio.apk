#!/usr/bin/env python3
"""Verify the catalogue as Android will open it, after APK asset packaging."""
import gzip
import hashlib
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as apk:
    root = 'assets/ocean/repository/'
    metadata = dict(line.split('=', 1) for line in apk.read(root + 'catalog.properties').decode().splitlines() if line)
    for name in ('Packages.gz.bin', 'InRelease', 'ocean.gpg'):
        data = apk.read(root + name)  # Fails if aapt renamed or omitted the asset.
        assert hashlib.sha256(data).hexdigest() == metadata[name + '_sha256'], name
    packages = gzip.decompress(apk.read(root + 'Packages.gz.bin'))
    assert hashlib.sha256(packages).hexdigest() == metadata['packages_sha256']
    names = [line[9:] for line in packages.decode().splitlines() if line.startswith('Package: ')]
    assert len(names) == int(metadata['package_count'])
    assert {'pip', 'npm', 'python', 'nodejs', 'proot', 'proot-distro', 'ocean-distro'} <= set(names)
    print(f'Verified packaged APT catalogue: {len(set(names))} package names / {len(names)} versions, all asset hashes match')
