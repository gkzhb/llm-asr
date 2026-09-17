# Model management accepted review fixes — completed host/compile checkpoint

## Scope and disposition

Read **all** of `reports/review/model-management-disposition.md`, both original independent reports, and `docs/model-management-plan.md`. Applied the parent-accepted batch only. No APK/device acceptance is claimed; the preserved build947 APK remains a review baseline, not the rebuilt output of these fixes.

## Findings and fixes

- Closed P1 R1: android/app/src/org/llmasr/minimal/ModelManagementController.java:127-138,174-230 — INSPECT retains explicit user-operation identity/outcome/error/cleanup/delete stats/plan; independent inspectBusy/inspectError refresh inventory only.
- Closed P2 R2/F2: android/app/src/org/llmasr/minimal/SafModelSource.java:31-74 and ModelProviderBoundary.java:14-28 — every query/cursor/URI/open/read/close provider call executes a sanitizing wrapper; provider-authored Java or app-typed cancellation is never trusted.
- Closed P2 R3/F3: android/app/src/org/llmasr/minimal/ModelOperationControl.java:53-56 and ModelManagementController.java:184-230 — terminal decisions reject late cancellation before callbacks without releasing the actual owner; includes executor-rejected finalization.
- Closed Medium F1: android/app/src/org/llmasr/minimal/ModelAccess.java:14-22 and ModelRepository.java:185-191 — lazy/cached READY validates final and managed-part boundaries; safe regular orphans remain untouched.
- Closed P2 R4/F4: android/app/src/org/llmasr/minimal/ModelManagementState.java:37-48 and ModelRepository.java:342-367 — immutable exact copy/reuse/required/available-at-check plan published before rejection and all-local verification, retained through INSPECT.
- Closed P3 R5 and scoped completeness: android/app/src/org/llmasr/minimal/ModelUiText.java:55-92 and ModelRepository.java:485-495 — omit terminal live-file denominator, expose live deletion failures/successes and per-file reuse/failure observations.
- Closed Medium F5 evidence defect: tests/ModelReadinessTest.java:148-181 — strong listener references, bounded worker teardown and failure assertions outside production listener guard; compiled monitor-held mutant killed.
- Closed Low F6 detached-report gap: scripts/apk_report_binding.py:10-36, scripts/check-minimal-apk.py:15-17 and scripts/build-minimal-apk.sh:73 — APK SHA plus hashes of all consumed detached metadata reports checked before archive acceptance; actual checker rejects stale/modified fixtures.

### Intentional semantics and self-review

- INSPECT has its own control operation but does not install that ID into the retained user record. `inspectBusy`/`inspectError` are transient; inventory/readiness refresh, retained explicit history does not. A new IMPORT/VERIFY/DELETE supersedes history. Busy refusal still leaves cached state untouched.
- Terminal arbitration is callback-free and contains no IO. The owner remains held during actual stream close, cleanup, terminal inspection and runner finalization. Both reentrant and separate-thread late cancellation are tested, including FAILED/CANCELLED/SUCCEEDED and executor rejection.
- Each SAF framework/provider invocation is inside the production pure-Java sanitizing supplier wrapper; application gate checks/validation are outside. Provider `CancellationException`, provider-created app `CancelledException` with malicious cause/suppressed details, IOException, SecurityException, runtime failure and LinkageError all become fixed-message IOException. Genuine latched gate cancellation stays typed. This is supplier/stream execution proof, not real Android cursor execution.
- Import plan records copy bytes, reusable bytes/names, conservative required bytes and **the single actual available-at-check reading after reclaim**. Before reclaim/source rejection the completed copy/reuse plan has availableAtCheck=-1, honestly unknown. All-local reuse needs zero extra space, preserves zero opens, and does not introduce a padding-only refusal. Historical plan values are not recomputed from terminal free-space readings.
- Per-file strings are explicitly “recent task” observations, separate from current inventory/SHA readiness. Terminal live-file rows are omitted rather than mixing managed-part item counts with manifest file count.
- The checker now validates a SHA binding for current APK plus permissions/badging/signature/manifest/method/native provenance reports. Build generates binding serially after successful tool reports. Tests execute the **actual checker and binding generator**, with self-contained fake metadata/native/manifest payloads; no production permission/signature/manifest condition was relaxed. This keeps the normal host suite runnable before any APK exists.
- `PartRecoveryTest` mutation still tests early aggregate refusal before reclaim; adapted its source mutation to the new captured-plan implementation and corrected misleading comments. Listener assertion count intentionally drops 35→33: previously three callback-local assertions were swallowed; now one outside-guard assertion plus an exact callback-count assertion path validates the three callbacks safely. No count target drove changes.
- Self-review found an additional same-scope late-cancel case in executor-rejected `finish()` and a LinkageError publication route; each was reproduced red before correction. Own complete diff was read, then final incremental corrections reread against the accepted scope/spec. Untouched `tests/p0/test_audio_contract.py` was excluded from own-diff bookkeeping (initial snapshot covered immediate tests only); it was never edited.

