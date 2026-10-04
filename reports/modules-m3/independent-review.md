All tests have real negative fixtures, not smoke tests. The "direct production owner" check is exact — not a set-based update that would be hidden by inner classes.

I have enough evidence. Now let me complete the review by looking at one more concern: the assignment says "tests/module_owners_guard_test.py普通/-O 5 each" — total 5 tests in this file. Confirmed.

I now have a comprehensive review base. Let me summarize the findings and form the report.

# Code review

**Status:** COMPLETE
**Reviewed:** `llm-asr` repo, base/head 538dcb66f16d41adeba440d934d3a0d2e8c4eb6d (no new commits; all M3 work is unstaged `M` plus untracked `??`). Scope: M3 implementation per `.planning/llm-asr-76d.4/changed-files.txt` (11 unstaged-modified + 27 untracked). Excluded: `.planning/llm-asr-76d.4-supervision/`, `.planning/llm-asr-ap5-toolchain-analysis/`, `.planning/llm-asr-msj-modern-android-analysis/`.

## Scope confirmation

- HEAD is exactly the parent-pinned `538dcb66`. Confirmed via `git rev-parse HEAD`. No commits added; everything is working-tree state awaiting father approval per assignment.
- 11 modified files: `settings.gradle.kts`, `build.gradle.kts`, `android/app/build.gradle.kts`, `buildscript-gradle.lockfile`, `gradle/verification-metadata.xml`, `scripts/verify-gradle-apk.py`, `tests/gradle_apk_identity_test.py`, `tests/gradle_dependency_verification_test.sh`, `docs/gradle-build.md`, `docs/native-build.md`, `.beads/issues.jsonl`.
- 27 untracked source files: 3 module build files (`core/build.gradle.kts`, `core/gradle.lockfile`, `android/asr-runtime/{build.gradle.kts, gradle.lockfile, src/main/AndroidManifest.xml, src/test/java/RuntimeRegressionJUnitTest.java}`), JUnit adapter `core/src/test/java/CoreRegressionJUnitTest.java`, 3 new test files (`tests/module_boundary_test.py`, `tests/module_owners_test.py`, `tests/module_owners_guard_test.py`), `docs/module-test-map.md`, `reports/modules-m3/*` (10 files), `.planning/llm-asr-76d.4/{task_plan,findings,progress}.md` (writer plan).
- Production source tree (63 files) is byte-identical to baseline. Confirmed `find` returns 63 and `baseline['files']` lists 63.
- APK at `android/app/build/outputs/apk/debug/app-debug.apk`: SHA256 `a0494d25...a3f3` (matches `reports/modules-m3/result.json`); contained native `libqwen_asr_jni.so` SHA256 `d5bacffc...416` (unchanged from M2); 12 DEX files; no `junit`/`hamcrest` strings inside the APK. The final freeze inputs match the build-input list and `native-verification.json` shows ELF LOAD alignment 16384 (16 KiB) preserved.
- Old host script `scripts/test-minimal-apk.sh` is **not** in the diff — it is wired through `verifyHost` Exec task unchanged. The M2 `scripts/verify-gradle.sh` is also unchanged. No previous gate was weakened.

## Findings

### [P2] m3-review-F001 — `subprojects { JavaCompile.sourcepath = files() }` may break third-party plugins that need to auto-discover source files for annotation processing

**Location:** `build.gradle.kts:20-22`

**Provenance:** reviewer m3-review, evidence: source diff vs. base + `core/build.gradle.kts:34` (`tasks.withType<KotlinCompile>().configureEach { compilerOptions { allWarningsAsErrors.set(true) } }`) and `KotlinCompile` KGP defaults.

**Evidence status:** Trace-backed

**Preconditions:** Any annotation processor (e.g. `AutoService`, `Lombok`, future `Dagger`) added to any subproject needs to discover annotated classes by source.

