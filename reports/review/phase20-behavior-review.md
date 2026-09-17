# Phase20 R5/R6 final focused behavior review

## Scope, method, and conclusion

Read-only source/test review of the migrated transcription `ResultState`, `TextExportController`, `TextExportPage`, `AsrOperation`, root `MainActivity`, model-management controller/state and connected typed model outcomes, and diagnostics codec with its direct close-owning caller. Read `docs/android-r5-r6-validation.md`, `reports/review/phase20-r5-independent.md`, and `reports/review/phase20-r5-disposition.md` as requirements/history, not proof of correctness. Traced directly connected AppGraph/AppState, runner/coordinator, cancellation/readiness, and focused tests where necessary. Build scripts/package-verification lane was not reviewed.

**Both previous R5 P2 findings are fixed in the frozen source. No new concrete defect was found in the reviewed TXT implementation, model-controller locking/cancellation changes, typed outcome migration, or codec extraction itself.** There is one concrete **adjacent, pre-existing P2** in the codec's log-export caller and one **pre-existing P2 test-quality defect**, detailed separately below. Neither is attributed to the R6 package migration. This is not unconditional release acceptance.

No tests, builds, compilation, application code, or Android/device commands were executed. The parent's concurrent build and previous green/red observations were not independently reproduced. Only this report was written.

## Explicit disposition of the previous two P2 findings

### R5-IR-1 — obsolete QUEUED/revocable UI while actual provider IO blocks: **fixed by source inspection**

- `android/app/src/org/llmasr/minimal/MainActivity.java:130-136,144-159` renders the current export state and schedules foreground busy refresh at 250 ms. `onChange()` and `whileExportBusy()` remove this Runnable's pending callbacks before posting; therefore the mechanism is coalesced rather than an accumulating polling queue. Terminal render removes the delayed refresh without scheduling another.
- Resume attaches listeners, makes the page foreground, and immediately refreshes (`162-171`). Pause clears foreground, detaches listeners/page, and removes callbacks (`173-183`); destroy also removes callbacks (`280-283`). A racing previously captured notification may post once after detach, but `run()` checks foreground/destroyed/finishing and does not restart polling in the background.
- `transcription/TextExportController.java:118-128` still installs WRITING atomically with clear-epoch validation and proceeds to provider open **without invoking arbitrary observers between admission and open**. The Activity can now observe WRITING while open/write/close remains blocked. QUEUED wording is conditional, not an unconditional revocability promise (`178`).
- `tests/text_export_source_test.py:5-39` guards lifecycle/poll wiring and has eight weakening fixtures. These are lexical guards, **not a real Handler scheduler test**. The 250 ms delay is not a guaranteed wall-clock delivery bound if the main thread is blocked.

### R5-IR-2 — ordinary TXT write failure hides a fatal close Error: **fixed by source inspection and meaningful regression source**

- `android/app/src/org/llmasr/minimal/transcription/TextExportController.java:130-155` explicitly arbitrates primary and close exceptions. A non-Linkage close Error replaces a recoverable primary and retains it as suppressed. A primary non-Linkage Error remains the exact propagated primary when close also fails. Ordinary IO/runtime/Linkage failures retain the intended recoverable FAILED behavior.
- Close is attempted exactly once after a successful non-null open. SUCCEEDED is installed only after close; `complete()` releases the slot in the outer finally, after the close scope unwinds (`152-162`). Open failures also finalize.
- `tests/TextExportFatalTest.java:11-27` invokes the actual queued production worker, captures failure into a local outside the assertion, and checks exact fatal identity for **IOException write + InternalError close** and **InternalError write + IOException close**, one close attempt, FAILED, and released busy state. Its assertions are not caught by its failure-capture block. `TextExportTest.java:139-169` separately checks retention through a blocked close. These files were read, not run.

## Concrete adjacent production finding

### BR-1 — P2 / Medium: log-export caller still swallows fatal close errors beneath ordinary codec failures

**File/lines:** `android/app/src/org/llmasr/minimal/diagnostics/LogExportController.java:85-98`, specifically try-with-resources at `89-92` and the recoverable catch at `94-95`.

