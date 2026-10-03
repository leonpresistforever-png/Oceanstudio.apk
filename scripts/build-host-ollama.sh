#!/usr/bin/env bash
# Real integration server from the exact upstream commits used by the APK.
set -euo pipefail
work="${1:?Integration build directory required}"
mkdir -p "$work"
work="$(cd "$work" && pwd)"
for item in 'ollama|https://github.com/ollama/ollama.git|cc4069396f3ad2c370c53eed2e4a42ac13adab84' \
            'llama|https://github.com/ggml-org/llama.cpp.git|161755f29e415e2c33efe906e91843c068efd664'; do
    IFS='|' read -r name repository commit <<< "$item"
    git init -q "$work/$name"
    git -C "$work/$name" remote add origin "$repository"
    git -C "$work/$name" fetch --depth 1 origin "$commit"
    git -C "$work/$name" checkout --detach FETCH_HEAD
    test "$(git -C "$work/$name" rev-parse HEAD)" = "$commit"
done
(
    cd "$work/ollama"
    CGO_ENABLED=1 GOMAXPROCS=2 go build -p 2 -trimpath \
        -ldflags '-s -w -X github.com/ollama/ollama/version.Version=0.35.0' -o "$work/ollama-host" .
)
cmake -S "$work/ollama/llama/server" -B "$work/backend" \
    -DFETCHCONTENT_SOURCE_DIR_LLAMA_CPP="$work/llama" -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_BACKEND_DL=OFF
cmake --build "$work/backend" --target llama-server --parallel 2
