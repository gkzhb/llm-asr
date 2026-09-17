# Phase20 narrow final-fix closure review

## Disposition and scope

**PKG-1, PKG-2, PKG-3, BR-1 and BR-T1 are closed by source inspection. No concrete remaining defect or unexpected frozen-input change was found within this closure scope.** This is not a full-build or release approval.

Read the specified findings in `reports/review/phase20-package-review.md` and `reports/review/phase20-behavior-review.md`, then inspected only the requested fixes, fixture membership/consumer, manifests, and parent red log. No tests, builds, compilers, application code, JNI tools, or APK checks were executed. No project source/test/report was edited; the sole write is this authoritative review artifact. The parent's concurrent final APK result remains the parent's responsibility.

## Closed findings

### PKG-1 — previously High: fresh classpath reaches the mutation gate

**Closed.** `scripts/test-minimal-apk.sh:5-6` creates a unique `mktemp -d` class directory and arranges its removal on exit. Line 48 compiles production dependencies and tests into that directory; line 110 explicitly passes `MODEL_REVIEW_CLASSES="$classes"` to the mutation invocation before cleanup.

`tests/model_review_mutation_test.py:10-11` requires the environment key, strictly resolves the supplied path, and checks that it is a directory. There is no fallback to `.work/build/minimal-apk-tests` or any other stale class tree. Both `javac -cp` (57) and the Java runtime classpath (61) use this same `CLASSES`; the fresh mutant directory is first at runtime, followed by the fresh host dependencies/tests.

Lines 57-64 preserve the compilation-versus-behavior distinction: compilation must return zero before the runtime invocation; runtime must return nonzero **and** contain the specific expected failure text before printing a killed-mutant PASS. Compilation and runtime timeouts propagate rather than being accepted as kills. This is source verification of the normal checked-in invocation, not a fresh mutant execution.

### PKG-2 — previously Medium, evidence scope: consumed JNI fixture frozen

**Closed.** New `frozen-sha256.json` contains `tests/fixtures/jni-pre-r3.sha256`, whose SHA-256 is `ce7a0622dc94d392b5c571afaa89ef73461b6daf71c76a716d3ca7de718f6df8`, matching the previously reported fixture digest. The direct read/compare at `tests/app_report_source_test.py:50,62` confirms this is the consumed digest fixture, not merely an adjacent file.

All five fix-reviewed source/script/test files are members of frozen126 and match their entries. All **126/126** manifest entries matched at both review checkpoints. This closes the identified inventory omission; it does not assert that the manifest is an exhaustive inventory of every conceivable build/environment input.

### PKG-3 — previously Low: standalone JNI subset accurately described

**Closed.** `scripts/compile-jni-object.sh:12-17` now explicitly describes ASR sources plus transitive Java dependencies, distinguishes full-APK native-owner/linked-DSO validation, and no longer carries the unused `all_sources` list. This matches the actual ASR-only source discovery and sourcepath compilation.

The generated-header contract remains intact: `javac -h` at 16, migrated header path plus existence check at 20-22, forced C++ `-include` at 24-26, and actual object symbol-check invocation at 27. No standalone whole-production or linked/runtime coverage is claimed here. These commands were read, not executed.

### BR-1 — previously P2 / Medium: log write/close fatal arbitration

**Closed.** `android/app/src/org/llmasr/minimal/diagnostics/LogExportController.java:89-117` replaces implicit try-with-resources arbitration with explicit primary capture and one close attempt after a successful non-null open:

- The primary write/flush failure is recorded and rethrown (`94-96`).
- With no primary, the close failure propagates to the outer handling (`100`).
- A non-Linkage close `Error` outranks a recoverable primary, retains that primary as suppressed, and propagates the exact close object (`103-106`).
- An existing non-Linkage primary `Error` retains priority and exact identity; the close failure is added as suppressed (`108`). The identity guard prevents self-suppression.
- IOException, ordinary RuntimeException and LinkageError remain recoverable in the outer catch (`112-113`). Non-Linkage fatal errors are not caught there.
- Success is assigned only after close completes (`111`); outer finally completes FAILED/releases the slot on failure, after the close scope unwinds (`114-122`). Open failure/null does not attempt a close.

