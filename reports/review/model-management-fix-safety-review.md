# Focused independent safety follow-up — model-management fixes

## Verdict and review boundary

**Android R1, R2/Core F2, R3/Core F3, and Core F1 are closed at the reviewed production-source level. No remaining concrete sequence defeating these fixes, and no important new owner/IME/ASR-privacy regression, was found.** This is a static safety review, not Android runtime acceptance or independent confirmation that the checkpoint's tests/build passed.

Reviewed the original Android/core independent reports, disposition, implementation checkpoint **as claims**, and the model-management plan. Traced the actual Controller, State, Control, PageSession, Access, ProviderBoundary, SafModelSource, Activity and Repository implementations, related readiness/file safety/runner/coordinator/wiring, and focused tests. Android R4/R5 and Core F4/F5/F6 are not separately certified by this focused assignment; adjacent planning/presentation changes were considered for important safety regressions only.

No tests, builds, test imports, device operations, network, delegation, staging or commits were executed. Only this report was authored. Parent's concurrent build was neither interfered with nor treated as this review's evidence.

All paths below are repository-relative. In the explanations, `src/` abbreviates **`android/app/src/org/llmasr/minimal/`**.

## Disposition of the requested issues

| Accepted issue | Independent conclusion | Production anchors | Focused test source read (not executed) |
|---|---|---|---|
| Android R1: automatic INSPECT erases user outcome | **Closed.** INSPECT retains the explicit user identity, phase/outcome, error, failure names, cleanup/deletion statistics and import plan; inventory and inspection status are independently updated. | `android/app/src/org/llmasr/minimal/ModelManagementController.java:127–138,171–226`; `ModelManagementState.java:78–106,174–191`; `ModelManagementActivity.java:161–191,195–220` | `tests/ModelReviewFixTest.java:10–43,134–146`; busy rejection in `tests/ModelManagementControllerTest.java:62–69`; lifecycle policy in `tests/ModelAndroidHelpersTest.java:48–97` |
| Android R2/Core F2: provider cancellation message escape | **Closed for every provider/framework invocation on the production SAF enumeration/open/read/close path.** Java cancellation and provider-created application cancellation are sanitized, not trusted as user cancellation. | `android/app/src/org/llmasr/minimal/ModelProviderBoundary.java:14–27`; `SafModelSource.java:30–74`; `ModelManagementController.java:106–123,185–200` | `tests/ModelProviderBoundaryTest.java:10–77`; actual compiled-mutation recipe in `tests/model_review_mutation_test.py:15–19` |
| Android R3/Core F3: late cancel overwrites FAILED/CANCELLED | **Closed.** All normal terminal decisions are latched before publication, and the rejected/abandoned explicit-operation finalizer also latches before notifying. Neither transition releases the shared owner. | `android/app/src/org/llmasr/minimal/ModelOperationControl.java:33–61`; `ModelManagementController.java:185–226`; `RequestRunner.java:49–67`; `TaskCoordinator.java:44–62` | `tests/ModelReviewFixTest.java:45–94`; existing owner/close/cleanup cases in `tests/ModelManagementControllerTest.java:157–187` |
| Core F1: unsafe managed parts bypass lazy/cached READY | **Closed.** Every lazy access checks all final and explicit part names, even before a cached-READY return; both full-verification overloads also check the complete boundary. | `android/app/src/org/llmasr/minimal/ModelAccess.java:13–31`; `ModelRepository.java:185–195,224–234`; `FileSafety.java:16–29` | `tests/ModelReviewFixTest.java:111–124`; lazy hash/cache/stale-token cases in `tests/ModelAndroidHelpersTest.java:99–168` |

## Safety reasoning and qualifications

### 1. History is no longer confused with active ownership or inspection failure

`pending(INSPECT)` copies the current immutable snapshot, changes only `inspectBusy`/`inspectError`, and does not adopt the INSPECT ID as the user record (`src/ModelManagementController.java:127–130`). Terminal inspection updates readiness, storage/file inventory and current unexpected items; the INSPECT branch returns before assigning user outcome/error/cleanup/deletion fields (`171–209`). Failed inspection makes inventory unknown and reports a fixed independent inspection error without relabeling the retained operation. `finish(INSPECT)` clears the transient busy marker; an admitted-but-never-run inspection gets its own generic error (`214–217`). A newly admitted IMPORT/VERIFY/DELETE deliberately replaces the old user record (`132–138`).

