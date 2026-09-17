# Independent review — current first voice IME implementation

## Verdict and evidence boundary

**Request changes: one P2 lifecycle/recovery blocker (B1). No P0/P1 privacy, cross-field commit, shared-owner, or missing production capture/native-wiring defect was found in the reviewed source.** This is a source/host-test conclusion, not Android acceptance.

Reviewed the current files in `/home/zhb/gitrep/llm-asr`, independently of any earlier writer draft/report. The existing dirty 0.3 work was not treated as IME regression evidence; I read the current production implementation rather than reviewing the entire git diff. Git HEAD at review: `a4c6a659bb0728cfc62e39a22f02edce5790ba3c`; HEAD alone does **not** identify this dirty/untracked implementation. Exact source hashes appear below.

No production, test, script, documentation, or git-index edits were made. No device, microphone, Android execution, full APK build, native inference, or signing command was run. The authorized host-test command generated ignored `.work/build/minimal-apk-tests` class files and refreshed the Nix toolchain mirror; this report is the only intentional review artifact. Other APK report files changed externally while the review was running; they are not evidence of a build performed or accepted by this reviewer. The scoped Java hashes remained unchanged across the review fingerprint/check interval.

## Blocking finding

### B1 — P2 / lifecycle acceptance blocker: cancelling the keyboard picker strands a still-visible IME without a session

**Location:** `android/app/src/org/llmasr/minimal/AsrImeService.java:60–64`, recovery paths at `:77–92`, eligibility/UI at `:111–114,128–138`.

**Concrete scenario:**

1. Show this IME in an ordinary eligible text field; optionally obtain a preview.
2. Tap `切换键盘`. Lines 61–63 invalidate the current session and force the private `visible` flag to false before showing the system input-method picker.
3. Dismiss the picker with Back/tapping outside, or select the already-current IME. Neither operation requires Android to start a new input view: the existing input target and IME window can remain in place.
4. The keyboard remains on screen, but its record, commit, and discard controls are disabled. `eligible()` cannot succeed because both `visible` and `session` were cleared. Only `onStartInputView()` restores `visible=true`; even an `onWindowShown()` callback merely renders the invalid state. `onStartInput()` alone cannot recover because `renewSession()` requires `visible=true`.

The user must force a genuine hide/re-show or switch away and back to use recording again. Clearing the preview before switching is correct; conflating a requested picker launch with a completed input-view hide is not. The same lack of recovery exists if the Settings activity launch fails at lines 55–58, although that is a less likely path than normal picker dismissal.

**Suggested fix direction (not applied):** preserve invalidation/cancellation of the old transaction, but establish a deterministic recovery path to a **fresh** session for the still-current, visible, eligible editor. One option is to explicitly hide the IME before opening the picker so any later usable keyboard necessarily receives a real input-view start; another is a tested picker/window-return lifecycle state that revalidates the current editor and renews only after the picker is gone. Do not restore the old preview, auto-record, or simply reenable recording while the picker is still covering the window.

**Required regression evidence:** picker dismiss and current-IME reselect while idle, capturing, and holding a preview; record becomes usable again on return, old preview stays empty, any old native completion remains discarded, and no automatic capture/commit occurs. Include ordinary hide/re-show without a field change.

**Confidence/limit:** high-confidence source state-machine defect on a permitted Android callback sequence; not reproduced on Android in this read-only review. The current host suite cannot exercise the Service/picker sequence.

## Nonblocking findings and improvements

### N1 — P3 / usability: shared-owner contention is disabled but not explained

`AsrImeService.java:128–137` disables record/commit when the App owns the coordinator, but status still displays the untouched session prompt ("点击录音…") rather than a shared-task-busy message. `ImeController.java:51` rejects a busy start before the runner can set its busy status. A user opening the IME during App inference sees an instruction to record alongside a disabled record button. The owner exclusion is correct; add a distinct waiting/shared-owner status. This also narrows the discrepancy with `docs/voice-ime.md:50`, which explicitly expects a busy prompt.

### N2 — P3 / test-evidence quality: some IME assertions do not establish the integration properties their labels suggest

`tests/ImeSessionTest.java:15–39,41–59` uses a fake backend returning the three bytes `{1,2,3}` and a constant transcript, and creates an unrelated `AppState` that is never passed into either the controller or backend. Consequently:

- "actual capture audio/language passed to inference" proves forwarding of the exact fake byte-array reference, **not** a valid production WAV or native invocation.
- "independent App state retained" is effectively a disconnected-state assertion; it cannot catch a future `ImeBackend` write into `graph.appState()` or App reports.
- The fake cleanup deletes its temporary file **before** throwing `failCleanup` (`:35–38`), so the cleanup-failure test checks status/release handling, not retention/retry of a genuinely undeletable private WAV.

This is not a fake replacement of the controller: the controller, runner, owner, field policy and recording gate under test really are production classes, and those checks are valuable. Rename/narrow claims and add integration evidence against the real adapter/Service when possible. In particular, add stale **failure** delivery into a newly begun session, blocked-cleanup ownership, actual stop-to-handoff, and B1 return-path tests. The existing threaded test covers stale **success** during blocked fake inference, not every Android lifecycle callback promised at `docs/voice-ime.md:29–35`.

### N3 — P3 / optional resource-saving and documentation precision: cancellation cutoff precedes actual JNI entry

`ImeController.java:70` commits the inference gate immediately after capture release. `ImeBackend.java:16–26` subsequently canonicalizes the WAV and may hash the entire model before entering native. If the user hides/changes fields during that verification, invalidation safely clears the session but `RecordingControl.cancel()` (`RecordingControl.java:18–20`) no longer cancels, and the backend still enters JNI afterwards. Ownership/privacy/late-result rejection remain intact, so this is not a cross-field or recording leak blocker. Consider a pre-JNI cancellation checkpoint or document that the noncancellable processing phase begins at capture handoff, rather than literally only when native has already begun (`docs/voice-ime.md:23`). Never release ownership before the current worker actually finishes.

## Production flow and safety properties verified by source inspection

### Actual capture → WAV → native → preview → explicit commit is wired

- `AsrImeService.java:116–118` starts only from the record action after current-editor eligibility, input-view visibility, and already-granted microphone permission checks.
- `ImeController.java:53–86` binds a new recording control/revision synchronously at admission and submits through the shared `RequestRunner`/`TaskCoordinator`.
- `ImeBackend.java:11–12` really calls `ForegroundRecorder.capture`, not a placeholder. `ForegroundRecorder.java:17–41` initializes 16 kHz mono PCM16 `AudioRecord`, performs nonblocking reads into `PcmWave`, limits capture by sample count, and produces a WAV. `PcmWave.java:17–24` writes the RIFF/fmt/data headers.
- The recorder's `finally` stops/releases the hardware before capture returns (`ForegroundRecorder.java:42–49`). Only then can `tryCommitInference()` succeed (`RecordingControl.java:23–25`). A cancel that wins before this handoff prevents transcription.
- `ImeBackend.java:16–30` assigns a request-UUID private WAV filename before writing; canonicalizes the captured bytes; verifies models when needed; loads the JNI DSO; calls `MainActivity.invokeTranscribe`; parses a real `NativeResponse`; returns only its display text. `MainActivity.java:25–27` matches the exported JNI symbol at `native/apk/asr_jni.cpp:22–24`.
- JNI configures synchronous CPU inference and a 128-token cap (`native/apk/asr_jni.cpp:34–48`), streams output to a local `ostringstream`, and releases the per-call engine via RAII before returning (`:34–56`). No second Java report-oriented inference path is used for IME.
- Results are placed only in a matching live session/revision (`ImeController.java:73–78`). `AsrImeService.java:120–126` obtains the current `InputConnection` on the main thread only at the explicit confirmation action. `ImeController.java:90–100` checks identity, sensitivity and idle ownership, consumes the preview **before** calling the connection, and never automatically retries null/false/throwing connections.

These are connected production-source facts. They do not attest microphone compatibility, model execution success, recognition quality, or host insertion on a device.

### App and IME share one owner through cleanup and native completion

