# Independent read-only review — 0.5 model-management core

## Decision and scope

**Static review completed against the frozen 61-file input. No demonstrated model-file corruption, out-of-root deletion, premature owner release, or cancellation-before-publication/READY bypass was found in the production shared-owner path. This is not unconditional 0.5 acceptance:** the concrete correctness/privacy-boundary and evidence gaps below remain. Most are recovery/presentation issues rather than concurrent-write or inference-corruption blockers.

Read `docs/model-management-plan.md` in full, all nine requested core components, FileSafety/manifest, SAF adapter, AppGraph/AppState/ModelAccess wiring, pertinent App/IME entry paths, all model repository/controller/readiness/control/helper tests, RequestRunner/TaskCoordinator/admission tests, mutation harness and retained evidence, and build/check provenance scripts. Compared the owner/runner changes with `.work/model-management-baseline`. The authoritative output is this report only.

**No tests, builds, Java programs, Android runtime, devices, network, delegation, staging or commits were executed.** Existing 847-check, 947-check and javac logs were read as other actors' evidence, not reproduced. Parent's concurrent build outputs are not the frozen source or proof of runtime behavior.

Paths below are repository-relative; Java production paths abbreviate `android/app/src/org/llmasr/minimal/` as `src/` only in explanatory prose. Findings give the full path.

## Findings

### F1 — Medium: lazy ASR readiness can accept an unsafe managed `.part` that management rejects

- **Location:** `android/app/src/org/llmasr/minimal/ModelAccess.java:14–19`; `ModelRepository.java:223–239` versus `ModelRepository.java:164–165`.
- **Trigger:** all official files have correct size/SHA, but a fixed managed `a.part` is a symlink or directory. Start App/IME inference in a fresh/unverified process without first inspecting the management page. Lazy verification validates official filenames only and commits READY; management inspection validates official **and part** paths and rejects exactly that tree. A cached READY also skips inspection entirely.
- **Impact:** inconsistent unified readiness / INVALID semantics under MM-01/MM-05 and unsafe-managed-path rules. **Not evidence of native inference following the part link:** native uses the verified official files, so this is not an established out-of-root read/write or corruption exploit. It is a fail-closed state-contract gap.
- **Recommendation:** validate the complete managed boundary (including explicit part paths) on the lazy verification path before READY; centralize that boundary validation so management and ModelAccess agree. Safe ordinary orphan parts can be reported separately without unnecessary hashing or deletion.
- **Missing test:** use real valid finals plus a managed part symlink/directory in `ModelAndroidHelpersTest`, invoke ModelAccess under the actual coordinator, assert failure, no native continuation, INVALID, and preservation of the external target. Existing deferred-root tests at `tests/ModelAndroidHelpersTest.java:152–168` cover a root link, not this successful-hash/unsafe-part case.

### F2 — Medium, privacy/error boundary: provider cancellation exceptions bypass SAF message sanitization

- **Location:** `android/app/src/org/llmasr/minimal/SafModelSource.java:62–64,74–86,89–91`; `ModelManagementController.java:100,162`; final UI guard `ModelUiText.java:48–53`.
- **Trigger:** provider query/open/read/close throws `java.util.concurrent.CancellationException` with provider-controlled message, e.g. a full selected URI or a private document title. Those exceptions are expressly rethrown unchanged, unlike other IOException/RuntimeException failures. The controller catches them and copies `getMessage()` into the long-lived model snapshot. No operation-control cancellation need have been requested; the result is FAILED with the raw provider message.
- **Impact:** contradicts the SAF adapter's “Provider exception messages/URIs never cross this boundary” contract and the plan's snapshot/path privacy constraints. UI masks many URI/path strings, but that is downstream and heuristic; a private title without slashes is displayed unchanged. **No network/report exfiltration demonstrated:** maintenance still has no ASR-report port and the page is internal. This is nevertheless a real privacy-boundary exception, not merely untranslated wording.
- **Recommendation:** do not pass arbitrary provider cancellation messages through. Translate to a safe, typed cancellation/failure without retaining provider message/cause in published state; distinguish a latched operation cancellation from unsolicited provider cancellation. Apply equivalent handling to all adapter boundaries.
- **Missing test:** adapter-level controlled query/open/read/close cancellation exceptions containing sensitive marker strings; assert marker absent from snapshot/UI/error details and actual owner retained until stream cleanup. These require an Android adapter fixture or a safely factored adapter seam; the current host Map sources do not exercise SAF sanitization.

### F3 — Medium presentation/recovery: late cancel can overwrite a completed FAILED result during finalization