The historical `activeOperationId` is explicitly documented as current-or-retained, not a second owner (`src/ModelManagementState.java:78–80`). `ModelPageSession.ownsActive()` compares the page's ID to the **actual control operation**; `cancellable()` now uses one local operation and excludes success, termination, prior cancel, DELETE and INSPECT (`39–48`). Thus a returned page retaining its old ID does not own an automatic INSPECT, nor can it cancel another page's task.

Actual Activity resume still requests inspection when not owning an active task (`src/ModelManagementActivity.java:202`), but its renderer gates the refresh on the coordinator and shows the separate inspect message alongside the retained task (`163–177`). Refused busy admission never calls `pending`: `TaskCoordinator.java:46` returns first. A competing App/IME owner therefore cannot erase history or provoke parallel disk inspection. This preserves the accepted generic owner hint rather than inventing a new ownership identity.

The new history test drives real controller cancellation, SHA failure, failed part cleanup and partial deletion, alters disk contents, refreshes, then forces an unsafe inspection. It asserts retained identity/outcome/error/cleanup/counts and fresh inventory. It does **not** execute Android onResume/rotation or combine every picker/page callback with every history scenario; lifecycle integration is established here by source tracing plus the existing page-policy tests, not by a device reproduction. Preservation of `deleteFreedBytes` and all ancillary history fields is apparent from the copy/branch logic; the history test does not independently assert every field.

### 2. Provider-authored exceptions cannot become trusted gate cancellation

The factored wrapper discards the original message, cause and suppressed list and throws a new fixed-message IOException for IOException, RuntimeException and LinkageError (`src/ModelProviderBoundary.java:14–16`). This includes Java `CancellationException`, SecurityException, Android-style runtime cancellation, and the application's `ModelRepository.CancelledException` even if a provider constructs one with sensitive nested exceptions. Own cancellation checks are outside that wrapper (`18–19`), so an actual latched gate still throws the fixed typed application exception.

Audited SAF callsites individually: tree/child URI construction (`31–32`), resolver query (`34–37`), cursor close (`39–42`), cursor movement (`48`), string/null/flags reads (`52–57`), document URI construction (`63–64`), and resolver stream open (`73`). Each invokes the wrapper. Validation outside the wrapper uses only fixed text and names already matched against the trusted expected-name set. Stream single-byte read, bulk read and close are wrapped (`ModelProviderBoundary.java:25–27`); repository `in.read(byte[])` reaches the overridden bulk-read path through FilterInputStream. Unused InputStream methods such as skip/reset are not asserted to be a general-purpose sanitized API; the repository never calls them.

The cursor's try-with-resources close boundary remains active when row checks cancel or fail. A secondary close exception is sanitized before Java attaches it as suppressed. Likewise repository input/output close must finish before part verification/rename (`src/ModelRepository.java:405–441`); sanitized stream failures reach its normal cleanup path (`443–448`). An unsolicited provider cancellation produces FAILED, not CANCELLED: controller outcome is selected from the operation's latched cancel flag, not the exception's type (`ModelManagementController.java:191–194`). If the real user gate wins while close is blocked, the result is CANCELLED with the safe provider failure detail and independent cleanup outcome.

The new tests execute the actual supplier wrapper and actual wrapped streams, including malicious application-typed cancellation, then connect sanitized open failures and a blocked close to the real controller/owner. They are **not ContentResolver/Cursor fixtures**. The implementation report accurately bounds that evidence. Other fatal Errors are not broadly recovered: unlike LinkageError, they bypass the controller's published failure-message path and fall to runner/finalization behavior. This review does not certify recovery from VM failure.

### 3. Terminal immutability and real-owner finalization are distinct

`decideTerminal()` latches `terminated` without clearing the active slot (`src/ModelOperationControl.java:53–56`). `requestCancel` and publish reservation reject a terminated operation (`33–44`). Normal terminal publication calls this latch before exposing the snapshot (`src/ModelManagementController.java:185–209`), including FAILED and CANCELLED. Success already has its own callback-free cancellation/READY arbitration (`ModelOperationControl.java:47–51`). There is no callback between a failure/cancel latch and computation of the corresponding terminal phase.

