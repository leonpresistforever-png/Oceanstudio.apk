#!/usr/bin/env python3
"""Build OceanStudio from CURRENT Android source, validate required features, align and sign it.

This script intentionally refuses to:
- repackage an existing release APK as a substitute for compilation;
- generate a new signing key silently;
- publish an APK that omits required 1.2.1 UI/diagnostic features.
"""
from __future__ import annotations
import hashlib, json, os, shutil, subprocess, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android"
RELEASES = ROOT / "releases"
BUILD_APK = ANDROID / "app/build/outputs/apk/debug/app-debug.apk"
PREVIOUS_APK = RELEASES / "OceanStudio-1.2.0-arm64-debug.apk"
VERSION_NAME = "1.2.1"
VERSION_CODE = 10
OUTPUT_APK = RELEASES / f"OceanStudio-{VERSION_NAME}-arm64-debug.apk"
LATEST_APK = RELEASES / "OceanStudio-latest-debug.apk"

def run(args, *, cwd=None, env=None, capture=False):
    print("+", " ".join(map(str,args)), flush=True)
    return subprocess.run(
        list(map(str,args)), cwd=cwd, env=env, check=True, text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None)

def tool(name):
    path=shutil.which(name)
    if not path:
        raise SystemExit(f"Required tool is missing: {name}")
    return path

def cert_digest(apk: Path) -> str:
    out=run([tool("apksigner"),"verify","--print-certs",apk],capture=True).stdout
    for line in out.splitlines():
        lower = line.lower()
        target = "certificate sha-256 digest:"
        if target in lower:
            idx = lower.find(target)
            return line[idx + len(target):].strip().lower().replace(":", "")
    raise SystemExit(f"Could not read signing certificate from {apk}")

def validate_compiled_features(apk: Path):
    if not apk.is_file() or apk.stat().st_size < 1_000_000:
        raise SystemExit("Compiled APK missing or implausibly small")

    with zipfile.ZipFile(apk,"r") as z:
        dex_names=[n for n in z.namelist() if n.startswith("classes") and n.endswith(".dex")]
        if not dex_names:
            raise SystemExit("Compiled APK has no classes*.dex")
        dex=b"".join(z.read(n) for n in dex_names)

    required_classes=[
        b"PluginCenterActivity",
        b"AgentSettingsActivity",
        b"CrashSurvival",
        b"CrashDiagnosticsActivity",
        b"OceanForgeActivity",
    ]
    missing=[x.decode() for x in required_classes if x not in dex]
    if missing:
        raise SystemExit("Compiled APK is missing required classes: "+", ".join(missing))

    aapt_tool = tool("aapt2") if shutil.which("aapt2") else tool("aapt")
    resources = run([aapt_tool, "dump", "resources", apk], capture=True).stdout
    if not resources and aapt_tool != tool("aapt"):
        resources = run([tool("aapt"), "dump", "resources", apk], capture=True).stdout
    required_ids = ["agent_controls_drawer", "agent_controls_button", "nav_plugins", "nav_agent_settings", "nav_crash_diagnostics"]
    missing_ids = [x for x in required_ids if x not in resources]
    if missing_ids:
        raise SystemExit("Compiled APK is missing required UI resources: " + ", ".join(missing_ids))

    manifest = run([tool("aapt"), "dump", "xmltree", apk, "AndroidManifest.xml"], capture=True).stdout
    for activity in ["PluginCenterActivity", "AgentSettingsActivity", "CrashDiagnosticsActivity"]:
        if activity not in manifest:
            raise SystemExit(f"Compiled manifest is missing {activity}")

    badging = run([tool("aapt"), "dump", "badging", apk], capture=True).stdout
    expected = [f"versionCode='{VERSION_CODE}'", f"versionName='{VERSION_NAME}'", "package: name='studio.ocean.app'"]
    for item in expected:
        if item not in badging:
            raise SystemExit(f"Badging validation failed: {item}")