## Deterministic evidence

All paths in this section are under `.work/model-review-fixes/` unless qualified.

| Focus | Red evidence | Final green evidence |
|---|---|---|
| R1 history after cancellation/SHA failure/cleanup failure/partial deletion | `red-history.log`, exit 1, inspect admission replaced user ID | `final-host.log`: history PASS; fresh inventory + same identity/outcome/errors/cleanup/delete stats, even subsequent unsafe inspect |
| R3 terminal cancellation | `red-terminal.log`, exit 1; `red-abandoned-terminal.log`, exit 1 | terminal/window PASS, reentrant + bounded cross-thread failure/cancel/success, executor rejection; repeated window 3× |
| F1 lazy managed part | `red-boundary.log`, exit 1, unsafe part permitted continuation | boundary PASS, link/directory invalid for fresh and cached READY, external target preserved, safe regular orphan retained |
| R5 deletion formatting | `red-deletion.log`, exit 1 | deletion PASS for 1/7/14 item terminal fixtures; liveDeletion PASS with real ordinary deletion fault |
| R4/F4 import planning | `red-planning.log`, exit 1, all-local reuse was zero | planning PASS; all-reuse/parts-only zero opens, mixed exact cumulative multi-copy totals, immutable map/list, space rejection retains 17 available / 67,108,867 required |
| R2/F2 provider boundary | `red-provider.log`, exit 1 on unsanitized cancellation; `red-provider-linkage.log`, exit 1 | provider suite PASS; fixed message/no provider cause or suppressed data, snapshot/UI isolation, blocked close retains owner, real gate wins independently |
| F5 listener evidence | compiled monitor-held mutation: exit 1 behavioral assertion, compile exit 0 | final normal readiness suite PASS; outside-guard assertion and strong references |
| F6 APK binding | `red-binding.log`: test exit 1 because stale APK checker incorrectly returned 0 | final actual checker: bound fixture exit 0; stale APK, all six modified reports and missing binding exit 1 |

### What each compiled mutant catches

- `mutants/readiness-monitor-uox59z_b/`: adds `synchronized` to production `ModelReadiness.notifyChange`; compilation exit 0, `ModelReadinessTest` exits 1 at **readiness listener lock regression** after bounded joins outside the production guard.
- `mutants/provider-cancel-trust-nrwyycng/`: restores Java CancellationException passthrough at the real provider wrapper; compilation exit 0, provider suite exits 1 with sensitive sentinel exception. Not a source-string-only mutation.
- `mutants/terminal-cancel-vuzl0z24/`: removes the production terminal decision latch; compilation exit 0, controller test exits 1 at **late reentrant cancel must not overwrite terminal**.
- Existing `.work/red/` PartRecovery mutant: adds pre-reclaim aggregate refusal; mutant/driver compilation must succeed. Driver intentionally exits **0 only when expected insufficient-space failure occurs**; unexpected import success is exit 2. The surrounding production suite confirms production import succeeds before and after this negative control.

