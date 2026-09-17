# Focused follow-up review: recording R1 / R2 / R3 fixes

## Verdict

**The three original source-level blockers are resolved under the final lifecycle contract in `docs/app-roadmap.md:53–59`. No new blocking race was identified in the inspected changes.** This is approval of the reviewed ordering/ownership fixes, not evidence of Android hardware shutdown, lifecycle integration, or packaged-APK correctness.

Read the original `reports/apk/recording-review.md`, current `RecordingControl.java`, `ForegroundRecorder.java`, all of `MainActivity.java`, `tests/RecordingRaceTest.java`, and the roadmap's final contract. Also inspected the lightweight test runner. No application, test, script, or documentation source was changed. The authorized host suite independently passed **32 existing checks + 20 lifecycle-gate checks**.

## Review findings

### R1 — Original High / P1: resolved

**Locations:** `android/app/src/org/llmasr/minimal/RecordingControl.java:10–20`; `android/app/src/org/llmasr/minimal/ForegroundRecorder.java:23–24,28–49`; `android/app/src/org/llmasr/minimal/MainActivity.java:89–94`.

The final eligibility check and `AudioRecord.startRecording()` execute under the same session monitor as `cancel()`. If cancellation wins, it sets `stopped`, and a later `start()` refuses to call the backend. If startup wins, cancellation cannot complete inside that backend call: it takes effect after startup relinquishes the monitor. This removes the original ordering in which an already-completed cancellation was followed by microphone startup.

The read loop does not hold the monitor across reads. Once cancellation is accepted, the worker observes stop and proceeds through cleanup. Cancellation can still arrive just after a loop check and allow the current read/append to finish; that is consistent with asynchronous shutdown, and does not permit inference after accepted cancellation. Cleanup attempts stop and then release; the outer `finally` records completion of the cleanup path.

**Accepted limitation, not a reopened blocker:** the UI thread can wait for `startRecording()` while acquiring this monitor; cancellation does not synchronously stop/release hardware. The final roadmap explicitly discloses both facts. There is no source-level wall-clock upper bound on this wait or actual hardware shutdown.

### R2 — Original High / P1: resolved

**Locations:** `android/app/src/org/llmasr/minimal/RecordingControl.java:18–25`; `android/app/src/org/llmasr/minimal/ForegroundRecorder.java:40–49`; `android/app/src/org/llmasr/minimal/MainActivity.java:89–94,103–111,215–221`.

`tryCommitInference()` and `cancel()` now make mutually exclusive decisions under the same monitor. Commit requires successful startup, capture-cleanup acknowledgement, and no accepted cancellation. Capture must return normally before the caller attempts commit; failures in capture/cleanup skip the handoff entirely.

The published session remains available until after the commit decision (or capture failure). Therefore:

- If cancel/pause wins before commit, commit fails, a cancellation exception is raised, and `runAudio()` is not called. No private input WAV is persisted and no JNI transcription is launched through this path.
- If commit wins, subsequent cancellation returns false; clearing `recording` afterward does not create an uncancellable *pre-commit* interval. Native work may begin after a subsequent pause, but it belongs to the already-committed inference task permitted by the final contract.
- `onPause()` cancels the session **before** setting `foreground=false`. Request publication and lifecycle callbacks are UI-thread operations; cancellation is sticky in the session, so resume cannot resurrect it. A pause callback that loses the monitor to commit is the documented inference-wins case, not a lost accepted cancellation.

`pcm.finish()` may encode an in-memory WAV before commit, including when cancellation arrives after the last capture check. That array is not handed to `runAudio()` when cancellation wins. “No WAV publication” is supported; a stronger claim of no in-memory WAV encoding before commit would not be accurate.

### R3 — Original Medium / P2: resolved

**Location:** `android/app/src/org/llmasr/minimal/MainActivity.java:253–257`.

Back now takes one volatile snapshot, `RecordingControl r=recording`, and dereferences only `r`. Concurrent clearing of the static field cannot null that local reference. It promises cancellation only when `r.cancel()` returns true; when commit has won, it follows the task-in-progress path instead. Stop, Cancel, pause, and button-state handling also use local snapshots.

### F1 — Low / non-blocking: host checks validate the gate, not recorder/lifecycle integration

**Locations:** `tests/RecordingRaceTest.java:17–60`; `scripts/test-minimal-apk.sh:7–9`.

The added tests exercise the real `RecordingControl` with fake startup callbacks and latch-controlled workers. They cover cancel-before-start, start-before-cancel, release/stop/cancel before commit, commit-before-cancel, duplicate commit/start rejection, and a throwing startup callback. They do **not** compile or execute `ForegroundRecorder` or `MainActivity`. Cleanup is simulated by explicitly calling `captureReleased()`, and WAV/inference publication is represented by counters rather than actual file/JNI hooks. Consequently, “20 fake-backend/latch checks passed” must not be expanded into “Android stop/release and pause/Back integration tested.”

Additionally, `cancelAttempt.countDown()` at line 36 occurs just before calling `cancel()`: the start-wins test does not guarantee the cancel thread actually attempts monitor acquisition before the main thread unblocks startup at line 37. It provides useful coverage but is not by itself a deterministic detector of every check/start synchronization regression. The source proof above, rather than that test alone, establishes the fix. Future integration tests should drive the production capture wrapper with an injectable backend and cover cleanup/read failures and real lifecycle entry points.

## Residual risks and evidence boundaries

