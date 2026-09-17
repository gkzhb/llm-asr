# Focused read-only evidence follow-up — model-management accepted fixes

## Verdict and scope

**R4/F4 completed-plan reporting, R5 terminal deletion formatting, F5 listener regression evidence, and F6 detached-report integrity binding are fixed in the reviewed source, subject to the evidence limits below. R6's scoped UI completeness work is only partly complete: a successfully copied/published file can still be described as waiting to copy after a later file fails.** No new signing, permission, IME, or internal-Activity checker clause was weakened to accommodate the fixture tests. No storage corruption or unsafe deletion is established by this focused review.

This was independent static inspection of `/home/zhb/gitrep/llm-asr`, the two original independent reports, disposition, implementation report, full model-management plan, relevant production code/tests/scripts, and selected retained writer evidence. The accepted scope is Android R4/R5/R6 and core F4/F5/F6, including the inventory/history interfaces they use. This is **not** a new full review of R1–R3/F1–F3 or Android runtime acceptance.

**No tests, builds, checker invocations, Java programs, network/device operations, delegation, staging, or commits were run.** The parent is building concurrently; no concurrently produced APK/report tree is treated as this review's package evidence. Only read/list/search/diff/hash operations and this authorized report write were performed.

Java locations below use the prefix **`android/app/src/org/llmasr/minimal/`**. All line references are to the frozen current files unless explicitly identified as retained evidence.

## Remaining findings

### E1 — P2 presentation/recovery: a published file still says “待复制 / 修复” if a later file fails

**Locations:** `android/app/src/org/llmasr/minimal/ModelRepository.java:341–347,377–379,434–442,255`; `ModelManagementController.java:161–164,197–200`; `ModelUiText.java:55–59`; `ModelManagementActivity.java:179–183`.

**Trigger:** repair/import needs to copy files A and B. A copies, closes, passes part SHA and is renamed to its official name. B then fails reading, size validation, part SHA, or closing, so execution never reaches the final full verification pass. The only stored observation for A remains the planning value **“待复制 / 修复”** from repository line 344. Neither the copy completion nor the successful publication writes a `fileResult`. Only final verification later replaces that observation with “最终 SHA 通过”.

Terminal inspection correctly sees A's official file and size, but `ModelUiText.fileResult()` renders its retained observation as **“最近任务：待复制 / 修复”**. This is not simply a historical/current distinction: the historical observation itself is now wrong about what that same task accomplished. The failed B may get an accurate structured failure while A misleadingly still appears pending. The same issue occurs if cancellation wins after A's publication before the final verify pass. When the next file becomes active, A also reverts to that pending observation during the live task.

**Impact:** incomplete R6 scoped per-file copy/recovery presentation; correct files are preserved and global READY is not falsely asserted. Generic terminal advice saying published files are retained does not identify which files actually reached publication.

**Suggested correction:** publish an immutable per-file observation after successful rename, e.g. “已复制并发布（临时文件 SHA 通过；仍需全清单核验）”, before the next cancellation checkpoint. Do not call this current global SHA/READY evidence. Optionally record completed copy/part verification separately. Add a real queued-controller A-success/B-failure and publish-then-cancel regression, asserting A's terminal observation, retained official bytes, B's error, and UNVERIFIED/INCOMPLETE rather than READY. `tests/ModelReviewFixTest.java:134–169` currently covers all-reuse and successful mixed copying but not this terminal per-file truthfulness case.

### E2 — P3 evidence gap: zero-space all-reuse behavior is implemented, but the new fixture does not exercise zero available space

**Locations:** `tests/ModelReviewFixTest.java:134–146`; `tests/ModelManagementControllerTest.java:25–26`; production `ModelManagementState.java:41–44`, `ModelRepository.java:362–375`.

The all-reuse and parts-only tests use **123,456,789 available bytes**, exceeding the 64 MiB pad. They assert `requiredBytes == 0` and zero source opens but do not reject a regression that reintroduces a separate padding-only admission check. Production currently does the right thing: required is zero for `copyBytes == 0`, aggregate refusal is guarded by `totalCopy > 0`, and reused entries skip per-file budget/open/copy paths.

**Suggested evidence:** inject usable space 0 (also a parts-only reclaim fixture with post-reclaim space below 64 MiB), require success, exact manifest-total reuse, no source opens, and no COPY events. This is a missing behavioral regression, **not a present zero-space functional defect**. The source still enumerates the selected directory before local checking; “zero opens/zero extra copy space” is not “no provider interaction”, nor a promise that arbitrary filesystem/stat failures succeed.

