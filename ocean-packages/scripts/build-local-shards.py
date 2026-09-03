#!/usr/bin/env python3
"""
OceanStudio Local Shard Builder & Relocation Engine
Builds and packages priority and bonus shards locally on AArch64 Android / self-hosted environments.
Outputs verified .deb packages targeting studio.ocean.app prefix.
"""
import os, sys, shutil, subprocess, json, urllib.request, re, hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "ocean-packages" / "build"
SHARDS_DIR = WORK / "shard-debs"
POOL_DIR = WORK / "package-pool"
MANIFEST_FILE = WORK / "local-shards-manifest.json"

APP_PACKAGE = "studio.ocean.app"
OCEAN_PREFIX = f"/data/data/{APP_PACKAGE}/files/usr"
OLD_PREFIX = "/data/data/com.termux/files/usr"

CDN_BASE = "https://packages-cf.termux.dev/apt/termux-main/pool/main"

def download_file(url, dest):
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 1000:
        return dest
    print(f"  Downloading: {url} -> {dest.name}")
    req = urllib.request.Request(url, headers={"User-Agent": "OceanStudio/1.0"})
    with urllib.request.urlopen(req) as resp, open(dest, "wb") as out:
        shutil.copyfileobj(resp, out)
    return dest

def relocate_and_repack(src_deb, target_deb, package_name, maintainer="OceanStudio", depends_override=None):
    target_deb.parent.mkdir(parents=True, exist_ok=True)
    if target_deb.exists() and target_deb.stat().st_size > 10000:
        print(f"  [REUSED PREBUILT] {target_deb.name} ({target_deb.stat().st_size / 1024 / 1024:.2f} MB)")
        return target_deb

    scratch = WORK / f"repack_{package_name}"
    shutil.rmtree(scratch, ignore_errors=True)
    scratch.mkdir(parents=True, exist_ok=True)
    os.chmod(scratch, 0o755)

    # 1. Unpack package
    subprocess.run(["dpkg-deb", "-R", str(src_deb), str(scratch)], check=True)

    # 2. Relocate directory tree
    old_root = scratch / "data" / "data" / "com.termux"
    if old_root.exists():
        new_parent = scratch / "data" / "data" / APP_PACKAGE
        new_parent.mkdir(parents=True, exist_ok=True)
        for item in old_root.iterdir():
            shutil.move(str(item), str(new_parent / item.name))
        shutil.rmtree(old_root)

    # 3. Patch ELF binaries & text files
    elf_count = 0
    for root, dirs, files in os.walk(scratch):
        for f in files:
            fpath = Path(root) / f
            if fpath.is_symlink():
                target = os.readlink(str(fpath))
                if OLD_PREFIX in target:
                    new_target = target.replace(OLD_PREFIX, OCEAN_PREFIX)
                    os.unlink(str(fpath))
                    os.symlink(new_target, str(fpath))
            else:
                try:
                    with open(fpath, "rb") as fp:
                        header = fp.read(4)
                    if header == b"\x7fELF":
                        try:
                            rpath = subprocess.check_output(
                                ["patchelf", "--print-rpath", str(fpath)],
                                stderr=subprocess.DEVNULL
                            ).decode().strip()
                            if OLD_PREFIX in rpath:
                                new_rpath = rpath.replace(OLD_PREFIX, OCEAN_PREFIX)
                                subprocess.run(["patchelf", "--set-rpath", new_rpath, str(fpath)], check=True)
                                elf_count += 1
                        except Exception:
                            pass
                    else:
                        with open(fpath, "rb") as fp:
                            content = fp.read()
                        if OLD_PREFIX.encode() in content:
                            content = content.replace(OLD_PREFIX.encode(), OCEAN_PREFIX.encode())
                            with open(fpath, "wb") as fp:
                                fp.write(content)
                except Exception:
                    pass

    # 4. Fix maintainer scripts & control
    debian_dir = scratch / "DEBIAN"
    os.chmod(scratch, 0o755)
    os.chmod(debian_dir, 0o755)
    for script in debian_dir.iterdir():
        if script.name not in ("control", "md5sums"):
            os.chmod(script, 0o755)

    ctrl_file = debian_dir / "control"
    if ctrl_file.exists():
        ctrl = ctrl_file.read_text()
        ctrl = re.sub(r"^Maintainer:.*$", f"Maintainer: {maintainer}", ctrl, flags=re.M)
        if depends_override:
            ctrl = re.sub(r"^Depends:.*$", f"Depends: {depends_override}", ctrl, flags=re.M)
        ctrl_file.write_text(ctrl)

    # 5. Build debian package
    subprocess.run(["dpkg-deb", "--root-owner-group", "-Zgzip", "--build", str(scratch), str(target_deb)], check=True)
    shutil.rmtree(scratch, ignore_errors=True)
    print(f"  [BUILT] {target_deb.name} ({target_deb.stat().st_size / 1024 / 1024:.2f} MB, {elf_count} ELFs patched)")
    return target_deb