- **Location:** `android/app/src/org/llmasr/minimal/ModelOperationControl.java:33–38`; `ModelManagementController.java:159–185` (terminal then notify at `117–118`).
- **Trigger:** a failed import/verify has published FAILED, but worker has not yet reached `finish()`. Unlike success, failure has no committed-terminal flag in control. A UI cancellation already queued from the preceding active render can still succeed, changing phase to CANCELLING. `finish()` sees a nonterminal phase and replaces the original detailed failure with generic `任务未启动或异常终止`. A synchronous listener offers a deterministic way to expose this interval.
- **Impact:** loss of accurate terminal reason/outcome and misleading cancellation feedback; **not premature owner release or bad-file publication**. The already measured cleanup/file data remain mostly intact.
- **Recommendation:** arbitrate all terminal outcomes, not only success. Commit FAILED/CANCELLED under a short control transition before publishing their snapshots; reject late cancel after any terminal decision while retaining owner until final cleanup. Alternatively enforce terminal immutability consistently in requestCancel and finish, not just a UI button check.
- **Missing test:** pause after a real failed controller terminal publication but before finalizer, request cancellation, then finish; require original FAILED reason and cleanup conclusion to remain intact. Existing `tests/ModelManagementControllerTest.java:80–81` only tests late cancel after completed success.

### F4 — Low/Medium presentation: all-local reuse reports zero, and failed preflight loses its space budget

- **Location:** `android/app/src/org/llmasr/minimal/ModelRepository.java:317–325,335–358`; `ModelManagementController.java:143`; `ModelManagementActivity.java:171–173`.
- **Trigger A:** import with every local official file already correct. Checking-existing emits reused=0; reusable entries are skipped; no copy event publishes the accumulated `reused`; final verification intentionally does not update reusedBytes. Snapshot/UI therefore say “已复用 … 0 bytes” although the entire model was reused. The same occurs when orphan parts alone are reclaimed.
- **Trigger B:** aggregate free-space preflight fails at repository line 340. `totalCopy + SPACE_PAD` is local-only, never published; the failure and terminal snapshot retain no required budget. The page displays unknown “本次仍需空间” outside COPYING, including the very error where current versus required space matters most.
- **Impact:** MM-03/MM-04/MM-07/MM-08 recovery and progress contracts not fully met; no copying is mis-authorized by these display gaps. Copy cumulative counters themselves are correctly accumulated across newly copied files; reuse is not counted as new copying.
- **Recommendation:** publish an explicit immutable planning result (copy budget, reused bytes) before reclaim/budget failure and before all-reused final verification. Retain that plan in terminal state. Keep copy/part-hash/final-hash stages separate; do not replace them with an installation percentage.
- **Missing test:** controller snapshot for fully reused import and parts-only reuse; mixed reused/new multi-file exact cumulative counters and monotonicity; aggregate-space rejection must include usable and required bytes. `tests/ModelRepositoryCancelTest.java:151–161` checks bounds for a **single** file and never asserts exact reuse or multi-file stage totals. Existing empty-source reuse at lines 101–102 verifies opens, not reported bytes.

### F5 — Medium evidence gap: listener-lock test swallows its own failing assertion

- **Location:** `tests/ModelReadinessTest.java:150–158`; production `android/app/src/org/llmasr/minimal/ModelReadiness.java:157–163`.
- **Trigger:** mutate readiness so a notification runs while its monitor is held. Test listener starts another thread to take a snapshot, joins, then `check(!t.isAlive())` throws AssertionError. Production intentionally catches **Throwable** from listeners, swallowing that assertion. The main test does not inspect an independent failure holder afterward. Thus a lock regression can still print PASS (and increment its check count).
- **Impact:** this particular advertised callback-outside-lock evidence is not defect-catching. It does **not** negate all the controller/repository tests, whose worker errors and filesystem outcomes are observed outside callbacks.
- **Recommendation:** capture callback outcome in an AtomicReference/AtomicBoolean and assert after the production API returns, outside its listener exception guard. Keep timed join/final teardown. Add a compiled behavioral mutation holding the readiness monitor during notification and require this test to kill it.
- **Related small test weakness:** weak listeners created only as temporaries in `ModelReadinessTest.java:83–88` have no strong test reference, permitting GC-dependent notification counts. Hold them strongly for the subscription duration.

### F6 — Low, provenance limitation: standalone APK checker trusts detached text reports