A reentrant state listener requesting cancellation therefore sees a rejected cancel, not CANCELLING. A different thread cancelling after terminal publication but before `finish()` has the same result. The finalizer preserves an existing terminal; if execution was rejected or otherwise never reached terminal, it builds generic FAILED and **also latches before publishing** (`ModelManagementController.java:220–225`). An INSPECT rejection is noncancellable and touches only inspection fields.

The active control slot is cleared only after final state publication; runner once-only finalization precedes coordinator release (`ModelManagementController.java:226`; `RequestRunner.java:49–61`; `TaskCoordinator.java:55–62`). This ordering covers ordinary completion and executor rejection. The snapshot's `pageOwnerHeld=false` is not an early release mechanism; actual admission still uses the coordinator.

`tests/ModelReviewFixTest.java:45–67` observes reentrant late cancel for failed/cancelled/succeeded verification and executor rejection. Lines 69–94 pause in a readiness listener **after** terminal publication and outside the controller monitor, assert the real owner remains held, attempt both controller/direct late cancel and competing admissions, and then release with bounded teardown. Callback failures are captured and checked outside readiness's Throwable guard. This specifically exercises the formerly vulnerable interval rather than testing only a completed operation's flags. Test execution and mutation kills remain checkpoint claims, not reproduced results.

### 4. Complete managed-path checks now precede lazy permission to infer

`ModelAccess.requireReady()` validates all managed names before `tryBeginVerify()` can return null for cached READY (`src/ModelAccess.java:16–19`). The shared validator checks each final and `.part` through the same root/canonical/symlink/nonregular-file safety primitive used by management (`src/ModelRepository.java:185–195`; inspection at `167–168`). Both verification overloads invoke it before hashing. Unsafe parts thus fail even when all official files hash correctly or cached SHA evidence exists.

The catch path finishes any pending verification and inspects; the same unsafe path makes inspection fail and marks readiness INVALID (`ModelAccess.java:23–27`). No native continuation follows an exception. Ordinary regular orphan parts are not deleted by validation and do not automatically revoke correct final-file SHA evidence. The focused fixture covers directory, external symlink and regular part for both fresh and cached READY, checks the external target, and runs access under the real coordinator.

This is complete **managed-path validation**, not a new promise to rehash cached models on every inference or detect hostile concurrent same-size content replacement. The accepted private-storage/shared-owner assumption still matters; checks are not atomic fd-relative nofollow operations.

## Important regression check: owner, IME and ASR privacy

No important new regression found. The final frozen manifest and previous 61-file review manifest share **47 unchanged hashes**; relevant unchanged files include AppGraph, AppState, RequestRunner, TaskCoordinator, RecordingControl, ImeController, AsrImeService, ImeBackend, AsrOperation, MainActivity and AndroidManifest.xml. Their actual integration was reread rather than assuming unchanged files guarantee safe composition.

- `src/AppGraph.java:39–48,108` injects one coordinator/readiness/repository/access into management and App; IME uses the same coordinator (`AsrImeService.java:36`) and access (`ImeBackend.java:20`). App also calls access before native loading (`AsrOperation.java:153–156`). The newly added validation remains worker work under that shared owner.
- Management still constructs private runner State and no-op reports/cleanup/preflight, and submits MAINTENANCE (`ModelManagementController.java:20–33,59,228–237`). `RequestRunner.java:59,71–87` preserves text and suppresses all maintenance report paths. The connected test ports in `tests/ModelManagementControllerTest.java:27–48` would observe model operations writing ASR text or reports; they are not disconnected dummy transcript assertions.
- IME management navigation still invalidates the editor session/preview, requests actual hide, then launches without text/audio extras (`AsrImeService.java:65–79`; `ImeController.java:34–45`). Late inference uses the live identity/revision check (`ImeController.java:80–85`), and InputConnection is obtained only at explicit commit (`AsrImeService.java:135–140`). RecordingControl is not reused for model cancellation.
- Management picker eligibility, stale-ticket rejection, no persisted URI grant and owner-before-enumeration remain intact. The manifest retains only RECORD_AUDIO and an internal, nonexported, same-process management Activity; no new permission/background channel was introduced.

## Frozen-input integrity and residual risks

After source inspection, read-only Python `hashlib` verification reported **69 entries, 69 MATCH, 0 MISMATCH** against `.work/model-review-fixes/parent-frozen-input-sha256.json`. Manifest SHA256: **`c3b2a777234d8805322f4b6cc1035d721b3a9c7d1fbb426cbc6512bc398c69a1`**. `git diff --cached --name-only` was empty. No APK hash or concurrent build result is certified here.

