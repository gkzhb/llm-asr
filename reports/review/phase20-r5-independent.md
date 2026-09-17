# Independent read-only review — repaired R5 TXT export

## Scope and disposition

Reviewed the 14 files named by `.work/refactor-phase20-r5/frozen-sha256.json`, with `docs/android-r5-r6-validation.md` and `.work/refactor-phase20-r5/parent-rejection.md` used as **requirements, not evidence of correctness**. Compared only the relevant baseline AppState/AsrOperation/AppGraph/MainActivity/ResultFiles files. Read the immediately connected RequestRunner, AppRequestPolicy, TaskCoordinator and OperationContext to trace the real owner/context wiring; no broad historical review or R6 implementation audit.

**Disposition: core clear-epoch/admission repair is sound by source inspection, but not an unconditional acceptance. Two concrete medium-severity defects remain, and coverage gaps are listed separately.** No demonstrated high-severity clear-before-admission bypass was found in the frozen sources. This is static review, not a claim of Android runtime validation.

No tests, builds, Java compilation, device commands or application code were executed. The parent's host/javac green observation is acknowledged, not independently reproduced and not Android evidence. Only this report was written, at the authoritative artifact output path.

## Concrete findings

### R5-IR-1 — Medium / P2: a blocked write can indefinitely retain the obsolete QUEUED/revocable UI status

**Locations:** `android/app/src/org/llmasr/minimal/TextExportController.java:109-112,118-128,156-160`; `android/app/src/org/llmasr/minimal/MainActivity.java:123-125,133-156`.

**Trigger / source trace:**

1. A picker callback is consumed on resume and `admit()` installs QUEUED, submits the worker and invalidates the UI.
2. The main-thread refresh runs before the worker's final admission and renders QUEUED. This is possible with the real asynchronous executor, including `onResume()`'s immediate `refresh()`.
3. The worker installs WRITING at line 124, then enters an arbitrarily blocked `backend.open()` (or write/close).
4. There is no invalidation for this transition and no foreground polling. If no unrelated AppState/owner/readiness event occurs, MainActivity continues displaying “等待写入；清除结果或关闭页面可撤销尚未开始的写入” until completion, although this particular export has crossed the irreversible boundary.

The slot remains correctly held, so this is not simultaneous provider IO. It is an incorrect privacy/revocation presentation during precisely the slow-provider case that motivated R5. The WRITING wording exists but is not reliably delivered. The existing notification-count assertion only requires begin + enqueue + completion; it does not cover the transition.

**Minimal repair:** retain the no-arbitrary-observer-between-admission-and-open constraint. Add lifecycle-scoped, coalesced, bounded foreground re-rendering while export state is busy (one pending Handler callback, removed on pause/destroy and stopped at terminal), so a blocked open is eventually rendered as WRITING. Alternatively provide an asynchronous invalidation mechanism that cannot run arbitrary listeners inline at the admission/open boundary. Do not fix this by calling the existing unrestricted listener loop synchronously between admission and provider open.

**Regression needed:** render QUEUED before releasing a worker-start gate, then block real backend open and verify the foreground status advances to WRITING without another AppState event. Assert observations outside the listener.

### R5-IR-2 — Medium / P2: fatal close errors are swallowed when a normal write failure is already primary

**Location:** `android/app/src/org/llmasr/minimal/TextExportController.java:128-136`.

**Trigger / source trace:** `ResultFiles.writeText()` throws `IOException` (or `RuntimeException`); the same stream's `close()` then throws `InternalError`, another `VirtualMachineError`, `ThreadDeath`, or `AssertionError`. Java try-with-resources keeps the write exception primary and attaches the close error as suppressed. The catch at line 134 catches and discards the primary exception and, with it, the fatal close error. This differs from a fatal primary open/write/close error, which escapes after `finally` completes.

The slot is released and status becomes FAILED, not false success. The defect is failure to preserve fatal propagation, not failure to attempt close. It is a rare compound-error path, but concrete Java control flow and directly relevant to the requested fatal/unwind audit.

**Minimal repair:** after cleanup, propagate fatal non-Linkage `Error` instances even if they are suppressed on an otherwise recoverable failure; preserve the controller's intended recoverable treatment of ordinary IO/runtime/LinkageError. This can be implemented by explicit primary/close exception arbitration or by inspecting suppressed exceptions before discarding a recoverable primary. Keep the existing `finally` completion/release and fixed public status; do not emit raw provider diagnostics.

