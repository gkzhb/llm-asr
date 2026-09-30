#!/usr/bin/env bash
# Run in Nix apk shell. Gradle/AGP build the package, not the legacy APK script.
set -euo pipefail
: "${ANDROID_HOME:?Use bash scripts/nix-env.sh apk bash scripts/verify-gradle.sh}"
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$root/.cache/gradle-user-home}"
printf '%s\n' 'cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8  gradle/wrapper/gradle-wrapper.jar' | sha256sum --check
mkdir -p "$root/.work/gradle-m1"
run=$(mktemp -d "$root/.work/gradle-m1/verify-XXXXXX")
# Explicit Nix-patched SDK tool: AGP still controls resource compilation/linking.
# --offline, --rerun-tasks and clean may be supplied; bootstrap metadata updates
# are deliberately not part of this accepted verification entry point.
for argument in "$@"; do
  case "$argument" in
    --write-verification-metadata*|--dependency-verification*|--write-locks|--update-locks*)
      echo 'FATAL: verification entry cannot rewrite/relax dependency evidence' >&2; exit 2;;
  esac
done
./gradlew verifyDebug --no-daemon --console=plain --dependency-verification strict \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/35.0.0/aapt2" "$@" \
  2>&1 | tee "$run/build.log"
if grep -Eiq 'malformed kotlin.Metadata|Unexpected rewriting|metadata.*maximum supported|WARNING: D8|WARNING: R8|Warning.*[DR]8' "$run/build.log"; then
  echo 'FATAL: DEX diagnostics require review' >&2
  exit 1
fi
printf 'GRADLE_VERIFY_LOG %s\n' "$run/build.log"
