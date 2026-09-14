# Minimal APK — build verified, user-reported Snapdragon success

## Deliverable
- APK: `dist/qwen-asr-minimal-debug.apk`
- Size: 2,380,584 bytes (about 2.27 MiB)
- SHA-256: `ff432527ad62e696208f3311804265da3a0369c5ffa9c1c36dda6478073b6877`
- Package: `org.llmasr.minimal`, arm64-v8a, minSdk29, targetSdk35.
- Local debug signature: APK Signature Scheme v3 verified (appropriate for minSdk29).
- Usage: `docs/minimal-apk.md`; machine-readable summary: `result.json`.

## Verified
- SDK35/build-tools35.0.0/JDK17 realized through locked Nix environment.
- 21 pure Java WAV boundary/malformed input and protocol-display checks passed.
- All Activity Java sources compiled, d8 conversion completed.
- Final P0 libMNN identity checked before native relink.
- JNI + 578 existing final-P0 objects linked into one DSO with one static C++ runtime; original P0 library unchanged. Object hashes in `native-link-provenance.json`.
- APK signature, ZIP CRC/integrity/alignment, exact embedded model manifest/public sample, single arm64 DSO hash, zero permissions, API/ABI and absence of weights/intermediate objects verified.
- Independent review completed; parent fixes and remaining runtime tests documented in `review-disposition.md`.

## Build failures and recovery
- `b0c0c6b1f`: Java lambda compilation failed with Android boot stubs. Minimal red/green probe and full Java compile passed after switching to `--release 8` + Android classpath, followed by d8 desugaring.
- `bbe10cd9b`: APK built/signed, but final checker expected old `sdkVersion` field. Actual pinned aapt2 emits `minSdkVersion:'29'`. Checker corrected without relaxing API assertion; existing signed APK then passed all archive checks. Original build log remains a failure record, not rewritten as exit0.
- Build input manifest reflects the checker at build time; `result.json` separately records the corrected post-build checker SHA. APK bytes were not changed by checker correction.

## User-reported manual device test
- User reports successful model loading and correct output on a Snapdragon phone. See `user-snapdragon-validation.md`.
- Exact device/OS, installed artifact identity and raw logs/metrics not supplied; this is not automated verification.

## Not independently verified / blocked
- Agent has not independently collected install/launch or APK UID inference logs. SAF negative import, repeated requests, process interruption and Activity recreation tests remain pending.
- Original authorized ADB endpoint `100.64.0.3:33317` refused connection. User must restore wireless debugging/provide current connection port. Do not scan or bypass lockscreen.
- Existing native-shell P0 inference is not APK evidence. New single-DSO runtime must be validated on device.
- Debug-only local test artifact; no production/distribution readiness claim. Model weights (~1.57GB) separate; no microphone/IME/API/VAD/GPU/quantization.

Logs: `.pi/tasks/session-525127-525127/bbe10cd9b.output`, plus `package-checks.txt`, `signature.txt`, `badging.txt`, `permissions.txt`, `contents.txt`, `java-tests.txt`.
