#!/usr/bin/env bash
# Compile an explicit Java/Kotlin source set. Java files are visible to kotlinc
# for cyclic type resolution; javac then emits Java classes against Kotlin output.
# No downloads, packaging, native linking or global output cleanup.
set -euo pipefail
: "${KOTLIN_HOME:?Use scripts/nix-env.sh apk}"
out=${1:?Usage: compile-jvm-sources.sh OUT CLASSPATH SOURCE...}
classpath=${2?Pass a classpath (empty is allowed)}
shift 2
[ "$#" -gt 0 ] || { echo 'FATAL no JVM sources' >&2; exit 2; }
java_sources=(); kotlin_sources=()
declare -A seen=()
for source in "$@"; do
  [ -f "$source" ] || { echo "FATAL source missing: $source" >&2; exit 2; }
  name=$(basename "$source"); name=${name%.*}
  [ -z "${seen[$name]+set}" ] || { echo "FATAL duplicate source name: $name" >&2; exit 2; }
  seen[$name]=$source
  case "$source" in
    *.java) java_sources+=("$source");;
    *.kt) kotlin_sources+=("$source");;
    *) echo "FATAL unsupported source: $source" >&2; exit 2;;
  esac
done
stdlib="$KOTLIN_HOME/lib/kotlin-stdlib.jar"
annotations="$KOTLIN_HOME/lib/annotations-13.0.jar"
test -f "$stdlib"; test -f "$annotations"
mkdir -p "$out"
cp="$out:$stdlib:$annotations"
[ -z "$classpath" ] || cp="$cp:$classpath"
if [ "${#kotlin_sources[@]}" -gt 0 ]; then
  timeout --foreground --kill-after=5s 120 kotlinc -jvm-target 1.8 -Xjvm-default=all -no-stdlib -no-reflect -classpath "$cp" \
    -d "$out" "${kotlin_sources[@]}" "${java_sources[@]}"
fi
if [ "${#java_sources[@]}" -gt 0 ]; then
  timeout --foreground --kill-after=5s 120 javac --release 8 -encoding UTF-8 -classpath "$cp" -d "$out" "${java_sources[@]}"
fi
printf 'PASS mixed JVM compile: %d Kotlin + %d Java sources\n' "${#kotlin_sources[@]}" "${#java_sources[@]}"
