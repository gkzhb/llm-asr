# 0.2 foreground recording — build and focused review passed

## Deliverable
- APK: `dist/qwen-asr-minimal-debug.apk`, versionCode2 / 0.2-debug.
- SHA-256: `6c5c2fb7c298482b42fc968081806b39665b4b6cd64505fc4f0c3717a99ec98b`.
- Same package and local debug signer as 0.1; upgrade without uninstalling or deleting models.
- Android29+/arm64 CPU, target35, RECORD_AUDIO-only permission, no bundled weights/network/vendor SDK.
- Generic SoC app roadmap: `docs/app-roadmap.md`; usage and device checklist: `docs/minimal-apk.md`.

## Verified
- Build task b83c3f5b0 completed exit0; Java/d8/native linking, signing, exact version/permission/ABI and archive checks passed.
- 32 helper checks and 20 fake-backend session-gate checks passed and independently reproduced by follow-up reviewer. Five P0 contract tests also passed this iteration.
- Native DSO remains byte-identical to 0.1; no inference math/model/vendor optimization change.
- Original review R1 start/cancel, R2 inference handoff/cancel and R3 Back double-read blockers resolved under the documented lifecycle contract. Follow-up review identified no new blocking source-level race.
- Reviewed source fingerprints match current sources AND recorded APK build inputs; current APK hash matches result.json.

## Exact recording contract
Only explicit user tap with runtime permission starts capture; granting permission does not automatically record. Standard 16kHz mono PCM16, sample-count ceiling30s, stop-to-transcribe/cancel-to-discard. Cancel, microphone start and inference commit share a session gate. Accepted cancellation prevents later start/commit; commit winning means later cancellation is unavailable. Worker attempts stop/release before handoff; hardware release is not synchronously guaranteed onPause. No capture loop or JNI execution holds the gate.

## User manual feedback
- User reports the fixed0.2 test had no issues and authorizes committing it. See `user-v0.2-validation.md`. No per-case logs or hardware latency supplied; remaining items below refer to independently collected evidence.

## Still unverified / residual risks
- No0.2 device install, real microphone, permission denial/revocation, lock/Home/rotation, repeated recording, source contention or shutdown-latency testing. Host gate tests do not execute AudioRecord or Activity lifecycle integration.
- Slow/stuck vendor startRecording can delay UI cancellation/status queries under the gate and risk ANR. Actual release latency/microphone indicator disappearance requires measurement.
- Some devices return silence on mic privacy switch/source contention rather than a read error; no-data timeout is not VAD or silence detection.
- Devices without16kHz mono PCM16 capture get an error and retain WAV import; multi-rate fallback is not yet implemented.
- Committed inference may continue after pause and cannot yet be cancelled. No background survival guarantee, FGS, long audio, IME/API or VAD.
- Process death can leave private input WAVs; normal deletion is best effort and results intentionally persist. No secure-erasure/production-privacy claim.
- Existing user Snapdragon success is0.1 evidence only, archived under `reports/apk/v0.1/`; original APK is ignored at `dist/v0.1/`. Original rejected0.2 binary is quarantined at `dist/review-rejected-v0.2/`, not an acceptance artifact.
- P0 strict frontend/window/independent-golden and production release requirements remain open. No broad all-SoC performance/compatibility guarantee.

## Evidence
`result.json`, `recording-review.md`, `recording-fix-review.md`, `java-tests.txt`, `package-checks.txt`, `build-input-sha256.json`, `signature.txt`, `badging.txt`, `permissions.txt`.
Build log: `.pi/tasks/session-525127-525127/b83c3f5b0.output`.