def main():
    for required in ("java", "aapt", "zipalign", "apksigner"):
        tool(required)
    gradlew = ANDROID / "gradlew"
    if not gradlew.is_file():
        raise SystemExit("android/gradlew is missing")
    gradlew.chmod(gradlew.stat().st_mode | 0o111)

    env = dict(os.environ)
    try:
        commit = run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture=True).stdout.strip()
    except Exception:
        commit = "local"
    env["OCEAN_BUILD_COMMIT"] = commit

    # Compile current Java/resources using pure on-device build_source_apk
    source_builder = ROOT / "scripts/build_source_apk.py"
    run([sys.executable, str(source_builder)], cwd=ROOT, env=env)

    validate_compiled_features(BUILD_APK)

    RELEASES.mkdir(parents=True,exist_ok=True)
    work=ROOT/"build/release-apk"
    if work.exists(): shutil.rmtree(work)
    work.mkdir(parents=True)
    aligned=work/"aligned.apk"
    signed=work/"signed.apk"

    run([tool("zipalign"),"-f","-p","4",BUILD_APK,aligned])
    run([tool("zipalign"),"-c","-v","4",aligned])

    keystore=os.environ.get("OCEAN_RELEASE_KEYSTORE","").strip()
    if not keystore:
        legacy=Path.home()/"oceanstudio-release.jks"
        if legacy.is_file():
            keystore=str(legacy)
            print(f"Using existing local OceanStudio keystore: {legacy}")
        else:
            raise SystemExit(
                "No release keystore supplied. Set OCEAN_RELEASE_KEYSTORE. "
                "A new key will NOT be generated automatically."
            )
    if not Path(keystore).is_file():
        raise SystemExit("OCEAN_RELEASE_KEYSTORE does not exist")

    store_pass=os.environ.get("OCEAN_RELEASE_STORE_PASS","").strip()
    if not store_pass and keystore == str(Path.home()/"oceanstudio-release.jks"):
        store_pass = "oceanstudio"
    key_alias=os.environ.get("OCEAN_RELEASE_KEY_ALIAS","oceanstudio")
    key_pass=os.environ.get("OCEAN_RELEASE_KEY_PASS",store_pass)
    env["OCEAN_RELEASE_STORE_PASS"]=store_pass
    env["OCEAN_RELEASE_KEY_PASS"]=key_pass

    if not store_pass:
        raise SystemExit("Set OCEAN_RELEASE_STORE_PASS securely in the environment")

    run([
        tool("apksigner"),"sign",
        "--ks",keystore,
        "--ks-pass","env:OCEAN_RELEASE_STORE_PASS",
        "--ks-key-alias",key_alias,
        "--key-pass","env:OCEAN_RELEASE_KEY_PASS",
        "--min-sdk-version","28",
        "--v1-signing-enabled","true",
        "--v2-signing-enabled","true",
        "--v3-signing-enabled","true",
        "--in",aligned,
        "--out",signed,
    ],env=env)

    verify=run([tool("apksigner"),"verify","-v","--print-certs",signed],capture=True).stdout
    print(verify)
    run([tool("zipalign"),"-c","-v","4",signed])
    validate_compiled_features(signed)

    new_cert=cert_digest(signed)
    previous_cert=cert_digest(PREVIOUS_APK) if PREVIOUS_APK.is_file() else ""
    if previous_cert and new_cert != previous_cert:
        raise SystemExit(
            "Signing certificate mismatch with the published 1.2.0 APK. "
            f"previous={previous_cert} new={new_cert}. Refusing update release."
        )

    data=signed.read_bytes()
    digest=hashlib.sha256(data).hexdigest()
    if OUTPUT_APK.exists(): OUTPUT_APK.unlink()
    if LATEST_APK.exists(): LATEST_APK.unlink()
    shutil.copy2(signed,OUTPUT_APK)
    shutil.copy2(signed,LATEST_APK)
    (OUTPUT_APK.with_suffix(OUTPUT_APK.suffix+".sha256")).write_text(digest+"\n")
    (RELEASES/"OceanStudio-latest-debug.apk.sha256").write_text(digest+"\n")

    validation={
        "apk":OUTPUT_APK.name,
        "sourceCommit":commit,
        "buildMode":"compiled-current-android-source",
        "versionCode":VERSION_CODE,
        "versionName":VERSION_NAME,
        "applicationId":"studio.ocean.app",
        "sha256":digest,
        "size":len(data),
        "certificateSha256":new_cert,
        "previous120CertificateSha256":previous_cert or None,
        "updateCertificateMatch": (not previous_cert) or new_cert==previous_cert,
        "requiredFeatures":{
            "pluginCenter":True,
            "agentSettings":True,
            "rightAgentControlsDrawer":True,
            "crashSurvival":True,
            "manualCrashDiagnostics":True,
        },
        "status":"BUILT_AND_VALIDATED_NOT_PUBLISHED",
    }
    (RELEASES/f"OceanStudio-{VERSION_NAME}-validation.json").write_text(json.dumps(validation,indent=2)+"\n")
    print(json.dumps(validation,indent=2))
    print(f"READY LOCAL ARTIFACT: {OUTPUT_APK}")
    return 0

if __name__=="__main__":
    raise SystemExit(main())