- **UI responsiveness / hardware latency:** `RecordingControl.java:10–14` holds the monitor across hardware startup. Cancel, pause, and synchronized UI status queries can wait behind it; a slow or stuck vendor audio call could stall the UI or cause an ANR. `ForegroundRecorder.java:28–49` relies on worker scheduling and Android read/stop/release behavior for shutdown. The disclosed asynchronous contract is acceptable for this review; latency and microphone-indicator disappearance still require device measurement.
- **Cleanup acknowledgement is not device proof:** `ForegroundRecorder.java:49` marks `released` even if `release()` throws. In the current caller an escaping exception prevents commit, so this does not reopen R2. Nevertheless, `released()` means the cleanup path finished attempting cleanup, not independently verified hardware release; do not use it as physical microphone evidence.
- **Untested Android behavior:** no Android compilation, new-APK permission/signature validation, install, microphone access, permission revocation, privacy-switch/contended-source behavior, Home/lock/rotation/recreation, or API/vendor/page-size runtime test was performed here. Silence returned by the platform is not detected by the no-data timeout, as the roadmap already states.
- **Commit is the cancellation boundary:** after commit, model loading/file preparation/native inference can continue even if the activity pauses. This is intentional under `docs/app-roadmap.md:57`, not a guarantee of cancelling everything until JNI entry.
- **Inherited persistence risk remains:** `MainActivity.java:154,215–217` deletes private input WAVs best-effort during ordinary unwinding; process death can leave an input file, and `:238–242` intentionally persists results. These fixes do not provide stale-file cleanup or secure erasure and should not be described as privacy-hardening signoff.

## Validation

Executed from `/home/zhb/gitrep/llm-asr`:

```text
PATH=/nix/store/4dqvcqjnhj6n29r6sh5wwrz58540gbfn-openjdk-17.0.20.1+1/bin:$PATH bash scripts/test-minimal-apk.sh
PASS 32 minimal APK Java checks
PASS 20 recording lifecycle gate checks (fake backend; not device release latency)
```

`git diff --check` passed and the index was empty. Existing working-tree changes were left untouched; the allowed runner generated lightweight class outputs under `.work/build/minimal-apk-tests`. No heavy model/build or device command ran.

Reviewed-source SHA-256 fingerprints:

```text
50bd19ba89a63cb1c2d2e292b1e042b81528514444721df67af0c04544de519f  RecordingControl.java
32f5fe894e0af4ccff46bc889048bf3671bb36d168a6b8454c0ccdc42fa914c4  ForegroundRecorder.java
38839217851ced59ef2bd69f6ff60bde8c2a71eac9d2e3615541b3c75246f591  MainActivity.java
ac38e3e50e67490ae5438dbffa5189d3c55b60f8c4c5727a09b507f0ccdf41c0  tests/RecordingRaceTest.java
79d9087e0c27c81b4d03e9f721b36254dde42088383e8a2fc329396a4f6b4eca  docs/app-roadmap.md
```

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Concrete path-and-line review resolves original R1/P1, R2/P1, and R3/P2 under the final lifecycle contract; records low-severity F1 coverage limits and explicit residual hardware/privacy risks."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/b3c4efaf/reports/apk/recording-fix-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "PATH=/nix/store/4dqvcqjnhj6n29r6sh5wwrz58540gbfn-openjdk-17.0.20.1+1/bin:$PATH bash scripts/test-minimal-apk.sh",
      "result": "passed",
      "summary": "Independently reproduced 32 minimal APK Java checks and 20 recording lifecycle gate checks."
    },
    {
      "command": "git status --short; git diff --check; git diff --cached --name-only; test -z \"$(git diff --cached --name-only)\"",
      "result": "passed",
      "summary": "Existing working-tree changes inspected without edits; no whitespace errors or staged files."
    },
    {
      "command": "Read requested sources, prior review and runner; sha256sum requested current sources and lifecycle contract",
      "result": "passed",
      "summary": "Reviewed actual call-site ordering and recorded source fingerprints."
    },
    {
      "command": "Android build, packaged-APK validation, device microphone/lifecycle tests, heavy model execution",
      "result": "not-run",
      "summary": "Outside this focused read-only host review."
    }
  ],
  "validationOutput": [
    "PASS 32 minimal APK Java checks",
    "PASS 20 recording lifecycle gate checks (fake backend; not device release latency)",
    "PASS diff whitespace and empty index"
  ],
  "residualRisks": [
    "Hardware startup under the monitor can delay UI cancellation/pause and risk ANR; actual asynchronous shutdown latency is unmeasured.",
    "Host tests exercise RecordingControl, not production AudioRecord cleanup or MainActivity lifecycle integration; the start-wins latch does not guarantee acquisition contention.",
    "released() acknowledges attempted cleanup, not physical microphone release; Android hardware, packaged APK and cross-device behavior remain untested here.",
    "Committed inference intentionally survives subsequent pause; inherited private input-WAV retention on process death and persisted result text remain."
  ],
  "noStagedFiles": true,
  "diffSummary": "Wrote only this review report; no source/test/documentation mutations. Authorized host runner generated lightweight class outputs.",
  "reviewFindings": [
    "Resolved R1/P1: RecordingControl.java:10-20 and ForegroundRecorder.java:23-24 serialize backend startup with cancellation.",
    "Resolved R2/P1: RecordingControl.java:18-25 and MainActivity.java:89-111 atomically arbitrate cancel versus released-capture inference commit.",
    "Resolved R3/P2: MainActivity.java:253-257 uses one volatile snapshot and respects cancellation rejection.",
    "Low F1, non-blocking: tests/RecordingRaceTest.java:17-60 and scripts/test-minimal-apk.sh:7-9 cover gate logic, not Android cleanup/lifecycle integration.",
    "No new blocking source-level race identified under docs/app-roadmap.md:53-59."
  ],
  "manualNotes": "Attests focused review completion and source-level blocker resolution only, not hardware or production-readiness acceptance. No microphone/device use, heavy build/model execution, or source edits."
}
```
