#!/usr/bin/env bash
# Build official Ollama and its official llama.cpp CPU backend for Android.
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
ndk="${ANDROID_NDK_HOME:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/ndk/27.0.12077973}"
test -d "$ndk" || { echo 'Android NDK 27 is required'; exit 1; }
revision=cc4069396f3ad2c370c53eed2e4a42ac13adab84
llama_revision=161755f29e415e2c33efe906e91843c068efd664
destination="$project_root/android/app/src/main/jniLibs/arm64-v8a"
receipt="$project_root/android/app/src/main/assets/ocean/native/ollama-runtime.json"
if [ -s "$destination/libollama.so" ] && [ -s "$destination/libollama-llama-server.so" ] \
    && [ -s "$receipt" ] && grep -Fq "$revision" "$receipt"; then exit 0; fi
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
for item in "ollama|https://github.com/ollama/ollama.git|$revision" \
            "llama|https://github.com/ggml-org/llama.cpp.git|$llama_revision"; do
    IFS='|' read -r name repository commit <<< "$item"
    git init -q "$work/$name"
    git -C "$work/$name" remote add origin "$repository"
    git -C "$work/$name" fetch --depth 1 origin "$commit"
    git -C "$work/$name" checkout --detach FETCH_HEAD
    test "$(git -C "$work/$name" rev-parse HEAD)" = "$commit"
done
# Android extracts only lib*.so filenames into its executable native directory.
# Keep upstream CPU behavior; adapt that helper filename and NDK C++ linking.
python3 - "$work/ollama" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
path=root/'llm/llama_binary.go'
source=path.read_text()
needle='func llamaCppBinaryName(name, goos string) string {'
assert source.count(needle)==1
source=source.replace(needle,needle+'\n\tif goos == "android" && name == "llama-server" { return "libollama-llama-server.so" }')
path.write_text(source)
path=root/'mlx/mlx.go';source=path.read_text()
assert source.count('// #cgo LDFLAGS: -lstdc++')==1
path.write_text(source.replace('// #cgo LDFLAGS: -lstdc++','// #cgo !android LDFLAGS: -lstdc++'))
PY
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p "$destination" "$(dirname "$receipt")"
(
    cd "$work/ollama"
    GOOS=android GOARCH=arm64 CGO_ENABLED=1 GOMAXPROCS=2 \
    CC="$toolchain/aarch64-linux-android28-clang" CXX="$toolchain/aarch64-linux-android28-clang++" \
    CGO_LDFLAGS='-static-libstdc++ -lc++_static -lc++abi -Wl,-z,max-page-size=16384' \
    go build -p 2 -trimpath -tags 'netgo osusergo' -buildmode=pie \
        -ldflags '-s -w -X github.com/ollama/ollama/version.Version=0.35.0 -extldflags=-Wl,-z,max-page-size=16384' \
        -o "$destination/libollama.so" .
)
cmake -S "$work/ollama/llama/server" -B "$work/build" \
    -DFETCHCONTENT_SOURCE_DIR_LLAMA_CPP="$work/llama" \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DGGML_CPU_ARM_ARCH=armv8-a \
    -DGGML_OPENMP=OFF -DGGML_BACKEND_DL=OFF -DGGML_CPU_ALL_VARIANTS=OFF \
    -DCMAKE_EXE_LINKER_FLAGS='-Wl,-z,max-page-size=16384'
cmake --build "$work/build" --target llama-server --parallel "${OCEAN_NATIVE_JOBS:-2}"
backend="$(find "$work/build" -type f -name llama-server | head -1)"
test -n "$backend"
cp "$backend" "$destination/libollama-llama-server.so"
"$toolchain/llvm-strip" "$destination/libollama-llama-server.so"
for executable in libollama.so libollama-llama-server.so; do
    "$toolchain/llvm-readelf" -h -l -d "$destination/$executable" > "$work/elf.txt"
    grep -Fq AArch64 "$work/elf.txt"
    grep -Fq /system/bin/linker64 "$work/elf.txt"
    if grep -E 'NEEDED.*(libggml|libllama|libc\+\+_shared|libomp|libssl|libcrypto)' "$work/elf.txt"; then
        echo 'Unexpected unbundled Ollama dependency'; exit 1
    fi
done
cp "$work/ollama/LICENSE" "$(dirname "$receipt")/ollama-LICENSE"
python3 - "$destination" "$receipt" "$revision" "$llama_revision" <<'PY'
import hashlib,json,pathlib,sys
directory,receipt,revision,llama=sys.argv[1:]
pathlib.Path(receipt).write_text(json.dumps({'upstream':'https://github.com/ollama/ollama',
 'commit':revision,'version':'0.35.0','llamaCommit':llama,'target':'aarch64-linux-android28',
 'executables':{name:hashlib.sha256((pathlib.Path(directory)/name).read_bytes()).hexdigest()
                for name in ['libollama.so','libollama-llama-server.so']},
 'androidAdaptations':['extracted helper filename','static NDK C++ linkage'],'license':'MIT'},indent=2)+'\n')
PY
echo 'Packaged real Android Ollama server and CPU inference backend'
