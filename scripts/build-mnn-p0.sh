#!/usr/bin/env bash
set -euo pipefail
mode=${1:?Usage: build-mnn-p0.sh host|android}
root=$(cd "$(dirname "$0")/.." && pwd)
source_dir="$root/.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36"
build_dir="$root/.work/build/mnn-$mode"
flags=(
  -G Ninja -DCMAKE_BUILD_TYPE=Release
  -DMNN_BUILD_LLM=ON -DMNN_BUILD_LLM_OMNI=ON -DMNN_BUILD_AUDIO=ON
  -DMNN_BUILD_OPENCV=OFF -DMNN_BUILD_TOOLS=OFF -DMNN_BUILD_TEST=OFF
  -DMNN_LOW_MEMORY=ON -DMNN_SUPPORT_TRANSFORMER_FUSE=ON
  -DMNN_OPENCL=OFF -DMNN_VULKAN=OFF -DMNN_BUILD_SHARED_LIBS=ON
  -DLLM_SUPPORT_HTTP_RESOURCE=OFF -DMNN_SEP_BUILD=OFF
)
case "$mode" in
  host)
    flags+=(-DMNN_BUILD_CONVERTER=ON)
    targets=(MNNConvert llm_demo)
    ;;
  android)
    : "${ANDROID_NDK_ROOT:?Use nix develop .#native}"
    flags+=("-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake"
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static
      -DMNN_BUILD_FOR_ANDROID_COMMAND=ON -DMNN_BUILD_CONVERTER=OFF)
    targets=(llm_demo)
    ;;
  *) echo 'Unsupported mode' >&2; exit 2 ;;
esac
cmake -S "$source_dir" -B "$build_dir" "${flags[@]}"
cmake --build "$build_dir" --parallel 2 --target "${targets[@]}"