Every new Java suite runs under timeout 45; compiler timeout 60; Python mutant child compile/run timeouts 30/20. Gate release and worker teardown are bounded. Mutant compilation errors/timeouts do not count as red. Listener failures are observed outside production listener exception handling.

## Exact commands, exits, changed files and residual risks

The structured report below contains the complete changed-file list and command results. Final host and SDK35 compile both exited **0**; no full APK build was run. Parent should freeze/review the authoritative dirty source and rebuild serially, then reconcile complete build-input hashes with that freeze. `final-input-sha256.json` is only this writer's changed-file manifest, not a substitute for parent build provenance.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Concrete path/severity findings and bounded implementation evidence for accepted R1-R5/F1-F6; deterministic red/green logs, compiled semantic mutants, final host and actual SDK35 javac exits 0; candid residual risks below."
    }
  ],
  "changedFiles": [
    "android/app/src/org/llmasr/minimal/ModelAccess.java",
    "android/app/src/org/llmasr/minimal/ModelManagementActivity.java",
    "android/app/src/org/llmasr/minimal/ModelManagementController.java",
    "android/app/src/org/llmasr/minimal/ModelManagementState.java",
    "android/app/src/org/llmasr/minimal/ModelOperationControl.java",
    "android/app/src/org/llmasr/minimal/ModelPageSession.java",
    "android/app/src/org/llmasr/minimal/ModelProviderBoundary.java",
    "android/app/src/org/llmasr/minimal/ModelRepository.java",
    "android/app/src/org/llmasr/minimal/ModelUiText.java",
    "android/app/src/org/llmasr/minimal/SafModelSource.java",
    "scripts/apk_report_binding.py",
    "scripts/build-minimal-apk.sh",
    "scripts/check-minimal-apk.py",
    "scripts/test-minimal-apk.sh",
    "tests/ModelProviderBoundaryTest.java",
    "tests/ModelReadinessTest.java",
    "tests/ModelReviewFixTest.java",
    "tests/PartRecoveryTest.java",
    "tests/apk_report_binding_test.py",
    "tests/model_review_mutation_test.py",
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/186ebfc3/.work/model-management-review-fixes.md"
  ],
  "testsAddedOrUpdated": [
    "tests/ModelProviderBoundaryTest.java",
    "tests/ModelReadinessTest.java",
    "tests/ModelReviewFixTest.java",
    "tests/PartRecoveryTest.java",
    "tests/apk_report_binding_test.py",
    "tests/model_review_mutation_test.py"
  ],
  "commandsRun": [
    {
      "command": "PATH=/nix/store/4dqvcqjnhj6n29r6sh5wwrz58540gbfn-openjdk-17.0.20.1+1/bin:$PATH timeout 180 bash scripts/test-minimal-apk.sh (before production fixes)",
      "result": "passed",
      "summary": "Exit 0; .work/model-review-fixes/baseline-host.log; original 947 named checks plus source checks reproduced."
    },
    {
      "command": "timeout 30 javac --release 8 -cp .work/build/minimal-apk-tests -d .work/build/minimal-apk-tests tests/ModelReviewFixTest.java; timeout 15 java -cp .work/build/minimal-apk-tests ModelReviewFixTest ISSUE",
      "result": "failed",
      "summary": "Expected deterministic pre-fix exit 1 for history, terminal, boundary, deletion and planning. red-ISSUE.log has specific behavioral assertion, not compiler error."
    },
    {
      "command": "timeout 30 javac --release 8 -cp .work/build/minimal-apk-tests -d .work/build/minimal-apk-tests android/app/src/org/llmasr/minimal/ModelProviderBoundary.java tests/ModelProviderBoundaryTest.java; timeout 20 java -cp .work/build/minimal-apk-tests ModelProviderBoundaryTest",
      "result": "failed",
      "summary": "Expected pre-fix cancellation passthrough exit 1 in red-provider.log; later LinkageError regression exit 1 in red-provider-linkage.log. Final production run passes both."
    },
    {
      "command": "timeout 45 python3 tests/apk_report_binding_test.py (before binding verification)",
      "result": "failed",
      "summary": "Expected exit 1: actual checker incorrectly exited 0 on stale APK; red-binding.log. Final self-contained synthetic version passes all rejection cases."
    },
    {
      "command": "timeout 15 java -cp .work/build/minimal-apk-tests ModelReviewFixTest terminal (executor-rejection regression before correction)",
      "result": "failed",
      "summary": "Expected exit 1 late reentrant cancellation assertion; red-abandoned-terminal.log. Corrected finalizer now decides terminal before listener publication."
    },
    {
      "command": "PATH=/nix/store/4dqvcqjnhj6n29r6sh5wwrz58540gbfn-openjdk-17.0.20.1+1/bin:$PATH timeout 240 bash scripts/test-minimal-apk.sh",
      "result": "passed",
      "summary": "Exit 0; final-host.log. Every existing host suite, source-policy checks, 7 focused review groups, provider boundary suite, 3 compiled mutants and actual checker fixtures pass."
    },
    {
      "command": "PATH=/nix/store/4dqvcqjnhj6n29r6sh5wwrz58540gbfn-openjdk-17.0.20.1+1/bin:$PATH ANDROID_HOME=/nix/store/425pl60rnjsr0lgs3ibcdijifvmgrxx5-androidsdk/libexec/android-sdk timeout 75 bash scripts/compile-android-java.sh",
      "result": "passed",
      "summary": "Exit 0; final-javac.log. Actual SDK35 all-source javac --release 8; only existing deprecated-API notes. No packaging/native/signing."
    },
    {
      "command": "timeout 30 bash -c 'for n in 1 2 3; do timeout 8 java -cp .work/build/minimal-apk-tests ModelReviewFixTest window || exit; done'",
      "result": "passed",
      "summary": "Exit 0; repeat-window.log, three repeated bounded cross-thread post-terminal/pre-finalize cases."
    },
    {
      "command": "bash -n scripts/{test-minimal-apk,build-minimal-apk,compile-android-java}.sh; git diff --check; git diff --cached --name-only",
      "result": "passed",
      "summary": "All exits 0; syntax/whitespace checks clean, cached names empty."
    },
    {
      "command": "Full APK build / device / network / microphone / native operations",
      "result": "not-run",
      "summary": "Explicitly excluded. Parent owns final build and review."
    }
  ],
  "validationOutput": [
    "All pre-existing host suites passed; original named count changes from 947 to 945 only because three swallowed callback assertions became one outside-guard aggregate assertion, not removed semantic coverage. New groups intentionally report semantic PASS labels rather than inflate counts.",
    "final-host.log SHA256 a654fb3fc24a2f165fa3f353f91b94e48354e8512e8019603bc4d9bec8b17448",
    "final-javac.log SHA256 8b70237a490261f17a9c4b1eb3a800244bf121b09bc1424e86d7c4d627bcbff1",
    "own-complete.diff SHA256 1d70d664173546d53b964b9ac5be50c2af3c493abbc561660122401f8d754982; reviewed own entire diff and final incremental corrections against accepted disposition/plan.",
    "final-input-sha256.json SHA256 9a1565f1ce086a8c95a3ec1f298f2c812e82eab62ef2a109cada7b30067bf6e4; hashes cover this writer's 20 changed production/test/script files, not a complete build-input freeze."
  ],
  "residualRisks": [
    "No full APK build, DEX/resource/signature regeneration, device, microphone, native inference or Android runtime proof in this batch; parent must rebuild and perform focused review against final hashes.",
    "Provider tests execute the factored production Java boundary and actual stream/owner path, not Android ContentResolver/Cursor or real OperationCanceledException. Real SAF enumeration cap, duplicates, permissions, cursor lifecycle, Activity rotation/Home/lock/picker delivery and visual/accessibility behavior remain untested Android boundaries.",
    "APK binding detects stale/modified detached inputs, not a malicious actor rewriting both artifacts and binding. Synthetic signature/manifest/native fixtures are deliberately not signing/runtime evidence. The standalone checker still relies on serially generated tool reports; parent must actually run apksigner/aapt and preserve source freeze.",
    "Cooperative cancellation still waits for blocking provider IO/close/cleanup; no timeout-driven owner release. No fd-relative nofollow atomic protection against hostile concurrent filesystem writers, fsync durability, seven-file transaction or process-survival guarantee.",
    "Scoped per-file observations cover reuse, active stage, final SHA and structured missing/size/SHA failures; arbitrary filesystem/provider errors remain generic operation failures rather than a complete typed error taxonomy. Empty model-directory removal failure is still not separately exposed in UI.",
    "Historical activeOperationId is explicitly documented as current-or-retained user identity; actual active owner/identity remains ModelOperationControl. Inventory fields can become unknown after failed inspection while retained user result stays intact. External-owner display remains the accepted generic App/IME/other-page hint.",
    "Fatal VM Errors other than LinkageError are not broadly caught/sanitized by the provider wrapper; they are not published as model snapshot failures by the controller. This is not a claim to recover from VM failure."
  ],
  "noStagedFiles": true,
  "diffSummary": "20 bounded production Java/test/build-input script files changed. Separate inventory refresh/history, all-terminal arbitration, provider boundary sanitization, complete lazy managed-path checks, immutable import planning, deletion/per-file presentation, listener evidence repair and APK report binding. Docs/root plans/reports/dist/native/RecordingControl untouched by writer.",
  "reviewFindings": [
    "Closed P1 R1: android/app/src/org/llmasr/minimal/ModelManagementController.java:127-138,174-230 — INSPECT retains explicit user-operation identity/outcome/error/cleanup/delete stats/plan; independent inspectBusy/inspectError refresh inventory only.",
    "Closed P2 R2/F2: android/app/src/org/llmasr/minimal/SafModelSource.java:31-74 and ModelProviderBoundary.java:14-28 — every query/cursor/URI/open/read/close provider call executes a sanitizing wrapper; provider-authored Java or app-typed cancellation is never trusted.",
    "Closed P2 R3/F3: android/app/src/org/llmasr/minimal/ModelOperationControl.java:53-56 and ModelManagementController.java:184-230 — terminal decisions reject late cancellation before callbacks without releasing the actual owner; includes executor-rejected finalization.",
    "Closed Medium F1: android/app/src/org/llmasr/minimal/ModelAccess.java:14-22 and ModelRepository.java:185-191 — lazy/cached READY validates final and managed-part boundaries; safe regular orphans remain untouched.",
    "Closed P2 R4/F4: android/app/src/org/llmasr/minimal/ModelManagementState.java:37-48 and ModelRepository.java:342-367 — immutable exact copy/reuse/required/available-at-check plan published before rejection and all-local verification, retained through INSPECT.",
    "Closed P3 R5 and scoped completeness: android/app/src/org/llmasr/minimal/ModelUiText.java:55-92 and ModelRepository.java:485-495 — omit terminal live-file denominator, expose live deletion failures/successes and per-file reuse/failure observations.",
    "Closed Medium F5 evidence defect: tests/ModelReadinessTest.java:148-181 — strong listener references, bounded worker teardown and failure assertions outside production listener guard; compiled monitor-held mutant killed.",
    "Closed Low F6 detached-report gap: scripts/apk_report_binding.py:10-36, scripts/check-minimal-apk.py:15-17 and scripts/build-minimal-apk.sh:73 — APK SHA plus hashes of all consumed detached metadata reports checked before archive acceptance; actual checker rejects stale/modified fixtures."
  ],
  "manualNotes": "Batch complete and ready for parent source freeze/focused review and full rebuild. The pre-existing dirty tree is authoritative; no reset/clean/staging/commit/push/delegation. Report only at the authoritative run-specific output path. Derived logs/classes/mutants/fixtures are under .work (existing PartRecoveryTest also uses .work/red and Java temp fixtures). Initial command attempt lacked Java in PATH (exit 127, not defect evidence); rerun used installed JDK17 directly with no network. A broad SDK discovery find timed out after the binding test passed; explicit installed SDK path resolved it. No outstanding commands."
}
```
