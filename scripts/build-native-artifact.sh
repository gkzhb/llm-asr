#!/usr/bin/env bash
# Offline compilation only: run prepare-native-sources.py separately first.
set -euo pipefail
: "${ANDROID_NDK_ROOT:?Use Nix apk shell}"
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
sources=${1:?Usage: build-native-artifact.sh PREPARED_SOURCES BUILD_DIR}
build=${2:?Usage: build-native-artifact.sh PREPARED_SOURCES BUILD_DIR}
# Reject symlinks/traversal before realpath or creating/deleting output files.
python3 - "$sources" "$build" <<'PY'
import importlib.util,sys
from pathlib import Path
s=importlib.util.spec_from_file_location('sources','scripts/prepare-native-sources.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
m.validate(Path(sys.argv[1]));build=Path(sys.argv[2]);m.safe_path(build)
if build.exists() and any(p.is_symlink() for p in build.rglob('*')):
    raise SystemExit('Native build descendants must not be symlinks')
PY
sources=$(realpath "$sources")
mkdir -p "$build"
build=$(realpath "$build")
exec 9>"$build/.artifact-build.lock"
flock -x 9
python3 scripts/prepare-native-sources.py --check --output "$sources"
export JNI_MNN_SOURCE="$sources/mnn" JNI_CHECK_OUT="$build/jni-check"
bash scripts/compile-jni-object.sh
cmake -S "$sources/mnn" -B "$build" -G Ninja \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static \
  -DMNN_BUILD_LLM=ON -DMNN_BUILD_LLM_OMNI=ON -DMNN_BUILD_AUDIO=ON \
  -DMNN_BUILD_OPENCV=OFF -DMNN_BUILD_TOOLS=OFF -DMNN_BUILD_TEST=OFF \
  -DMNN_LOW_MEMORY=ON -DMNN_SUPPORT_TRANSFORMER_FUSE=ON \
  -DMNN_OPENCL=OFF -DMNN_VULKAN=OFF -DMNN_BUILD_SHARED_LIBS=ON \
  -DLLM_SUPPORT_HTTP_RESOURCE=OFF -DMNN_SEP_BUILD=OFF \
  -DMNN_BUILD_FOR_ANDROID_COMMAND=ON -DMNN_BUILD_CONVERTER=OFF \
  -DMNN_KLEIDIAI=ON -DKLEIDIAI_SRC_DIR="$sources/kleidiai" \
  -DCMAKE_PROJECT_MNN_INCLUDE="$root/native/cmake/mnn-artifact.cmake" \
  -DQWEN_ARTIFACT_FINALIZER="$root/native/cmake/finalize-mnn.cmake" \
  -DQWEN_LINK_SCRIPT="$root/scripts/link-native-artifact.py" \
  -DQWEN_JNI_SOURCE="$root/native/apk/asr_jni.cpp" \
  -DQWEN_JNI_HEADER="$build/jni-check/headers/org_llmasr_minimal_asr_JniNativeTranscription.h" \
  -DQWEN_COMPILER="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++"
cmake --build "$build" --parallel 2 --target qwen_asr_artifact
# Independent native byte/config/ELF/provenance gate; no expected hash rewrite.
python3 scripts/verify-native-artifact.py --build "$build" --sources "$sources"