**Regression needed:** ordinary write IOException + fatal close Error, asserting the exact fatal reaches the harness, close is attempted exactly once, FAILED is published, and the slot becomes available only after close exits. Also cover fatal primary + ordinary close failure.

## Contract audit and positive source evidence

All paths below are under `android/app/src/org/llmasr/minimal/` unless otherwise specified.

| Area | Source-level conclusion |
| --- | --- |
| Atomic text/edit snapshot | `ResultState.java:23-33` synchronizes text and revision. `MainActivity.java:230-239` captures one Snapshot and uses its text/revision together. `AsrOperation.java:120-126` compares/applies the revision inside the original maintenance runner. A→B→A, clear→same text, and new inference cannot make an old revision current. The Snapshot overload rejects another ResultState owner. Production uses the revision overload on the graph's single AppState, not a foreign snapshot. |
| Begin versus clear/new result | `TextExportController.java:72-85` holds controller lock then ResultState monitor through snapshot and SELECTING installation. `ResultState.clear()` uses that same monitor. Thus begin precedes clear and is invalidated, or follows clear and sees empty/new text. New inference/edit increments revision but not clear epoch, intentionally preserving a previously selected immutable export. |
| Selected clear | `expirePicker()` (`64-71`) retires SELECTING lazily; `admit()` additionally validates under the ResultState monitor (`99-107`). An old cleared ticket cannot reach provider open, even if a newer result has the same text. Lazy expiration does not emit its own notification, but production `AppState.clearText()` notifies MainActivity, whose refresh calls controller state. |
| Deferred callback clear | `TextExportPage.java:47-74` only saves the target while backgrounded; resume routes it through the same production `admit()` epoch validation. Destroy discards it. No alternate deferred-write bypass exists. |
| Queued clear/destroy | QUEUED is not write admission. `write()` checks both revocation and clear epoch under the two locks before WRITING (`118-125`). `revoke()` does not free QUEUED (`87-94`); page retains ticket identity after consumption (`67-74`). Revoked work holds the slot until its runnable reaches cleanup, preventing replacement enqueues while stale work remains outstanding. |
| Clear versus final admission | The linearization point is the WRITING assignment under the ResultState monitor, not executor submission and not return from provider open. Clear wins first ⇒ zero provider open. WRITING wins first ⇒ clear/destroy cannot retract the frozen write. There is no observer, IO or untrusted callback inside the synchronized region or inserted between release and open. A scheduler gap before the physical call is possible; the documented contract explicitly makes admission the boundary. |
| Bounded independent slot | One `active` ticket under controller lock, plus production executor maximum one worker and queue capacity one (`43-60`). Open/write/close all run outside the owner and the state locks. `complete()` frees the active ticket only after the stream scope unwinds, including close failure (`128-143`). Final observer notification is after IO cleanup; a successor can be queued during that notification, but production still has one worker and a bounded queue. No overlapping external stream lifetime was found. A permanently blocked provider intentionally retains the export slot indefinitely, not the ASR owner. |
| Duplicate/foreign callback | Page accepts only its current request code once (`47-59`); controller requires exact ticket identity and SELECTING (`99-107`). Codes increase without recycling and fail closed after 65534. Old page callbacks cannot consume a new in-process page's ticket. A cancellation callback revokes only the matching ticket. |
| Page recreation/destruction | MainActivity constructs a new page (`36`), connects true/false foreground (`155,166`), and destroys the page (`265-267`). No body/URI/ticket serialization or restoration was found. Selected/deferred work is discarded, queued work is revocable, and WRITING survives page loss. AppGraph's application-context resolver, not Activity, is retained by the worker (`AppGraph.java:37-43`; OperationContext.Android). |
| Real notification wiring | `MainActivity.java:133-167` actually registers/removes the export listener. The listener is a weak-target invalidation posted to the main Handler, not worker-thread view mutation; queued renders check foreground/destroyed/finishing. Resume rereads current state, so completion while paused is not lost permanently. R5-IR-1 remains for the unnotified WRITING transition. |
| Rejection and ordinary errors | Submission RuntimeException/LinkageError rolls back to FAILED; other submission Error completes then rethrows (`109-111`). Open/null stream/write/close IO/runtime/LinkageError produce fixed FAILED. Success is installed only after close. Primary fatal worker Error unwinds close and completes via finally before propagation. See R5-IR-2 for compound fatal close. |
| Observer isolation | Export observers are called outside locks and observer exceptions are swallowed (`149-154`); a throwing observer does not replace the terminal state or skip cleanup. This also swallows VM/ThreadDeath errors from observers, unlike TaskCoordinator's fatal-observer policy. That is the explicit implementation behavior, not proof of fatal delivery. A non-returning arbitrary observer can block its invoking thread; the actual Activity observer is a short Handler invalidation. |
| Status separation | Controller has no AppState status dependency. MainActivity renders export status separately and uses Toast for launch/begin issues (`90-102,123-125`); export button is not in the ASR-disabled `buttons` list. No new export failure path writes ASR status. |
| Original shared owner | `AppGraph.java:41,54-60,119-123,131` passes the same original coordinator to AsrOperation and model management; `AsrOperation.java:51-56,106-127` retains runner admission, preflight and maintenance ownership for local clear/edit. TXT IO no longer submits a maintenance task. Baseline comparison confirms the owner was not replaced by a per-export or per-clear owner. |
| Privacy and cap | Controller State contains phase/IDs/count, not body or URI. No raw provider exception/log, target-derived filename, background retry, persisted grant or ticket serialization was found in this path. Actual output uses `ResultFiles.writeText()` and UTF-8. `begin()` rejects length >100000 before picker/open; ResultFiles retains its unchanged >100000 write guard. Limit is Java UTF-16 code units, as before, not Unicode code points or bytes. Success byte count is UTF-8 length. Cloud-provider networking and partial external files are disclosed. |

