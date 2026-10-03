#!/usr/bin/env bash
# Build a real PIE inference server from official llama.cpp, with Android-only dependencies.
set -euo pipefail
revision=4d9176092d00586775af140581bb0b558ddc4389
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ndk="${ANDROID_NDK_HOME:-${ANDROID_HOME:-}/ndk/27.0.12077973}"
test -f "$ndk/build/cmake/android.toolchain.cmake" || { echo 'Android NDK 27 is required' >&2; exit 1; }
destination="$project_root/android/app/src/main/jniLibs/arm64-v8a"
receipt="$project_root/android/app/src/main/assets/ocean/native/llama-runtime.json"
if [ -s "$destination/libllama-server.so" ] && [ -s "$receipt" ] \
    && grep -Fq "$revision" "$receipt"; then exit 0; fi
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
git init -q "$work/source"
git -C "$work/source" remote add origin https://github.com/ggml-org/llama.cpp.git
git -C "$work/source" fetch --depth 1 origin "$revision"
git -C "$work/source" checkout --detach FETCH_HEAD
test "$(git -C "$work/source" rev-parse HEAD)" = "$revision"
cmake -S "$work/source" -B "$work/build" \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DGGML_CPU_ARM_ARCH=armv8-a \
    -DGGML_OPENMP=OFF -DGGML_BACKEND_DL=OFF -DGGML_CPU_ALL_VARIANTS=OFF \
    -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=ON \
    -DLLAMA_BUILD_TOOLS=ON -DLLAMA_BUILD_UI=OFF -DLLAMA_OPENSSL=OFF \
    -DCMAKE_EXE_LINKER_FLAGS='-Wl,-z,max-page-size=16384'
cmake --build "$work/build" --target llama-server --parallel "${OCEAN_NATIVE_JOBS:-2}"
mkdir -p "$destination" "$(dirname "$receipt")"
cp "$work/build/bin/llama-server" "$destination/libllama-server.so"
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
"$toolchain/llvm-strip" "$destination/libllama-server.so"
"$toolchain/llvm-readelf" -h -l -d "$destination/libllama-server.so" > "$work/elf.txt"
grep -Fq AArch64 "$work/elf.txt"
grep -Fq /system/bin/linker64 "$work/elf.txt"
if grep -E 'NEEDED.*(libggml|libllama|libc\+\+_shared|libomp|libssl|libcrypto)' "$work/elf.txt"; then
    echo 'Unexpected unbundled inference dependency' >&2; exit 1
fi
cp "$work/source/LICENSE" "$(dirname "$receipt")/llama-LICENSE"
python3 - "$destination/libllama-server.so" "$receipt" "$revision" <<'PY'
import hashlib,json,pathlib,sys
binary,receipt,revision=sys.argv[1:]
pathlib.Path(receipt).write_text(json.dumps({'upstream':'https://github.com/ggml-org/llama.cpp',
 'commit':revision,'target':'aarch64-linux-android28','executable':'libllama-server.so',
 'sha256':hashlib.sha256(pathlib.Path(binary).read_bytes()).hexdigest(),
 'license':'MIT','runtimeDependencies':['libc.so','libm.so','libdl.so']},indent=2)+'\n')
PY
echo 'Packaged Android arm64 llama-server with static inference and C++ libraries'