**Trigger:** A valid selected log snapshot is submitted. Its real output stream throws an `IOException` or ordinary `RuntimeException` during `RuntimeLogCodec.writeSnapshot()`'s write or flush. The same stream's `close()` then throws `InternalError`, another non-Linkage VM/Error, `ThreadDeath`, or `AssertionError`.

**Control flow and impact:** Java try-with-resources keeps the ordinary write/flush exception primary and attaches the fatal close error as suppressed. The catch discards that primary, including the fatal. The finally correctly publishes FAILED and releases the export slot, but the fatal never reaches the worker harness/runtime. This is loss of fatal propagation, not false success or early stream release. It is the same compound-error class as the repaired TXT P2, now observed in the direct close-owning caller of the scoped diagnostics codec.

**Origin/scope:** Pre-existing, not introduced by R6. The caller's worker body matches `.work/refactor-phase20-baseline/inputs/android/app/src/org/llmasr/minimal/LogExportController.java` after substituting `RuntimeLogWorker.writeSnapshot` with `RuntimeLogCodec.writeSnapshot`. It is also unchanged modulo package/imports from the prepackage input. Included as adjacent evidence because codec correctness alone cannot establish its caller's close/fatal contract; this is not a broader log-subsystem audit.

**Narrow repair/verification:** Apply equivalent explicit primary/close arbitration to this caller, preserving a primary fatal's identity, attempted close exactly once, post-close slot release, fixed public failure state, and recoverable Linkage behavior. Add production-controller cases for ordinary write/flush + fatal close and fatal primary + ordinary close, asserting exact throwable identity **outside** the catch. No fix or test execution was performed in this review.

## Concrete test-quality finding (separate from application defects)

### BR-T1 — P2 / Medium, validation: fatal-propagation assertion catches itself and can falsely pass

**File/line:** `tests/LogExportTest.java:69` (fault setup at `54-65`).

```java
try { tasks.run();check(fault!=7,"fatal rethrown"); }
catch(AssertionError fatal) { check(fault==7,"only hard error escapes"); }
```

**Trigger:** For `fault == 7`, suppose production swallows the stream's close `AssertionError` but still records FAILED and releases the slot. `tasks.run()` returns normally; `check(fault!=7, ...)` throws its own `AssertionError`; the immediately following catch catches that assertion and `check(fault==7, ...)` passes. Remaining state assertions can also pass. Thus the test does not prove its advertised fatal propagation.

**Origin:** The same statement exists in the Phase20 baseline at `tests/LogExportTest.java:62`; this is not a package-migration regression. This specific false-positive does not invalidate `TextExportFatalTest`, which uses a safe capture-then-assert structure.

**Narrow repair:** Keep a unique expected fatal instance, catch only around worker invocation into a variable, and assert identity/no-error expectations afterward. Add the compound failure matrix required by BR-1. This finding concerns false-green evidence, not an assertion that the current primary-only close case actually fails to propagate.

## R6 locking, ordering, and production behavior audit

Paths in this section are under `android/app/src/org/llmasr/minimal/`.

### Model controller installs and invalidations

All **10 controller `state.install` sites** are inside `synchronized (this)`; all corresponding notifications are after its release:

| Transition | Install lines in `modelmanagement/ModelManagementController.java` | Outside-lock notification/log |
|---|---:|---:|
| cancellation | 66 | 68 |
| inspect pending / explicit operation pending | 147 / 159 | 162-163 |
| progress | 182 | 184 |
| import plan | 192 | 194 |
| typed file result | 202 | 204 |
| deletion progress | 212 | 214 |
| inspect terminal / explicit operation terminal | 240 / 263 | 286-287 |
| finalization, including never-run/rejected work | 312 | 315-316 |

Each builder/current read and install for these paths shares the controller monitor; there is no read-under-lock/install-after-unlock stale snapshot overwrite. State listeners receive invalidations and reread current state, not a captured obsolete payload. `safeLog()` appends only after releasing the controller monitor (`289-292`). The controller's locked readiness reads and terminal/control methods are callback-free.

`ModelManagementState.java:190-207` separates install from notify, with immutable snapshots/copying collections (`113-123`). Its compatibility `publish()` method does not itself enforce controller locking, but search found **no production call to that method**; all actual controller installs use the locked paths above. This is not reported as a demonstrated race.

### Cancellation, epoch proof, and owner ordering

