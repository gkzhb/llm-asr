# Read-only review: uncommitted 0.2 foreground recording

**Baseline:** `HEAD` = `89d244a6fd9716279e375406269fffe41e2de86c`.

**Verdict:** Request changes before accepting the foreground-recording privacy/lifecycle contract. Two high-priority concurrency gaps and one medium-priority crash race are present. These are source-level interleavings, not claims of observed device failures. No source was modified.

Reviewed the four requested Java files, `android/app/AndroidManifest.xml`, `tests/MinimalApkTest.java`, `scripts/check-minimal-apk.py`, and `docs/app-roadmap.md`, including tracked changes against HEAD and the three new untracked recording classes/roadmap. Also read the lightweight test runner and `WaveInput.java` to check the test scope and PCM handoff. Generic API29+ ARM64 CPU functionality is the target; vendor optimization and native numerical changes are outside this review.

## Blocking findings

### R1 — High / P1: cancellation can complete before the worker starts the microphone

**Locations:** `android/app/src/org/llmasr/minimal/ForegroundRecorder.java:23–24`; `android/app/src/org/llmasr/minimal/MainActivity.java:86–89`; `android/app/src/org/llmasr/minimal/RecordingControl.java:5–9`.

The last `control.stopped()` check and `recorder.startRecording()` are separate operations on the worker. `onPause()` merely sets volatile flags through `cancel()`; it does not serialize with startup or await recorder shutdown.

Concrete ordering:

1. Worker checks `control.stopped()` at line 23 and sees false.
2. Main thread runs `onPause()`, sets `foreground=false`, calls `cancel()`, and returns.
3. Worker calls `startRecording()` at line 24 **after cancellation and pause**.
4. The loop subsequently notices stop, cancellation throws, and `finally` releases the recorder.

Thus cleanup is structurally present, but it does not establish “never start after leaving the screen.” Even when no PCM is retained, the microphone can be activated after cancellation. Non-blocking reads reduce normal shutdown latency; they do not make this check/start pair atomic. OS background restrictions may reject the start on some devices, but cannot serve as the app's correctness mechanism.

**Minimal proposed fix:** Introduce one recorder-session lifecycle gate shared by startup and cancel/pause. Serialize the final eligibility check plus start with cancellation, so a cancellation that wins the gate prevents start. If start wins first, cancellation must arrange stop/release with explicit ownership and shutdown acknowledgement; do not hold a lifecycle lock across the whole capture loop or inference. If immediate hardware release before pause returns is not supportable without UI blocking, explicitly define the prompt-shutdown guarantee and measure its latency rather than claiming synchronous release. Adding another unsynchronized boolean check is insufficient.

**Regression needed:** A fake audio backend with a latch immediately before startup: let pause/cancel win, then unblock the worker; assert zero start calls and appropriate release. Separately exercise cancel during active capture and exception cleanup.

### R2 — High / P1: accepted cancellation can be lost at the capture-to-inference handoff

**Locations:** `android/app/src/org/llmasr/minimal/MainActivity.java:102–105`, with cancel entry points at `:65–66`, `:86–89`; `android/app/src/org/llmasr/minimal/RecordingControl.java:3–9`.

The worker checks cancellation/foreground once at line 103, then clears the shared recording handle and calls `runAudio()` without an atomic transition or subsequent cancellation decision. Volatile flags give visibility, not atomicity across these operations.

Concrete ordering:

1. Capture finishes and releases the microphone; the worker passes line 103.
2. Before line 104, the user taps Cancel, or `onPause()` runs. The control is still published, so `cancel()` is called successfully.
3. Worker clears `recording` and calls `runAudio()` at line 105 anyway.
4. Private recorded audio is written to an input WAV and transcribed; a success report can be persisted despite the accepted cancellation.

Alternatively, pause can arrive after the handle is cleared but before inference starts, leaving no control for `onPause()` to cancel. This conflicts with the roadmap's discard-on-leave promise for collected audio not yet handed to transcription (`docs/app-roadmap.md:32`) and the control's stated stop/pause-race guarantee. This finding is **not** a request to interrupt native inference already legitimately committed.

**Minimal proposed fix:** Add an explicit atomic/synchronized `CAPTURING -> TRANSCRIBING` commit shared with cancel/pause, retaining the session handle until that decision. If cancellation wins, discard and report cancelled without calling `runAudio`; if commit wins, mark cancellation unavailable and treat the task as inference. Order the foreground decision with the same transition and use a session generation/latching cancellation so a later resume cannot resurrect it. Do not hold the gate during model loading/JNI. A second standalone flag check only moves the race.

