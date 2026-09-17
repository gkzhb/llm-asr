# Phase20 final package/toolchain review

**Disposition: changes requested for one high-severity toolchain/evidence defect; production package migration itself is source-equivalent within the intended visibility changes.**

Read-only review of `/home/zhb/gitrep/llm-asr`. No tests, builds, compilers, JNI/device execution, source edits, staging, or concurrent APK/report writes were performed. The only file written by this review is this report. R5 behavior review was deliberately not repeated.

## Concrete findings

### PKG-1 — High: mutation gate reads stale classes instead of the host run's fresh classes

- **Locations:** `scripts/test-minimal-apk.sh:5-6,48,110`; `tests/model_review_mutation_test.py:10,56-62`; build entry at `scripts/build-minimal-apk.sh:14`.
- The host runner now compiles into a unique `minimal-apk-tests-XXXXXX` directory and deletes it on exit. The mutation runner still hardcodes `.work/build/minimal-apk-tests` for both `javac -cp` and `java -cp`; the invoking shell never passes its live `$classes` directory.
- **Trigger/consequence:** on a clean workspace the three compiled semantic mutations cannot resolve their production/test dependencies, aborting the host suite and consequently the full build. In a reused workspace they can compile/run against stale production dependencies and stale test classes, so mutant PASS is not evidence for the freshly compiled frozen sources/tests.
- **Direct read-only evidence:** the fixed directory currently contains both old `org/llmasr/minimal/ModelReadiness*.class` and migrated `org/llmasr/minimal/model/ModelReadiness*.class`, plus `ModelReviewFixTest.class`. Existing successful host logs cannot resolve this freshness defect.
- **Narrow correction:** explicitly pass the active host classes directory to the mutation runner, require/use it for both subprocesses, and retain the current rule that compilation failures/timeouts do not count as killed mutants. Parent should then obtain fresh mutation/full-build evidence; this review did not execute a reproduction.

### PKG-2 — Medium (evidence scope): frozen125 omits a consumed JNI baseline fixture

- **Locations:** `.work/refactor-phase20-final/frozen-sha256.json:119-126` (fixture inventory); consumer `tests/app_report_source_test.py:50,62`; final build inventory `scripts/build-minimal-apk.sh:89`.
- `tests/fixtures/jni-pre-r3.sha256` is read to establish the frozen native source-body contract but has no entry in frozen125. The adjacent report-body fixture is included.
- **Trigger/consequence:** a change to the JNI digest fixture would not appear in a frozen125 recheck. Thus “all125 unchanged” is accurate, but “all consumed source/test inputs frozen” is not. There is no evidence here that this fixture was actually altered.
- Current fixture-file SHA-256: `ce7a0622dc94d392b5c571afaa89ef73461b6daf71c76a716d3ca7de718f6df8`.
- **Narrow correction:** include this fixture in the parent’s next freeze/binding. The full build already recursively includes `tests/fixtures`, so its source-list construction is correct for this file; final binding must still be checked after the concurrent build finishes.

### PKG-3 — Low: standalone JNI script overstates all-production coverage

- **Location:** `scripts/compile-jni-object.sh:12-19,29`.
- The comment promises compilation of every production Java file and `all_sources` discovers that list, but the variable is unused: `javac` receives only `asr/*.java`, with automatic sourcepath compilation of dependencies.
- **Trigger/consequence:** a native declaration in an unrelated production class is outside this standalone invocation's compiled-class owner check. A standalone JNI PASS must not be described as complete production-native-owner coverage.
- **Narrow correction:** accurately describe the standalone subset or deliberately supply the complete Android compilation environment/list. This is **not** a full-APK gate bypass: `build-minimal-apk.sh:55-57` compiles every production source and applies the owner/actual-DSO checker to that complete output.

## Positive review evidence

### Source equivalence, visibility, and identity

- Verified all **113 prepackage snapshot entries** against `.work/refactor-phase20-prepackage/sha256.json`: no mismatches. Matched all **62 original Java basenames to exactly 62 current files**, with no missing/new basename.
- Compared Java bodies after removing package/import declarations; compared nonlocal imports separately, with no changes. All bodies are identical except these three intentional visibility sites:
  1. `ime/ImeBackend.java:17,21`: class and constructor public, needed by root `AppGraph.java:149`.
  2. `ime/ImeSession.java:7`: immutable `fieldKey` public final, read by root `AsrImeService.java:136`.
  3. `model/ModelReadiness.java:80`: `commitVerified` public, used across packages by `modelmanagement/ModelOperationControl.java:50`; existing token/epoch validation and synchronization are unchanged.
