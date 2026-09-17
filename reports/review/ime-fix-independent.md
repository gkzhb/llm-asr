# Independent narrow IME fix follow-up

## Exact verdict

**NONBLOCKING — close B1 as a source defect. No remaining blocking defect found in this frozen follow-up scope.** The production Service now issues Android's actual hide request before opening the picker/settings, rather than only clearing its own visibility flag. This accepts the source correction and host evidence, **not actual Android hide/picker/re-show behavior or release readiness**. Two P3 nonblocking refinements remain below.

Read `reports/review/ime-independent.md` first, then the five requested current files. Supporting reads were limited to the host scripts and controller/session/runner/gate/backend dependencies. No production/test/script/doc edits, device or microphone use, Android execution, full build, signing, or git operations were performed. The parent’s concurrent APK/report work was not inspected or adopted as review evidence.

## B1 closure: API usage and ordering

Locations: `android/app/src/org/llmasr/minimal/AsrImeService.java:55–70,81–105,121–130`; `android/app/src/org/llmasr/minimal/ImeController.java:28–45,51–56,78–94,98–105`.

1. Both settings and picker buttons use the same production `leaveForExternalUi` path. The Service first clears its local `visible`/`session`, then the controller invalidates the active transaction, clearing validity/preview/status and signalling its recording gate.
2. The injected hide action is **`InputMethodService.requestHideSelf(0)`**, not a fake Service flag or a host-window-token hide call. This is the appropriate public IME-side API; zero does not impose the conditional `HIDE_IMPLICIT_ONLY`/`HIDE_NOT_ALWAYS` restrictions. There is no new API-level dependency beyond the app’s minimum. The click handler runs on the main thread, and `invalidate()` completes before the hide and launch calls; no controller monitor is retained around those Android calls.
3. The hide request is invoked before `showInputMethodPicker()` or `startActivity(...FLAG_ACTIVITY_NEW_TASK)`. Null input-method manager or a `RuntimeException` during external launch cannot resurrect the old session. The controller-null fallback also requests hiding before launch. A throwing hide action is not swallowed and prevents launch; host tests do not test this, but there is no demonstrated ordinary-path API misuse here.
4. No picker-dismiss handler renews the old session. `onStartInputView()` establishes visibility and calls `renewSession()`, which obtains current editor metadata and begins a fresh policy-checked session. Ordinary hide/re-show with the same field therefore has a source recovery path when Android supplies the input-view callback. `onWindowShown()` only renders; it does not auto-recover or restore text.
5. Invalidation does not release the task owner. Before handoff it cancels the recording; after committed handoff it discards results while processing/cleanup retain ownership. Stale status/success are guarded by current session identity and revision. Neither external launch nor a new input view calls recording or commit. An explicit commit still acquires the current connection and consumes the preview once.

**Important distinction:** `requestHideSelf` is a request with no completion acknowledgement here. Java call order does **not** establish that the IME window is already hidden when the picker appears, that every Android/OEM version keeps it hidden after dismissal/reselection, or that every editor tap triggers the expected callback. The unconditional source comment at `AsrImeService.java:63–65` describes intended behavior, not measured evidence. The old defect was “only logical visible=false, no Android hide request”; that omission is now fixed. No concrete remaining normal-callback source flaw was found warranting keeping B1 open solely because device execution is out of scope.

Device acceptance must still check cancel and current-IME reselect while idle, capturing, holding preview, and processing; settings success/failure; and ordinary same-editor hide/re-show. Expected contract: no dead visible keyboard, no automatic capture/commit or old preview, and manual editor re-show creates a fresh usable session after any outstanding owner finishes.

## Follow-up findings

### N1 — prior App-contention finding resolved; P3 nonblocking previous-session wording gap remains

`AsrImeService.java:135–143` now explicitly displays a waiting message for an eligible, permitted session when the shared task belongs to the App (or another controller). This fixes the original instruction-to-record with App-owned disabled controls.

However, `ImeController.java:48,90–92` defines `ownsTask()` as any non-null recording control, not ownership by the **current session**. If the IME is invalidated during inference and a fresh input view/session opens before that old task completes, `busy()==true` and `ownsTask()==true`; the fresh session therefore shows its default “点击录音…” while record remains disabled. The message's “上一会话” branch is not reached for this same-controller case. This is presentation-only: owner exclusion and late-result rejection remain safe.

Optional follow-up: distinguish current-session task ownership from an older IME transaction when choosing status; test fresh-session rendering while the previous IME inference remains blocked. This does not reopen B1.

### N2 — evidence substantially corrected; P3 nonblocking stop assertion is weaker than its label

`tests/ImeSessionTest.java:15–40,100–108,111–136,152–159` and `docs/voice-ime.md:38–40` address the prior evidence problems:

- The unrelated `AppState` assertion is gone. Fake three-byte forwarding is explicitly labelled as reference/language forwarding, not real WAV/JNI evidence.
- Cleanup failure now throws **before** deletion, asserts the retained file exists, and explicitly removes it in test teardown. This proves injected failure retention/status/release, not an OS-denied delete or production recovery retry.
- Stale capture failure is delivered after a new session begins and cannot overwrite that session. Existing stale-success coverage remains.
- Latches block fake cleanup and establish that coordinator admission is rejected until cleanup completes.
- The external-UI cases exercise the **production controller method**, checking invalidation before the fake hide command and hide before launch, for idle, preview, capture and inference. Fresh `begin()` is explicitly a simulated input-view callback. Failed launch leaves old state invalid. These are useful order/state tests, not Service/Android picker tests.

Remaining weak assertion: `tests/ImeSessionTest.java:109–110` checks `control.stopped()` only **after** inference handoff. `RecordingControl.tryCommitInference()` itself sets `stopped=true` (`RecordingControl.java:23–25`), and fake capture returns independently of a stop request. Thus this test can still pass if `ImeController.stop()` is a no-op. Production `stop()` correctly delegates by source inspection (`ImeController.java:50`), so this is not a production blocker. Strengthen the fake capture callback to assert `stopped()` immediately after calling `stop()` and before capture release/handoff. The unused local `scenario` is merely cosmetic and not a finding.

### N3 — documentation precision resolved

`docs/voice-ime.md:23` now correctly places the noncancellable cutoff at committed post-capture processing, including WAV/model work before JNI. This agrees with `ImeController.java:78–80`, `RecordingControl.java:18–25`, and `ImeBackend.java:14–28`. No pre-JNI cancellation checkpoint was added or claimed. Optional resource-saving remains optional; ownership is not prematurely released.

## APK checker review

`scripts/check-minimal-apk.py:34–49` now finds service nodes at their actual whitespace depth, ends the subtree at a sibling/ancestor depth, requires exactly one service, and accepts the textual AAPT2 `android:exported...=true` form. This removes dependence on a fixed service indentation and the wrong boolean representation. The boundary on `true` avoids accepting `trueSomething`; the service node regex does not match similarly prefixed element names. Expected component/action/metadata/permission strings are checked within that service subtree; the existing same-process denial remains.

**No blocker in this format correction.** Python syntax passed. I did not execute this checker against the concurrently built APK/reports or independently generate an AAPT2 dump. The checker still consumes externally generated manifest/badging/signature/provenance reports; source review of its parser is not signed-APK provenance acceptance, and running it against stale dumps would not bind them to a particular APK. No current APK pass is claimed here.

## Commands, validation and limits

All shell commands ran from `/home/zhb/gitrep/llm-asr`.