The manifest declares no separate process. Both entry points call the singleton `AppGraph.install/get`; `AppGraph.java:35–50` constructs a single coordinator shared by the App operation and the IME controller (`AsrImeService.java:32–33`). `TaskCoordinator.java:42–60` admits at most one owner and notifies after release (`:71–74`). `RequestRunner.java:70–94` runs body and cleanup before returning to that release. IME invalidation signals only its own recording gate; it does not release the coordinator. App `onPause()` cancels only `AsrOperation.recording` (`MainActivity.java:142–147`, `AsrOperation.java:56–61`), not IME capture. These prevent App/IME cleanup or model tasks from overlapping an active IME WAV/native operation.

Cleanup **attempt** is held under the owner even if deletion fails. Failure surfaces an error and releases after the attempt (`RequestRunner.java:89–93`); successful deletion under every filesystem/kill condition is not promised. `ResultFiles.java:10–25` recognizes the UUID filename for bounded startup recovery, and `AppGraph.java:49` starts recovery under the same owner.

### Sensitive denial, stale results, explicit permission flow

`ImeFieldPolicy.java:7–11` masks class/variation bits and denies TYPE_NULL/unknown classes, text/visible/web/numeric passwords and `IME_FLAG_NO_PERSONALIZED_LEARNING`. Session begin and final commit both check policy; Service eligibility checks again against current `EditorInfo`. It does not read surrounding or existing host text. Incorrectly labelled ordinary fields remain a documented limitation, not something this policy can infer.

`AsrImeService.java:83–108` invalidates on every input restart, input-view finish, input finish, hide, unbind and destroy; `onStartInputView` creates a fresh session. `ImeController.java:34–44,47–48,73–75` discards stale success/progress/failure status using current object identity and request revision, rather than trusting package/field ID alone. The Service renders current state from posted invalidation notifications; no old transcript is embedded in a posted runnable.

`MainActivity.java:48–51,151–165` requests permission only after user action, and its permission-result callback sets a status only. It does not resume recording. The IME never requests permission itself and neither lifecycle nor native completion contains a commit call.

### Privacy and Service/reference boundary

`ImeController.java:55–64,102–106` supplies private session status, no App text state, no-op text mutation and no-report sentinels. Its `MAINTENANCE` request selects `RequestRunner.java:71–88`'s non-reporting path for success, failure and cancellation. `ImeBackend.java:20–23` shares only model verification metadata with AppState. The IME does not call the App `AsrOperation` method that persists raw/display inference reports (`AsrOperation.java:193–240`). No clipboard/history/export/log sink for the IME transcript was found in the scoped Java/JNI bridge. Native stream output is local; this is not an exhaustive audit of every linked MNN logging/cache/error path or crash dump.

`OperationContext.java:21–32` retains application context. The controller's notifier retains `UiRefresh`, whose only Service reference is weak (`AsrImeService.java:140–148`); UI work is posted to the main looper and ignored after destruction. Worker bodies retain a private session, controller and application graph, not a Service/View/InputConnection. The non-editable/non-selectable preview does not open another IME or intentionally save/copy text. Ordinary immutable Java/native strings are not securely erased; invalidation removes active references/visible text, not all historical heap bytes.

### Manifest, resource and build/check wiring

`AndroidManifest.xml:2–5,13–17` specifies 0.4-ime-debug/versionCode 4, API29/35, RECORD_AUDIO only, optional microphone hardware, an exported IME protected by BIND_INPUT_METHOD, the correct InputMethod action and `android.view.im` metadata. `res/xml/method.xml` declares a non-default voice subtype with a valid string label resource. There is no forced default input-method switch or extra background/network/storage permission.

`build-minimal-apk.sh:47–53` compiles/links resources and compiles all production Java classes before dexing; `:69–84` dumps/checks the manifest and method metadata. `check-minimal-apk.py:21–42` adds IME resource/service/version/same-process checks. `test-minimal-apk.sh:9–22,33` really includes the production controller/policy/session and invokes `ImeSessionTest`.

Shell syntax, Python syntax and XML well-formedness passed. AAPT2 compilation, Android javac/d8, packaging, signature/native provenance and the current signed APK were **not** run/validated by this review. The checker reads some external report dumps; invoking it separately against stale dumps would not by itself bind every assertion to the APK. The build script regenerates those dumps in sequence, but no claim here relies on externally appearing build reports.

## Host tests run and validity

