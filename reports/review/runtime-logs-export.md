# Runtime logs 0.6 export/UI — read-only review

## Verdict and scope

**No release-blocking export correctness, privacy, Activity-ownership, or navigation defect identified in the current implementation.** Two concrete nonblocking issues are listed below: a maintenance-log terminal-event gap and a transient/misleading picker error message. The former is diagnostic correctness, not merely visual polish; neither compromises export authorization, result isolation, or model ownership.

Reviewed the frozen working-tree implementation in `/home/zhb/gitrep/llm-asr`, particularly `LogsActivity`, `LogExportController`, `LogExportPage`, `RuntimeLogText`, `AppGraph`, launcher/maintenance navigation, maintenance logging, `tests/LogExportTest.java`, and the source/package gates. Supporting reads covered the immutable event/store, snapshot encoder, application-context adapter, runner/coordinator, page ownership, manifest, and report binding. Paths below are repository-relative; line numbers refer to the reviewed source, not an attribution to this change's diff. Pre-existing dirty work is **not** attributed wholesale to this feature.

This reviewer ran **no tests, builds, device commands, or network operations**, and made no source edits. The parent reports a complete host pass, including **96 export checks**; that is parent-provided evidence, not a test execution by this reviewer. Parent builds were concurrent; no claim is made here that their final artifacts passed. The only written file is this report.

## Concrete findings

### F1 — Medium / nonblocking: synthesized maintenance failure has no terminal runtime-log event

**Locations:** `android/app/src/org/llmasr/minimal/ModelManagementController.java:151–158, 238–263, 265–280`; supporting dispatch: `RequestRunner.java:49–75` and `TaskCoordinator.java:50–62` in the same source directory.

`pending()` appends `MODEL_IMPORT_STARTED`, `MODEL_VERIFY_STARTED`, or `MODEL_DELETE_STARTED` during admission, before executor submission. Normal completion emits the matching completed/cancelled/failed event only in `terminal()`. When an admitted task never runs (for example, executor rejection), or exits before `terminal()`, `finish()` synthesizes a FAILED maintenance state and releases the operation but does **not** log the corresponding failure. Thus the exported diagnostic history can contain a permanent STARTED-only operation while the maintenance UI correctly reports failure and the owner is free. This is a source-traced failure path, not an executed reproduction.

**Impact:** incomplete diagnostic lifecycle and potentially misleading investigation of a failed model operation. It does not report false success, leak exception text into logs, mutate the transcription result, or retain the owner. The ordinary production executor is process-owned and not normally shut down, so rejection is exceptional; hard abnormal exits are also exceptional. Accordingly this is not a release blocker for the reviewed export/UI safety contract.

**Follow-up:** append a fixed-vocabulary, exactly-once terminal failure when `finish()` itself supplies the terminal state, avoiding duplication when `terminal()` already ran. Add a logging-enabled maintenance test for admitted executor rejection and abnormal exit. The reviewed export test does not exercise maintenance logging, and the test-source search found no direct maintenance STARTED/terminal sequence assertion; an all-enum text-mapping check does not cover this path.

### F2 — Low / usability polish: picker launch failure is overwritten with an inaccurate expiration reason

**Locations:** `android/app/src/org/llmasr/minimal/LogsActivity.java:74–78, 109–110, 119–124`; `RuntimeLogText.java:47`.

If `startActivityForResult()` throws, the handler displays “无法打开保存选择器” but calls `page.abandon(true)`, which leaves the controller in EXPIRED. The next foreground refresh, within roughly one second, unconditionally replaces the actionable launch-failure message with “页面已重建或离开，旧选择失效”. No rebuild or departure need have occurred. Other local-only status messages and the process-recreation notice at line 56 can likewise be replaced by the global status poll.

**Impact:** users can miss the actual cause and see an incorrect explanation. Reservation cleanup is correct, retry remains possible, and no provider write occurs, so this is polish rather than a safety blocker.

**Follow-up:** represent picker-unavailable as a fixed safe status, or retain a local notice until a relevant new action/state transition. Keep provider exception text out of the display. A future Activity/UI test should check message persistence across a refresh tick.

## Safety and behavior review evidence