**Reproduction or trace:** The root `subprojects { tasks.withType<JavaCompile>().configureEach { options.sourcepath = files() } }` sets an empty source path for **every** subproject's `JavaCompile`. This is required to prevent javac from re-compiling excluded `.java` from the shared `android/app/src` root. However, this also prevents any future annotation processor from finding source roots, since the `sourcepath` is what apt uses for source-level discovery when the source set is split across multiple `setSrcDirs`. Today there are no annotation processors configured (verified — `configurations.configureEach` in `core` and `asr-runtime` is only for dependency resolution), so the impact is latent.

**Expected behavior:** Either scope the sourcepath clearing to specific subprojects that have the shared root (`:core` and `:asr-runtime`) and exclude `:app`, or use `setSrcDirs(emptyList())` per source set as already done. The current `subprojects` scope is broader than needed.

**Actual behavior:** A future plugin or a code-level annotation processor added to any subproject will silently miss sources because javac's annotation processor default sourcepath is empty.

**Impact:** Latent build fragility; not blocking the current M3 since no annotation processors are configured. Becomes blocking the moment any is added.

**Smallest safe fix:** Replace `subprojects { ... }` with explicit `tasks.matching { it.project in listOf(project(":core"), project(":asr-runtime")) }` blocks, or apply via `subprojects { afterEvaluate { ... } }` for the two known-shared-root projects.

### [P2] m3-review-F002 — `test { java.setSrcDirs(... "tests" ...) }` compiles `InferenceAdapterTest.java` twice (once for `:core` and once for `:asr-runtime`) and `PartRecoveryTest.java` is also part of `:core` test classpath, weakening the "fresh classpath / actual module owner" guarantee

**Location:** `core/build.gradle.kts:18-19` and `android/asr-runtime/build.gradle.kts:15-17`

**Provenance:** reviewer m3-review, evidence: source diff + `tests/` listing (28 main test files, including `InferenceAdapterTest.java` and `PartRecoveryTest.java`).

**Evidence status:** Trace-backed

**Preconditions:** `tests/` is the shared physical root of all 28 main test entries. Both `:core` and `:asr-runtime` source sets include `tests/`. `:core` uses `java.include("*.java")` which captures **all** `tests/*.java` (including `InferenceAdapterTest.java` and `PartRecoveryTest.java`); `:asr-runtime` uses `java.include("InferenceAdapterTest.java", "RuntimeRegressionJUnitTest.java")`.

**Reproduction or trace:**
- `InferenceAdapterTest.java` will be compiled into BOTH `:core` and `:asr-runtime` test class outputs. Each module's JUnit task uses `include("**/CoreRegressionJUnitTest.class")` (core) and `include("**/RuntimeRegressionJUnitTest.class")` (asr-runtime), so neither task's test classpath will load the duplicate — but the `compileDebugJavaWithJavac` step in `asr-runtime` test compile and the `compileTestJavaWithJavac` step in `:core` will both produce an `InferenceAdapterTest.class` for the same source file. The `tests/module_owners_test.py` only checks production class roots, so the duplicate is invisible to the gate.
- `PartRecoveryTest.java` compiles production `ModelRepository.java` into a red mutant under `.work/red/`. With `tests/PartRecoveryTest.java` now under `:core` test source set, the `partRecoveryProductionMutant` JUnit method is calling the test's main from the `:core` JUnit context. If the test main starts a child `javac` process (it does — see `tests/PartRecoveryTest.java:128` and `:145`), that process inherits the `:core` JUnit worker JVM, not a fresh classloader. The classpath on which the mutant is built still must be the **production** `core` classes directory (`:core/build/classes/java/main`), not the test classpath. The current test main does build with a separately-launched `javac` ProcessBuilder, so the JUnit wrapping does not contaminate the red/green evidence. But the **JUnit body** of `partRecoveryProductionMutant` runs the same script in a `:core` JUnit JVM that has JUnit/Hamcrest + the entire `tests/` compiled into its classpath; if `PartRecoveryTest.main` ever assumed an isolated classpath, this would be a problem. Today it is safe because it uses `ProcessBuilder` to compile into a fresh `mktemp -d` classes dir.

