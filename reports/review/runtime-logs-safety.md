# Runtime logs 0.6 — current-source safety review

## Verdict and scope

**No release-blocking defect identified in the reviewed ordinary production paths**, subject to the documented single-process, single-persistence-owner, trusted application `filesDir` boundary. There are **two concrete nonblocking hardening findings** below, plus timing and test-evidence qualifications. This is a source-review conclusion, **not** build/test/device acceptance.

Read the current implementation, not the replaced partial implementation and not a blanket dirty-tree diff. Meaning/privacy authority: `docs/runtime-logs-plan.md:7-20` and `docs/runtime-logs.md:7-26`. Reviewed the full RuntimeLogEvent/Store/Persistence/Worker, InferenceAdapter/LoggingInferencePhaseListener and `native/apk/asr_jni.cpp`; traced graph, App and IME wiring, request ownership, cleanup, and relevant log UI/export ownership. Read both requested test files completely. Source was not edited; no build, test, device or network operation was performed. The parent owns concurrent full-build validation. The only written file is this requested review artifact.

All paths below are relative to `/home/zhb/gitrep/llm-asr`.

## Concrete findings

### F1 — Medium, nonblocking hardening: an escaping sink Error permanently latches store publication

**Location:** `android/app/src/org/llmasr/minimal/RuntimeLogStore.java:103-120`; relevant containment: `LoggingInferencePhaseListener.java:38-45`; evidence limitation: `tests/InferenceAdapterTest.java:258-262`.

`publish()` sets `publishing=true`, and only resets it on the normal `!pending` exit. Sink callbacks catch `RuntimeException`, not other `Throwable`s, and there is no outer cleanup of the publisher flag. An `AssertionError` from a sink therefore escapes with `publishing` still true. The inference listener catches that error, preserving the current ASR payload, but all later `publish()` calls return at line 105. Even removing the bad sink does not restart publication. Memory snapshots continue to grow/rotate, while the persistence sink no longer receives normal dirty notifications. A drain already running may incidentally save newer events; after it becomes idle, automatic persistence can silently stop. The UI can still show “no current error” because no failed save occurs.

This is a **concrete behavior of the current code**, not merely a missing test. The existing Error-sink test checks current response preservation and telemetry failure only, not subsequent successful requests or worker saves. Nonblocking classification is deliberate: the core explicitly promises isolation of sink **RuntimeExceptions**, and production registers only the bounded scheduling sink, not arbitrary UI/plugin callbacks (`RuntimeLogWorker.java:64-66`; `LogsActivity.java:113-124` polls). Reaching this with current wiring needs an unusual Error/resource fault rather than a normal filesystem IOException.

**Suggested follow-up:** make publisher ownership exception-safe and define the policy for non-RuntimeException listener failures. Add a one-shot Error sink, remove/recover it, then assert a later append is delivered and persisted and no stale publisher flag remains. Do not conflate preserving one inference return with restoring logger liveness.

### F2 — Low, nonblocking hardening: near-limit recovered sequences can exhaust future logging

**Location:** `android/app/src/org/llmasr/minimal/RuntimeLogStore.java:47-53,86-91`; numeric boundary: `RuntimeLogEvent.java:39-40`; recovery test: `tests/RuntimeLogCoreTest.java:347-356`.

Recovery rejects history that immediately overflows while rebasing current live events, but does not reserve meaningful future sequence space. For example, with no early live append, a syntactically valid single history event numbered `Long.MAX_VALUE - 2` passes the guard and sets `nextSequence` to `Long.MAX_VALUE - 1`. One live append succeeds; subsequent appends return false because the constructor rejects `Long.MAX_VALUE`. With an early live event, choose a correspondingly lower historical value to reach the same exhaustion. The existing test uses `Long.MAX_VALUE - 1` with an early append and exercises immediate rejection only.

This is not an exploitable cross-app privacy issue under the private-directory trust boundary, and natural exhaustion is not practical. It is relevant to robustness against corrupted/tampered *otherwise well-formed* persistence: the file is accepted as OK, then later live logging stops for that process. A later restart can reject the exhausted history, but that does not repair the current store.

**Suggested follow-up:** normalize restored sequences or reject implausibly high recovered sequence values with a defined recovery policy, and test repeated post-recovery appends around the boundary. Preserve immutable pre-recovery snapshots and event ordering when choosing that policy.

## Checks supporting the verdict

### Privacy and fixed vocabulary