| Area | Assessment and concrete evidence |
| --- | --- |
| Picker-time immutable snapshot | **Satisfied by source.** `LogsActivity.java:71–77` snapshots before launching the picker. `LogExportController.java:32–39, 62–68` makes a defensive unmodifiable copy and caps admission at 1000 events. `RuntimeLogEvent.java:11–12, 18–27, 42–56` is final with final primitive/enum/String fields; event contents cannot change underneath a ticket. The worker encodes `ticket.snapshot`, never a fresh live-store snapshot (`LogExportController.java:89–91`). Recovery rebasing creates replacement events, not mutations of captured events (`RuntimeLogStore.java:71–95`). |
| Stale, duplicate, deferred, cancelled, and rotated results | **Fail closed by source.** `LogExportPage.java:18–47` requires a live page, its ticket, matching request code, and a first callback. A URI received while paused is held only in the page and consumed on foreground reentry; null results cancel without I/O. Consumption clears the page's ticket/URI before submission. Destroy expires a selecting/deferred ticket and cannot revive it. `LogsActivity.java:37, 80–94` creates a new page on recreation and saves neither URI nor ticket. Controller request codes are never reused within the process and fail closed above 65535 (`LogExportController.java:45, 63–66`). A stale result cannot consume a newer ticket in the same process. |
| Background work / Activity leak | **No process-worker-to-Activity path found.** The production pool is asynchronous, maximum one worker, queue capacity one (`LogExportController.java:54–58`). The provider backend captures the constructor's `OperationContext`, not a page callback (`AppGraph.java:121–124`); `OperationContext.java:21–27` normalizes it to `getApplicationContext()`. `LogsActivity` supplies only immutable events and a URI. Its polling runnable is static with a weak Activity reference and is removed on pause/destroy (`LogsActivity.java:89–94, 115–125`). No UI callbacks are passed to the export worker. |
| One owner through close | **Satisfied by source.** Admission reserves one process slot during SELECTING; submission moves to WRITING under the same controller monitor. A page can abandon only SELECTING, never WRITING (`LogExportController.java:62–82`). Backend open, encoding/write/flush, and provider close all finish before success; completion and slot release occur in `finally` (`85–105`). Competing exports reject instead of accumulating tickets/tasks. Activity destruction after submission cannot prematurely release the slot. This lane is separate from both the ASR coordinator and persistence executor. |
| Failure-safe result | **Satisfied by source.** Null streams, checked I/O, runtime/provider/security failures, and linkage failures result in FAILED with zero success bytes; executor submission failure also releases the reservation (`LogExportController.java:80–104`). Other hard Errors may propagate, but the worker's finally releases the slot; this is not a promise that the process survives a VM error. State contains only IDs, phase, and byte count (`20–30`), never URI/payload/exception text. `RuntimeLogWorker.java:168–187` encodes bounded UTF-8, enforces a 1 MiB cap, then writes/flushes. No silent retry, file deletion, persistable URI permission, or success-before-close path was found. External partial-file/cloud-sync warnings are explicit (`LogsActivity.java:57`, `RuntimeLogText.java:43–46`). |
| Private ASR result isolation | **Satisfied by source.** The Logs Activity has no `graph.appState()` access; its body is only typed log events. The export controller has no ASR state/report dependencies. Maintenance production wiring selects `PrivateState` and `NO_REPORTS` (`ModelManagementController.java:28–31, 282–291`) rather than AppState; `RequestRunner.java:64–65, 79–98` preserves the maintenance no-result/no-report policy. Global log-export status can be seen by another log page, but it is only safe process-wide metadata, not transcription text or a provider target. |
| Recording / maintenance navigation | **Safe conservative policy by source.** The launcher log button is outside the ASR-busy button list (`MainActivity.java:71–74`). `openLogs()` permits committed inference but asks for explicit consent during active capture; stop remains on the recording page and discard is explicit (`183–195`). It does not silently navigate through `onPause()` cancellation. Maintenance log navigation refuses while that page owns an active operation (`ModelManagementActivity.java:76–81`), including its cleanup window because `ModelPageSession.java:38–40` checks the actual control owner. This avoids navigation-induced maintenance cancellation; it intentionally prevents watching that same page's active operation from the log page. |
| Maintenance telemetry privacy and meaning | Normal maintenance terminal events follow real operation and final inspection outcomes (`ModelManagementController.java:114–143, 213–257`). INSPECT is excluded. Log append uses only kind, SHARED, internal operation UUID, and fixed `ok/cancelled/failed`; raw exception text in maintenance state is not passed to the log store. Optional telemetry faults are isolated (`261–263`). F1 is the exception to lifecycle completeness. Maintenance events currently do not supply `elapsedNanos`, so their timestamps must not be advertised as monotonic operation durations. |
| Timestamps and event meanings | `RuntimeLogStore.java:125–137` renders date, milliseconds, explicit local UTC offset, and supplied monotonic durations as numeric `elapsed_ms`; unknown durations are omitted. It does not subtract recovered process-local times. `RuntimeLogText.java:10–11, 19–25` correctly distinguishes readiness/access (possibly cached SHA proof), full SHA verification, response parsing versus report/editor success, and optional telemetry failure versus ASR failure. UI pairs Chinese meanings with canonical event fields (`LogsActivity.java:101–102`); TXT export is canonical event lines, not the Chinese UI description. `AppGraph.java:56–58` emits STARTUP/READY after startup initialization, so these are initialization markers, not a measured process-launch interval. |