- **Location:** `scripts/check-minimal-apk.py:23–33,65–66`; generation order in `scripts/build-minimal-apk.sh:67–86`.
- **Trigger:** run the checker alone on a replaced APK while `permissions.txt`, `badging.txt`, `signature.txt`, manifest/method trees are stale from an earlier APK. The script reads the current ZIP but does not bind those text reports to its SHA or re-run aapt/apksigner. Merely finding “Verifies” in the detached signature report is not verification of that current ZIP.
- **Impact:** do not treat a standalone checker PASS as independently proving version/permission/export/signature provenance. **Not a demonstrated defect in the parent full-build flow:** that script signs, regenerates the reports serially, hashes outputs and then invokes the checker, which is the appropriate present execution context.
- **Recommendation:** bind derived reports to an APK SHA/run manifest, or generate them from the checked APK in the checker. Also compare recorded build-input hashes to the frozen review input at parent completion; hashes generated after compilation alone do not establish a source freeze.
- **Missing test:** stale report/current APK mismatch must fail. `tests/model_android_source_test.py:43–64` correctly exercises the extracted activity-tree clause and mutations on synthetic text, not archive-to-report provenance.

## Positive safety assessment and what the tests actually connect

1. **Each publish is independently cancelled/arbitrated.** `ModelOperationControl.java:41–44` checks cancel afresh on every reservation; `ModelRepository.java:410–418` reserves before rename and checkpoints afterward. No monitor spans provider read/close/hash/rename. Cancel wins first => no current rename; reservation wins first => that verified file may finish, next boundary stops. `tests/ModelRepositoryCancelTest.java:93–114` covers the second file and a real rename seam, not just an unused control flag.
2. **READY is not published after winning cancellation.** Explicit verify obtains a token before IO (`ModelManagementController.java:97`); import acquires it on the first FINAL_VERIFY event before hashing (`81–82`; repository `233–235`). `trySucceed` checks cancellation and commits the identity/epoch-bound token under the short arbitration monitor (`ModelOperationControl.java:47–51`, `ModelReadiness.java:80–85`). Controller inspects actual leftovers before commit. Tests cover cancel after real final hash and epoch invalidation (`tests/ModelManagementControllerTest.java:93–111`), and direct success-first/cancel-first ordering (`tests/ModelOperationControlTest.java:155–170`). Missing: a controller-level latch proving success-first while finalization is still pending; direct control ordering is not that integration test.
3. **Preflight does not require sources for reusable finals.** It hashes existing size-matching files, checks all needed sources before any copy, reclaims safe parts before aggregate budget, then checks per-file space. Bad existing SHA explicitly revokes cached READY even if missing source aborts planning (`ModelRepository.java:320`; controller `77`). Safe valid same-epoch READY survives permission failure before mutation; tests distinguish these paths (`ModelManagementControllerTest.java:113–132`). Reclaim failure propagates; correct finals are not predeleted to create space. IO is bounded to a 1 MiB buffer; actual byte limit/SHA and clean closes precede rename.
4. **Deletion is confined and partial failure is measurable.** Whole managed-path plan validated before first delete (`ModelRepository.java:452–459`), path rechecked outside ordinary delete-failure collection (`464–466`), no recursive traversal, unknown children retained, epoch revoked before deletion. Controller rechecks confirmation both at admission and worker execution. Real ordinary fault/retry tests check retained bytes and free-space snapshot (`tests/ModelManagementControllerTest.java:140–155`), not only a fake success flag. Directory removal failure is returned as modelDirRemoved=false but is not exposed in controller state; minor additional reporting limitation, not loss of managed-file safety.
5. **Owner covers close, cleanup and inspection.** Admission first acquires shared TaskCoordinator ownership, then creates operation identity. Abandoned admission/submission invokes once-only finalization before release (`TaskCoordinator.java:44–62`, `RequestRunner.java:49–67`). Busy leaves cached model state unchanged. Actual blocked close/cleanup tests attempt competing operations and observe cancellation residue (`tests/ModelManagementControllerTest.java:157–187`). No timeout forcibly releases production ownership. Direct coordinator tests retain the original inference/recording policy.
6. **Real production isolation wiring is present.** AppGraph constructs a single readiness/coordinator/repository/access and injects the management five-argument constructor (`AppGraph.java:39–48,108`); App and IME consume the same ModelAccess (`AsrOperation.java:153`, `ImeBackend.java:20`), and IME controller receives graph.coordinator (`AsrImeService.java:36`). Model controller uses private runner State, no-op report/cleanup/preflight, and MAINTENANCE. `RequestRunner.java:59,71–87` protects text and all report outcomes. The diagnostic controller tests connect runner text methods to the actual AppState and report counters (`tests/ModelManagementControllerTest.java:27–48`), so they are **not disconnected transcript assertions**. Status intentionally goes to a private test port. They are not Android AppGraph runtime tests or actual on-disk `last-result.json` integration tests; production wiring was independently traced, not executed.
7. **Stage/failure caveats:** part hash and final hash are separate from copy; copy100 never commits READY. Missing/size failures mostly have generic string errorCode and no structured failedFiles entry (SHA failures do). Live deletion failed count is intentionally terminal-only (`ModelUiText.java:61`), not MM-04's requested live count. Those and F4 are presentation/recovery gaps, not evidence that corrupt data reaches READY.

