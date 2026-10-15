#!/usr/bin/env bash
# Backward-compatible full entry. All original verifyDebug gates remain required.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
exec bash "$root/scripts/verify-local.sh" full "$@"