- Shared runner ownership is acquired before operation identity/pending (`ModelManagementController.java:71-83`; `task/RequestRunner.java:42-61`; `task/TaskCoordinator.java:45-64`). Cancellation does not release the worker or coordinator.
- Controller progress consults cancellation while holding the same monitor used by `requestCancel()` (`57-68,170-184`), so subsequent progress cannot overwrite CANCELLING with an uncancelled phase. Plan/file/deletion updates copy the latest state in that same domain.
- Verify token capture remains before hashing; import obtains it at the first FINAL_VERIFY update, before the first final digest. Mutation invalidates readiness before file mutation. `ModelOperationControl.trySucceed()` atomically arbitrates cancel versus epoch-checked READY commit without callbacks (`modelmanagement/ModelOperationControl.java:34-56`; controller `94-101,111-138`).
- Terminal decision happens before terminal notifications, retaining the cancellation identity/slot until finalization (`controller:235-240,261-287`). Finalization installs its state under lock, notifies outside, then completes control in finally (`294-316`); coordinator release follows runner finalization. Reentrant cancellation after committed terminal cannot replace success/failure/cancel detail.
- Inspection remains under the shared owner and keeps the previous explicit operation history; its transient busy/error fields clear during finalization. No early owner release across cancellation, inspect, or terminal IO was found.

### Typed model observations

`model/ModelReports.java:50-76` contains immutable closed status/failure values. Actual repository emissions remain at existing-file classification (`ModelRepository.java:382-384`), successful publication after rename but before post-publication cancellation (`481-486`), and per-file final SHA completion (`291-294`). Controller carries these immutable values in copied maps and maps `ModelFileException.failure` on terminal failure (`196-204,248-253`); UI presentation resides in `modelmanagement/ModelUiText.java:57-81`.

These per-file observations remain historical, not a replacement for global current-epoch READY. The fixed current reason-to-failure mapping (`ModelRepository.java:226-242`) covers its actual throw sites; no mismatched reason was found. Distinct cancellable/noncancellable SHA entrypoints remain intentionally separate, as recorded in `.work/refactor-phase20-r6/sha-disposition.md`; no IO reduction or algorithm equivalence claim is made.

### TXT state/lifecycle and owner preservation

`transcription/ResultState.java:23-38` keeps text/revision snapshots atomic, rejects ABA edits, and separates clear epoch from ordinary edits/new results. Controller begin, picker admission, and final write admission retain controller-to-ResultState lock ordering (`TextExportController.java:72-112,118-126`). Clear that wins the final admission race prevents provider open; queued revoke retains the slot until real runnable/rejection cleanup. Page destroy discards deferred targets and revokes queued tickets but cannot retract admitted IO (`TextExportPage.java:47-85`).

MainActivity edits use one snapshot's text/revision (`245-254`); `AsrOperation.editResult()` validates in the original maintenance runner (`132-138`). `AsrOperation.clearResults()` changes the epoch **inside the accepted worker body after preflight**, not at button tap (`118-129`). AppGraph still supplies the original shared coordinator to ASR/model management while TXT has its separate application-context worker (`AppGraph.java:65-80,153-156`). UTF-8 and the 100000 UTF-16-unit limit remain (`ResultFiles.java:29-32`). No new capture of Activity in export worker, ASR-status write from TXT export, or persistence/restoration of body/URI/ticket was found.

### Codec extraction

`diagnostics/RuntimeLogCodec.java:33-46` retains frozen-list copying, event-count and encoded-byte preflight before output, UTF-8 line format, flush, actual byte count, and caller-owned close. Its algorithm matches the baseline `RuntimeLogWorker.writeSnapshot()` (`174-187`). The production log-export caller invokes this codec directly (`LogExportController.java:91`), not a substituted test implementation. BR-1 is in that caller's exception arbitration, not the codec's encoding algorithm.

## Test evidence and residual coverage gaps

These are source-inspection conclusions, **not rerun results**.

**Meaningful production-port coverage:**