## Tests and compiled-package gates

- **`tests/LogExportTest.java:21–44`** exercises the actual pure-Java production page/controller: foreground admission, frozen snapshot and unmodifiable list, paused result deferral, duplicate suppression, destroy during write, second-page busy rejection, real exported content, unique request codes, stale callbacks, cancellation, and destruction of deferred selection.
- **`tests/LogExportTest.java:45–83`** covers null open; I/O, security, linkage and runtime failures; flush/close failure; fatal close Error; executor rejection; and a latched background close that must keep the export slot busy until released. `84–85` checks all event meanings and the telemetry caveat. These are valuable executable host tests, **not** Android callback, provider-grant, UI-thread, or leak instrumentation. The default production pool itself is source-reviewed; most test work uses injected executors. Parent-reported result: 96 checks passed; not rerun here.
- **`tests/RuntimeLogCoreTest.java:63–102`** contains privacy, immutability, offset/millisecond formatting, and wall-rollback-versus-monotonic-duration assertions. Reviewed as supporting source evidence only.
- **`tests/model_android_source_test.py:9–24, 49–74, 76–102`** checks version 0.6/code 6, RECORD_AUDIO-only permissions, internal model/log Activities, shared process, wiring strings, and mutations of the real extracted checker clause. The isolated log-node negatives include exported/filter/process changes, removal, and duplication. Its own disclaimers correctly distinguish source/synthetic fixtures from compiled APK verification. Wiring assertions are textual: they are not a control-flow or lifecycle test.
- **`scripts/check-minimal-apk.py:14–38, 55–77`** checks a bound signed APK/report set, archive/ABI/assets, version 0.6/code 6, API29/35, RECORD_AUDIO-only permissions, no independent process, three Activity nodes, exactly one matching internal model page and log page, and absence of intent filters on both. **`scripts/build-minimal-apk.sh:68–74, 87`** generates actual post-signing aapt/apksigner reports, binds them, then invokes the checker; **`scripts/apk_report_binding.py:10–26`** hashes the APK and reports to reject accidental stale/mixed evidence. This is substantially stronger than the source fixture alone.
- Package-gate limits remain explicit: the checker consumes trusted tool reports and their binding, not an independently reverified signature; an actor rewriting both artifacts and binding is outside its integrity model. `classes.dex` presence (`check-minimal-apk.py:21`) is not an independent assertion about every expected class's implementation. Activity identification uses class-name suffix matching (`66–67`), not exact fully qualified names; the current manifest has the intended exact names. No current unsafe declaration was found. Final build/gate execution results must come from the parent's completed build, not this read-only review.

## Residual risks / validation still needed