Command: `bash scripts/nix-env.sh apk bash scripts/test-minimal-apk.sh` — **passed**, exit 0.

```text
PASS 32 minimal APK Java checks
PASS 20 recording lifecycle gate checks (fake backend; not device release latency)
PASS 41 result cleanup/export checks (host only)
PASS 25 native response parser checks (host only; strict UTF-8, 128 token cap, empty raw allowed)
PASS 33 model repository checks (host only; no test backdoors)
PASS 15 part-recovery red/green checks (host only)
PASS 44 request runner checks (host only; covers success/failure/cancel/report-failure/owner/cleanup-id/clear-text policy)
PASS 14 task coordinator checks (host only; no Thread.sleep busy polling)
PASS 22 production admission/owner/recording/export boundary checks (host only)
PASS 89 IME production controller checks (not Android hardware/InputConnection tests)
```

Total: **245 existing baseline checks + 89 IME checks = 334**. Useful evidence includes queued-before-start cancellation, fake capture handoff cancellation, identity/revision invalidation, one-shot commit failure behavior, App/IME coordinator exclusion, failure cleanup invocation, executor rejection rollback, and a latch-controlled concurrent invalidation while fake native is blocked. The runner/owner/gate tests exercise real production Java and are not Android stub tests.

Evidence does **not** include `AsrImeService`, `ImeBackend`, `AppGraph`, `ForegroundRecorder`, `MainActivity` runtime integration: they are excluded from the host compile list. No microphone/real WAV-to-JNI roundtrip, InputConnection, picker, Binder callback ordering, rotation/unbind/re-show, UI thread enforcement, or private on-device report/log/cache inspection is established by this pass. Claims at N2 must stay narrowed accordingly.

Additional validation: `bash -n scripts/build-minimal-apk.sh scripts/test-minimal-apk.sh scripts/nix-env.sh`; Python `ast.parse` of the APK checker and ElementTree parse of the manifest/resource XML — all passed. A read-only broad `/nix/store` search for Android framework source timed out after 20 seconds; it yielded no framework evidence and was not a build/test failure. Git index listing was empty.

## Residual risks / next acceptance gate

1. Resolve B1 and validate picker dismissal/current-selection plus ordinary hide/re-show. Retest old result rejection; never resurrect old text or auto-start audio as a recovery shortcut.
2. Build/dex/package/sign the frozen corrected source, bind APK and input hashes, then run user-authorized nonprivate Android tests. This review does not accept any newly appearing APK reports as its own evidence.
3. Actual AudioRecord startup/stop/release calls may block in the driver; the main thread can wait on the gate while hardware start owns its lock. Host tests cannot bound that latency. Device microphone privacy switches, contention, lock-screen/hide order, Android background capture restrictions and memory pressure remain untested.
4. The per-call native engine can consume roughly the documented multi-GiB memory and is not safely interruptible; IME lifetime may outlast visible UI until work really finishes. Source ownership is correct but process kill/OOM behavior is not validated.
5. Failed deletion or process death may retain private audio until bounded later cleanup. No secure erase guarantee is made. Whole-DSO logs/caches, system snapshots/accessibility/debug/crash surfaces were not exhaustively audited; the app is explicitly debuggable and this is not a hardened-release privacy certification.
6. Sensitive-policy safety relies on correct host `EditorInfo` labels; the current-connection safeguards cannot detect a host that silently changes targets without proper input lifecycle notification. Within normal Android callbacks the reviewed identity/revision guards are conservative.

## Source fingerprints (SHA-256)

Paths below are relative to `/home/zhb/gitrep/llm-asr`. These identify the current source, not HEAD or an APK. The 11 scoped production Java entries were explicitly checked unchanged again after inspection/tests. The remaining scoped/resource/script hashes were collected during the same frozen review.

