#!/usr/bin/env bash
# Compile only. No resource packaging, native linking, DEX, signing or reports/dist writes.
set -euo pipefail
: "${ANDROID_HOME:?Use scripts/nix-env.sh apk}"
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
work=$(mktemp -d "$root/.work/build/android-javac-XXXXXX")
cleanup() { rm -rf "$work"; }
trap cleanup EXIT
# Recursive discovery: each R6 subpackage contributes its own .java files.
mapfile -t sources < <(find android/app/src/org/llmasr/minimal -name '*.java' -type f | LC_ALL=C sort)
# Fail on any duplicate top-level class name across the migrated tree: a
# simple-name collision between two .java files is a real migration defect.
declare -A seen=()
for f in "${sources[@]}"; do
  cn=$(awk '
    /^[[:space:]]*\/\// { next }
    /^[[:space:]]*\/\*/,/\*[[:space:]]*\/$/ { next }
    match($0, /(^|[^A-Za-z0-9_])(class|interface|enum)[[:space:]]+[A-Za-z_][A-Za-z0-9_]*/) {
      s = substr($0, RSTART, RLENGTH)
      sub(/.*[[:space:]]+/, "", s)
      print s
      exit
    }' "$f")
  [ -n "$cn" ] || { echo "FATAL cannot determine class name in $f" >&2; exit 2; }
  if [ -n "${seen[$cn]+set}" ]; then
    echo "FATAL duplicate top-level class $cn in $f (already in ${seen[$cn]})" >&2
    exit 2
  fi
  seen[$cn]="$f"
done
printf 'javac sources (%d files):\n' "${#sources[@]}"
printf '  %s\n' "${sources[@]}"
timeout 60 javac --release 8 -encoding UTF-8 -classpath "$ANDROID_HOME/platforms/android-35/android.jar" \
  -d "$work/classes" "${sources[@]}"
printf 'PASS all-source Android javac --release 8 / SDK 35 (compile only)\n'