- `TextExportTest.java:32-91` exercises actual ResultState/controller/page ABA, immutable snapshot, selected/queued/deferred clear, duplicate/stale callbacks, resume/destroy. `139-169` now shares **the actual AppState.resultState** with real RequestRunner/AppRequestPolicy and one coordinator while real controller open/write/close fake ports block. It executes accepted clear and inference-policy work, checks report effects, and checks actual App text/status after export completion. This repairs the earlier disconnected-state coverage gap at the pure-Java port level, not full Android graph integration.
- Exact accepted 100000 UTF-16 units with supplementary UTF-8 output, oversize refusal, ordinary/fatal cleanup, throwing sibling observer and listener removal are covered by production calls (`TextExportTest.java:95-136`). Callback tests record observations and assert afterward. `TextExportSafetyTest.java:10-20` still checks zero provider opens after clear against the actual controller.
- `ModelNotificationTest.java:9-46` uses the production controller/repository/runner fixture. It records `Thread.holdsLock`, inspects inspect pending/terminal/finish notices, exercises reentrant cancellation and different-thread acquisition of the controller monitor, and checks observations outside callbacks. Cross-thread waits are bounded.
- `ModelManagementControllerTest.java:79-119,148-202` covers real cancellation identity, stale epoch after hashing, cancellation before success, deletion invalidation, retained ownership during close/cleanup, and confirmation recheck. `ModelReviewFixTest.java:54-103,186-208` records terminal callbacks/post-terminal windows and publication-before-cancellation history; callback errors in the blocking readiness listener are captured and checked afterward.
- `RuntimeLogCoreTest.java:369-415` calls the production codec directly for frozen UTF-8/count, event-count refusal before write, null-entry rejection, and write/flush failure. Its `rejects` helper does not swallow assertion failures (`28-33`). Actual log-controller close/slot tests exist, but BR-T1 invalidates the specific fatal-propagation claim at line 69.

**Remaining gaps/limitations, not additional app defects:**

1. There is no real Handler scheduling or Android SAF/rotation/process-death execution here. Foreground poll proof is source plus lexical weakening guards, not an observed 250 ms UI transition. Model lock tests cover important paths, not an exhaustive concurrent schedule for every install/log callback.
2. Full AppGraph/AsrOperation runtime integration remains unexecuted. The connected export fixture supplies a real runner/policy with a maintenance body calling `asr.clearText()`, not the Android `AsrOperation.clearResults()` method/preflight filesystem path. Stale edits through the actual Android operation and clear-preflight failure remain source-traced rather than directly exercised.
3. `AdmissionBoundaryTest.java:80-103` still begins a SELECTING export before the ASR task and then checks another begin is refused by the already occupied export slot. It does not independently prove a **fresh** export begins while the owner is busy. The improved blocked fixture demonstrates the other direction (real owner work while export blocks); actual graph wiring is supported by source inspection.
4. `RuntimeLogCoreTest.java:401-411` is labeled as a cap/malicious-callback test but its stream deliberately throws IOException for a small legitimate write. It tests write failure, **not crossing the codec's 1 MiB encoded-byte threshold**. The 1001-event rejection is meaningful; the larger byte-cap claim should not be inferred from this fixture. The self-contained try-with-resources close pattern at `387-400` is not itself a production-controller lifecycle test.
5. Compound TXT fatal cases are correctly asserted, but exhaustive fatal-open/submission/Linkage and compound-blocked-close permutations, exhaustive begin/clear interleavings, and distinctive body/URI/error sentinel checks for every terminal status are not established by these targeted files.

## Migration mechanicality and frozen identity

Read-only comparison against `.work/refactor-phase20-prepackage/inputs` found these **16 focused production files identical after removing only package declarations and `import org.llmasr...` lines**: ResultState, TextExportController, TextExportPage, AsrOperation, AppState, MainActivity, AppGraph, ModelManagementController, ModelManagementState, ModelOperationControl, ModelReports, ModelRepository, ModelUiText, RuntimeLogCodec, RuntimeLogWorker, LogExportController. Their new imports and directly connected wiring were read. This supports mechanicality of these implementations; it is not an APK/DEX/JNI or whole-repository equivalence assertion.

Read-only SHA-256 comparisons of `.work/refactor-phase20-final/frozen-sha256.json`:

- Initial checkpoint: `2026-09-17T12:52:58.345596+00:00` — **125/125 match**, no missing/mismatched paths.
- Final source checkpoint: `2026-09-17T12:56:58.798394+00:00` — **125/125 match**, no missing/mismatched paths.
- Manifest SHA-256: `e0223b049a41d7a4dba55e5ea374bdd4cf610d3bf690e4b6a86079722f6bead3`.
- `git diff --cached --name-only` returned no paths. The existing dirty working tree was preserved; no source/test changes or staging were performed.

## Residual runtime risks

Real provider grant/cancellation/cloud networking, indefinitely blocked ContentResolver, partial external files, main-thread responsiveness, rotation/process death, and multiple pages require separate Android acceptance. Providers may already create a document in the picker before the app opens it; clear revocation is not secure erasure or rollback of external side effects. Explicit clear linearizes when its worker changes the epoch, not at the user's tap. IME/InputConnection, microphone/recording, native inference, APK identity/signing/components and performance were not validated by this review. The parent build is a separate lane and cannot be inferred from this artifact.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "BR-1 identifies a concrete adjacent/pre-existing P2 in diagnostics/LogExportController.java:89-98 with compound write/flush-plus-fatal-close trigger; BR-T1 separately identifies a false-passing fatal assertion at tests/LogExportTest.java:69. Both prior R5 P2 findings are explicitly dispositioned fixed; coverage and residual risks are separate. Frozen identity rechecked 125/125."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/193f09cb/.work/refactor-phase20-final/behavior-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "Read-only cat/nl/sed/rg of requirements, previous review/disposition, focused production source and tests",
      "result": "passed",
      "summary": "Traced TXT fixes, all 10 controller installs, notification/log lock boundaries, cancellation/epoch ordering, typed observations, codec caller and assertion quality. No application code executed."
    },
    {
      "command": "Read-only Python text comparison against .work/refactor-phase20-prepackage/inputs and relevant baseline codec/caller",
      "result": "passed",
      "summary": "16 focused production files match modulo package/import declarations; BR-1 and BR-T1 also exist in baseline and are not migration regressions."
    },
    {
      "command": "Read-only Python hashlib verification of .work/refactor-phase20-final/frozen-sha256.json, two checkpoints",
      "result": "passed",
      "summary": "125/125 match at both checkpoints; final source check 2026-09-17T12:56:58.798394+00:00."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "No staged paths."
    },
    {
      "command": "Tests/builds/compilation/Android or device execution",
      "result": "not-run",
      "summary": "Prohibited for this review; parent's concurrent build is separate evidence."
    }
  ],
  "validationOutput": [
    "Previous R5-IR-1 fixed by lifecycle-scoped coalesced busy refresh without admission observer.",
    "Previous R5-IR-2 fixed by explicit TXT primary/close arbitration; production-port regression captures then asserts exact fatal identity.",
    "All 10 ModelManagementController state.install sites are under controller monitor; notifications and actual log append occur outside it.",
    "Frozen check: 125/125 match; mismatches []."
  ],
  "residualRisks": [
    "Adjacent pre-existing log-export compound fatal-close propagation bug BR-1 remains in reviewed source.",
    "Pre-existing LogExportTest fatal assertion can catch itself; its advertised fatal-propagation evidence is invalid until repaired.",
    "Handler scheduling, complete Android operation/graph integration, exhaustive concurrency/fatal permutations and codec byte-threshold rejection are not established by inspected targeted tests.",
    "No Android provider/lifecycle, IME/recording/native or APK/runtime validation; no performance claim."
  ],
  "noStagedFiles": true,
  "diffSummary": "Added only the authoritative review artifact; no app/source/test changes and no staging.",
  "reviewFindings": [
    "P2 BR-1 (adjacent/pre-existing): android/app/src/org/llmasr/minimal/diagnostics/LogExportController.java:89-98 — ordinary codec write/flush failure suppresses fatal close Error, then recoverable catch discards it.",
    "P2 BR-T1 (test-quality/pre-existing): tests/LogExportTest.java:69 — fatal assertion is caught by its own catch and falsely passes when fault==7.",
    "Both previous R5 P2 findings are fixed; no new concrete defect found in scoped TXT/controller/type/codec-extraction migration."
  ],
  "manualNotes": "Static review completion is attested, not product acceptance. Scope/origin distinction for the adjacent codec caller was confirmed with the parent. No tests or builds were run; only this report was written."
}
```
