#!/usr/bin/env bash
# Isolated verification metadata mutation: never alter the live project/cache.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$root/.cache/gradle-user-home}"
mkdir -p "$root/.work/gradle-m1"
fixture=$(mktemp -d "$root/.work/gradle-m1/dependency-negative-XXXXXX")
cp settings.gradle.kts build.gradle.kts gradle.properties buildscript-gradle.lockfile "$fixture/"
mkdir -p "$fixture/android/app" "$fixture/android/asr-runtime" "$fixture/core" "$fixture/gradle"
cp android/app/build.gradle.kts android/app/gradle.lockfile "$fixture/android/app/"
cp android/asr-runtime/build.gradle.kts android/asr-runtime/gradle.lockfile "$fixture/android/asr-runtime/"
cp core/build.gradle.kts core/gradle.lockfile "$fixture/core/"
cp gradle/verification-metadata.xml gradle/verify-lint-tools.gradle gradle/lint-tool-identity.json "$fixture/gradle/"
run_help() {
  local name=$1
  set +e
  "$root/gradlew" -p "$fixture" help --offline --no-daemon --console=plain --dependency-verification strict > "$fixture/$name.log" 2>&1
  help_code=$?
  set -e
  printf '%s\n' "$help_code" > "$fixture/$name.exit"
}
run_help original
[ "$help_code" -eq 0 ] || { echo "FATAL: checksum original positive failed log=$fixture/original.log" >&2; exit "$help_code"; }
expected=$(python3 tests/dependency_checksum_fixture.py mutate "$fixture/gradle/verification-metadata.xml")
run_help rejection
code=$help_code
[ "$code" -ne 0 ] || { echo 'FATAL: checksum mutant was accepted' >&2; exit 1; }
python3 tests/dependency_checksum_fixture.py verify "$fixture" --expected "$expected"
cp gradle/verification-metadata.xml "$fixture/gradle/verification-metadata.xml"
run_help restored
[ "$help_code" -eq 0 ] || { echo "FATAL: checksum restored positive failed log=$fixture/restored.log" >&2; exit "$help_code"; }
printf 'PASS strict checksum original=0 mutant=%s restored=0 log=%s\n' "$code" "$fixture/rejection.log"
