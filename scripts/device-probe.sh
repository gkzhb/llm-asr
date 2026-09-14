#!/usr/bin/env bash
# Read-only, allowlisted capability probe; never collects serials, accounts or files.
set -euo pipefail
serial=${1:?Usage: device-probe.sh HOST:PORT [OUTPUT]}
output=${2:-reports/p0/device-probe.txt}
mkdir -p "$(dirname "$output")"
timeout 10s adb -s "$serial" get-state >/dev/null
{
  printf 'Probe UTC: '; date -u +%FT%TZ
  printf 'Scope: read-only platform capabilities; no personal data\n'
  timeout 45s adb -s "$serial" shell sh <<'ANDROID'
section() { printf '\n=== %s ===\n' "$1"; }
section properties
for key in ro.product.manufacturer ro.product.model ro.product.device ro.build.version.release ro.build.version.sdk ro.product.cpu.abilist ro.board.platform ro.hardware ro.soc.model ro.soc.manufacturer; do
  printf '%s=' "$key"; getprop "$key"
done
section memory
grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo
section storage
df -h /data/local/tmp
section cpu
# Deliberately exclude Serial and unrelated identifiers.
grep -E '^(processor|Features|CPU implementer|CPU architecture|CPU variant|CPU part|CPU revision|Hardware)[[:space:]:]' /proc/cpuinfo
section cpufreq
for dir in /sys/devices/system/cpu/cpufreq/policy*; do
  [ -d "$dir" ] || continue
  echo "$dir"
  for name in related_cpus cpuinfo_max_freq scaling_max_freq scaling_cur_freq; do
    printf '%s=' "$name"; cat "$dir/$name" 2>&1
  done
done
section gpu-features
pm list features | grep -Ei 'vulkan|opengles' || true
section gpu-libraries
for file in /vendor/lib64/libOpenCL.so /vendor/lib64/egl/libGLES_mali.so /system/lib64/libvulkan.so; do
  if [ -r "$file" ]; then echo "$file: readable (not proof of app linker access)"; else echo "$file: absent or inaccessible"; fi
done
section thermal
dumpsys thermalservice 2>&1 | head -100
section battery
# Do not expose battery serial or full vendor dump.
dumpsys battery | grep -E '(AC powered|USB powered|Wireless powered|status:|level:|scale:|voltage:|temperature:)'
ANDROID
} > "$output" 2>&1
printf 'Saved %s\n' "$output"
