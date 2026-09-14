#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_NDK_ROOT:?Use scripts/nix-env.sh native}"
root=$(cd "$(dirname "$0")/.." && pwd)
source_dir="$root/.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36"
build_dir="$root/.work/build/mnn-android"
compiler="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++"
lib=$(find "$build_dir" -name libMNN.so -print -quit)
[ -n "$lib" ] || { echo 'Build MNN first' >&2; exit 1; }
mkdir -p "$root/.work/bin"
"$compiler" -std=c++17 -O2 "$root/native/p0/device_capabilities.cpp" -ldl -static-libstdc++ -o "$root/.work/bin/p0_capabilities"
"$compiler" -std=c++17 -O2 "$root/native/p0/asr_main.cpp" \
  -I"$source_dir/include" -I"$source_dir/transformers/llm/engine/include" \
  -L"$(dirname "$lib")" -lMNN -llog -ldl -static-libstdc++ \
  -o "$root/.work/bin/p0_asr"
cp "$lib" "$root/.work/bin/libMNN.so"
sha256sum "$root/.work/bin/"* > "$root/reports/p0/native-artifact-sha256.txt"
