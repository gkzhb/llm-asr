#!/usr/bin/env bash
# Reads only this run's PID memory/thermal state. Program/model deployment is separate.
set -euo pipefail
serial=${1:?Usage: run-device-p0.sh HOST:PORT [REMOTE_MODEL_DIR]}
model=${2:-/data/local/tmp/qwen-asr-p0/model}
case "$model" in /data/local/tmp/qwen-asr-p0/*) ;; *) echo 'Refusing path outside P0 directory' >&2; exit 2;; esac
# Conservative path whitelist because paths appear in the remote shell command.
[[ "$model" =~ ^[a-zA-Z0-9_./-]+$ ]] || exit 2
case "/$model/" in */../*|*/./*) echo 'Dot path components forbidden' >&2; exit 2;; esac
mkdir -p reports/p0
timeout 10s adb -s "$serial" get-state >/dev/null
# Resolve remotely to reject symlink escapes as well as lexical traversal.
resolved=$(timeout 10s adb -s "$serial" shell "readlink -f '$model'" | tr -d '\r')
case "$resolved" in /data/local/tmp/qwen-asr-p0/*) ;; *) echo 'Resolved model outside P0 directory' >&2; exit 2;; esac
[[ "$resolved" =~ ^[a-zA-Z0-9_./-]+$ ]] || exit 2
model=$resolved
timeout 10s adb -s "$serial" shell "test -f '$model/config.json' && test -f /data/local/tmp/qwen-asr-p0/p0_asr"
# Dedicated test directory only; no persistent system settings or microphone access.
timeout 600s adb -s "$serial" shell "cd /data/local/tmp/qwen-asr-p0 && sh -s -- '$model'" <<'ANDROID' > reports/p0/device-inference.txt 2>&1
set -u
model=$1
# Atomic remote lock prevents two collectors from overwriting shared evidence.
lock=/data/local/tmp/qwen-asr-p0/.run-lock
if ! mkdir "$lock" 2>/dev/null; then
  echo 'P0_BUSY: run lock exists; inspect previous run before retrying' >&2
  exit 75
fi
pid=''
monitor=''
cleanup() {
  trap - EXIT HUP INT TERM
  if [ -n "$monitor" ]; then kill "$monitor" 2>/dev/null || true; fi
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    kill -TERM "$pid" 2>/dev/null || true
    sleep 2
    kill -KILL "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  fi
  rmdir "$lock" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
export LD_LIBRARY_PATH=/data/local/tmp/qwen-asr-p0
printf 'BEGIN elapsed-realtime='; cat /proc/uptime
# Dedicated harness has no tuning and reports errors with nonzero exit codes.
# Close stdin so background processes cannot retain adb's input pipe.
./p0_asr "$model/config.json" /data/local/tmp/qwen-asr-p0/sample.wav </dev/null > inference-output.txt 2>&1 &
pid=$!
(
  count=0
  while kill -0 "$pid" 2>/dev/null; do
    count=$((count + 1))
    if [ "$count" -gt 120 ]; then
      echo 'P0_MONITOR_LIMIT reached; terminating only this test PID'
      kill -TERM "$pid" 2>/dev/null || true
      sleep 2
      kill -KILL "$pid" 2>/dev/null || true
      break
    fi
    printf '\nSAMPLE '; cat /proc/uptime
    grep -E '^(VmRSS|VmHWM|VmSize|Threads):' "/proc/$pid/status" 2>/dev/null || true
    grep -E '^(Rss|Pss|Private_Clean|Private_Dirty):' "/proc/$pid/smaps_rollup" 2>/dev/null || true
    sleep 1
  done
) </dev/null > inference-memory.txt 2>&1 &
monitor=$!
wait "$pid"
status=$?
pid=''
kill "$monitor" 2>/dev/null || true
wait "$monitor" 2>/dev/null || true
monitor=''
printf 'EXIT=%s\n' "$status"
printf 'END elapsed-realtime='; cat /proc/uptime
cat inference-output.txt
printf '\nMEMORY_SAMPLES\n'; cat inference-memory.txt
exit "$status"
ANDROID
printf 'Saved reports/p0/device-inference.txt\n'