```text
fa849935b8a4a7bbd9ca86781bcd049843d474bf40e76115baf4eabea9bc2d19  android/app/src/org/llmasr/minimal/AsrImeService.java
4f032ce42b848432a3078cb787f24d9e377992e9f69277ebdb7fd8c2cff20eb6  android/app/src/org/llmasr/minimal/ImeBackend.java
46c93f5132cec927cc406034f37d846990f6ec25d4ce4b30a486877dfd977416  android/app/src/org/llmasr/minimal/ImeController.java
946e0855ab809f3cfe384ad12c3e9cacde92c6a90773edd3514ee0b210fd016e  android/app/src/org/llmasr/minimal/ImeSession.java
856c9ad88852453e8a0f51b177f252f0d56bf473b31fb93bff7a0c03ffd755a0  android/app/src/org/llmasr/minimal/ImeFieldPolicy.java
0a8ddc048525808eb5ebe54fc70a5bf0f338956c93dd1e8712f3dcba456fc97e  android/app/src/org/llmasr/minimal/AppGraph.java
4504310a43d54cd4b611c48d57df9808b801daffeb5afd40809d459954781e1c  android/app/src/org/llmasr/minimal/RequestRunner.java
90cc7ee59e55296cf04344dc00d55ec2634b693840565cd00632088e5324f9db  android/app/src/org/llmasr/minimal/TaskCoordinator.java
50bd19ba89a63cb1c2d2e292b1e042b81528514444721df67af0c04544de519f  android/app/src/org/llmasr/minimal/RecordingControl.java
32f5fe894e0af4ccff46bc889048bf3671bb36d168a6b8454c0ccdc42fa914c4  android/app/src/org/llmasr/minimal/ForegroundRecorder.java
 a5173c344b96b6a034422951674a34db3803d035d8566e8f0788fbd6f94678e9  android/app/src/org/llmasr/minimal/MainActivity.java
7ad75687deb3943c9b4bf4483c6acc86308734b8246983bd92679da747b18622  android/app/AndroidManifest.xml
6fe5023411f28b1d2b1f52b080039caca05a569128be8cd8c854d4056a557d44  tests/ImeSessionTest.java
1f347732a300d7246823a3f819e41f8c66006e59a72ae3b6470056018d9ba154  scripts/build-minimal-apk.sh
e1278a1add3d801ea025a0360d5f7f3f669e7ba6f7a6bb6c79cb1620766206ea  scripts/test-minimal-apk.sh
409be7c010e28ac72f361fa5f6d422670595af3d4b59b242f72dfa9891248106  scripts/check-minimal-apk.py
8c1a28deece7fbfcf9b1c8b1d716f0d49d54c2aad2b92f41f0f456b38a9118fb  docs/voice-ime.md
1fd511fd92ebf881aacc98fddc2a5ef414d960fbcf00d49ade7309c55be96d68  android/app/res/values/strings.xml
944818acd60bb81fbb3ecf11bccc36efa8db0b423a0158c9cac7365701d92d37  android/app/res/xml/method.xml
```

Supporting production dependencies traced for the end-to-end/privacy/cleanup conclusions:

```text
27cd133f2ee627b78ffff713a5a95b73d712c258c79e63ee09720c102681e092  android/app/src/org/llmasr/minimal/AsrOperation.java
783c5baef3ab6bc026eb7a1095040e63825c677fa612dd0eeda3bc2deb2c76d0  android/app/src/org/llmasr/minimal/OperationContext.java
8da466700f7707853d5904044aaaff7a495e0a632b5a0dd910c862f9f908c3dc  android/app/src/org/llmasr/minimal/AppState.java
c7b7330dcf3dd12b50ca8d248e2807b23de6419d245a4b442bfa1f9afdf5899e  android/app/src/org/llmasr/minimal/NativeResponse.java
089bf01a52f5ddd07e1bf4962f6162804dd995c7e06dfc284f5cdfe7ca57bb10  android/app/src/org/llmasr/minimal/WaveInput.java
3f690d9a1ed1e954a43e4a5256d7203dd22843ccf7c71e12a95f8f65bf9763d7  android/app/src/org/llmasr/minimal/PcmWave.java
7ce787785e4dc20e6e320824119c758d6792358b28151290bb8f72f47fd67e01  android/app/src/org/llmasr/minimal/ResultFiles.java
3d7ad2d87ce3e2ae7d318be508be135c1d2d7f0ff968acf80a83ee37175df6c7  android/app/src/org/llmasr/minimal/FileSafety.java
53824f6a5497ce32a5849c5e338fc03524ace35d052fd09e400ef14629d8b07d  android/app/src/org/llmasr/minimal/TaskKind.java
d4b12d677c4dde8dce2e9e595106c869ad6d4db8bbd6048d8e19555386526c19  android/app/src/org/llmasr/minimal/RequestContext.java
67dfeaa2f286df633726d1d7b90e8822352f2fb50bb40100554920ce17bd3dbd  native/apk/asr_jni.cpp
8db37f0e2e6eddbcba7b6963cd48626185c32dafbecb1d44853e47932bed282d  scripts/nix-env.sh
```