- Earlier parent-audit leakage is repaired: `diagnostics/RuntimeLogEvent.java:65` keeps `withSequence` package-private; `tests/RuntimeLogCoreTest.java:1` is in diagnostics, and the host invocation uses its new FQCN.
- Exactly five root Java files remain: AppGraph, MainActivity, AsrImeService, ModelManagementActivity, LogsActivity. All retain package `org.llmasr.minimal`; their bodies are unchanged after import normalization.
- The prepackage inputs do **not** contain the manifest. Independently compared the current manifest and `res/xml/method.xml` to `.work/refactor-phase20-baseline/inputs`: both byte-identical. Manifest lines 2,7,13-15 retain application ID and four Android entry identities, code6/version0.6-debug, internal model/log pages, and BIND_INPUT_METHOD service protection. All current `android/app` files are present in frozen125.

### Architecture guard and migrated test paths

- `tests/architecture_boundary_test.py:135-161` uses explicit allowed destination **sets**, subtracts the current package, and executes the same `check_package` function for every real mapped source. Recursive inventory equality and package declarations are asserted; local wildcard imports are rejected.
- Lines 163-173 call that same function for **15 actual negative fixtures**: six specific forbidden edges plus AppGraph from each of nine non-root packages. Missing failure is an error. The earlier tuple-comparison/no-op fixtures are no longer present.
- Static import inventory has no feature-to-root or feature-to-feature cycle: task/audio/model/platform/diagnostics are lower layers; asr references audio/diagnostics; ime/transcription reference their allowed lower layers; modelmanagement references diagnostics/model/task. Root imports features, not vice versa.
- Guard limitations remain explicitly lexical, not AST/runtime enforcement. Comments and literals are stripped together before reference-token extraction. No silently skipped tuple edges were found in the current implementation.
- All **51 host production class names** are preserved from the prepackage source list, with migrated paths, explicit existence/duplicate checks, and fresh host output. PKG-1 is the separate mutation consumer break.
- Java test-body differences inspected are relocation-only: InferenceAdapterTest source paths/JNI prefix, ModelOperationControlTest qualified model names, and PartRecoveryTest mutation compile paths/driver import. Python source guards now resolve migrated files; unknown mapped names fail. No R5 behavior judgment is made here.

### Android build, JNI, and report/checker strength

- Android compile-only and full APK compile discover production Java recursively and sort it; full APK build clears classes before compiling, then recursively collects emitted classes for d8. Build-input hashing recursively covers `android/app` and fixtures, and includes top-level tests plus compile/JNI helpers (`build-minimal-apk.sh:86-92`).
- `native/apk/asr_jni.cpp` differs from prepackage only in four occurrences of the class-symbol prefix, from `Java_org_llmasr_minimal_JniNativeTranscription_` to `Java_org_llmasr_minimal_asr_JniNativeTranscription_`. No native inference body changed.
- `compile-jni-object.sh:18,26-29` still generates a Java `-h` header, force-includes it during C++ object compilation for declaration/type checking, then checks the actual object. `check-jni-symbols.py:19-51` still checks exact private-static-native descriptors, exactly one owner, and actual defined/external `llvm-nm` symbols; DSO inspection adds `--dynamic`. Full APK uses freshly compiled complete Java classes and the actual linked DSO. Wrong/stale prefix, missing/extra symbol and declaration-shape fixtures remain.
- `scripts/link-apk-native.py` is byte-identical to prepackage; it still uses the explicit final MNN object link edge. Its separately compiled linked bridge does not consume the header-checked object, but the full build explicitly runs header validation first on the same frozen source; no new weakening was introduced here.
- Model/recording/audio Java math and logic are unchanged by normalized source comparison. MNN/P0 build scripts are byte-identical to prepackage; the four MNN-related patch files have no Git delta. No inference or recording runtime claim follows from this review.
- `scripts/check-minimal-apk.py`, `scripts/apk_report_binding.py`, and `tests/apk_report_binding_test.py` are byte-identical to prepackage. Synthetic fixture tests invoke the real checker and retain stale-APK, every detached-report modification, and missing-binding negatives. Synthetic signature metadata is explicitly not genuine signing evidence. The native-source guard permits only package-prefix canonicalization before the existing digest comparison.