- `RuntimeLogEvent.java:13-17,39-62` admits enums, lowercase canonical UUID shape, fixed detail labels and numeric fields only. Unknown detail strings are rejected, not partially redacted. The immutable/final event type prevents a malicious event subclass from injecting formatter text.
- Recovery does not trust previously persisted free-form text: `RuntimeLogPersistence.java:78-91,164-187` enforces byte bounds, ASCII, canonical numeric syntax, exact count/end framing, eight fields, valid enums/UUID/detail and increasing sequence. Invalid files are discarded all-or-nothing. Old permissive JSON is not imported.
- Adapter errors use fixed categories (`InferenceAdapter.java:28-45`) and parser wrapping has no original payload/cause (`:51-55`). Real upstream native/readiness exceptions are still rethrown to their existing caller; they are **not** copied into runtime logs. Existing App result reports/error UI are separate artifacts, not a claim that the whole application stores no user content (`AsrOperation.java:175-202,272-275`). IME logging shares the event store, not transcript reporting (`ImeBackend.java:25-28`; `ImeController.java:63-84,110-114`).
- Model-management log calls use operation UUID and fixed outcome codes, not filenames/provider messages (`ModelManagementController.java:151-158,238-263`).

### Filesystem safety and durability boundary

- Only `runtime-logs.json` and its fixed `.part` are managed (`RuntimeLogPersistence.java:29,55-67,114-126`). Both final and staging leaf symlinks, including dangling links, and non-regular leaf objects are refused before load/save. A suspicious `.part` also makes loading the final fail closed.
- Trusted platform parent aliases are canonicalized/pinned; the managed leaves are not canonicalized to excuse a symlink. Opens use NOFOLLOW, staging uses CREATE_NEW, and a recheck precedes atomic replacement. Only a checked regular stale `.part` is removed. There is no recursive cleanup or non-atomic rename fallback. Result clearing excludes these names (`ResultFiles.java:10-24`).
- Check/open races against a hostile same-UID process replacing directories are **not** a defended boundary; the source explicitly says so (`RuntimeLogPersistence.java:22-27`). This review does not upgrade pathname checks to descriptor-relative race-proof containment or filesystem authentication. Hard links are likewise not an authenticated-history guarantee. Neither is a blocker for the stated private application directory threat model.
- `force(true)` flushes staging before rename (`:119-126`); no parent-directory fsync establishes a power-loss guarantee. The contract promises ordinary-restart recovery and explicitly permits loss of unflushed events, not crash-proof or power-loss-proof durability. A failed regular staging file is ignored on load and replaced on a later save.

### Bounds, recovery and concurrency

- Store count is capped at 1000 (`RuntimeLogStore.java:14,29,45-64`), persistence at 1000 events and 512 KiB (`RuntimeLogPersistence.java:78-85,106-113,173-176`), and export at 1000 events/1 MiB (`RuntimeLogWorker.java:174-183`). Fixed fields bound per-event output. Saved history is merged ahead of early live events under the same sequence/insertion lock, then trimmed; old snapshots remain immutable (`RuntimeLogStore.java:67-98`).
- Publication is serialized/coalesced and sink callbacks occur outside the **store** lock (`:103-120`). This does not promise arbitrary sinks can block safely: a publisher's own append waits for synchronous sinks, and stage callbacks hold their request-local listener monitor. Current production sink only schedules asynchronous I/O.
- Worker uses one owned executor, one queued drain at most and a dirty bit, not an event-per-task executor queue (`RuntimeLogWorker.java:64-95,113-130`). Recovery precedes writes. Ordinary persistence errors are status returns, and repeated fixed failures are deduplicated before invoking the failure sink (`:98-110,122-145`). The graph's failure callback appends a fixed event; same-failure deduplication prevents an endless self-logging retry storm.
- Closing detaches without waiting for I/O and allows accepted scheduled work to drain (`:153-166`). Production graph keeps the singleton for process lifetime. Filesystem failure does not call readiness invalidation, acquire ASR ownership, or run disk I/O on JNI/UI producer threads. Rejected scheduling is visible and retries on later activity; it is not falsely counted as a successful save.

### Native exception preservation and actual phase semantics