## Evidence quality and provenance

- Retained `.work/model-core-repair/final-green.log` contains 15 suite PASS lines totalling **847** checks; `.work/model-android/host-final.log` includes those plus **100** helper checks and source-policy checks. `.work/model-android/javac-final.log` states all-source SDK35 compile only. All are **read evidence from other runs**.
- `tests/PartRecoveryTest.java:117–162` mutates the real aggregate reclaim/budget order, requires successful mutant and driver compilation, then demands expected low-space failure from actual import. It does not accept a compiler error as red. Its header has stale reversed free-space/nonzero-exit descriptions; actual fixture at lines 75–94 and driver at 228–233 implement the correct free-space behavior and use zero exit for expected mutant failure. Mutation is run by the normal host script.
- Retained `.work/model-core-repair/mutant/ModelOperationControl.java` differs only by removal of cancel rejection in tryReservePublish. Retained red log fails check13 at production test line57. This is a relevant behavioral mutant, but its compilation/run wrapper is not incorporated in `scripts/test-minimal-apk.sh`; I did not independently compile it. `repeat-and-mutation.log` contains older **209**-check controller repeats; the later `final-repeat.log` contains three **223**-check repeats. Do not conflate these stages.
- New repository gates time out in 3–4s; workers join with bounds and finally release gates (`tests/ModelRepositoryCancelTest.java:47–63`); suite runner uses compiler/suite timeouts (`scripts/test-minimal-apk.sh:12,29,46`). A few older tests assert from asynchronous workers without a general failure collector, and F5 proves one concrete swallowed-assertion gap. “947” is a count, not proof of all interleavings or adapter behavior.
- SAF duplicate-name/type/10000-row and per-row cancellation behavior is actual adapter code, but the host sources cannot exercise a real cursor/provider. Existing repository enumeration-failure test is honestly labelled as propagation, not cap execution (`tests/ModelRepositoryTest.java:250–263`). Android provider behavior remains unverified.
- Build script includes all `android/app` files and all immediate test files in build-input-sha256 (`scripts/build-minimal-apk.sh:79–84`), plus updated scripts. Frozen source input does not include the external fixed model manifest/config assets or all native/build dependencies; final build provenance still needs the parent check.

## Hash checks performed (read-only)

Python `hashlib.sha256` was used only to read/hash files; no test module was imported or run.

- Frozen `.work/model-android/review-input-sha256.json`: **61 / 61 match**, checked at beginning and end of source review, **0 mismatches**.
- Manifest SHA256: `2769f2c6669c9c2f3877c2fea715d04ffd7e71ae20ef3a8ee9ed4a3681d836d9`.
- Baseline snapshot: **44 / 44 stored files match** `.work/model-management-baseline/source-sha256.json`, none missing. Against frozen input, 17 shared entries changed and 17 entries are new.
- Baseline hash-list SHA256: `daf1886f30c7598b0cf8e32cd73ce3d5d669f228415cd8f6d13783a1f2afe6fc`.
- Full plan SHA256: `39bc2b47cc6ad0377ac8090315fd5a76e1d8424468e64bfb22f160563eb41282`.
- Core repair report SHA256: `2fa5e1184459bf61d0d32df1d7d446dfa5960e224f394356c2a61364fb6795b9`.
- Core final-green log SHA256: `e9074cfba1e7313c157d07e1065712e2aa6d4b3afe27441f45d35f35e357245f`.
- Cancel-mutant red log SHA256: `34938e5d518972df28ce347cf12ac30bf71cff16486f45c21b891b8bfe9b6290`.
- Android writer host-final log SHA256: `f45f014fdd7727b4594268137199615e7e056749e316be972f101533af0fd705`.
- Android writer javac-final log SHA256: `8b70237a490261f17a9c4b1eb3a800244bf121b09bc1424e86d7c4d627bcbff1`.
- `git diff --cached --name-only`: empty on both observations. No source edits by this reviewer. No hash/signature claim about the concurrently generated APK.

## Residual risks / parent follow-up