1. **No device validation:** confirm picker result delivery before resume; rotation while picker is open and while a write is running; process recreation; duplicate/stale callbacks; finish/back/home; and temporary URI-grant behavior with local and cloud document providers. The no-stale-write conclusion above rests on source and host page-model behavior, not Android runtime observation.
2. **Blocked provider:** open/write/flush/close may block indefinitely. The design intentionally holds one bounded export owner and does not release early or launch replacements; there is no timeout/cancellation guarantee. This protects ownership and ASR independence, but can make further log exports unavailable until provider completion or process restart. No foreground service/process-survival guarantee exists; partial external output may remain after termination.
3. **UI/device polish:** validate API29/35 insets, font scaling, multiline buttons/disabled affordance, selectable long text, accessibility, scroll position under updates, and responsiveness with 1000 retained events. `LogsActivity.java:98–103` reformats the entire changed snapshot on the main thread (bounded, and once per polling tick, but not performance-measured). F2 remains a concrete status-message issue.
4. **Time-zone semantics:** immutable snapshot means immutable event data, not pre-rendered bytes. Local time-zone conversion occurs at render/export time (`RuntimeLogStore.java:127–129`), and the event-time zone is not stored. Changing the system zone during a picker can change the textual local representation without changing the event instant. Maintenance events lack explicit monotonic durations; do not infer those by wall-time subtraction.
5. **Availability limits:** a live paused picker intentionally retains the single reservation; only cancellation/destroy/submission releases it. Request-code exhaustion fails closed after 57,536 admitted picker tickets in one process, but is currently explained only by the generic unavailable/busy text. These are boundedness tradeoffs, not discovered privacy violations.
6. **Coverage:** maintenance telemetry rejection/finalization coverage should be added for F1. Activity lifecycle, provider I/O thread enforcement, actual Activity collection, and final compiled-package success remain separately unvalidated here. The parent must record its final build results; its already-reported host pass is not device evidence.

## Acceptance report

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Concrete findings F1 (medium, nonblocking) at ModelManagementController.java:151-158,238-280 and F2 (low, usability) at LogsActivity.java:74-78,109-124; source-line safety matrix, test/package-gate review, and six explicit residual-risk categories are included."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/d73c3154/.work/runtime-logs/review-export.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "Read-only source inspection via functions.read/grep/find/ls and nl -ba/sed",
      "result": "passed",
      "summary": "Reviewed requested implementation, tests, navigation, immutable event/encoder/context/ownership support, manifest and package-report binding; did not execute inspected scripts."
    },
    {
      "command": "GIT_OPTIONAL_LOCKS=0 git diff --cached --name-only",
      "result": "passed",
      "summary": "Empty output: no staged files at inspection time. No index writes performed."
    },
    {
      "command": "Tests, builds, package checker execution, device checks, and network operations",
      "result": "not-run",
      "summary": "Prohibited for this reviewer; parent reports complete host pass including 96 export checks and owns concurrent build validation."
    }
  ],
  "validationOutput": [
    "Read-only source review: no export authorization, privacy, owner-through-close, Activity-retention, or navigation blocker identified.",
    "Parent-provided host evidence: full host pass including 96 LogExportTest checks; not rerun or independently attested by this reviewer.",
    "No device result or completed concurrent-build result is claimed."
  ],
  "residualRisks": [
    "Android lifecycle/result ordering, rotation/process recreation, temporary provider URI grants, and actual Activity collection remain device-unvalidated.",
    "A provider can block indefinitely while one bounded export slot remains held; process termination can leave partial external output.",
    "F1: maintenance admitted-but-never-run/abnormal finalization can leave STARTED without a matching terminal log event.",
    "F2: periodic refresh replaces picker launch failure with a misleading expiration message.",
    "Long-log rendering/accessibility/insets need device validation; time-zone formatting happens at render/export time and maintenance durations are not explicitly supplied.",
    "Parent must record final compiled-package/build results; source fixtures and host checks are not device or final-artifact evidence."
  ],
  "noStagedFiles": true,
  "diffSummary": "Report-only write. No source/test/build/network changes; pre-existing working-tree changes were neither modified nor attributed to this feature.",
  "reviewFindings": [
    "No release blockers found in the reviewed current export/UI safety implementation.",
    "Medium, nonblocking: android/app/src/org/llmasr/minimal/ModelManagementController.java:151-158,238-280 — synthetic finalization failure does not emit a matching terminal runtime-log event.",
    "Low, usability polish: android/app/src/org/llmasr/minimal/LogsActivity.java:74-78,109-124 and RuntimeLogText.java:47 — polling overwrites picker-unavailable explanation with generic EXPIRED text."
  ],
  "manualNotes": "Authoritative report path used. Review scope is the frozen current implementation, not ownership of all dirty work. Parent host pass is acknowledged, not rerun; concurrent build and device validation are outside this review."
}
```