- `PhaseNotifier` checks for an already pending exception before lookup and before each callback (`native/apk/asr_jni.cpp:36-57`). It clears only a throwable introduced after that check by optional lookup/invocation, disables subsequent callbacks after a callback throw, and frees class local references. JNI invocation is synchronous with a borrowed per-call listener; there is no global current Activity/listener.
- Native entry and `throwFailure()` preserve pending Java exceptions (`:62-68,83-88`). A failed GetStringUTFChars throws into the native catch while leaving the original JNI exception pending (`:18-25`); `throwFailure()` then does not replace it. Array allocation/copy failures similarly retain the pending throwable (`:125-128`). RAII retains mutex ownership and frees each Llm request, with the raw stream outliving Llm (`:85,93,96,129`). This is source reasoning, not an executed pending-exception probe.
- Load callbacks surround actual create/config/load (`:95-109`), not SHA verification. Response completion follows the NORMAL_FINISHED check; truncation and abnormal status throw before completion (`:111-120`). Byte-array/protocol failure can follow valid inference completion but still cannot produce REQUEST_SUCCESS (`InferenceAdapter.java:35-47`). Missing/duplicate/wrong-thread/stale phases invalidate optional telemetry, not a correct result (`LoggingInferencePhaseListener.java:61-103`).
- **Timing qualification:** these are real boundary wall/monotonic observations, not timings reconstructed from the returned header or a mislabeled SHA duration. They are not zero-overhead engine microbenchmarks. Synchronous publication following the start callback contributes to the listener duration (`LoggingInferencePhaseListener.java:66-83`); native `begin/loaded/done` also include portions of notifier overhead (`asr_jni.cpp:92-123`). Accordingly, unchanged payload means raw transcript/input/settings and protocol interpretation remain unchanged if logging fails—not bit-for-bit equality of freshly measured timing header values across instrumented/uninstrumented runs. Quantifying callback overhead requires runtime work outside this review.

### App/IME wiring and no false success

- Graph publication is synchronized/volatile and supplies one shared store/worker to both call paths (`AppGraph.java:20,32-59`; `AsrOperation.java:65-67,170-174`; `ImeBackend.java:11-14,25-28`). IME can install the graph first (`AsrImeService.java:35-36`). Manifest specifies no secondary process, keeps LogsActivity non-exported, and adds no storage/network permission (`android/app/AndroidManifest.xml:3-20`).
- Existing UUID/owner/WAV cleanup remain outside the adapter (`RequestRunner.java:49-61,70-94`; `TaskCoordinator.java:44-63`; `ImeController.java:71-94`). Logging does not take that owner. REQUEST_SUCCESS deliberately means validated native response, not report save or editor commit, matching `docs/runtime-logs.md:11` and `RuntimeLogText.java:19`; App reporting and IME preview/commit happen afterwards. APP_READY is described as services initialized, not a successful model load or completed startup cleanup (`RuntimeLogText.java:9`; `AppGraph.java:54-59`).
- Main log navigation requires explicit recording discard or stopping/staying; model-page-owned work blocks that page's log navigation (`MainActivity.java:184-195`; `ModelManagementActivity.java:76-80`). Native work can continue while viewing logs.
- The relevant export path captures an immutable click-time ticket, writes on a separate process-owned lane and only completes successfully **after provider close** (`LogsActivity.java:71-82`; `LogExportController.java:62-104`; `LogExportPage.java:24-47`). Worker export helpers themselves do not own close, as documented (`RuntimeLogWorker.java:169-187`). The reviewed controller, not the legacy helper's return, owns actual UI success.

## Test evidence and nonblocking validation gaps

These are observations of test **source**, not claims that tests passed during this review.

1. `tests/RuntimeLogCoreTest.java:63-150` covers typed rejection, deterministic timestamps/rollback durations, immutable bounded snapshots, actual concurrent append/coalescing and RuntimeException/reentrant sinks. It does not cover post-Error publication recovery (F1).
2. `:153-247` uses real temporary files/symlinks, every truncated prefix of a valid sample, framing/private-data corruption, oversize, stale `.part`, final/part symlinks and directories, parent alias pinning, unchanged final after failed staging, and permission denial. Permission denial expressly requires **non-root execution** (`:239-245`). This is not Android libcore/filesystem validation, a concurrent hostile path-replacement test, injected force/rename failure, or crash/power-loss recovery.
3. `:250-357` exercises asynchronous first recovery, early-event rebasing, blocked persistence plus 10,000 appends, bounded scheduling, close/drain, rejection/retry, real owned executor and failure deduplication. The overflow test is narrower than F2. There is no claim that every possible scheduling/close interleaving is explored.
4. `:360-409` verifies snapshot bytes/caps, write/flush failures and an illustrative try-with-resources close-owner pattern. That illustrative pattern alone would not prove Android export success handling; the current production controller was inspected separately above.
5. `tests/InferenceAdapterTest.java:80-232` exercises the actual shared pure-Java adapter with a fake native port, real ModelAccess cold/cached SHA behavior, failure identity including LinkageError/VM-error objects, null/malformed responses, phase failure ordering and malformed/stale/wrong-thread callbacks.
6. `:234-296` simulates native callback-fault isolation in a Java loop, checks current payload preservation under logging faults, and persists/exports actual adapter events for privacy checks. It does **not execute JNI**, cannot prove JNI ExceptionClear behavior, and does not measure true engine load/response or callback overhead. `:328-359` is substring/order checking of source, not ABI loading, JNI local-reference/CheckJNI behavior, or compiled MNN execution.
7. `:298-326` exercises real RequestRunner owner/cleanup boundaries with fabricated reports/state for App and IME source enums. It does not instantiate Android AsrOperation/ImeBackend/Service/InputConnection. Real editor commit, permission/capture lifecycle, screen rotation/picker delivery, UI responsiveness and process-death recovery remain device/runtime checks. The docs appropriately acknowledge this (`docs/runtime-logs.md:30-31`).
8. Parent full build/native compile/package checks and MNN object-fingerprint validation remain external evidence. This reviewer did not inspect or infer successful outputs from the concurrently running build.