No remaining swallowed-fatal compound path was found in this scoped arbitration. Suppression-enabled ordinary throwables are reasoned from source; catastrophic failures inside exception bookkeeping were not exercised.

### BR-T1 — previously P2 / Medium, test quality: exact identity asserted outside capture

**Closed.** `tests/LogExportTest.java:54-75` constructs a distinct expected close `AssertionError`; only `tasks.run()` is inside the catch (`70-71`). The assertion at 72 is outside it and requires exact object identity for fatal mode, or no thrown object for recoverable modes. It can no longer catch its own failed assertion. FAILED/non-busy, safe public status, and zero failure bytes are checked afterward.

The new compound matrix (`77-87`) executes the actual queued production controller with these two rows:

| Write failure | Close failure | Required escaping object |
|---|---|---|
| IOException | InternalError | Exact close InternalError |
| InternalError | IOException | Exact primary InternalError |

Each row captures outside its assertions, checks identity at 86, and checks exactly one close plus FAILED/non-busy state at 87. The existing blocked-close fixture (`92-105`) still checks slot retention until close and success only afterward. This is meaningful regression-test source, not an independently observed green run.

## Parent red evidence (read only)

`.work/refactor-phase20-final/log-red/run.log` contains:

```text
Exception in thread "main" java.lang.AssertionError: compound exact fatal propagation
    at LogExportTest.check(LogExportTest.java:18)
    at LogExportTest.main(LogExportTest.java:86)
```

This is the newly externalized compound identity assertion, consistent with the reported run of the new regression against previous production. **Previous-production provenance is parent-attested; this short log alone does not establish its compilation/classpath provenance.** The reviewer did not independently rerun or reconstruct red/green execution.

## Frozen change accounting and hashes

Compared the prior reviewed manifest to the new frozen manifest by path and digest:

- Prior: **125 entries**. New: **126 entries**.
- **120 unchanged**, **5 changed**, **1 added**, **0 removed**.
- Exactly the five requested fix files changed; only the consumed JNI fixture was added.
- **Unexpected changed/added/removed paths: none.** This statement concerns the two frozen inventories, not unrestricted repository/generated-output changes.
- Actual-file rechecks: `2026-09-17T13:05:41.151183+00:00` and `2026-09-17T13:05:53.071484+00:00`; both **126/126 matching, no missing files**.
- `git diff --cached --name-only` returned no paths, including at the final checkpoint.

| File | SHA-256 |
|---|---|
| `.work/refactor-phase20-final/reviewed-frozen-sha256.json` | `e0223b049a41d7a4dba55e5ea374bdd4cf610d3bf690e4b6a86079722f6bead3` |
| `.work/refactor-phase20-final/frozen-sha256.json` | `6e4dd260667b8564b627246497e37b4e9e36895835b7c21cc795873e9cb25723` |
| `scripts/test-minimal-apk.sh` | `82838d618e622836dcba0b700064cd27a6202f5333ac7b876a5645c962fa6f9b` |
| `tests/model_review_mutation_test.py` | `068b3b4b52b84e405585a826efe5858ca9258197e4b7db94124320aab5e29f20` |
| `scripts/compile-jni-object.sh` | `9c1ba51a43c321a3857c14013402c820d9633baa85cceaee6d099f08b0a34064` |
| `android/app/src/org/llmasr/minimal/diagnostics/LogExportController.java` | `b12e279d5b06c8fee6494893ae7de20bf4b6d274ddf50f1e8626f491ce62da92` |
| `tests/LogExportTest.java` | `9d600df936e892ef265b5362dbcd7ca791a09acccdb9dfcb6a17f8e64b0c13a7` |
| `tests/fixtures/jni-pre-r3.sha256` | `ce7a0622dc94d392b5c571afaa89ef73461b6daf71c76a716d3ca7de718f6df8` |
| `.work/refactor-phase20-final/log-red/run.log` | `6f77a7e901cdf3802b0ded45749188d37b926660e9f1fb2ec55695d09221b4f8` |

## Open findings, concrete defects, and limitations

