# Final narrow read-only publication review

## Verdict / scope

**E1 (prior P2 published-A/pending observation) is closed in the frozen production source. E2 (prior P3 zero-space reuse fixture gap) is closed in the frozen test source. No new blocker or callback/IO-ownership regression was found in this exact three-file delta.** These are static-review conclusions, not a claim that this reviewer executed or witnessed a green test/build.

Reviewed only the final publication/zero-space changes and the unchanged immediate controller, cancellation, readiness, snapshot and runner connections needed to assess them. Prior scope and safety dispositions remain those of `reports/review/model-management-fix-safety-review.md` and `reports/review/model-management-fix-evidence-review.md`; this is not a renewed broader review or additional polish request.

No tests, builds, checker/Java execution, device/network activity, delegation, staging, commits or source edits were performed. The concurrent parent build and its moving outputs were not inspected or certified. Only read/list/hash/in-memory diff operations and this authorized return artifact write were performed.

## Exact delta and frozen-input verification

Both manifests have **69 entries with identical path sets**. Exactly these three hashes differ; the other **66 are unchanged**:

| Path | Previous frozen SHA-256 | Current frozen SHA-256 |
|---|---|---|
| `android/app/src/org/llmasr/minimal/ModelRepository.java` | `688dd928945d3cd707c72c8d2bbade846f0b09e1fa30ee4f80c6adf49ee2d6b4` | `af28db4fc0a70a83ae880b8dc652506c446617dba4368989635389bc72ca95cd` |
| `tests/ModelReviewFixTest.java` | `c5dbb96ce813138098bf82e0843eb4d5b4913abbfdb8bb8a6c3180fd1d747667` | `4582d0145fd04b4c64c5d7e96d5a871d825d92dcd8902d4fe8d0add11f00735e` |
| `scripts/test-minimal-apk.sh` | `078fef29240981107456f9b7e99f2f788d26a2cbba46388e269aea4c36a228cc` | `128270a9a9dbd6f5d286a41ed068643b323820f3b82cdfdd94c3829bf4e77b31` |

To establish actual text differences rather than trusting the change description, reconstructed each prior file **in memory** from `.work/model-review-fixes/before/` plus the corresponding section of `.work/model-review-fixes/own-complete.diff` (the formerly new test starts from empty content). All three reconstructed SHA-256 values matched `parent-frozen-input-sha256.json` before comparison to current source. No reconstruction files or patch outputs were written.

The resulting exact delta is:

- Repository: two explanatory comment lines and one `progress.fileResult(...)` call immediately after successful `publishPart(part, dest)` and before `checkpoint(cancel)`; no other production change.
- Test: zero-space provider, strongly retained COPY-event listener, safe-part/no-COPY assertion and available-at-check=0 expectation in both reuse fixtures; new two-branch `publicationHistory()` and its command dispatch.
- Test runner: adds `publication` to the existing issue loop at line 50; no other runner change.

Initial and final independent byte-hash reads both reported **69 MATCH, 0 MISMATCH** against `.work/model-final-polish/frozen-input-sha256.json`. Final output:

```text
FINAL FROZEN INPUT HASH CHECK: 69 entries; 69 MATCH; 0 MISMATCH []
previous manifest SHA256: c3b2a777234d8805322f4b6cc1035d721b3a9c7d1fbb426cbc6512bc398c69a1
current manifest SHA256: 8c04f3de1f2fbdc93313d1d5ca866dbf9a6aefdfe8a91c384e820c03eaabdb0c
```

`git diff --cached --name-only` returned no paths on both observations. The authoritative output artifact is outside the 69 frozen source inputs.

## E1 — closed: publication survives later B failure and already-latched cancel

**Production:** `android/app/src/org/llmasr/minimal/ModelRepository.java:405–445`; `ModelManagementController.java:79–90,161–164,185–209`; `ModelUiText.java:55–59` (the latter two filenames share the same Android source directory).