## Acceptance attestation

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Complete current-source review above separates no ordinary-production blockers from F1 medium nonblocking publisher-latch hardening at RuntimeLogStore.java:103-120 and F2 low nonblocking near-limit recovery hardening at RuntimeLogStore.java:86-91, with concrete mechanisms, paths, tests and residual limits."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/fe64f868/.work/runtime-logs/review-safety.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "Read-only ls/find/rg and nl -ba/sed/cat inspections of the documented current source and test files",
      "result": "passed",
      "summary": "Inspected complete requested core/native/adapter/test files and traced relevant graph, App, IME, ownership and UI/export wiring."
    },
    {
      "command": "wc -l docs/runtime-logs*.md android/app/src/org/llmasr/minimal/{RuntimeLog*,InferenceAdapter*,LoggingInferencePhaseListener*,App*,*IME*} native/apk/asr_jni.cpp tests/{RuntimeLogCoreTest,InferenceAdapterTest}.java",
      "result": "failed",
      "summary": "Inventory returned available line counts but uppercase *IME* matched no file; actual ImeBackend/ImeController/AsrImeService were subsequently located and inspected. No source change or validation failure implied."
    },
    {
      "command": "Build, test execution, device, network and git mutation operations",
      "result": "not-run",
      "summary": "Prohibited for this read-only frozen-source review; parent owns concurrent full build."
    }
  ],
  "validationOutput": [
    "Source review found no release-blocking issue in ordinary production paths under the documented single-process/single-worker/private-directory boundary.",
    "Fixed vocabulary, strict bounded recovery, static final/part symlink refusal, coalesced background persistence, pending JNI exception preservation, real MNN boundaries and validated-response-only success were traced to current source lines.",
    "Both requested test files were read completely; no test pass count or build/device success is attested."
  ],
  "residualRisks": [
    "F1: A non-RuntimeException sink failure can leave publication latched and prevent future automatic persistence while inference payload remains usable.",
    "F2: Otherwise valid near-Long.MAX_VALUE historical sequences can exhaust future live appends without immediate recovery failure.",
    "JNI pending-exception preservation, ABI/MNN runtime status, actual timing overhead and native payload behavior require runtime validation; fake-port and substring tests are not that evidence.",
    "Android filesystem/SAF/IME/lifecycle behavior, force/rename/crash failure windows and parent full-build/package/fingerprint results were not executed or independently attested here.",
    "Same-UID hostile concurrent directory replacement and power-loss durability are outside the stated path/durability guarantees."
  ],
  "noStagedFiles": true,
  "diffSummary": "Only this requested report artifact was written. No source or test edit, no staging, and no blanket comparison against the pre-existing dirty tree.",
  "reviewFindings": [
    "No release-blocking defect identified in reviewed ordinary production paths under the documented trust boundary.",
    "Medium, nonblocking F1: android/app/src/org/llmasr/minimal/RuntimeLogStore.java:103-120 — escaping sink Error leaves publishing=true and stops later publication/automatic persistence.",
    "Low, nonblocking F2: android/app/src/org/llmasr/minimal/RuntimeLogStore.java:86-91 — accepted near-limit recovered sequence can exhaust subsequent appends; RuntimeLogEvent.java:39 rejects Long.MAX_VALUE.",
    "Nonblocking evidence qualification: tests/InferenceAdapterTest.java:234-252,328-359 simulates callback faults and checks source strings; it does not execute JNI or measure engine timing."
  ],
  "manualNotes": "noStagedFiles attests that this reviewer staged nothing, not that the pre-existing index was audited or empty. Repository-wide dirty/staged state was intentionally not inferred. The source stayed untouched, only the authoritative report was written. Parent must combine this review with its own full-build evidence and outstanding runtime/device validation before product acceptance."
}
```