## Structured acceptance report

This attests completion of the independent review criterion, **not** acceptance of the implementation for release.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "B1 identifies a concrete P2 lifecycle blocker at AsrImeService.java:60–64 with picker-dismiss/current-IME-reselect scenario, recovery guidance and regression expectations; N1–N3 separately document nonblockers; production wiring, test evidence limits, residual risks and exact source SHA-256 fingerprints are recorded."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/ec61deb8/.work/ime-independent-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "bash scripts/nix-env.sh apk bash scripts/test-minimal-apk.sh",
      "result": "passed",
      "summary": "334 host checks: 245 baseline plus 89 production IME controller checks; no Android execution."
    },
    {
      "command": "bash -n scripts/build-minimal-apk.sh scripts/test-minimal-apk.sh scripts/nix-env.sh",
      "result": "passed",
      "summary": "Shell syntax valid; build scripts were not executed."
    },
    {
      "command": "Python ast.parse checker; ElementTree.parse manifest/resources; hashlib SHA-256 source inventory and scoped Java recheck",
      "result": "passed",
      "summary": "Python/XML syntax valid; source fingerprints recorded and 11 scoped production Java hashes unchanged across snapshot/recheck."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "Empty git index diff; no staging or commit performed."
    },
    {
      "command": "Broad read-only /nix/store search for Android framework sources",
      "result": "failed",
      "summary": "Timed out after 20 seconds, no framework evidence obtained; unrelated to host test result."
    },
    {
      "command": "Full APK build/package/signing/check and Android/device/microphone execution",
      "result": "not-run",
      "summary": "Outside this read-only review authorization."
    }
  ],
  "validationOutput": [
    "PASS 32+20+41+25+33+15+44+14+22 baseline checks and 89 IME checks = 334 total.",
    "One P2 lifecycle blocker; no P0/P1 source-level privacy or missing native-wiring blocker found.",
    "Host tests exclude actual Service, Android backend, microphone and InputConnection integration."
  ],
  "residualRisks": [
    "B1 picker-dismiss/current-IME-selection recovery requires correction and Android regression evidence.",
    "No APK build, microphone/native roundtrip or Android lifecycle/commit acceptance performed.",
    "N2 disconnected AppState/fake WAV/cleanup assertions do not establish production privacy and filesystem integration.",
    "Driver release latency, memory pressure/process kill, failed deletion and whole-DSO/system privacy surfaces remain unverified.",
    "External APK report changes occurred during review and are not attributed to or accepted as evidence by this reviewer."
  ],
  "noStagedFiles": true,
  "diffSummary": "No reviewed source/test/script/doc/index mutations. Only the required report was written; authorized host tests generated ignored classes/Nix mirror outputs. Pre-existing dirty changes were left intact.",
  "reviewFindings": [
    "P2 blocker B1: android/app/src/org/llmasr/minimal/AsrImeService.java:60–64 — picker dismissal/current-IME reselect can leave a visible keyboard permanently session-ineligible until a real hide/re-show.",
    "P3 nonblocker N1: android/app/src/org/llmasr/minimal/AsrImeService.java:128–137 — shared owner disables controls without a busy explanation.",
    "P3 nonblocker N2: tests/ImeSessionTest.java:15–59 — fake WAV/backend and disconnected AppState assertions are narrower than full integration/privacy evidence; cleanup failure occurs after deletion.",
    "P3 nonblocker N3: android/app/src/org/llmasr/minimal/ImeController.java:70 and ImeBackend.java:16–26 — noncancellable handoff precedes model verification/JNI entry; stale results remain safely discarded."
  ],
  "manualNotes": "Independent current-source review, no reliance on rejected first draft. Full evidence and source hashes are in this report. Review criterion satisfied does not mean IME release accepted."
}
```