**Regression needed:** Force cancellation/pause between capture return and the commit, assert zero inference calls/no input WAV/no successful result; also test the explicitly defined inference-wins ordering and stop-then-pause.

### R3 — Medium / P2: Back can dereference a recording handle concurrently cleared by the worker

**Location:** `android/app/src/org/llmasr/minimal/MainActivity.java:248`; concurrent writes at `:104` and `:147`.

`if(recording!=null) { recording.cancel(); ... }` reads the volatile field twice. The worker can clear it between the check and invocation, causing an uncaught `NullPointerException` on the UI thread when Back coincides with capture completion/error. The Stop/Cancel click handlers already use the safe snapshot pattern.

**Minimal proposed fix:** Read once: `RecordingControl r=recording; if(r!=null) { r.cancel(); ...; return; }`. Coordinate its cancellation result with R2's transition so the message does not promise cancellation after inference has committed.

## Properties that look correct in the inspected source

- **Fresh tap after permission:** `MainActivity.java:91–114` returns after requesting permission. The permission callback only updates status; it never starts capture. A subsequent foreground button tap is required. Denied permission leaves WAV import available.
- **Release before inference on ordinary paths:** `ForegroundRecorder.java:15–47` wraps initialization, start, reads, callbacks, and WAV finishing in a `finally`. Stop is attempted and release is in a nested `finally`, including initialization failure after construction, negative reads, too-short PCM, and cancellation. `capture()` cannot return normally until this cleanup completes, so `runAudio()` does not normally overlap an active recorder. R1 concerns lifecycle ordering, not a missing ordinary-path `finally`.
- **Single task ownership:** The existing process-wide worker and `RUNNING.compareAndSet` at `MainActivity.java:23–24,125–151` prevent overlapping recording/model/JNI jobs through the normal UI entry points. Stop/cancel flags are visible cross-thread and cancellation is sticky; that alone does not resolve R1/R2.
- **Bounded PCM:** `PcmWave.java:8–24` caps retained samples at 480,000 (960,000 PCM bytes), emits little-endian mono 16 kHz PCM16, and rejects less than 0.1 seconds. Buffer/WAV copies add bounded heap overhead. `ForegroundRecorder.java:17–20,26–36` bounds the requested recorder buffer and has no-data/elapsed-time guards. Thirty seconds is a **sample-count** limit; the 35-second wall-clock guard is separate and consistent with the roadmap wording.
- **Permission scope:** Source manifest line 4 contains only `RECORD_AUDIO`; microphone hardware is optional at line 5, with no service or receiver. No network/storage/background-microphone permission was introduced. `scripts/check-minimal-apk.py:21–22` requires exactly the expected permission in its supplied permissions dump rather than merely allowing it among arbitrary extras.
- **Compatibility scope:** No vendor-specific backend or CPU-name restriction was introduced. The roadmap explicitly distinguishes generic ARM64/API29+ intent from real hardware proof and defers vendor optimization.

## Validation and residual risks