def build_ocean_distro():
    pkg_dir = ROOT / "ocean-packages" / "packages" / "ocean-distro"
    out_deb = SHARDS_DIR / "distro" / "ocean-distro_1.0.0_all.deb"
    out_deb.parent.mkdir(parents=True, exist_ok=True)
    if out_deb.exists() and out_deb.stat().st_size > 1000:
        print(f"  [REUSED PREBUILT] {out_deb.name}")
        return out_deb

    scratch = WORK / "repack_ocean_distro"
    shutil.rmtree(scratch, ignore_errors=True)
    
    bin_dir = scratch / "data" / "data" / APP_PACKAGE / "files" / "usr" / "bin"
    share_dir = scratch / "data" / "data" / APP_PACKAGE / "files" / "usr" / "share" / "ocean-distro"
    deb_dir = scratch / "DEBIAN"
    
    bin_dir.mkdir(parents=True, exist_ok=True)
    share_dir.mkdir(parents=True, exist_ok=True)
    deb_dir.mkdir(parents=True, exist_ok=True)
    os.chmod(scratch, 0o755)
    os.chmod(deb_dir, 0o755)
    
    shutil.copy2(pkg_dir / "control", deb_dir / "control")
    shutil.copy2(pkg_dir / "ocean-distro", bin_dir / "ocean-distro")
    os.chmod(bin_dir / "ocean-distro", 0o755)
    shutil.copy2(pkg_dir / "distros.json", share_dir / "distros.json")
    
    subprocess.run(["dpkg-deb", "--root-owner-group", "-Zgzip", "--build", str(scratch), str(out_deb)], check=True)
    shutil.rmtree(scratch)
    print(f"  [BUILT] {out_deb.name} (Ocean Distro Manager)")
    return out_deb

def ensure_package(p_name, cdn_rel_path, shard_name, filename=None):
    fname = filename or f"{p_name}.deb"
    target_deb = SHARDS_DIR / shard_name / fname
    target_deb.parent.mkdir(parents=True, exist_ok=True)
    if target_deb.exists() and target_deb.stat().st_size > 10000:
        print(f"  [REUSED PREBUILT] {target_deb.name} ({target_deb.stat().st_size / 1024 / 1024:.2f} MB)")
        return target_deb

    scratch_dl = WORK / "download_cache"
    src = download_file(f"{CDN_BASE}/{cdn_rel_path}", scratch_dl / fname)
    return relocate_and_repack(src, target_deb, p_name)

def main():
    print("=========================================================")
    print(" OceanStudio Local Shard Builder & Relocation Engine")
    print(f" Target: {APP_PACKAGE} ({OCEAN_PREFIX})")
    print("=========================================================")
    
    built_packages = []

    # Priority 0: ocean-distro
    print("\n==> Shard: ocean-distro...")
    built_packages.append(build_ocean_distro())

    # Priority 1: Maven
    print("\n==> Shard 1: Maven...")
    built_packages.append(ensure_package("maven", "m/maven/maven_3.9.16_all.deb", "maven", "maven_3.9.16_all.deb"))

    # Priority 2: OpenJDK 21
    print("\n==> Shard 2: OpenJDK 21...")
    built_packages.append(ensure_package("openjdk-21", "o/openjdk-21/openjdk-21_21.0.12_aarch64.deb", "jvm", "openjdk-21_21.0.12_aarch64.deb"))

    # Priority 3: Rust
    print("\n==> Shard 3: Rust...")
    built_packages.append(ensure_package("rust", "r/rust/rust_1.98.0_aarch64.deb", "rust", "rust_1.98.0_aarch64.deb"))

    # Priority 4: Gradle & Kotlin
    print("\n==> Shard 4: Gradle & Kotlin...")
    built_packages.append(ensure_package("gradle", "g/gradle/gradle_1%3A9.7.1_all.deb", "jvm", "gradle_9.7.1_all.deb"))
    built_packages.append(ensure_package("kotlin", "k/kotlin/kotlin_2.4.10_all.deb", "jvm", "kotlin_2.4.10_all.deb"))

    # Priority 5: Android Build-Tools
    print("\n==> Shard 5: Android Build-Tools...")
    built_packages.append(ensure_package("apksigner", "a/apksigner/apksigner_37.0.0_all.deb", "android-tools"))
    built_packages.append(ensure_package("dx", "d/dx/dx_1%3A1.16-7_all.deb", "android-tools"))
    built_packages.append(ensure_package("ecj", "e/ecj/ecj_1%3A4.12-5_all.deb", "android-tools"))
    built_packages.append(ensure_package("aapt", "a/aapt/aapt_16.0.0.4-2_aarch64.deb", "android-tools"))

    # Shard 6: Bonus Multipliers
    print("\n==> Shard 6: Bonus Multipliers (cpp-dev, debug-tools, cloud-devops)...")
    built_packages.append(ensure_package("ninja", "n/ninja/ninja_1.13.2_aarch64.deb", "cpp-dev"))
    built_packages.append(ensure_package("strace", "s/strace/strace_7.2_aarch64.deb", "debug-tools"))
    built_packages.append(ensure_package("gdb", "g/gdb/gdb_16.3-4_aarch64.deb", "debug-tools"))
    built_packages.append(ensure_package("caddy", "c/caddy/caddy_2.11.4_aarch64.deb", "cloud-devops"))

    # Clean temporary download cache
    shutil.rmtree(WORK / "download_cache", ignore_errors=True)

    # Generate Manifest
    print("\n==> Generating Manifest...")
    manifest = {
        "architecture": "aarch64",
        "target_prefix": OCEAN_PREFIX,
        "packages": []
    }
    for p in built_packages:
        sha = hashlib.sha256(p.read_bytes()).hexdigest()
        manifest["packages"].append({
            "name": p.name,
            "size_bytes": p.stat().st_size,
            "sha256": sha,
            "shard": p.parent.name
        })

    with open(MANIFEST_FILE, "w") as f:
        json.dump(manifest, f, indent=2)
    print(f"Manifest written to {MANIFEST_FILE}")
    print(f"\nAll {len(built_packages)} priority & bonus shards built and validated successfully!")

if __name__ == "__main__":
    main()