**Expected behavior:** The writer reports the JUnit test classpath is "fresh" because the `verifyModuleBoundary` task runs against `compile.destinationDirectory` (fresh compiled output) and the JUnit test classpath is a `runtimeClasspath` derived from `compile` output, not from shared intermediates. This is correct.

**Actual behavior:** This is a **non-blocker** trace observation, not a defect. The 35 JUnit methods run real bodies, fresh mktemp classpath for the PartRecovery red/green javac step, and `:core` / `:asr-runtime` test source sets only share the source tree but not the compiled class output. The duplicate `InferenceAdapterTest.class` is wasted CPU/IO but does not weaken any gate.

**Impact:** Maintainability and slow test builds; not a correctness issue. Becomes a problem only if a future test relies on a single physical location for a stateful resource (e.g. `.work/red/` shared with concurrent writers — already constrained by `maxParallelForks = 1` and `maxHeapSize = 512m`).

**Smallest safe fix:** Either split `tests/` so that runtime-only entries (`InferenceAdapterTest.java`) live in `android/asr-runtime/src/test/java/` (already done for `RuntimeRegressionJUnitTest.java` — the JUnit adapter), or constrain `core` test source set to exclude `InferenceAdapterTest.java` explicitly (currently relies on `RuntimeRegressionJUnitTest.java` living only in `asr-runtime/src/test/java/`). The latter is a one-line exclude.

### [P3] m3-review-F003 — `tests/module_owners_test.py` enforces 63-source inventory via `len(sources) != 63`, making the gate brittle to legitimate source additions

**Location:** `tests/module_owners_test.py:15`

**Provenance:** reviewer m3-review, evidence: source code.

**Evidence status:** Trace-backed

**Preconditions:** Any new production class added in M4+ will trip this hard-coded 63.