### Important boundary, not overclaimed

`AsrOperation.clearResults()` invokes `appState.clearText()` in the maintenance **worker body**, after RequestRunner preflight (`AsrOperation.java:106-117`; `RequestRunner.java:64-68`). A successful submission/click is not itself the epoch increment. An export can win final admission before that body runs; a preflight failure can prevent clear from occurring at all. This preserves the original runner/owner structure. This review's no-open conclusion means **clear epoch changed before final write admission**, not “the user tapped Clear before any provider call.” The UI promises revocation of not-yet-started writes, and device acceptance should check this asynchronous boundary rather than treating click time as proven linearization.

## Test quality and missing tests (separate from defects)

### Useful existing production coverage, inspected but not executed

- `tests/TextExportSafetyTest.java:8-18` invokes the actual production ResultState/controller selected-clear path and checks zero provider opens outside all callbacks. It is not a replacement state machine.
- `tests/TextExportTest.java:24-52` exercises actual ABA, immutable snapshot, selected/queued clear, duplicate controller admission and slot retention. `53-72` uses the actual page for foreground/defer/resume/destroy and foreign/recreated callback scenarios. `77-107` covers primary failures, close failure, rejection, stale revoke, code exhaustion and over-limit text.
- `TextExportTest.java:116-139` uses bounded latches and actual backend open/write/close boundaries, releases gates in finally, captures worker errors, and asserts terminal state after worker completion. This meaningfully tests stream ownership/cleanup.
- Export observer assertions at `TextExportTest.java:109-113` are **outside** the listener; the listener only records notice count/reentry. No swallowed assertion substitutes for those checks. Related frozen owner/model observer tests similarly record observations for outside assertions.
- `tests/text_export_source_test.py` checks real MainActivity source and has six negative fixtures for missing lifecycle/observer/snapshot wiring. These are useful lexical guards, not execution of Android lifecycle/Handler ordering.

### Gaps that must not be represented as passing integration evidence