## Accepted fixes verified in source

### R4 / F4 — exact completed reuse/copy plan and preflight budget: fixed

- `ModelManagementState.java:36–46` defines a final immutable `ImportPlan`: copy bytes, reused bytes, reusable filename list, required bytes, and available-at-check. The reusable list is defensively copied/unmodifiable. `Snapshot` also defensively copies `fileResults` (`149–150`).
- `ModelRepository.java:326–347` completes local SHA reuse classification before publishing the plan. A full-local reuse import now publishes the full manifest reuse sum even though it has no copying events; this repairs the original terminal-zero defect. The first plan is published before needed-source rejection, with `availableAtCheck = -1`, not a fabricated free-space reading.
- Safe part reclaim remains before aggregate space sampling (`352–365`). A **single** `space.usableBytes()` result is both retained and used for the actual aggregate check. A refusal therefore preserves the actual check's required and available values, not a later terminal storage sample. The per-file budget check remains at `374–375`; no correct official file is predeleted to finance copying.
- Controller `publishPlan()` saves both the plan and exact reuse (`156–159`); final-verification events intentionally do not reset reuse (`153`). For copying, repository `377–379,416–417` keeps cumulative newly copied bytes distinct from reuse and part/final hash progress.
- `ModelUiText.java:8–9,61–66,80` displays exact byte integers as well as MiB, the planned copy budget, required space, and the historical available-at-check value. It does **not** mislabel planned copy bytes as actual copied bytes. It also does not claim a completed install at copy 100% (`81–82`). There is no separate persistent terminal actual-copied-byte counter; the accepted completed plan should not be described as such a counter.
- `tests/ModelReviewFixTest.java:134–169` uses actual repository/controller operations: all reuse and parts-only reuse report 6 bytes and zero opens; mixed preflight rejects with copy 3/reuse 3/required 67,108,867/available 17 before any open; three-file mixed success observes cumulative copy 0→6 with reuse 3. Assertions are outside observer callbacks. Lists/maps are tested unmodifiable, and INSPECT retains the same plan object. These are small host fixtures, not seven real model files or Android visual evidence.

**Boundary:** before the local-check loop completes, no completed plan exists. CHECKING_EXISTING events still carry reused=0 (`334–336`), and the task card unconditionally displays that count. Cancellation partway through local checking can therefore still leave “0 reused” despite earlier per-file local SHA observations. The accepted disposition explicitly asks for a **completed** plan, which is now delivered; do not expand the closure claim to confirmed-so-far reuse on every early-cancel path. A future label such as “复用总量待规划完成” would make the pre-plan meaning clearer.

### Inventory versus retained history: substantially separated, not live disk telemetry

- `ModelManagementController.java:127–138` keeps INSPECT admission separate from the explicit user record. `174–189` updates storage/files/readiness while retaining the user task fields, import plan and per-file observations; `214–217` clears the transient inspect-busy flag. A new explicit IMPORT/VERIFY/DELETE intentionally replaces the previous history.
- `ModelUiText.java:55–66` labels stored observations “最近任务” and the plan “最近导入计划”; Activity `172,174–177` says available storage is from the recent check and that occupied-owner inventory is cached. `179–183` separates inventory existence/size/current shared readiness from the last task observation. The fresh-readiness epoch check avoids displaying an obsolete model-snapshot proof as current SHA success.
- `tests/ModelReviewFixTest.java:10–42` tests cancellation, SHA failure, partial deletion and cleanup failure followed by a real inventory refresh, verifies preserved identity/error/cleanup/deletion stats and changed actual file size, then checks a failed unsafe inspection independently of the retained result.
- A terminal DELETE's success/failure counts are historical, but its “剩余受管正式文件” quantity uses `s.installedBytes` (`ModelUiText.java:90–92`), which INSPECT refreshes. This is current/recent inspected remainder, **not an immutable deletion-time remainder**. The separate storage card identifies cached observations; no claim should treat the whole task card as a frozen transaction receipt.
- One small inherited stale-inventory caveat remains: inspection failure clears files and installed/part/available totals but does not clear `unexpectedFiles` (`ModelManagementController.java:182–183`); Activity `185` can still print the previous unknown-file count alongside the explicit refresh error. Treat that count as cached, not newly verified inventory. This does not expand deletion scope.

### R5 and scoped R6 deletion UI: fixed, with bounded observation limits

