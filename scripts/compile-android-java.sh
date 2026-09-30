#!/usr/bin/env bash
# Compile ALL Java/Kotlin Android sources only; no packaging/native/device writes.
set -euo pipefail
: "${ANDROID_HOME:?Use scripts/nix-env.sh apk}"
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
mkdir -p "$root/.work/build"
work=$(mktemp -d "$root/.work/build/android-javac-XXXXXX")
trap 'rm -rf "$work"' EXIT
mapfile -t sources < <(find android/app/src/org/llmasr/minimal -type f \( -name '*.java' -o -name '*.kt' \) | LC_ALL=C sort)
bash scripts/compile-jvm-sources.sh "$work/classes" "$ANDROID_HOME/platforms/android-35/android.jar" "${sources[@]}"
printf 'PASS all-source Android Java/Kotlin JVM 8 / SDK 35 (compile only)\n'