1. Close F1–F3 or explicitly triage their contract impact; F2 should be fixed before claiming the provider privacy boundary is complete. None is a demonstrated network leak or concurrent file corruption.
2. Fix F5 before citing listener-lock regression coverage. Add exact multi-file/reuse/budget tests for F4 and controller success-first/failure-finalization tests rather than more disconnected counters.
3. Android SAF/provider cancellation/permissions, framework path aliases (including canonical root behavior on actual Android storage), UI lifecycle, real App/IME mutual exclusion, and real last-result preservation remain device/integration acceptance work. A Java SDK compile and synthetic aapt tree cannot establish them.
4. Cooperative cancellation legitimately waits indefinitely for provider IO/close; no background continuation, process survival, IO deadline, fsync durability, seven-file transaction or hostile concurrent filesystem writer protection is promised. File path checks are not fd-relative nofollow atomic operations; private storage plus the shared owner is the assumed boundary.
5. Parent must finish its build and reconcile the final APK/report/build-input hashes with this frozen input. Do not reuse older signed/DEX outputs as this review's artifact evidence.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Read-only independent source review identifies six concrete findings with file/line triggers, severities, recommendations and missing tests; documents safety paths, evidence limitations, residual risks and 61/61 frozen plus 44/44 baseline hash matches."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/44a6d42a/.work/model-management-core-independent.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {"command": "Read-only nl/sed/cat/grep/find/ls/wc and baseline diff inspection", "result": "passed", "summary": "Read full plan, requested core, related tests/mutations, wiring and provenance scripts; inspected retained evidence without executing it."},
    {"command": "Python hashlib verification of review-input-sha256.json at review start and end", "result": "passed", "summary": "61 frozen entries match; zero mismatches on both checks."},
    {"command": "Python hashlib verification of .work/model-management-baseline/source-sha256.json", "result": "passed", "summary": "44 stored baseline entries match; none missing."},
    {"command": "git diff --cached --name-only", "result": "passed", "summary": "Empty on both observations; reviewer did not stage files."},
    {"command": "Tests/builds/Android runtime", "result": "not-run", "summary": "Explicitly prohibited for this reviewer; retained logs are attributed evidence only."}
  ],
  "validationOutput": [
    "Frozen manifest SHA256 2769f2c6669c9c2f3877c2fea715d04ffd7e71ae20ef3a8ee9ed4a3681d836d9; 61/61 inputs match.",
    "Baseline 44/44 match; no source edits; only this report artifact written.",
    "847/947 PASS and javac logs were read, not independently executed or treated as Android acceptance."
  ],
  "residualRisks": [
    "Provider cancellation messages bypass sanitization; unsafe managed part readiness differs between lazy ASR and management.",
    "Late failure/cancel finalization can lose the detailed result; reuse/space-plan presentation incomplete.",
    "Listener-lock test swallows assertions; standalone APK checker trusts detached reports.",
    "No Android runtime/provider/device or independent test/build execution; parent must reconcile final build provenance.",
    "Provider IO may block; no fsync/seven-file transaction or hostile external writer protection."
  ],
  "noStagedFiles": true,
  "diffSummary": "Report artifact only; production source, tests, scripts and build outputs untouched by reviewer.",
  "reviewFindings": [
    "Medium F1: android/app/src/org/llmasr/minimal/ModelAccess.java:14-19 — successful lazy hash ignores unsafe managed parts checked by management inspection.",
    "Medium F2 privacy/error boundary: android/app/src/org/llmasr/minimal/SafModelSource.java:62-64,74-91 — provider CancellationException messages bypass sanitization into model state.",
    "Medium F3: android/app/src/org/llmasr/minimal/ModelManagementController.java:159-185 — late cancel after FAILED publication can replace detailed terminal failure.",
    "Low/Medium F4: android/app/src/org/llmasr/minimal/ModelRepository.java:317-358 — all-local reuse reports zero and failed preflight does not retain required-space plan.",
    "Medium F5 evidence: tests/ModelReadinessTest.java:150-158 — production listener guard swallows the lock-test AssertionError.",
    "Low F6 provenance: scripts/check-minimal-apk.py:23-33,65-66 — standalone check trusts report text not bound to current APK.",
    "No demonstrated blocking model-file corruption, unsafe deletion or premature owner release in the reviewed production shared-owner path; not unconditional delivery approval."
  ],
  "manualNotes": "Authoritative output path honored. Read docs/model-management-plan.md in full. No tests/build/device/network/delegation/commit; parent's concurrent build changes only outputs and has not been accepted as runtime proof."
}
```