1. Source and part output streams must close cleanly at repository lines 405–420. The part then passes size and real SHA checks at 425–428. Publish reservation still precedes the actual rename at 436–441. The new observation at **444** is reached only after `publishPart` returns successfully; failed reservation, SHA, close or rename does not manufacture publication evidence.
2. The exact stored text is `已复制并发布（临时文件 SHA 通过；仍需全清单核验）`. This records a completed per-file action, explicitly not a completed full-manifest proof.
3. An already-latched cancel does **not** discard that observation: `Progress.fileResult` directly calls `publishFileResult` (`ModelManagementController.java:89`). Its guard at 162 excludes only inactive/terminated operations, **not cancelRequested**. `ModelOperationControl.requestCancel():33–38` sets the cancel flag without terminating or releasing the active operation. The immutable map update therefore reaches controller state before repository line 445 throws cancellation. Terminal publication copies the map; a structured B failure overwrites only B (`197–200`), not A. INSPECT preserves the history (`187–189`, builder copy).
4. While A remains the current live file, the formatter may show its active phase instead of the historical string; when B becomes active, or at terminal, A resolves to its stored publication result rather than the old pending-copy value. This is not a new claim of instant UI rendering.

### No false READY / callback or IO-owner regression

- The new call changes only the per-file history map. It does not issue `FINAL_VERIFY`, obtain a verification token, call `trySucceed`, commit READY or release ownership. The pre-mutation invalidation and final-full-verification flow are unchanged (`ModelRepository.java:355–382`; controller `76,83–86,106–123`). With A present/B absent, terminal inspection yields **INCOMPLETE**, not READY (`ModelReadiness.java:103–115`). A successful complete import still needs the existing full verification and epoch/cancel arbitration.
- The callback occurs **after** copy-stream closure, SHA-stream closure and rename; no provider/file stream or operation-control monitor is held across this new callback. `tryReservePublish()` returns before IO and callbacks (`ModelOperationControl.java:41–44`). The existing controller synchronization and listener invalidation mechanism are reused; no new thread, blocking IO or lock order is introduced by this delta.
- Reentrant cancellation from this notification remains legal and is caught by the following checkpoint. Cancellation or a runtime callback failure after publication cannot cause A's official file to be deleted: the unchanged catch path cleans only a remaining `.part` (`ModelRepository.java:446–451`). Ordinary state-listener RuntimeExceptions are already isolated (`ModelManagementState.java:211–217`). Arbitrarily blocking listeners/fatal Errors remain outside the existing observer contract, not newly solved behavior.
- Cleanup and terminal inspection remain inside the shared owner. Controller finalization clears the control slot only after terminal/final snapshot publication (`211–226`), then the runner/coordinator release sequence follows (`RequestRunner.java:49–61`; `TaskCoordinator.java:54–62`). This new history callback does not change IO admission, cancellation ownership or release ordering.

### Regression fixtures are real repository/controller paths

**`tests/ModelReviewFixTest.java:177–200`**, using `tests/ModelManagementControllerTest.java:13–45` and `tests/ModelRepositoryCancelTest.java:14–44`:

- Each branch starts with an empty directory and a real ordered two-entry manifest for A/B, each 3 bytes with SHA-256 of `{1,2,3}`. It runs the actual controller through the queued shared-owner executor, not a fabricated terminal snapshot.
- **A success / B SHA failure:** source A is valid, while B is `{9,9,9}` of the correct size. No read/close fault is injected. A's real `.part` is closed, hashed and renamed; B reaches the real digest mismatch at repository line 428 and produces `InvalidModelException`. Assertions check A's exact retained bytes, no official B, not READY, FAILED, B in `failedFiles`, and A's publication/full-list-caveat text. Thus this is not merely a size failure or a stubbed controller exception. The test asserts B's structured failure name, not the literal SHA error text; the injected data and traced path establish the SHA cause here.
- **Publish-internal cancellation:** the override first invokes `super.publishPart`, then requests cancel for A **inside that method before returning to the repository** (`181–184`). This deterministically latches cancellation before the newly added observation, unlike cancelling from the observation callback itself. Assertions require CANCELLED, retained correct A, no B, not READY, and the same A publication text.
- Both branches refresh real inspection and require unchanged formatted A history, then idle owner/control. All these assertions run after the queued operation, outside production listener exception guards.
- The test checks terminal not-READY, not every live readiness snapshot nor an exact INCOMPLETE enum assertion; unchanged production flow establishes the stronger source-level no-READY reasoning above. It is a deterministic boundary fixture, not a multithreaded/Android rename stress test.
- `scripts/test-minimal-apk.sh:50` now includes `publication`, with the existing compilation and bounded Java runner (`12–31`). Inclusion was inspected, **not executed**.

## E2 — closed: zero-space all-reuse and safe orphan reclaim are exercised

**`tests/ModelReviewFixTest.java:134–153`** now executes both no-part and regular-`a.part` branches against `new ModelRepository(..., () -> 0)`. The provider remains zero after reclaim, below the 64 MiB padding threshold.

Both fixture branches install valid A and B, provide an empty source mapping, and require:

- terminal SUCCEEDED, zero source opens, exact manifest-total reuse **6 bytes**;
- no observed COPYING phase, with a strongly held listener removed after execution and assertions outside its callback;
- no `a.part` remaining (the parts branch creates a real regular one-byte managed orphan);
- completed plan copy=0, reuse=6, required=0, **availableAtCheck=0**;
- the same immutable plan survives a real INSPECT refresh.

This now catches padding-only aggregate admission regressions, improper copy/source-open paths and failure to reclaim that safe managed orphan. Production still reclaims parts before sampling the aggregate budget (`ModelRepository.java:352–365`), skips the per-file budget/open path for reused entries (`368–375`), and requires zero additional budget for zero copying (`ModelManagementState.java:41–44`). E2 was an evidence gap, not a production defect; no production space behavior was changed here.

The fixture proves the intended **injected zero reported usable-space** path in host-test source. It does not simulate an actually full filesystem's metadata failures, prove all unsafe-orphan permutations anew, or mean no source enumeration/provider interaction. Those are not needed to close this narrowly identified fixture gap.

## Attributed red evidence, not a green execution claim

Read `.work/model-final-polish/publication-red.log` in full (SHA-256 **`ffe71ad584ec6ef9e95ce3c91aec037463904d65652b38c0c15530caf665da1d`**):

```text
Exception in thread "main" java.lang.AssertionError: published A observation must precede post-publish cancel and later B failure
    at ModelReviewFixTest.check(ModelReviewFixTest.java:9)
    at ModelReviewFixTest.publicationHistory(ModelReviewFixTest.java:196)
    at ModelReviewFixTest.main(ModelReviewFixTest.java:206)
```

This is **retained parent-provided red evidence**, consistent with the missing-publication defect and the current outside-callback assertion. The loop runs the B-failure branch first, so this first failure is **not evidence that the cancel branch also executed** in that run. The log contains no compile command, input binding, exit-code record or green result; it is not independently reproduced proof of the exact binary/source used. Closure here rests on independently hash-bound source/diff tracing and the adequacy of the new fixture code, not an invented green result.

## Residual risks / handoff

1. This reviewer ran no tests or builds. Parent must retain its completed green publication/planning/full-suite results and reconcile its final build inputs to all **69 current frozen hashes**. No result from the concurrent build is attested here.
2. Real Android SAF/lifecycle/rendering/device behavior, real model-scale IO and filesystem exhaustion remain outside this narrow static closure. Cooperative cancellation and existing private-storage/shared-owner assumptions remain unchanged.
3. The red log is attributed and unbound as described above. Tests cover deterministic terminal state and inspection history; they do not independently measure every live callback/readiness state or race interleaving.