**Reproduction or trace:** The script reads `gradle/baseline-inputs.json['files']` and checks `len(sources) != 63 or len({p.stem for p in sources}) != 63`. There is no comment linking this to a baseline (the file used to be 62 in M1, became 63 in M6 per writer's own correction log).

**Expected behavior:** Hard-coded count with no provenance = silent breakage when M4+ adds a class; the failure message "production source inventory changed: review module ownership" is reasonable but does not point to a baseline file or git range.

**Actual behavior:** Acceptable for M3. The writer explicitly notes this is a "hard 63 gate fail closed" design — new classes will need explicit baseline updates. This is intentional and documented in `self-review.md` and `progress.md`.

**Impact:** Low. Not a defect; a maintainability choice. The reviewer simply flags that the next writer will need to remember to update both this script and the per-module owner set (`APP`, `RUNTIME`).

**Smallest safe fix:** Add a comment referencing the Beads issue (`llm-asr-76d.4` -> `M3 source baseline`) and a pointer to `gradle/baseline-inputs.json` revision. No code change required.

### [P3] m3-review-F004 — `tests/gradle_dependency_verification_test.sh` checksum-flip fixture relies on sidecar `find` whose exit code is not strict-checked; one bad sidecar could silently pass

**Location:** `tests/gradle_dependency_verification_test.sh` (the 4-line M3 change just adds 2 new files to the fixture copy and one extra `mkdir`)

**Provenance:** reviewer m3-review, evidence: diff vs. base.

**Evidence status:** Trace-backed (script logic, not execution)

**Preconditions:** Strict Gradle dependency verification is the project's hermetic-grade gate.

**Reproduction or trace:** The script copies the verification metadata to a temp fixture, mutates a checksum, runs `./gradlew :app:assembleDebug --offline` in the fixture, and expects exit 1. With the M3 change, the fixture now also needs the `core/` and `android/asr-runtime/` build files and lockfiles copied in (the diff added these). This is correct. The M3 change does **not** alter the negative-fixture mutation logic. No regression.

**Expected behavior:** No regression introduced by the M3 change.

**Actual behavior:** The mutation and exit-code assertions in the M1 script remain intact. M3 only widens the copied fixture. No new risk.

**Impact:** None. This is a non-finding confirm; included for completeness because the assignment explicitly listed "checksum负例" as a review focus.

**Smallest safe fix:** None.

### [P2] m3-review-F005 — `:asr-runtime` test task uses `workingDir = rootProject.projectDir`, so `tests/InferenceAdapterTest.main` runs in the repo root and any relative paths in the test resolve against the repo root, not the module dir

**Location:** `android/asr-runtime/build.gradle.kts:28`

**Provenance:** reviewer m3-review, evidence: source code + `tests/InferenceAdapterTest.java` (which reads paths like `bench/audio/zh-original.wav` and `.work/red/`).

**Evidence status:** Trace-backed

**Preconditions:** `InferenceAdapterTest.main` uses `Files.readAllBytes(Paths.get("bench/audio/..."))` and similar relative paths. If the working dir were `android/asr-runtime/`, those paths would not resolve.

**Reproduction or trace:** The `testOptions.unitTests.all { it.workingDir = rootProject.projectDir }` (root) is correct and necessary. Same for `:core` (`tasks.test { workingDir(rootProject.projectDir) }`). Both modules point the JUnit JVM at the repo root so the test main bodies' relative paths still resolve to the same `bench/audio/...` and `.work/...` they used in `scripts/test-minimal-apk.sh`.

**Expected behavior:** Working dir preserved at repo root.

**Actual behavior:** Correct.

**Impact:** Non-issue; flagged as confirm-only because the assignment required checking test paths.

**Smallest safe fix:** None.

### Non-blocking observations (dispositions, not findings)

- **M6 baseline discrepancy resolved:** Status self-correction (62 → 63) is recorded in `reports/modules-m3/status.md` and `.planning/llm-asr-76d.4/progress.md`. The `tests/module_owners_test.py` and `scripts/verify-gradle-apk.py` now both use 63. No defect.
- **JUnit/Hamcrest SHA provenance:** `8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3` (JUnit) and `66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9` (Hamcrest) match the jars present in `.cache/gradle-user-home/caches/modules-2/files-2.1/...`. The `8ac9e16d...` and `42a25dc3...` SHA1 prefixes used as Gradle cache subdirectory names match the official Maven Central sidecars. Origin text "locally computed SHA256" is honest. No defect.
- **Native SHA unchanged:** `d5bacffc...416` in the new APK and in `reports/modules-m3/native-verification.json`. Math/flags unchanged per M2 close. No defect.
- **AGP/Kotlin version unchanged:** Still Gradle 8.7, AGP 8.6.1, Kotlin 2.0.21, JVM 1.8, build-tools 35.0.0. The diff only added the `useModule` mapping in `settings.gradle.kts` to avoid pulling an unverified `marker` artifact; the plugin coordinates themselves are unchanged. No defect.
- **`buildscript-gradle.lockfile` lost 2 entries** (the AGP/Kotlin Gradle plugin `marker` artifacts). This is correct: the marker artifacts have no real jar; they are just resolvable IDs. Removing them is what `useModule` does and is the documented pattern. No defect.
- **APK SHA `a0494d25...a3f3` vs M2 `5e1a1e...84c0`:** The M3 module re-organization changes the DEX layout (12 DEX files, 1327 class definitions, no duplicates) and APK bytes. Native and signing cert are unchanged. This delta is documented in `docs/gradle-build.md` and `reports/modules-m3/status.md` as expected for module reorganization. No defect.
- **JUnit body timeout 45000 ms** is appropriate for the existing host tests; matches `scripts/test-minimal-apk.sh` behavior. No defect.
- **`module_boundary_test.py` debug 4 of 5 (5 of 5 with the right diagnostic) negative probes:** Diagnostics are real (`package android.content does not exist`, etc.); locale is forced to `en`/`US` to prevent Chinese locale mismatches. No defect.
- **`tests/module_owners_guard_test.py` setUp copies the actual current compiled classes** — this is the right pattern. The 5 negative tests (missing, recompiled-dup, platform-ref, old-kotlin, current-positive) all use real fixtures. No defect.
- **`tests/gradle_apk_identity_test.py` new tests `test_missing_java_owner_rejected` and `test_duplicate_across_dex_rejected`:** Both have real fixtures and `assertRaisesRegex` against the production code path. No defect.

## Coverage and verification

- Read in full: `AGENTS.md`, `docs/modern-android-roadmap.md`, `docs/module-test-map.md`, `reports/modules-m3/{status,self-review}.md`, `.planning/llm-asr-76d.4/{task_plan,findings,progress,changed-files}.md`.
- Read in full: 11 modified files (full diff via `git diff`), 3 new module build files (`core/build.gradle.kts`, `core/gradle.lockfile`, `android/asr-runtime/build.gradle.kts`), `android/asr-runtime/gradle.lockfile`, `android/asr-runtime/src/main/AndroidManifest.xml`, `core/src/test/java/CoreRegressionJUnitTest.java`, `android/asr-runtime/src/test/java/RuntimeRegressionJUnitTest.java`, `tests/module_boundary_test.py`, `tests/module_owners_test.py`, `tests/module_owners_guard_test.py`, `scripts/verify-gradle-apk.py`, `reports/modules-m3/{result,junit-summary,build-input-sha256,native-verification,apk-report-binding}.json`.
- Inspected without modifying: APK at `android/app/build/outputs/apk/debug/app-debug.apk` (SHA verified, DEX count 12, no JUnit/Hamcrest strings); `gradle/baseline-inputs.json` (63 source files, unchanged SHA `2e3e3a6d...`); Gradle cache JUnit/Hamcrest jars (SHA256s match metadata); final verify log `.work/gradle-m1/verify-zLkkhQ/build.log` tail (BUILD SUCCESSFUL, 70 actionable tasks: 70 executed).
- Cross-checked 28 main test classes exist on disk and are all referenced from the JUnit adapter (28 unique `@Test` arg shapes; verified by enumeration).
- Cross-checked the 63-source inventory: `find android/app/src -name "*.java" -o -name "*.kt"` returns 63; `baseline['files']` filter returns 63; `module_owners_test.py` expects 63; `verify-gradle-apk.py` collects 63 from the same baseline.
- Cross-checked the new `module_owners_test.py` direct-owner check (not just set update) so inner classes cannot mask a missing top-level owner.
- Confirmed `scripts/test-minimal-apk.sh`, `scripts/verify-gradle.sh`, `scripts/build-minimal-apk.sh`, `flake.nix`, `flake.lock`, `gradle.properties`, `gradle/baseline-inputs.json` are **not** in the diff.
- Did **not** run Gradle, host tests, or any mutating command (per assignment "不跑共享Gradle/host覆盖产物"). Did not commit, push, install, or modify any tracked or untracked file.
- Did **not** read the three excluded planning directories (parent supervision + two analysis dirs).
- Could not independently reproduce the final `verifyDebug` exit 0 within the read-only review (writer's `70 actionable tasks: 70 executed` log is the supplied evidence; the parent is currently re-running per the task plan). Treated the writer's log as **unverified** for the live execution; the source/config/log is consistent and matches per-component expectations.

## Summary

M3 implementation is **code-complete and evidence-consistent** for the scope defined in `docs/modern-android-roadmap.md` §5 M3, with no P0/P1 defects found. Five minor P2/P3 observations (one is a latent fragility, one is a non-blocker code-duplication, two are documentation/maintainability suggestions, one is a confirm-only observation). The 63-source inventory, 35 JUnit bodies (28 main entries + 8 parameter paths for `ModelReviewFixTest` + 1 runtime), the new `module_boundary_test.py` real-Gradle-classpath probes, the new `module_owners_test.py` exact direct-owner gate, the `verifyApkChecker` chain extending to `:core:test` and `:asr-runtime:testDebugUnitTest`, the SHA256 metadata for JUnit/Hamcrest, and the duplicate/missing-owner cross-DEX gates are all in place and match the assignment rubric. The APK is **not** device-validated; 4 KiB/16 KiB runtime, IME, SAF, microphone, Activity lifecycle, and Android instrumented tests are all explicitly `not run` and must not be claimed as accepted. Father-side re-verification (`verifyDebug` rerun) and security commit are the next gate. No file modifications, no Beads updates, no `bd` calls were performed.