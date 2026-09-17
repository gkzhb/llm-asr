# Focused review: Android private model-root aliases

## Verdict

**No blocking regression found in the private-root alias fix.** The change accepts aliases only in the explicitly trusted application `filesDir` parent, preserves strict validation of the `model` leaf and managed final/`.part` files, and reaches the current model-management and native-inference entrypoints.

This was a **read-only source review** except for writing this requested report. No builds, tests, APK installation, or device commands were run. The large pre-existing dirty worktree was not treated as this patch. Scope was the new factory/resolution logic, AppGraph wiring, and corresponding test additions; neighboring code was inspected to trace callers and safety checks.

## Concrete findings

### No blockers: trust boundary is correctly narrowed

- `android/app/src/org/llmasr/minimal/ModelRepository.java:121-136`: `forAppFiles` stores the trusted parent without filesystem inspection; first `checkedRoot()` resolves **only** `appFilesDir`, requires a directory, and appends the literal `model` name. It does not replace a symlinked model leaf with its target. The resolved parent is retained after successful resolution; synchronized initialization and the volatile `modelDir` prevent publication of a partially initialized path.
- `android/app/src/org/llmasr/minimal/AppGraph.java:43-45`: the only production factory caller supplies `OperationContext.filesDir()`, backed by the application `Context.getFilesDir()` (`OperationContext.java:22-28`), not a SAF/provider-selected path.
- `android/app/src/org/llmasr/minimal/ModelRepository.java:94-118,152-160`: ordinary eager construction, `forWorker`, and static `checkRoot` retain their strict ancestor-alias policy. The latter still compares normalized absolute and canonical paths and checks the leaf for a symlink. Existing or dangling model symlinks and non-directory roots fail closed. Parent resolution is cached, **not** root safety validation: `checkRoot(modelDir)` runs on every `checkedRoot()` invocation.
- `android/app/src/org/llmasr/minimal/ModelRepository.java:214-224` and `FileSafety.java:16-30`: managed names continue through root validation, canonical containment, explicit symlink rejection (including links back inside the root and dangling links), and regular-file checks. Neither final nor `.part` aliases are legitimized by the new parent normalization.

### No blockers: current entrypoints use the resolved boundary

- `ModelRepository.java:179-181,329-333,492-493`: inspect, import, and delete enter via `checkedRoot`. Missing-root inspect/delete remain non-creating; creation remains in the import mutation phase (`:379-384`). Terminal inspection in `ModelManagementController.java:111-117` uses the same repository, addressing the secondary cleanup/overview failures rather than only the initial import error.
- `ModelRepository.java:169-173,214-218,242-261`: orphan reclamation, boundary validation, single-entry verification, and both full-verification variants reach `checkedRoot` through `safe`. Reclamation still validates the part it touches; full verification checks both final and part boundaries. These pre-existing helper contracts are not broadened by the fix.
- `ModelAccess.java:13-21`: even cached READY goes through `validateManagedBoundary` before allowing inference. `AsrOperation.java:152-160` (sample/WAV/recording path) and `ImeBackend.java:20-24` (IME path) call `requireReady()` **before** reading `repository.modelDir()` to construct the native config path. No additional production `modelDir()`/native config consumers were found in the Java source search. Therefore neither current native caller uses the unresolved alias path or bypasses managed-file checks.
- `AppGraph.java:41-44`, `TaskCoordinator.java:23-29`, and `ModelManagementController.java:56-71`: production repository IO is reached through the shared worker. The factory itself only constructs `File` objects and stores fields. This is a narrow model-path laziness guarantee, not a claim that all of AppGraph installation is IO-free: manifest asset parsing and Android's own `getFilesDir()` behavior are separate and pre-existing.

### LOW — regression fixture does not prove first-use worker resolution or once-only behavior

**Location:** `tests/ModelPrivateRootTest.java:17-25,67-70`.

The fixture resolves the repository with a direct `inspect()` at line 19 before the controller is constructed, and uses `Runnable::run` for controller execution. The poison `File` only checks that construction does not call that object's `getCanonicalFile()`; it does not count successful worker resolutions or catch every possible eager path/stat implementation. The implementation is correct by inspection, but this fixture would not detect repeated parent resolution or demonstrate the first canonicalization happening on the worker under ownership.

Suggested follow-up: use a queued worker and a counting parent `File`, verify no resolution before dispatch, exactly one successful parent resolution over multiple operations, retained destination after changing the original alias, and retry after an initial canonicalization/unavailable-parent error. Add direct aliased-parent assertions for `forWorker` and static `checkRoot`, not only eager construction.

### LOW — aliased-root fixture does not execute the native-readiness boundary

**Location:** `tests/ModelPrivateRootTest.java:30,45-60`; `tests/model_android_source_test.py:28-30`.

The assertion labelled “native path uses pinned parent” checks a `File` value after import; it neither invokes `ModelAccess.requireReady()` nor exercises an Android/JNI consumer. Source checks assert string presence, not call ordering or runtime behavior. Existing non-alias boundary tests help (`tests/ModelReviewFixTest.java:118-121` checks unsafe parts against a simulated native continuation), but are not the new aliased-parent case.

Suggested follow-up: call the real `ModelAccess` against the aliased-parent repository with cold and cached READY, then install model/final/part symlinks after initial resolution and assert no continuation is permitted. Add dangling final/part and in-root link cases to the alias fixture, plus direct first-use import/verify/delete/reclaim coverage. These are test-strengthening recommendations, not evidence of a current bypass.

