#!/usr/bin/env bash
# Do not import multi-GB models or extracted dependencies as a path flake source.
# No Git staging needed. Mirror ONLY the toolchain definition, keeping cwd intact.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
name=${1:?Usage: nix-env.sh adb|model|native|apk [COMMAND ...]}
shift
case "$name" in adb|model|native|apk) ;; *) echo 'Unknown shell' >&2; exit 2;; esac
hash=$(cat "$root/flake.nix" "$root/flake.lock" | sha256sum | cut -d' ' -f1)
mirror="$root/.cache/nix-env/$hash"
mkdir -p "$mirror"
cp "$root/flake.nix" "$mirror/flake.nix"
cp "$root/flake.lock" "$mirror/flake.lock"
if [ "$#" -gt 0 ]; then
  exec nix develop "path:$mirror#$name" --command "$@"
else
  exec nix develop "path:$mirror#$name"
fi