- `ModelUiText.java:72–79` omits the live filename/index row and byte-stage row for terminal phases. This closes “8 / 7” and “15 / 7”, even though terminal inspection still sets `fileCount` to manifest count (`ModelManagementController.java:180`). Live deletion uses processed/planned item counts, not the import/verify +1 convention.
- Repository `485–494` publishes initial plan size and success/failure counts after every managed-item attempt. Controller `166–169` retains those live counts and failure names; formatter `74` exposes them before terminal. Final report totals overwrite with the authoritative returned deletion report (`202–206`).
- `tests/ModelReviewFixTest.java:126–132` directly tests terminal formatter omission for 1/7/14 items. `96–109` drives real partial deletion with a narrow `deleteManaged` fault and observes live failed=1 before the terminal result, then exact success/failure totals.
- Counts and processed progress are two successive snapshot publications (`492–494`), so an intermediate observer can see updated counts with the preceding processed index; the final per-item snapshot is coherent. No Android rendering/coalescing timing was measured. Empty-directory removal failure still is not separately surfaced; exact outside-owner name remains the explicitly deferred generic App/IME/other-page hint. Per-file reuse and structured missing/size/SHA failures are present, but E1 prevents full per-file completion closure.

### F5 — listener assertion guard and compiled semantic mutant: fixed

- `tests/ModelReadinessTest.java:83–90` now keeps both counting/throwing listeners strongly referenced through removal. The lock regression listener likewise remains strongly referenced through `removeListener()` (`154–168`).
- Callback worker/timeout failures go into an `AtomicReference<Throwable>`, not an assertion thrown through production's catch-all listener guard (`151–163`). Callback joins are bounded (500 ms), workers are daemonized, and teardown joins are bounded (1,500 ms). After production API calls and teardown return, line **175** throws the behavioral failure, outside `ModelReadiness.notifyChange()`'s `catch(Throwable)` (`ModelReadiness.java:157–163`). Exact callback-count assertion at test line 176 also detects a vanished notification path.
- `tests/model_review_mutation_test.py:13–14,23–35` changes the actual production `notifyChange()` to `synchronized`, requires unique mutation text and successful javac, then places the mutant output **before** normal suite classes on Java's classpath. It requires nonzero runtime exit **and** the targeted listener-lock assertion marker. Compile errors/timeouts do not qualify as killed mutants; child compile/run timeouts are explicit.
- The normal test script compiles fresh suite classes and runs normal readiness before the mutation harness (`scripts/test-minimal-apk.sh:12–30,42,52`). Thus this is a compiled behavioral mechanism, not a source-string mutation claim alone.
- Independently read the retained writer mutant `.../mutants/readiness-monitor-uox59z_b/ModelReadiness.java`: its sole difference from frozen production is `public synchronized void notifyChange()`. Retained `compile.log` is empty; retained `run.log` has main-thread `AssertionError: readiness listener lock regression` at test line 175, caused by held-monitor detection at line 162. Writer `final-host.log` records normal readiness PASS and mutant exit 1. **These are attributed retained results, not executions by this reviewer.** Timing-based bounded joins can still false-fail under severe scheduler starvation; no GC stress or Android main-thread runtime proof is implied.

### F6 — actual checker/report binding: fixed as artifact integrity, not independent signature verification

- `scripts/apk_report_binding.py:10–27` hashes the current APK and all six consumed detached tool reports: permissions, badging, signature, manifest tree, IME method tree, and native-link provenance. Verification requires exact schema/APK hash/report-hash map equality.
- `scripts/check-minimal-apk.py:9–16` only adds an alternate artifact root and binding verification before existing ZIP/content/policy checks. Direct diff against `.work/model-review-fixes/before/scripts/check-minimal-apk.py` shows **no alteration to any existing acceptance clause**. Exact RECORD_AUDIO-only check (`29–30`), signature-report check (`38`), one protected/exported IME subtree and no separate process (`40–55`), two Activities/one nonexported filterless management page (`56–70`), method metadata (`71–72`), ABI/assets/native hash checks remain intact. `--root` is not a switch to skip a policy check.
- `scripts/build-minimal-apk.sh` remains `set -euo pipefail`. It signs and invokes the **real apksigner verifier**, generates real aapt reports serially (`67–72`), generates the binding (`73`), then finishes alignment/hashes/provenance and checker (`74–87`). No fake metadata is used in the production build path. Direct before/current script comparison adds only binding generation and the new script's input-hash coverage.
- `tests/apk_report_binding_test.py:49–65` invokes both the **actual binding generator and actual complete checker as subprocesses**, with bounded timeouts and a separate synthetic artifact root. It tests one successful bound ZIP; a changed ZIP with original detached metadata; modification of each of the six report files; and a missing binding. Stale/modified cases require the specific binding failure marker. This is materially stronger than only executing an extracted checker clause.
- The fixture intentionally contains fake DEX/manifest/native payloads and a **fake `Verifies` report** (`21–47`). That is honest for binding integrity and does not establish signing or Android parsing/policy. Negative report fixtures append bytes without rebinding, so they test integrity rejection, not policy rejection of newly bound unsafe metadata. The existing `tests/model_android_source_test.py:43–64` still tests the extracted new-Activity clause's exported/filter/process/name negatives; it is not a full checker policy fixture. No new full-checker negative fixtures for rebound bad permissions/signature/IME were added, but static diff proves the fixes did not relax those checks.

