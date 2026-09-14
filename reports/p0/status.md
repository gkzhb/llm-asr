# P0 current status

**Native CPU feasibility verified; fresh 20-case engineering regression passed; full quality closure remains pending.**

Read **[P0 report](report.md)** for deliverables, measured results, reproductions and limitations. This replaces earlier running-task status, not historical evidence.

## Current evidence

- Fixed official model/source/NDK/Python inputs, model conversion and patched host/Android builds completed.
- Python/ONNX/MNN encoder gates and actual native prompt checks passed on their declared fixtures.
- ASR audio boundary position bug reproduced (decode −2) and fixed, temporary trace removed.
- Full clean host regression: **20/20 complete and parsed exact**, `mnn-smoke-summary-final-position-fix.json`.
- Final patched phone runtime hashes verified, short Chinese transcription succeeds with EXIT=0 and no truncation.
- Latest single-phone-run: load **11.6248s**, inference **4.72811s** for **4.20394s** audio (RTF **1.12469**), sampled peak PSS **3.0604GiB**. See `final-device-result.json`. No claim of controlled comparison with old runs.
- Independent implementation review retained, with subsequent safety fixes documented in project progress.

## Open boundaries

- 20 engineering fixtures derive from only two speech recordings; no independent labeled CER/WER evaluation, incomplete planned coverage.
- Strict frontend numeric gate still fails despite large reduction after periodic Hann. Three downstream isolation cases show no token change, not universal harmlessness.
- Eager oracle only; no FA2/window/streaming equivalence.
- Current baseline misses load/memory product targets; quantization, thermal endurance, warm P95 and resource protections remain unverified.
- No APK/IME/API implementation. Native-shell OpenCL probing does not prove APK GPU usability.
- Failure-path cleanup, cross-session deploy/run serialization and full clean-machine reproduction remain limited; use only trusted, serialized test tasks.

Use `bash scripts/nix-env.sh {adb,model,native}` rather than root `path:$PWD` after downloading models. Models and extracted sources are gitignored; do not import multi-GB artifacts as Nix flake source.