1. **Disconnected ASR independence/status assertions:** `TextExportTest.java:119,132-139` creates a separate AppState never supplied to the export and a fresh TaskCoordinator during the block. Their unchanged/available state cannot detect wrong production graph wiring or export callbacks writing the real AppState. Likewise `AdmissionBoundaryTest.java:72-95` begins export before owner work and only checks that a second begin fails while the first SELECTING slot already exists; it does not demonstrate a fresh export beginning while the shared owner is busy. Source inspection above supplies the wiring argument, not these assertions. Add a fixture using the production composition/ports and **one shared AppState.resultState and coordinator**, running real runner operations while each IO stage is blocked.
2. **Missing targeted sequences:** clear while a page holds an unconsumed deferred target; destroy before deferred consumption; repeated same page callback while deferred and after queueing; repeated resume; stale callback after a newer same-page ticket; begin racing clear/new-result; final admission versus clear in both controlled schedules. Existing selected/queued/blocked cases cover important endpoints, not all those sequences.
3. **Missing transition-specific observer tests:** R5-IR-1; multiple listeners where one throws; no further notices after removal; notification after rejection/revoked queue/fatal unwind. Current `>=3` notice assertion does not establish phase-by-phase rendering or that listeners run outside locks on a different thread. Reentrant synchronized access on the same thread alone cannot prove lock release.
4. **Missing fatal matrix:** R5-IR-2 plus primary fatal open/close, fatal submission, LinkageError treatment, and ordinary-failure + blocked/fatal close. Tests presently exercise a primary AssertionError from write and ordinary close IOException separately.
5. **Missing production edit/clear integration:** tests directly exercise ResultState; they do not drive the actual AsrOperation revision-based edit through runner admission/preflight, nor its async clear/preflight-failure boundary. Add stale dialog A→B→A and clear→same-result sequences through that production path.
6. **Cap/privacy edges:** test exactly 100000 accepted with supplementary/non-ASCII UTF-8 data and verify 100001 causes zero opens; existing tests reject oversize and test UTF-8 but not the exact acceptance boundary. Verify all terminal statuses contain no body/target/raw error with distinctive sentinel values, not only the current generic `secret` failure substring check.

`ResultFilesTest.java:55-57` and `AdmissionBoundaryTest.java:77` use inline executors despite the controller documenting an asynchronous production executor. They exercise real methods for local assertions, but cannot establish production enqueue/lifecycle scheduling. No tests were changed as part of this read-only review.

## Frozen SHA-256 recheck

Read-only Python (`json`, `hashlib`, `pathlib`) hashed every manifest path at two checkpoints. **Both checks: 14/14 match, zero missing/mismatched files.** Final check timestamp: **2026-09-17T11:28:51.224921+00:00**. Manifest SHA-256: `4974cd88f5e72411bbb4421a34527410cf3056c64fff07ee63b8fb77ee38dc5d`.

| Frozen file | Actual SHA-256 (matches manifest) |
| --- | --- |
| android/app/src/org/llmasr/minimal/ResultState.java | `53ed2c1337f587927e0b7638fb95c032d31aaf03ef422a03cbc23973a3c3e44e` |
| android/app/src/org/llmasr/minimal/TextExportController.java | `00d0fbdc4cae9199457b35188ae6a501f21a062cf5a352387adcd92b8b6d46fc` |
| android/app/src/org/llmasr/minimal/TextExportPage.java | `9b76f371e9637bcfd0460649fab23a1291fcee2bf15d60cfe3ff47622a289758` |
| android/app/src/org/llmasr/minimal/AppState.java | `d608919c476d7fb59b853c7f0f2f3273363de69506ce425b0da8f26501dc6f3e` |
| android/app/src/org/llmasr/minimal/AsrOperation.java | `1f71baadcd89fcf8086d02b446fc58a9bcfceb117ca57431a5d0969bf5a9c286` |
| android/app/src/org/llmasr/minimal/AppGraph.java | `5382193592e8164b38608e864d44eb5c3f6c9727b726730949aa3fd8fb0c4549` |
| android/app/src/org/llmasr/minimal/MainActivity.java | `d9b0d3111d29336309acb69d2be5152aaf95cdab2133713f41c8bcc5e32a3745` |
| android/app/src/org/llmasr/minimal/ResultFiles.java | `7ce787785e4dc20e6e320824119c758d6792358b28151290bb8f72f47fd67e01` |
| tests/TextExportTest.java | `cf96ba0f8f44b3edb2b896e7cbb01516fe37fde64af1e66e2d286eb2e8e28eae` |
| tests/TextExportSafetyTest.java | `6dc63a96d29d3d2a9a268b243053bf2801fbfac493d8ee148f334ff3d234353b` |
| tests/text_export_source_test.py | `85ed7bc53b360d835e2462b03863e39f7e79d20e8236e4d2d9e90593fb94a1d1` |
| tests/ResultFilesTest.java | `b092bf2f651b402e05b4718389616307b7d408d2d157d8a95a20421aea6501c8` |
| tests/AdmissionBoundaryTest.java | `c26832eade7564cafc8ff2a46cf0735ac1d57a01e106335b366dfffbf1c5ea45` |
| tests/ModelAndroidHelpersTest.java | `dd603e6e79a5b523a3c767ac051952ff44001eba97c429e383727e460eb94f8d` |