**Trust distinction:** standalone checker PASS proves consistency with a self-contained SHA binding and its existing report-text conditions. It does **not** invoke apksigner/aapt itself, authenticate the binding's author, or cryptographically prove those reports were derived from that ZIP. Someone able to replace both reports and binding can make a consistent false attestation; the unsigned synthetic fixture deliberately demonstrates that scope. Real signature verification is the build's `apksigner verify`, whose successful execution and output provenance the parent must attest separately. As before, security clauses use Python `assert`; invoke normally, not with `-O`/`PYTHONOPTIMIZE`. No optimized-interpreter safety is claimed.

## Source provenance coverage

Read-only reconstruction of the input path list in `scripts/build-minimal-apk.sh:80–85` found **78 unique planned build-hash paths, covering all 69 frozen review paths (43 Android app files, 21 immediate tests, 5 scripts), zero missing**. The new provider helper, plan/UI/controller changes, focused tests, mutation runner and binding generator are all covered by the existing recursive Android/immediate-test collections and explicit script entries.

The nine build-listed paths outside this review freeze are `flake.nix`, `flake.lock`, `native/apk/asr_jni.cpp`, `scripts/link-apk-native.py`, `reports/p0/mnn-model-manifest.json`, and the four patch files. The fixed sample audio, small source model configs, native build/source tree and external SDK/JDK/tool binaries are not a complete transitive frozen set here. The sample/config/native output gates in build lines `21–43` provide other checks but are not equivalent to a comprehensive source-input freeze.

`build-input-sha256.json` is written **after compilation**, is not one of the six APK-bound detached reports, and is not checked against the review freeze by the standalone checker. The accepted F6 binding therefore does not itself prove reviewed-source-to-APK provenance. Parent must finish its serial build, compare all 69 corresponding build-input hashes to this exact freeze, retain real tool results, and reconcile final APK/report hashes. This review deliberately did not read moving parent build outputs to assert that reconciliation had already passed.

## Final read-only integrity attestation

At the end of source inspection, Python `hashlib` read/hash comparison reported:

```text
FINAL FROZEN INPUT HASH CHECK: 69 entries; 69 MATCH; 0 MISMATCH
Frozen manifest: .work/model-review-fixes/parent-frozen-input-sha256.json
Manifest SHA256: c3b2a777234d8805322f4b6cc1035d721b3a9c7d1fbb426cbc6512bc398c69a1
Retained writer final-host.log SHA256: a654fb3fc24a2f165fa3f353f91b94e48354e8512e8019603bc4d9bec8b17448
```

`git diff --cached --name-only` returned no paths on both observations. Diff commands that found the expected script/mutant differences returned ordinary difference status, not a validation/test failure. No test module was imported or run for hashing or provenance-path enumeration.

## Residual risks / handoff

