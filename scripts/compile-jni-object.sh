#!/usr/bin/env bash
# Compile bridge only against the existing MNN headers. No link/rebuild/package/device.
set -euo pipefail
: "${ANDROID_NDK_ROOT:?Use scripts/nix-env.sh apk}"
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
out="${JNI_CHECK_OUT:-$root/.work/refactor-phase19-r3/jni}"
mkdir -p "$out"
# Start from a clean tree; old layout .class files break the symbol owner check.
rm -rf "$out/classes" "$out/headers" "$out/asr_jni.o"
mkdir -p "$out/classes" "$out/headers"
# Standalone subset: ASR sources plus their transitive Java dependencies.
# Full APK build separately compiles ALL Android sources and checks native owners
# against the actual linked DSO; this object-only check makes no such claim.
# Mixed-language dependencies must be compiled before javac generates the bridge header.
# Header generation still uses the real Java native declarations, not a hand-written stub.
mapfile -t jvm_files < <(find android/app/src/org/llmasr/minimal -type f \( -name '*.java' -o -name '*.kt' \) | LC_ALL=C sort)
bash scripts/compile-jvm-sources.sh "$out/classes" "$ANDROID_HOME/platforms/android-35/android.jar" "${jvm_files[@]}"
javac --release 8 -encoding UTF-8 -h "$out/headers" -d "$out/classes" \
  -classpath "$out/classes:$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  android/app/src/org/llmasr/minimal/asr/JniNativeTranscription.java
source="${JNI_MNN_SOURCE:-$root/.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36}"
compiler="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++"
# Generated header now lives under org_llmasr_minimal_asr_*.h.
header="$out/headers/org_llmasr_minimal_asr_JniNativeTranscription.h"
test -f "$header" || { echo "FATAL JNI header not generated in $out/headers" >&2; exit 2; }
# The generated header also makes C++ compilation check the Java-derived signatures.
"$compiler" -std=c++17 -O2 -fPIC -c native/apk/asr_jni.cpp \
  -I"$source/include" -I"$source/transformers/llm/engine/include" \
  -include "$header" -o "$out/asr_jni.o"
python3 scripts/check-jni-symbols.py "$out/classes" "$out/asr_jni.o"
printf 'PASS Android arm64 JNI object compile (not linked or executed)\n'