Residual risks are bounded to the acceptance boundary, not new defect findings:

1. Real SAF resolver/cursor behavior, permissions, provider stalls and Android lifecycle/visual behavior remain unexecuted. Host supplier tests cannot certify them; specifically no real Activity resume/rotation/picker-history end-to-end run was performed.
2. No tests/builds were run by this reviewer. Parent must reconcile its final build-input/APK evidence with these 69 unchanged frozen inputs and retain the separately reported test results. This focused report does not replace review of the other accepted issues.
3. Cancellation is cooperative and can wait indefinitely for query/read/close/cleanup. No forced owner release, process-survival, fsync/seven-file transaction or hostile concurrent filesystem-writer guarantee is added.
4. Fatal VM-error recovery and sanitization of unused general InputStream methods are not certified. No remaining exception-message escape was found on the production SAF calls actually used by this repository.

```acceptance-report
{
  "criteriaSatisfied": [
    {"id": "criterion-1", "status": "satisfied", "evidence": "Independent source/test review closes Android R1/R2/R3 and Core F1/F2/F3 with concrete production/test path and line references; documents owner/IME/ASR regression assessment, test limits and 69/69 frozen-input matches."}
  ],
  "changedFiles": ["/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/8d949144/.work/model-management-fix-safety-review.md"],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {"command": "Read-only cat/nl/sed/grep/wc source, report and focused-test inspection", "result": "passed", "summary": "Reviewed actual implementations against independent reports/disposition; checkpoint test/build statements treated only as claims."},
    {"command": "Python hashlib/json comparison of parent-frozen-input-sha256.json", "result": "passed", "summary": "69 inputs match, zero missing/mismatched; manifest SHA256 c3b2a777234d8805322f4b6cc1035d721b3a9c7d1fbb426cbc6512bc398c69a1."},
    {"command": "Read-only comparison with previous 61-file review hash manifest", "result": "passed", "summary": "47 shared entries unchanged; 14 changed and 8 new entries in current freeze."},
    {"command": "git diff --cached --name-only", "result": "passed", "summary": "No staged paths."},
    {"command": "Tests/builds/device/network", "result": "not-run", "summary": "Prohibited for this focused reviewer; no execution or parent-build acceptance claimed."}
  ],
  "validationOutput": ["69/69 frozen inputs MATCH; 0 MISMATCH.", "No source/test/script/build-output changes by reviewer; authoritative report only."],
  "residualRisks": ["Static review only; real SAF and Android lifecycle/device evidence remain pending.", "Parent must bind completed build/APK evidence to the 69 frozen inputs; other accepted issues are outside this focused certification.", "Cooperative provider IO/close may block; private-storage/shared-owner assumptions and non-atomic filesystem checks remain.", "No fatal VM-error recovery or unused InputStream-method boundary certification."],
  "noStagedFiles": true,
  "diffSummary": "Report artifact only; production files, tests, scripts and concurrent build outputs untouched.",
  "reviewFindings": [
    "No remaining blocker or important new regression found within the requested focused scope.",
    "Closed Android R1: android/app/src/org/llmasr/minimal/ModelManagementController.java:127-138,171-226 — INSPECT preserves explicit history and separates transient inspection state.",
    "Closed Android R2/Core F2: android/app/src/org/llmasr/minimal/ModelProviderBoundary.java:14-27 and SafModelSource.java:30-74 — provider-authored cancellation/errors sanitized on every used SAF provider call; true gate cancellation remains separate.",
    "Closed Android R3/Core F3: android/app/src/org/llmasr/minimal/ModelOperationControl.java:33-61 and ModelManagementController.java:185-226 — normal and rejected explicit terminals immutable to late cancel, owner retained until finalization.",
    "Closed Core F1: android/app/src/org/llmasr/minimal/ModelAccess.java:16-27 and ModelRepository.java:185-195,224-234 — complete managed final/part safety checks precede fresh or cached READY continuation."
  ],
  "manualNotes": "Authoritative output path honored. No tests/build/device/network/commit/delegation; implementation checkpoint execution claims were not reproduced. Closure is source-level and does not imply unconditional Android delivery acceptance."
}
```