1. Fix or explicitly disposition E1 before claiming per-file repair-progress completeness. Add E2's zero-available behavioral fixture; current source correctly implements the no-padding all-reuse path.
2. Completed-plan totals and live copy counters are exact in source/host fixtures; pre-plan reuse, per-file publication observations, and cached versus historical inventory should not be described more strongly than their actual semantics above.
3. Real SAF query/cursor/permission/grant lifetime, Activity recreation/Home/lock/IME navigation, real rendering/accessibility and real model IO remain Android acceptance work. `ModelProviderBoundaryTest.java:27–57,61–77` executes the factored production supplier/stream/owner path, **not ContentResolver/Cursor or Android OperationCanceledException**. `SafModelSource.java:31–73` wiring was traced statically, not executed.
4. Retained normal/mutant/checker PASS logs are evidence from the implementation writer only. This reviewer did not run host tests, Android compile, full package build or independent signature verification; parent full build results are separate evidence.
5. Binding protects against stale/modified artifacts without corresponding rebinding, not a malicious evidence author or a concurrent artifact replacement race. Source freeze and post-build reconciliation remain necessary.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Concrete frozen-source findings E1 (P2 per-file published/pending contradiction) and E2 (P3 zero-space regression gap), with file/line triggers; verified R4/F4, R5, F5 and F6 mechanisms, explicit residual risks, and final 69/69 SHA matches."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/3db342f7/.work/model-management-fix-evidence-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "Read-only read/grep/nl/sed/cat/find/ls and before/current script plus retained-mutant diffs",
      "result": "passed",
      "summary": "Inspected accepted reports/disposition/implementation claims, full plan, production UI/planning/history, readiness tests/mutation mechanism, actual checker and fixture source; no existing checker security clause altered."
    },
    {
      "command": "Read-only Python reconstruction of scripts/build-minimal-apk.sh input path coverage",
      "result": "passed",
      "summary": "78 unique planned hash paths cover all 69 frozen paths; nine outside-freeze paths explicitly identified. Did not read or validate moving parent build outputs."
    },
    {
      "command": "Final Python hashlib comparison to .work/model-review-fixes/parent-frozen-input-sha256.json",
      "result": "passed",
      "summary": "69 entries, 69 MATCH, 0 MISMATCH; manifest SHA256 c3b2a777234d8805322f4b6cc1035d721b3a9c7d1fbb426cbc6512bc398c69a1."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "No staged paths on both observations; reviewer staged nothing."
    },
    {
      "command": "Tests/build/checker execution/Android runtime/signature verification",
      "result": "not-run",
      "summary": "Prohibited for this reviewer. Retained writer host/mutant logs were read and attributed, not reproduced."
    }
  ],
  "validationOutput": [
    "69/69 frozen inputs match at review end; zero source edits.",
    "Existing checker signing-report, permission, IME and internal-Activity policy clauses unchanged; new binding validation precedes them.",
    "Retained listener mutant has only synchronized notifyChange change and fails at outside-guard assertion line 175; compile/runtime mechanism inspected, not run.",
    "Only this authorized review artifact was written."
  ],
  "residualRisks": [
    "E1: successfully published file can retain waiting-to-copy observation after later failure/cancel; per-file UI completeness not fully closed.",
    "E2: zero available-space all-reuse behavior is correct in source but not directly covered by new behavioral fixture.",
    "Pre-plan reuse remains zero; cached unknown-file inventory can survive inspect failure; no persistent actual-copied terminal total.",
    "Binding integrity is not independent signature verification or authenticated report derivation; synthetic checker fixture is intentionally unsigned/fake metadata.",
    "Parent must reconcile final APK/tool reports/build-input hashes with all 69 frozen inputs; full transitive native/assets/toolchain inputs are not frozen by this review.",
    "No Android provider/lifecycle/IME/visual/device runtime verification and no independent test/build execution."
  ],
  "noStagedFiles": true,
  "diffSummary": "Review artifact only; no production, test, script, documentation, report tree or package output edits by reviewer.",
  "reviewFindings": [
    "P2 E1: android/app/src/org/llmasr/minimal/ModelRepository.java:344,434-442,255 and ModelUiText.java:55-59 — published A remains pending-copy in per-file UI when later B fails before final verify.",
    "P3 evidence E2: tests/ModelReviewFixTest.java:134-146 — all-reuse fixture has 123456789 free bytes, so zero-space no-padding behavior lacks direct regression despite correct source guards.",
    "Fixed R4/F4: ModelRepository.java:347-365 and ModelManagementState.java:36-46 — exact completed immutable reuse/copy/required/available plan survives refusal and history refresh.",
    "Fixed R5/scoped delete progress: ModelUiText.java:72-79 and ModelRepository.java:485-494 — no terminal live denominator and real live failure/success counters.",
    "Fixed F5: tests/ModelReadinessTest.java:151-176 and tests/model_review_mutation_test.py:13-35 — strong reference, bounded teardown, outside-guard assertion and required successful compiled behavioral mutant.",
    "Fixed bounded F6: scripts/apk_report_binding.py:10-27 and tests/apk_report_binding_test.py:49-65 — current APK/report integrity checked by actual checker negative fixtures; independent cryptographic verification remains the real build apksigner step.",
    "No new checker security-clause weakening or storage safety blocker established by this focused static review."
  ],
  "manualNotes": "Acceptance attests completion of the focused read-only review, not unconditional product acceptance. Parent build concurrently running was neither executed nor certified here. Exact authoritative output path honored; no delegation/network/device/commit."
}
```