**No remaining E1/E2 issue or new broader review/polish work is requested by this report.**

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Concrete line-referenced closure findings for prior P2 E1 at ModelRepository.java:441-445 and controller:89,161-164, and prior P3 E2 at tests/ModelReviewFixTest.java:134-153; actual publication fixtures inspected at lines 177-200; exact three-file reconstructed baseline diff and final 69/69 hash verification."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/8c20d499/.work/model-management-final-publication-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "Read-only read/ls/find/grep/nl/sed inspection of prior reviews, three changed files and immediate production/test dependencies",
      "result": "passed",
      "summary": "Traced publication, already-latched cancellation, readiness, callback and real IO-owner finalization; inspected zero-space and publication fixtures."
    },
    {
      "command": "Read-only Python in-memory reconstruction from before/ plus own-complete.diff, SHA verification against previous freeze, and unified text diff",
      "result": "passed",
      "summary": "All three reconstructed previous files match previous frozen hashes; exact delta is publication observation, requested fixtures and publication runner inclusion only."
    },
    {
      "command": "Initial and final Python hashlib/json comparison of all 69 current frozen inputs",
      "result": "passed",
      "summary": "Both observations: 69 MATCH, 0 MISMATCH. Same 69 paths as previous freeze; 66 unchanged and exactly three changed. Current manifest SHA256 8c04f3de1f2fbdc93313d1d5ca866dbf9a6aefdfe8a91c384e820c03eaabdb0c."
    },
    {
      "command": "Read and SHA-256 hash .work/model-final-polish/publication-red.log",
      "result": "passed",
      "summary": "Attributed retained main-thread publication assertion failure at test line 196; not reviewer execution or a green/cancel-branch proof."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "No staged paths on both observations."
    },
    {
      "command": "Tests/builds/Java or checker execution/device/network/delegation",
      "result": "not-run",
      "summary": "Prohibited; concurrent parent build not executed, inspected or certified."
    }
  ],
  "validationOutput": [
    "Final frozen source: 69 entries; 69 MATCH; 0 MISMATCH.",
    "Previous three file contents independently reconstructed and hash-matched before exact diff.",
    "E1 closed at frozen production-source level; E2 closed at frozen fixture-source level.",
    "Only the authoritative review artifact was written."
  ],
  "residualRisks": [
    "No independent green execution: parent must retain final tests/build evidence and reconcile all 69 current frozen input hashes.",
    "No real Android SAF/lifecycle/rendering/device, full-filesystem or model-scale IO acceptance; existing cooperative cancellation and storage assumptions unchanged.",
    "Retained red log is attributed and lacks binary/input binding; its first-branch failure does not prove cancel-branch execution or current green behavior."
  ],
  "noStagedFiles": true,
  "diffSummary": "Reviewer changed only this return artifact. Reviewed parent delta: three-line repository publication observation, publication/zero-space regression fixtures, and publication inclusion in the existing test loop; no broader review or polish.",
  "reviewFindings": [
    "Closed prior P2 E1: android/app/src/org/llmasr/minimal/ModelRepository.java:441-445 records successful publication before cancel checkpoint; ModelManagementController.java:89,161-164 accepts it despite latched cancel and preserves A through B failure/inspection.",
    "Closed prior P3 E2: tests/ModelReviewFixTest.java:134-153 covers available=0 all-reuse and safe regular orphan reclaim, required=0, exact reuse=6, zero source opens and no COPY events.",
    "tests/ModelReviewFixTest.java:177-200 exercises actual A success/B same-size SHA failure and cancellation inside successful publishPart; scripts/test-minimal-apk.sh:50 includes publication.",
    "No new blocker, false-READY path or callback/IO-ownership regression found in the exact three-file delta."
  ],
  "manualNotes": "Acceptance attests the requested narrow read-only review, not product/build execution acceptance. Prior broader safety dispositions are not reopened; parent concurrent build remains separate evidence."
}
```