**Open findings among the five requested items: none. Concrete remaining defects found in these fixes: none.** No whole-project review or disposition of other prior coverage gaps is implied.

Residual limitations:

1. No independent host/mutation/Java/JNI/APK execution. Fresh green evidence, full-source compilation, actual linked DSO, APK signing/component checks, and final source/report binding remain parent obligations.
2. The compound test adds the two required write/close permutations, not exhaustive flush-plus-fatal, fatal-plus-fatal, Linkage permutations, or suppressed-list assertions. Source arbitration covers those failure categories, but this test does not independently exercise every combination.
3. Class freshness is established by the checked-in shell-to-Python wiring; a manually supplied external class directory is not cryptographically authenticated by the mutation script.
4. Hashes are read-only checkpoints during a concurrent build, not a guarantee against later source changes. Generated build outputs/reports are not attested here. No Android/provider/device/runtime behavior claim is made.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Concrete path/line closure evidence supplied for PKG-1 High, PKG-2 Medium, PKG-3 Low, BR-1 P2/Medium and BR-T1 P2/Medium. All five closed by source inspection; no remaining scoped defects. Two frozen126 checks matched and prior-to-current inventory changes were exactly five fixes plus one fixture."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/875e523f/.work/refactor-phase20-final/final-fix-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "Read-only cat/nl/sed and scoped diff of requested reports, five fix files, fixture consumer and parent red log",
      "result": "passed",
      "summary": "Verified fresh classpath wiring, compile-failure rejection, JNI subset/header contract, explicit fatal arbitration and safe exact-identity assertions."
    },
    {
      "command": "Read-only Python json/hashlib manifest accounting and repeated actual-file SHA-256 verification",
      "result": "passed",
      "summary": "125 to 126 entries: 120 unchanged, five expected changed, one expected added, zero removed; both checks 126/126 matching."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "No staged paths at both observations."
    },
    {
      "command": "Tests, builds, compilers, JNI/APK checks, independent red/green reproduction",
      "result": "not-run",
      "summary": "Prohibited by read-only closure scope; final APK result belongs to parent."
    }
  ],
  "validationOutput": [
    "Closed PKG-1/PKG-2/PKG-3/BR-1/BR-T1 by source inspection.",
    "Frozen126 actual-file mismatches [], missing []; unexpected inventory changes [].",
    "Parent red log contains AssertionError: compound exact fatal propagation at LogExportTest.java:86; read only, not independently rerun."
  ],
  "residualRisks": [
    "Static closure only; independent green host/mutant/JNI/APK execution and post-build binding remain parent responsibilities.",
    "Compound regression covers two required write/close rows, not exhaustive flush/fatal/Linkage or suppressed-list permutations.",
    "Prior-production red-run provenance is parent-attested rather than independently reconstructed.",
    "Hash checkpoints do not attest later changes or generated outputs; no Android/provider/device runtime claim."
  ],
  "noStagedFiles": true,
  "diffSummary": "Reviewer wrote only this authoritative report. Prior frozen inventory differs only in the five requested fix files and newly included JNI digest fixture.",
  "reviewFindings": [
    "Closed High PKG-1: scripts/test-minimal-apk.sh:5-6,48,110 and tests/model_review_mutation_test.py:10-11,57-64 require and use the fresh host class directory; compile failure/timeouts are not kills.",
    "Closed Medium PKG-2: frozen-sha256.json includes tests/fixtures/jni-pre-r3.sha256 and all five fix-reviewed files; 126/126 match.",
    "Closed Low PKG-3: scripts/compile-jni-object.sh:12-27 accurately states subset coverage and retains generated-header/object checks.",
    "Closed P2/Medium BR-1: diagnostics/LogExportController.java:89-117 explicitly preserves fatal write/close identity and finalizes after close.",
    "Closed P2/Medium BR-T1: tests/LogExportTest.java:70-87 captures outside assertions and checks exact fatal identity in both required compound rows.",
    "No open scoped findings, concrete remaining defects, or unexpected frozen-input changes found."
  ],
  "manualNotes": "Review completion is attested, not full product acceptance. No project edits, tests or builds were performed. Parent owns the concurrent final APK evidence."
}
```
