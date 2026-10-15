#!/usr/bin/env bash
# Local/CI-ready command contract. No remote provider or uploads are configured.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
mode=${1:-full}
if [ "$#" -gt 0 ]; then shift; fi
case "$mode" in
  fast) task=verifyFast;;
  full) task=verifyDebug;;
  lint) task=verifyLint;;
  format) task=verifyFormat;;
  graph-fast) task=verifyFast;;
  android-test-build) task=verifyAndroidTestBuild;;
  *) echo "FATAL: unknown verification layer: $mode" >&2; exit 2;;
esac
# Allowlist: Gradle flags can override strict, skip tasks or rewrite evidence.
# Never forward arbitrary tasks, properties, init scripts or abbreviated flags.
options=()
clean=()
for argument in "$@"; do
  case "$argument" in
    --offline|--rerun-tasks) options+=("$argument");;
    clean) clean=(clean);;
    *) echo "FATAL: unsupported verification argument: $argument" >&2; exit 2;;
  esac
done
: "${ANDROID_HOME:?Use scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$root/.cache/gradle-user-home}"
printf '%s\n' 'cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8  gradle/wrapper/gradle-wrapper.jar' | sha256sum --check
mkdir -p "$root/.work/verify-local"
run=$(mktemp -d "$root/.work/verify-local/$mode-XXXXXX")
if [ "$mode" = graph-fast ]; then options+=(--dry-run); fi
printf 'VERIFY_LAYER %s task=%s\n' "$mode" "$task"
set +e
./gradlew "${clean[@]}" "$task" --no-daemon --console=plain --dependency-verification strict \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/35.0.0/aapt2" "${options[@]}" \
  2>&1 | tee "$run/build.log"
pipeline_status=("${PIPESTATUS[@]}")
set -e
if [ "${#pipeline_status[@]}" -ne 2 ]; then
  echo 'FATAL: unexpected verification pipeline status' >&2
  exit 1
fi
code=${pipeline_status[0]}
if [ "${pipeline_status[1]}" -ne 0 ]; then
  printf 'FATAL: log capture failed Gradle=%s tee=%s\n' "${pipeline_status[0]}" "${pipeline_status[1]}" >&2
  if [ "$code" -eq 0 ]; then code=${pipeline_status[1]}; fi
fi
# No -q: read the complete log and distinguish no match (1) from IO/tool error.
set +e
grep -Ei 'malformed kotlin.Metadata|Unexpected rewriting|metadata.*maximum supported|WARNING: D8|WARNING: R8|Warning.*[DR]8' "$run/build.log" > /dev/null
scan_code=$?
set -e
case "$scan_code" in
  0) echo 'FATAL: DEX diagnostics require review' >&2
     if [ "$code" -eq 0 ]; then code=1; fi;;
  1) ;;
  *) printf 'FATAL: DEX log scan failed exit=%s\n' "$scan_code" >&2
     if [ "$code" -eq 0 ]; then code=$scan_code; fi;;
esac
if [ "$code" -eq 0 ] && [ "$mode" = graph-fast ]; then
  set +e
  python3 "$root/scripts/check-fast-task-graph.py" "$run/build.log"
  graph_code=$?
  set -e
  if [ "$graph_code" -ne 0 ]; then code=$graph_code; fi
fi
printf 'PIPELINE_STATUS Gradle=%s tee=%s scan=%s\n' "${pipeline_status[0]}" "${pipeline_status[1]}" "$scan_code"
printf '%s\n' "$code" > "$run/exit-code.txt"
printf 'GRADLE_VERIFY_LOG %s exit=%s\n' "$run/build.log" "$code"
if [ "$mode" = graph-fast ]; then echo 'TASK GRAPH ONLY: no verification tasks executed'; fi
exit "$code"