## Evidence inspected (not rerun)

- `.work/model-root-fix/red.log:1-4` records `java.io.IOException: 非法 model 根路径/符号链接`, originating in `ModelRepository.checkRoot`, then `inspect`, then `ModelPrivateRootTest.main:19`. This matches rejection of the real ancestor-symlink fixture and the reported user error. It establishes the recorded pre-fix failure; it is not a reproduction performed during this review.
- `.work/model-root-fix/host.log` records the private-root fixture PASS and the full host-suite PASS output, including controller, readiness, provider-boundary, mutation, and report-binding checks. The new fixture covers actual host symlinks, import/cleanup/READY, SHA reuse, overview, verification, deletion, unsafe leaf/final/part rejection, and preservation of an outside sentinel.
- `scripts/test-minimal-apk.sh:18,38,50` compiles and runs the new Java fixture and runs the source-wiring check. The script compiles pure-Java components, not AppGraph/Android runtime classes; it is not an Android integration or device test.
- `git status --short` confirmed extensive pre-existing modified/untracked content. `git diff --cached --name-only` returned no staged paths. The inspected older source snapshot contains other historical changes, and `.work/model-root-fix/baseline-repository.diff` is empty; neither was treated as an authoritative isolated patch. Conclusions are based on the narrowly identified current logic and caller tracing, not attribution of the whole worktree diff to this fix.

## Residual risks / limitations

1. **No device validation.** Host filesystem behavior and source wiring cannot prove the actual Android user's alias topology, SELinux/permission behavior, direct-boot/storage availability, SAF import behavior, JNI loading, or end-to-end recovery of the reported symptoms. Existing host logs were read, not independently rerun or source-hash-bound by this review.
2. **Trusted-parent contract remains essential.** `forAppFiles` is public and cannot establish that an arbitrary `File` really came from Context. Its sole production caller satisfies the documented contract. Passing provider/user-controlled directories to this factory in the future would broaden trust incorrectly.
3. **Path checks are not descriptor-level race protection.** Existing `FileInputStream`/`FileOutputStream`, rename, delete, and JNI path opens have check/use windows (`ModelRepository.java:236-239,290-294,413-424,465`; `AsrOperation.java:153-157`). Pinning here means retaining a canonical pathname, not holding a directory inode/file descriptor. Shared task ownership serializes cooperating app operations; it does not defeat a concurrent same-UID/root filesystem adversary. This limitation predates the fix and was not demonstrated as a new regression.
4. **`modelDir()` remains a passive accessor** (`ModelRepository.java:140`). Before first successful boundary resolution it returns the original alias path and performs no validation itself. Current native callers are ordered safely; future callers must preserve the readiness/boundary gate. Space reporting also continues through Context's original filesDir (`AppGraph.java:43`), relying on the trusted Android parent remaining stable.
5. The new fixture is a small, single-entry, synchronous host scenario. It does not independently cover all first-entrypoint permutations, re-resolution failure, concurrent initialization, post-resolution symlink replacement, or full production model/native dependencies. No source changes or new tests were made during this review.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Reviewed the five requested files and traced repository/native callers; recorded no blockers, two LOW test-coverage findings with file/line references, and explicit residual risks."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/35d4908c/.work/model-root-fix/review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "git status --short; git diff --stat; git diff --cached --name-only",
      "result": "passed",
      "summary": "Read-only worktree inspection; extensive pre-existing changes and no staged paths."
    },
    {
      "command": "Read/search requested source files, related callers, existing red.log and host.log; inspect source diffs and numbered lines",
      "result": "passed",
      "summary": "Completed static review and evidence inspection; historical diff output was not treated as the isolated fix."
    },
    {
      "command": "Builds/tests/device validation",
      "result": "not-run",
      "summary": "Prohibited for this read-only review; existing logs only were inspected."
    }
  ],
  "validationOutput": [
    "Existing red.log contains exact error: 非法 model 根路径/符号链接 at checkRoot -> inspect -> ModelPrivateRootTest.main:19.",
    "Existing host.log records the new private-root fixture and full host suite passing; not independently rerun.",
    "Static tracing confirms both current native config-path consumers gate through ModelAccess.requireReady before modelDir access."
  ],
  "residualRisks": [
    "No Android/device/JNI validation or independent rerun/source binding of existing logs.",
    "Factory relies on trusted Context filesDir; modelDir accessor itself is intentionally lazy and unvalidated.",
    "Pre-existing pathname check/use races remain outside shared-owner protection against noncooperating filesystem actors.",
    "New fixture does not prove worker-first/once-only resolution, aliased ModelAccess/native continuation, or every first-use and post-resolution symlink case."
  ],
  "noStagedFiles": true,
  "diffSummary": "Review artifact only; no source, test, build output, or staged-file modifications by this review.",
  "reviewFindings": [
    "No blockers found in the focused private-root alias fix.",
    "LOW: tests/ModelPrivateRootTest.java:17-25,67-70 — first-use worker resolution and once-only behavior are not demonstrated by the fixture.",
    "LOW: tests/ModelPrivateRootTest.java:30,45-60 and tests/model_android_source_test.py:28-30 — aliased-root native/readiness behavior is checked indirectly rather than executed."
  ],
  "manualNotes": "The large dirty worktree predates this fix and was left untouched. Only the requested review artifact was written. No device validation is claimed."
}
```