- Source manifest XML check passed: exactly `android.permission.RECORD_AUDIO`, no service/receiver. `git diff --check` passed; the index was empty at inspection.
- The supplied task reports **32 pure Java checks passed**. An independent rerun using a temporary output directory was attempted here but could not compile because `javac` was not on PATH (`javac: command not found`). This is an environment limitation, not a failing Java assertion. Temporary review files were removed.
- `tests/MinimalApkTest.java:50–61` tests PCM bounds/encoding and **sequential** stop/cancel flags. `scripts/test-minimal-apk.sh:7` does not compile `MainActivity` or `ForegroundRecorder`. These checks therefore do not exercise Android lifecycle, recorder cleanup, permission UI, or the interleavings above. Fake backend/latch tests would provide deterministic host coverage without using a microphone.
- **New 0.2 APK not built:** no Android compilation, signed-APK whitelist validation, installation, or recording test was performed. The APK checker reads pre-generated `reports/apk/{permissions,badging,signature}.txt`; those must be regenerated from the exact new APK. Current source-manifest evidence must not be presented as packaged-permission evidence. The checker also does not currently assert the 0.2 version in badging.
- **Untested hardware:** permission grant/deny/one-time revocation, global microphone privacy switch, audio-source contention, supported 16 kHz capture, non-blocking read behavior, actual stop/release latency and microphone indicator, Home/lock/Back/rotation/recreation, repeated capture-to-inference cycles, and installation/runtime behavior across ARM64 vendors/API levels/page sizes. A privacy switch or contention may produce silent buffers rather than a read error on some systems; no-data checks do not establish detection of that case. Existing no-VAD/silence warnings remain important.
- **Residual persistence risk, inherited path newly used for microphone audio:** `MainActivity.java:210–215` writes private audio under `filesDir`; deletion at `:148` is best effort and only runs on ordinary unwinding. Process kill during inference can leave `input-<request>.wav` behind; there is no startup stale-input sweep in `:37–39`. This is not evidence of background microphone use or a newly introduced unbounded capture, but it warrants startup cleanup and explicit retention wording before stronger no-retained-private-audio claims. `last-result.json` also intentionally persists transcription text. Backup is disabled, but this remains a debuggable prototype, not a production privacy-hardening signoff.
- No device microphone, heavy build, model execution, or `.work/models` traversal was performed. Native math was not changed or numerically revalidated. Only the requested review artifact was written.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Read-only review against HEAD 89d244a identifies R1/P1 at ForegroundRecorder.java:23-24, R2/P1 at MainActivity.java:102-105, and R3/P2 at MainActivity.java:248, each with a concrete interleaving and minimal proposed fix; residual risks and untested hardware are separately documented."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/d5741343/reports/apk/recording-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "git status --short; git diff --stat; git diff HEAD -- requested tracked files; numbered reads of requested files and supporting test runner/WaveInput",
      "result": "passed",
      "summary": "Inspected tracked delta and new untracked recording sources without source edits."
    },
    {
      "command": "javac -encoding UTF-8 -d <temporary directory> android/app/src/org/llmasr/minimal/{WaveInput,AsrText,PcmWave,RecordingControl}.java tests/MinimalApkTest.java && java -cp <temporary directory> MinimalApkTest bench/audio/zh-original.wav",
      "result": "failed",
      "summary": "Independent rerun unavailable: javac not on PATH; no Java tests executed by this review. Task-supplied 32-pass result is not claimed as independently reproduced."
    },
    {
      "command": "python3 inline XML check of android/app/AndroidManifest.xml",
      "result": "passed",
      "summary": "Source manifest has RECORD_AUDIO only and no service or receiver; not packaged APK validation."
    },
    {
      "command": "git diff --check; git diff --cached --name-only; git rev-parse HEAD",
      "result": "passed",
      "summary": "No diff whitespace errors; no staged files; HEAD recorded."
    },
    {
      "command": "Build/check/install new 0.2 APK and exercise device microphone",
      "result": "not-run",
      "summary": "Outside authorized read-only/lightweight review; new APK not yet built."
    }
  ],
  "validationOutput": [
    "PASS source manifest: RECORD_AUDIO only; no service or receiver (not packaged APK evidence)",
    "PASS git diff --check",
    "PASS no staged files",
    "javac: command not found",
    "Task reports 32 pure Java checks passed; those checks do not cover Android recorder/lifecycle concurrency."
  ],
  "residualRisks": [
    "R1 and R2 prevent signoff on cancel/pause ordering; R3 can crash Back handling.",
    "Android compilation, new signed-APK whitelist, and real microphone lifecycle behavior remain untested.",
    "Generic ARM64/API/page-size compatibility and actual AudioRecord release latency require hardware evidence, not vendor optimization.",
    "Process death can retain a private input WAV through the inherited inference path; successful transcription text is persisted."
  ],
  "noStagedFiles": true,
  "diffSummary": "No application, test, script, manifest, or roadmap source modified; wrote only the authoritative review report. Existing uncommitted 0.2 changes remain untouched.",
  "reviewFindings": [
    "blocker P1 R1: android/app/src/org/llmasr/minimal/ForegroundRecorder.java:23-24 - check/start race permits microphone startup after cancel/onPause.",
    "blocker P1 R2: android/app/src/org/llmasr/minimal/MainActivity.java:102-105 - cancellation can be accepted after the last check yet audio is still handed to inference.",
    "blocker P2 R3: android/app/src/org/llmasr/minimal/MainActivity.java:248 - double volatile read can dereference null when the worker clears recording."
  ],
  "manualNotes": "Acceptance here attests completion of the review, not approval of the implementation. No device microphone, heavy builds/models, or .work/models traversal; native math unchanged and not revalidated."
}
```
