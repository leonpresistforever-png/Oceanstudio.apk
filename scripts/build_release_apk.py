#!/usr/bin/env python3
"""Compile, test and sign a production APK with the existing release identity.

The caller refreshes the catalogue from an immutable, signed Ocean repository
snapshot. This entry point never merges DEX/native code from an older APK.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / 'android'


def run(args, **kwargs):
    print('+', ' '.join(map(str, args)), flush=True)
    return subprocess.run(list(map(str, args)), check=True, text=True, **kwargs)


def tool(name):
    found = shutil.which(name)
    if not found:
        sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT', '')
        candidate = Path(sdk) / 'build-tools/35.0.0' / name
        if candidate.is_file():
            return str(candidate)
        raise RuntimeError('Required Android build tool missing: ' + name)
    return found


def certificate(apk):
    result = run([tool('apksigner'), 'verify', '--print-certs', apk], capture_output=True)
    match = re.search(r'^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F:]+)$', result.stdout, re.M)
    if not match:
        raise RuntimeError('Cannot verify existing APK signing identity')
    return match.group(1).lower().replace(':', '')


def verify_compiled(apk, version, code):
    with zipfile.ZipFile(apk) as archive:
        dex = b''.join(archive.read(n) for n in archive.namelist()
                       if re.fullmatch(r'classes(?:[0-9]+)?\.dex', n))
        for name in (b'PluginCenterActivity', b'AgentSettingsActivity', b'CrashSurvival',
                     b'CrashDiagnosticsActivity', b'OceanForgeActivity', b'OceanPackageCatalog'):
            if name not in dex:
                raise RuntimeError('Compiled feature missing: ' + name.decode())
        for name in ('lib/arm64-v8a/liboceanpty.so', 'lib/arm64-v8a/libzstd-jni-1.5.6-9.so',
                     'assets/ocean/bootstrap/aarch64/ocean-aarch64.tar.zst'):
            if name not in archive.namelist():
                raise RuntimeError('Required native/bootstrap asset missing: ' + name)
            if archive.getinfo(name).compress_type != zipfile.ZIP_STORED:
                raise RuntimeError('Large/native runtime asset must be stored uncompressed: ' + name)
        bootstrap = json.loads(archive.read('assets/ocean/bootstrap/aarch64/ocean-aarch64.manifest.json'))
        payload = archive.read('assets/ocean/bootstrap/aarch64/ocean-aarch64.tar.zst')
        if hashlib.sha256(payload).hexdigest() != bootstrap['archiveSha256']:
            raise RuntimeError('Packaged bootstrap differs from its manifest')
    badging = run([tool('aapt'), 'dump', 'badging', apk], capture_output=True).stdout
    for value in ("package: name='studio.ocean.app'", f"versionCode='{code}'", f"versionName='{version}'"):
        if value not in badging:
            raise RuntimeError('APK manifest mismatch: ' + value)
    if 'application-debuggable' in badging:
        raise RuntimeError('Production APK is debuggable')
    resources = run([tool('aapt'), 'dump', 'resources', apk], capture_output=True).stdout
    for name in ('agent_controls_drawer', 'agent_controls_button', 'nav_plugins',
                 'nav_agent_settings', 'nav_crash_diagnostics'):
        if name not in resources:
            raise RuntimeError('Required UI resource missing: ' + name)
    run([sys.executable, ROOT / 'ocean-packages/tests/test-apk-package-catalog.py', apk, '--min-packages', '6482'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--previous-apk', required=True, type=Path,
                        help='Actual previously published APK used to verify update signing compatibility')
    parser.add_argument('--output', type=Path, default=ROOT / 'releases')
    args = parser.parse_args()
    env = dict(os.environ)
    keystore = env.get('OCEAN_RELEASE_KEYSTORE', '')
    if not keystore or not Path(keystore).is_file():
        raise RuntimeError('Supply the existing release keystore through OCEAN_RELEASE_KEYSTORE')
    for name in ('OCEAN_RELEASE_STORE_PASS', 'OCEAN_RELEASE_KEY_ALIAS', 'OCEAN_FIREBASE_API_KEY'):
        if not env.get(name):
            raise RuntimeError('Production build requires ' + name)
    env['OCEAN_RELEASE_KEY_PASS'] = env.get('OCEAN_RELEASE_KEY_PASS') or env['OCEAN_RELEASE_STORE_PASS']
    previous_cert = certificate(args.previous_apk)
    source = (ANDROID / 'app/build.gradle').read_text()
    version = re.search(r'versionName "([0-9][0-9.]+)"', source).group(1)
    code = int(re.search(r'versionCode ([0-9]+)', source).group(1))
    run([sys.executable, ROOT / 'ocean-packages/tests/test-apk-package-catalog.py',
         '--assets', ANDROID / 'app/src/main/assets/ocean/repository', '--min-packages', '6482'])
    run(['bash', ROOT / 'scripts/verify-native-only.sh'], cwd=ROOT)
    run([sys.executable, ROOT / 'ocean-packages/tests/test-pkg-auto-sync.py'])
    run([sys.executable, ROOT / 'ocean-packages/tests/test-hydrate-bootstrap.py'])
    commit = run(['git', 'rev-parse', 'HEAD'], cwd=ROOT, capture_output=True).stdout.strip()
    env['OCEAN_BUILD_COMMIT'] = commit
    env['OCEAN_FORGE_REUSE_NATIVE'] = 'false'
    (ANDROID / 'gradlew').chmod(0o755)
    run([ANDROID / 'gradlew', 'clean', 'testReleaseUnitTest', 'assembleRelease', '--no-daemon', '--stacktrace'],
        cwd=ANDROID, env=env)
    build_config = ANDROID / 'app/build/generated/source/buildConfig/release/studio/ocean/app/BuildConfig.java'
    if not re.search(r'OCEAN_DEV_AUTH_BYPASS\s*=\s*false', build_config.read_text()):
        raise RuntimeError('Production auth bypass must be disabled')
    unsigned = ANDROID / 'app/build/outputs/apk/release/app-release-unsigned.apk'
    work = ROOT / 'build/production-apk'
    work.mkdir(parents=True, exist_ok=True)
    aligned, signed = work / 'aligned.apk', work / 'signed.apk'
    run([tool('zipalign'), '-f', '-p', '4', unsigned, aligned])
    run([tool('apksigner'), 'sign', '--ks', keystore, '--ks-pass', 'env:OCEAN_RELEASE_STORE_PASS',
         '--ks-key-alias', env['OCEAN_RELEASE_KEY_ALIAS'], '--key-pass', 'env:OCEAN_RELEASE_KEY_PASS',
         '--min-sdk-version', '28', '--out', signed, aligned], env=env)
    current_cert = certificate(signed)
    if current_cert != previous_cert:
        raise RuntimeError(f'Release certificate changed: previous={previous_cert}; new={current_cert}')
    run([tool('zipalign'), '-c', '-p', '4', signed])
    verify_compiled(signed, version, code)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    final = output / f'OceanStudio-{version}-arm64-release.apk'
    if final.exists():
        raise RuntimeError('Refusing to overwrite an existing release artifact: ' + str(final))
    shutil.copyfile(signed, final)
    digest = hashlib.sha256(final.read_bytes()).hexdigest()
    final.with_suffix('.apk.sha256').write_text(f'{digest}  {final.name}\n')
    report = {'sourceCommit': commit, 'packageSnapshot': env.get('OCEAN_PACKAGE_COMMIT'),
              'versionName': version, 'versionCode': code, 'applicationId': 'studio.ocean.app',
              'apk': final.name, 'sha256': digest, 'size': final.stat().st_size,
              'certificateSha256': current_cert, 'previousCertificateSha256': previous_cert,
              'buildType': 'release', 'nativeCodeRecompiled': True, 'catalogueSignatureVerified': True,
              'physicalDeviceTested': False, 'allPackagesRuntimeTested': False,
              'status': 'built-and-statically-verified'}
    (output / f'OceanStudio-{version}-validation.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