`git diff --cached --name-only` produced no paths at both checks. No staging was performed. Relevant `diff -u` comparisons were read-only; ResultFiles was unchanged from baseline.

## Residual risks and device limitations

- No real SAF cancellation, URI-grant behavior, cloud provider, blocked Android ContentResolver, partial external file, rotation/process death or multi-Activity delivery sequence was exercised. Host fake streams prove only the inspected production control-flow scenarios when those tests run; this review did not rerun them.
- No APK/DEX/JNI build, signing/component/permission inspection, install or device validation belongs to this report. The previous APK identity cannot be assigned to repaired source from these hashes alone.
- Real IME/InputConnection, microphone/recording, inference/model/log coexistence and lifecycle responsiveness still require their separate device acceptance. No performance or MNN mathematics/weights claim is made.
- Providers may create an empty document before this app opens it, retain partial content after failure, block indefinitely, or network independently. Revocation means no app provider-open/write after an epoch-winning clear, not rollback of external picker/provider side effects or secure erasure.
- The disjoint writer's model/log changes are outside this frozen review. All conclusions are bound to the matching frozen R5 files and the source wiring inspected here, not a future combined package.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Two concrete P2 findings identify source paths, line ranges, triggering schedules and minimal repairs; the report separately records positive contract tracing, missing tests, residual/device limitations, and a 14/14 frozen SHA recheck."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/abd2a604/.work/refactor-phase20-r5/independent-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "Read-only nl/read/grep of the frozen source/tests and directly connected owner/context files",
      "result": "passed",
      "summary": "Inspected production control flow, lifecycle/notification wiring, tests and line references without executing application code."
    },
    {
      "command": "diff -u of relevant baseline and current AsrOperation/AppGraph/AppState/MainActivity/ResultFiles",
      "result": "passed",
      "summary": "Confirmed original shared-owner wiring and unchanged ResultFiles cap; expected source differences inspected."
    },
    {
      "command": "Read-only Python SHA-256 comparison against .work/refactor-phase20-r5/frozen-sha256.json (two checkpoints)",
      "result": "passed",
      "summary": "14/14 files match at both checkpoints; final 2026-09-17T11:28:51.224921+00:00."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "No staged paths at either checkpoint."
    },
    {
      "command": "Tests, builds, javac and device execution",
      "result": "not-run",
      "summary": "Prohibited by the read-only review request; parent green observations are not treated as Android evidence."
    }
  ],
  "validationOutput": [
    "Frozen SHA result: 14 / 14 matched; mismatches: [].",
    "Core epoch/admission, queued revocation, post-close release and real Activity lifecycle wiring are supported by static source tracing.",
    "No source/test changes; only the requested review report was written."
  ],
  "residualRisks": [
    "P2 stale QUEUED UI while WRITING/provider IO blocks remains unfixed.",
    "P2 fatal close Error suppressed under ordinary write failure remains unfixed.",
    "Shared graph integration, detailed race/observer/fatal and exact cap-boundary coverage gaps are listed separately.",
    "No Android SAF/provider, lifecycle/process-death, IME/recording/native or package/runtime validation was performed."
  ],
  "noStagedFiles": true,
  "diffSummary": "Added only this independent review artifact; no production or test edits.",
  "reviewFindings": [
    "P2 R5-IR-1: android/app/src/org/llmasr/minimal/TextExportController.java:118-128 and MainActivity.java:123-156 — no WRITING invalidation/poll; a blocked provider can leave revocability wording stale indefinitely.",
    "P2 R5-IR-2: android/app/src/org/llmasr/minimal/TextExportController.java:128-136 — ordinary write failure can hide a fatal close Error as a swallowed suppressed exception.",
    "No demonstrated high-severity clear-before-admission bypass in the frozen sources; this is not unconditional release acceptance."
  ],
  "manualNotes": "Review completion is attested, not product acceptance. All findings are static; tests/builds were intentionally not run. The report is stored only at the authoritative artifact output path."
}
```