## Evidence limits and required parent follow-up (not additional production defects)

- No test, build, APK checker, JNI tool, or device command was executed by this reviewer. Historical parent-host PASS logs were read only, and are not new validation or a remedy for PKG-1.
- Fresh positive host/mutation evidence using one shared fresh class tree, final all-source Android compilation, generated-header JNI checks, linked-DSO symbols, APK signing/component checks, and post-build source/report binding remain the parent's responsibility.
- Concurrent `reports/apk/*`, `dist/*`, generated native objects, and the ongoing build log are excluded from all source-hash/final-artifact claims in this report. No current APK SHA or final report-binding success is attested.
- Lexical architecture checks and compile/object checks do not establish Android lifecycle behavior, native execution, model accuracy, or device compatibility. R5 behavior belongs to the other review lane.

## Integrity attestation

Initial and subsequent read-only SHA-256 checks found **125/125 frozen entries matching**, with no missing entries. A final recheck is recorded in the completion response; PKG-2 explicitly limits the membership claim. Frozen manifest SHA-256 at review: `e0223b049a41d7a4dba55e5ea374bdd4cf610d3bf690e4b6a86079722f6bead3`.

The initial comparison command attempted a nonexistent prepackage manifest and stopped with FileNotFoundError; source comparison was rerun successfully, and manifest identity was established against the Phase20 baseline instead. `git diff --cached --name-only` was empty. Existing unstaged work was left untouched.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "PKG-1 high at tests/model_review_mutation_test.py:10,56-62; PKG-2 medium at frozen-sha256.json:119-126 and app_report_source_test.py:50; PKG-3 low at compile-jni-object.sh:12-19. Each includes trigger, consequence, and narrow correction."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/9df12f23/.work/refactor-phase20-final/package-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {"command": "Read-only Python SHA-256 comparison of frozen125 and prepackage113", "result": "passed", "summary": "All listed entries matched; JNI fixture membership omission separately reported."},
    {"command": "Read-only Java/native/test/script diff and import/source-list inventory", "result": "passed", "summary": "62 Java files; only three intended visibility sites; four native symbol-prefix replacements; same 51 host class names."},
    {"command": "Initial combined comparison including prepackage AndroidManifest.xml", "result": "failed", "summary": "Snapshot lacks manifest; corrected comparison used Phase20 baseline and found identical manifest/method.xml."},
    {"command": "git diff --cached --name-only", "result": "passed", "summary": "Empty staging diff."},
    {"command": "Tests, builds, javac, JNI checks, APK checks, device execution", "result": "not-run", "summary": "Prohibited by read-only review scope; parent build concurrent."}
  ],
  "validationOutput": [
    "125/125 frozen entries matched on repeated checks; 113/113 prepackage snapshot entries matched.",
    "All 62 migrated production Java sources accounted for; five root identities and baseline manifest unchanged.",
    "Architecture real-source and 15 negative-fixture paths call the same checker; previous tuple/no-op defect repaired."
  ],
  "residualRisks": [
    "PKG-1 blocks clean-workspace host/full build and invalidates freshness of existing mutation PASS evidence.",
    "JNI baseline fixture is outside frozen125; final parent freeze/binding must include it.",
    "Standalone JNI coverage is a subset; final full-source/DSO and APK binding evidence remains pending parent completion.",
    "No Android runtime/device or R5 behavior attestation is made."
  ],
  "noStagedFiles": true,
  "diffSummary": "Only this review artifact was written; production source, scripts, tests, reports/apk and build outputs were not modified by the reviewer.",
  "reviewFindings": [
    "High PKG-1: mutation runner hardcodes stale class directory instead of active fresh host classes.",
    "Medium PKG-2: consumed JNI digest fixture missing from frozen125.",
    "Low PKG-3: standalone JNI script's unused all_sources contradicts all-production coverage comment."
  ],
  "manualNotes": "Acceptance attests completion of the review, not approval of the package/build. Parent notified of findings while build was concurrent; no tests/builds were run by this reviewer."
}
```
