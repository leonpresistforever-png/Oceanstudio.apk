#!/usr/bin/env bash
# Build the LGPL FFmpeg command-line tools from FFmpeg's official source for Android arm64.
set -euo pipefail
source_url=https://ffmpeg.org/releases/ffmpeg-8.1.2.tar.xz
source_sha256=464beb5e7bf0c311e68b45ae2f04e9cc2af88851abb4082231742a74d97b524c
ndk="${ANDROID_NDK_HOME:-${ANDROID_HOME:-}/ndk/27.0.12077973}"
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
test -x "$toolchain/aarch64-linux-android28-clang" || {
    echo "Android NDK 27 arm64 compiler is required: $toolchain" >&2; exit 1;
}
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
destination="$project_root/android/app/src/main/assets/ocean/native"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
curl --fail --location --retry 3 --output "$work/source.tar.xz" "$source_url"
printf '%s  %s\n' "$source_sha256" "$work/source.tar.xz" | sha256sum --check --status
mkdir -p "$work/source" "$work/build" "$destination"
tar --no-same-owner -xf "$work/source.tar.xz" -C "$work/source" --strip-components=1
cd "$work/build"
"$work/source/configure" \
    --target-os=android --arch=aarch64 --enable-cross-compile \
    --cc="$toolchain/aarch64-linux-android28-clang" \
    --cxx="$toolchain/aarch64-linux-android28-clang++" \
    --ar="$toolchain/llvm-ar" --nm="$toolchain/llvm-nm" \
    --ranlib="$toolchain/llvm-ranlib" --strip="$toolchain/llvm-strip" \
    --disable-autodetect --disable-doc --disable-debug \
    --enable-shared --disable-static --disable-ffplay --enable-small \
    --extra-ldflags='-Wl,-z,max-page-size=16384' \
    --prefix="$work/install" \
    --pkg-config=false
make -j2 ffmpeg ffprobe
make install
echo "Inspecting Android FFmpeg installation"
ls -l "$work/install/bin/ffmpeg" "$work/install/bin/ffprobe"
for executable in ffmpeg ffprobe; do
    echo "Validating $executable"
    cp "$work/install/bin/$executable" "$destination/$executable"
    "$toolchain/llvm-strip" "$destination/$executable"
    "$toolchain/llvm-readelf" -l "$destination/$executable" | tee "$work/$executable.program-headers"
    grep -F '/system/bin/linker64' "$work/$executable.program-headers" >/dev/null
    "$toolchain/llvm-readelf" -h "$destination/$executable" | tee "$work/$executable.elf-header"
    grep -F 'AArch64' "$work/$executable.elf-header" >/dev/null
    sha256sum "$destination/$executable" | sed "s|$destination/||" > "$destination/$executable.sha256"
done
mkdir -p "$destination/lib"
echo "Inspecting FFmpeg shared libraries"
ls -l "$work/install/lib/"*.so*
for soname in "$work"/install/lib/*.so.[0-9]*; do
    name="$(basename "$soname")"
    [[ "$name" =~ \.so\.[0-9]+$ ]] || continue
    cp -L "$soname" "$destination/lib/$name"
    "$toolchain/llvm-strip" "$destination/lib/$name"
    sha256sum "$destination/lib/$name" | sed "s|$destination/||" >> "$destination/libraries.sha256"
done
test -s "$destination/libraries.sha256"
cp "$work/source/COPYING.LGPLv2.1" "$destination/COPYING.LGPLv2.1"