1. `sha256sum android/app/src/org/llmasr/minimal/AsrImeService.java android/app/src/org/llmasr/minimal/ImeController.java tests/ImeSessionTest.java scripts/check-minimal-apk.py docs/voice-ime.md` — passed; fingerprints below.
2. **Once only:** `bash scripts/nix-env.sh apk bash scripts/test-minimal-apk.sh` — exit 0. Output:

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
PASS 160 IME production controller checks (not Android hardware/InputConnection tests)
```

Total **405 = 245 baseline + 160 IME checks**, versus 89 IME checks in the prior report. The host compile list excludes `AsrImeService`, `ImeBackend`, `AppGraph`, `ForegroundRecorder`, and `MainActivity`; no real Service/hardware/connection/native integration is established. The script writes host classes under `.work/build/minimal-apk-tests`; the Nix wrapper refreshes `.cache/nix-env/<hash>`. These authorized generated outputs are not production edits.

3. Read-only `nl -ba ... | sed -n ...` for exact locations: Service `49,151p`; Controller `28,105p`; test `88,166p`; checker `24,56p`; doc `19,55p` — passed. Other file inspection used the read tool.
4. `python3 - <<'PY' ... PY` calling `ast.parse(Path('scripts/check-minimal-apk.py').read_text())`, then `hashlib.sha256(Path(name).read_bytes())` and equality assertions against all five recorded fingerprints — passed. Output: `PASS checker Python syntax (not executed against APK/reports)` and `PASS all 5 reviewed changed files unchanged across host-test/recheck interval`.

No additional tests, full build, aapt2, checker runtime, git/index query, device, microphone, or network research command was run. The index's pre-existing state is unknown; this reviewer staged nothing. This report is the sole intentional review-file write.

## SHA-256 of all reviewed changed files

Paths are relative to the repository root. All five remained unchanged across the fingerprint/host-test/recheck interval; these identify reviewed bytes, not HEAD or an APK.

```text
4f2f8f985d7c9a3d74b36865dcd022f24d96e0b07a005d4cf0835e4cf5d17d54  android/app/src/org/llmasr/minimal/AsrImeService.java
3cb7cc2af92355f1633ed050ba2b27d934b96e83d9dfc2bc0dbd37474bec527b  android/app/src/org/llmasr/minimal/ImeController.java
b032d7231b3ec71fe0002cba615b00e542d540ce165fc2ad7fd925d92830a508  tests/ImeSessionTest.java
dfc545ffcad805e35c5a199368004c898f76934747434fd2fbd22a32deac6e03  scripts/check-minimal-apk.py
08ef9fe84f319c209f6f6aad00d4beaa5680e73206d2a8a62b8d0d06294d807c  docs/voice-ime.md
```

## Residual risks

- Actual Android hide completion, picker cancellation/current-selection, settings focus changes, and editor-driven re-show are untested. Source ordering must not be marketed as measured window behavior.
- New host coverage does not execute the Service/real backend or prove real microphone release latency, native success, InputConnection insertion, filesystem-denied deletion, or on-device privacy. The two P3 findings above do not invalidate the controller safety checks.
- Processing remains noncancellable after handoff, with driver latency, native memory/process-kill behavior and private-file recovery risks unchanged from the prior review.
- The checker’s format changes were inspected and syntax-checked, not accepted against the parent's mutable APK output. Build/source/APK binding and real-device acceptance remain separate gates.

## Structured acceptance report

This attests completion of this independent review, not release or device acceptance.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "B1 source defect closed with concrete Service/Controller paths and ordering analysis; two P3 nonblocking findings have exact paths, scenarios and actionable follow-ups; host results, five stable SHA-256 fingerprints and residual Android/APK evidence limits are recorded."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/39a429db/.work/ime-fix-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "sha256sum android/app/src/org/llmasr/minimal/AsrImeService.java android/app/src/org/llmasr/minimal/ImeController.java tests/ImeSessionTest.java scripts/check-minimal-apk.py docs/voice-ime.md",
      "result": "passed",
      "summary": "Exact five-file source fingerprints recorded."
    },
    {
      "command": "bash scripts/nix-env.sh apk bash scripts/test-minimal-apk.sh",
      "result": "passed",
      "summary": "Executed once, exit 0: 245 baseline plus 160 IME host checks = 405; not Android execution."
    },
    {
      "command": "nl -ba selected Service/Controller/test/checker/doc paths | sed -n selected line ranges",
      "result": "passed",
      "summary": "Read-only location verification; exact paths and ranges documented above."
    },
    {
      "command": "python3 heredoc: ast.parse checker source; hashlib.sha256 five files with expected-digest assertions",
      "result": "passed",
      "summary": "Checker syntax valid; all five reviewed changed files unchanged across host-test/recheck interval."
    },
    {
      "command": "Full APK build, aapt2/checker runtime, device/microphone/Android execution, git operations",
      "result": "not-run",
      "summary": "Outside scope; parent's concurrent APK reports were not used as this review's evidence."
    }
  ],
  "validationOutput": [
    "NONBLOCKING: B1 source defect closed; no remaining blocking finding in narrow follow-up scope.",
    "PASS 32+20+41+25+33+15+44+14+22+160 = 405 host checks.",
    "Production invalidate -> requestHideSelf(0) -> external launch wiring verified by source, not actual Android window completion.",
    "All five reviewed changed source fingerprints stable; Python checker syntax passed."
  ],
  "residualRisks": [
    "Android picker dismissal/current-selection, actual hide completion and fresh editor re-show remain device-unverified.",
    "P3 previous-session busy prompt gap and weak stop assertion remain nonblocking refinements.",
    "Host tests do not execute real Service/backend/microphone/InputConnection/JNI or filesystem-denied deletion.",
    "Post-handoff noncancellability, driver latency, native memory/process-kill and private-file recovery remain unverified operational risks.",
    "No current signed APK/provenance acceptance; checker consumes externally generated reports."
  ],
  "noStagedFiles": true,
  "diffSummary": "No production/test/script/doc/index edits by this reviewer. Only the required review report was intentionally written; the authorized host command generated classes and refreshed its Nix mirror.",
  "reviewFindings": [
    "No blockers: B1 closed at android/app/src/org/llmasr/minimal/AsrImeService.java:55–70 and ImeController.java:42–45; actual Android hide request precedes picker/settings launch after invalidation.",
    "P3 nonblocking: android/app/src/org/llmasr/minimal/AsrImeService.java:135–143 and ImeController.java:48,90–92 — a fresh session can retain the default record instruction while an older same-controller task owns the coordinator; distinguish session ownership for waiting status.",
    "P3 nonblocking: tests/ImeSessionTest.java:109–110 — post-handoff stopped assertion cannot detect a no-op stop because tryCommitInference itself sets stopped; assert stopped immediately inside capture after stop."
  ],
  "manualNotes": "noStagedFiles means no staging performed by this reviewer; existing index state was intentionally not queried because git operations were forbidden. B1 closure is source-level only. Read-only constraints were respected apart from the expressly authorized host-generated outputs and required report artifact."
}
```
