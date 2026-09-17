# Independent Android review — 0.5 model management

## Verdict and scope

**Static review only. No demonstrated deadlock, premature shared-owner release, unintended ASR-result overwrite by maintenance, stale picker import after recreation, or IME stale-text submission was found in the frozen production wiring.** Two concrete issues should be fixed before calling the agreed lifecycle/recovery/privacy contracts complete: automatic inspection erases the very terminal outcome a returning user needs (R1), and the SAF exception boundary has an explicit unsanitized exception escape (R2). Additional snapshot/presentation issues are separated below; these are not claims of model corruption or concurrency-lock failure.

Read `docs/model-management-plan.md` in full (sections 1–11). Read the full current `ModelManagementActivity`, `ModelPageSession`, `ModelPickerTickets`, `MainActivity`, `AsrImeService`, `SafModelSource`, `AppGraph`, `ModelAccess`, `AppState`, `ImeBackend`, `ModelManagementController`, `ModelManagementState`, `ModelOperationControl`, `ModelReadiness`, `ModelUiText`, `RequestRunner`, `TaskCoordinator`, `AsrOperation`, `ImeController`, `RecordingControl`, `ModelRepository`, `FileSafety`, and manifest. Read the current Android helper/source-wiring tests and controller tests to assess their coverage, **not to claim execution**.

Paths below are relative to `/home/zhb/gitrep/llm-asr`; Java paths abbreviated as `android/app/src/org/llmasr/minimal/NAME.java` remain authoritative. Severity: P1 = required functional-contract fix before delivery; P2 = localized correctness/privacy-contract fix; P3 = partial UI/spec feature. Severity does not imply a device reproduction.

## Required fixes, prioritized

### R1 — P1: Re-entering the page erases cancellation/failure/partial-delete history with an automatic successful INSPECT

**Locations:** `ModelManagementActivity.java:193–200,161–167`; `ModelManagementController.java:42,121–128,146–174`; `ModelManagementState.java:193–205`; `ModelUiText.java:55–79`.

**Concrete sequence:**

1. Start an import, then press Home. `onStop()` calls `page.stopped(false)` at Activity line 213; the page requests cancellation for its operation.
2. The worker closes/cleans, publishes CANCELLED with its failure/cleanup conclusion and leftovers, and releases the shared owner.
3. Return to the same Activity. At line 200, `inspectNeeded = !page.ownsActive()` is true because the completed operation is no longer active.
4. The next render submits INSPECT at line 165. `pending()` builds from `ModelManagementState.empty()`, copying only storage/files from the prior snapshot. It discards the prior operation kind/ID, outcome, error, failed-file list, cleanup conclusion and deletion counts.
5. INSPECT completes and the task card now says “概况检查 … 已完成.” The returning user cannot see why their import stopped, whether cleanup failed, or the partial-deletion result. Rotation after a task finishes or opening a new page after a failed operation has the same outcome. If cancellation is still active when resuming, the history happens to survive; that timing dependence is itself the problem.

This is not merely a missing expandable detail widget: it violates the explicit Home/lock return and recent-outcome/recovery contracts. A fresh storage check is required, but must not replace the latest user operation record. It can also discard a partial-delete error before the user gets back to inspect it. ASR text/reports are **not** affected.

**Fix:** Separate the last user-operation terminal record from the transient inspect operation, or merge inspect results into inventory/readiness without overwriting the retained IMPORT/VERIFY/DELETE outcome. Keep the original operation ID, failure names, cleanup result, and deletion counts until another explicit user operation supersedes them.

**Regression suggestion:** With the real queued controller/repository and page policy, cancel an import, complete cleanup/release, perform the same refresh-on-resume sequence, and assert fresh storage **and** retained CANCELLED/operation ID/cleanup result. Repeat with SHA failure, failed `.part` cleanup, partial deletion, and rotation occurring immediately after completion. Include picker cancellation followed by resume: it must not erase an existing user-operation result.

### R2 — P2, required privacy-contract fix: `CancellationException` bypasses SAF message sanitization

**Locations:** `SafModelSource.java:62–64,73–91`; `ModelManagementController.java:100,162`; `ModelUiText.java:48–53,70`.

The adapter sanitizes ordinary `IOException`/`RuntimeException` from provider query/open/read/close, but explicitly rethrows `java.util.concurrent.CancellationException` unchanged at every boundary. The controller catches that exception as an ordinary failure and copies `failure.getMessage()` into the shared snapshot. The task card then applies only a path/URI/length heuristic.

**Concrete sequence:** A source stream's `read(byte[],off,len)` throws `new CancellationException("Account alice@example.test; selected folder Confidential")`. SAF line 80 rethrows it; repository cleanup runs; controller line 162 stores the provider message. `safeError()` accepts this text (no slash, URI separator or excessive length), so it is displayed. If the message contains a full URI the final UI guard usually hides it, but the raw message has already crossed into `Snapshot.errorCode`. This directly contradicts the adapter's “Provider exception messages/URIs never cross this boundary” contract. It does **not** establish network exfiltration or ASR-report persistence.

**Important qualification:** Android's usual `android.os.OperationCanceledException` is not this Java exception and falls through the generic sanitized runtime catch. This finding concerns the explicit alternate exception path, not a claim that all Android permission/cancellation failures leak.

**Fix:** Treat provider cancellation exceptions as sanitized source-read/enumeration failures, or map them to the app's own fixed-message cancellation only when the operation's cancel gate actually confirms cancellation. Never forward provider-authored message text or attach it to the model snapshot.

**Regression suggestion:** Exercise the actual SAF adapter with resolver/cursor/stream fixtures that throw `CancellationException` with sentinel sensitive strings from query, cursor movement/close, stream open/read/close. Assert no sentinel enters `errorCode`, displayed text, or reports. Pair with SecurityException, FileNotFoundException and Android OperationCanceledException fixtures to avoid weakening the already-sanitized paths. Keep genuine user cancellation and cleanup-failure conclusions independently visible.

## Other concrete issues / partial implementation

### R3 — P2: Cancel accepted after a failed/cancelled terminal publication can overwrite the terminal phase during finalization

**Locations:** `ModelManagementController.java:47–53,159–185`; `ModelOperationControl.java:33–38`; `ModelPageSession.java:43–46`.

For successful work, `trySucceed()` marks success before terminal publication, so late cancel is rejected. For FAILED/CANCELLED work, the control remains unterminated until `finish()` line 185, while the terminal snapshot has already been published. A main-thread cancel that wins the controller monitor between `terminal()` returning and `finish()` acquiring it can still be accepted. `requestCancel()` replaces the terminal phase with CANCELLING. `finish()` now sees a nonterminal snapshot and replaces it with FAILED/“任务未启动或异常终止”, losing the useful original conclusion. For an already-cancelled operation, the visible cancel button is disabled, but lifecycle `cancelOwn()` still calls requestCancel; for a just-failed operation the cancel button can also remain eligible in that small window.

This is a bounded terminal-state race, **not** a demonstrated stuck owner or deadlock. A repeated cancellation after terminal publication should be idempotent and must not revise the terminal record.

**Fix/regression:** Reject snapshot mutation when the matching operation is already terminal, and/or explicitly arbitrate every terminal outcome in control before notifying. A deterministic controller test can pause in a readiness listener after terminal publication (`run()` line 118), issue `page.stopped(false)` or cancel from another thread, then release finalization. Assert the original terminal/error/cleanup fields survive and owner release still occurs exactly once. Also check a reentrant terminal listener requesting cancel, without introducing any UI-blocking wait into production listeners.

### R4 — P2 presentation: Full reuse is reported as zero bytes

**Locations:** `ModelRepository.java:317–325,343–358`; `ModelManagementController.java:143`; `ModelUiText.java:67`.

A repair import against seven already-correct local files calculates `reused` internally but emits CHECKING_EXISTING updates with reused=0. It skips every COPYING update and proceeds straight to FINAL_VERIFY, whose updates intentionally do not change `b.reusedBytes`. The terminal card therefore says zero reused bytes, although all files were reused and none copied. Cancellation/preflight failure after checking reusable files can likewise leave zero as the displayed reuse amount.

**Fix/regression:** Publish the completed reuse plan before copying/final verification (and define whether the displayed number means confirmed-so-far or final planned reuse). Regression: import twice, with the second source refusing every `open()`; expect success, zero opens/copied bytes, and reusedBytes equal to the manifest total.

### R5 — P3 presentation: Partial-delete final row can use the wrong denominator

**Locations:** `ModelRepository.java:462–469`; `ModelManagementController.java:155`; `ModelUiText.java:59,78–79`.

During deletion `fileIndex` is the count of processed managed items and `fileCount` is the plan size, which may include up to fourteen finals/parts. Terminal inspection resets `fileCount` to seven but retains the last deletion filename/index. The text formatter then treats the terminal phase as a non-DELETING phase and adds one to the index. A terminal card can thus show a last-file row such as “15 / 7”; even deleting just seven finals produces “8 / 7”. Accurate success/failure totals remain separately present, and file deletion itself is unaffected.

**Fix/regression:** Do not render a live-file index for terminal snapshots, or retain explicit operation-specific processed/planned counts without the generic +1. Test terminal formatting for one final, seven finals, and fourteen final/part items.

### R6 — P3 / incomplete UI specification (not storage corruption)

- **Space preflight explanation:** `ModelRepository.java:340–341` reports only “存储空间不足：预检”; `ModelManagementActivity.java:173` exposes remaining-space estimates only while COPYING. A preflight failure never supplies the planned required bytes, so users get available bytes but not the conservative requirement promised in MM-03/error recovery. Add plannedRequiredBytes/available-at-check to a typed failure/snapshot; test a reject-before-first-copy plan with some reused files.
- **Per-file state:** `ModelManagementActivity.java:177–181` renders presence/size/global READY, not per-file reuse/copy/verified-current-file or SHA-failure state; SHA failure names are in the task card instead. This is a partial file-details implementation, not a false global READY claim.
- **Delete progress:** `ModelUiText.java:61` explicitly defers failure-count aggregation until the end, whereas MM-04 requested failure counts during deletion. With only fourteen managed items this is a limited UI completeness gap.
- Busy-source text at Activity lines 174–175 is honest but generic (“应用 / 输入法 / 另一管理页”), not the requested precise owner-source explanation. The coordinator currently exposes only busy, not typed owner identity.

## Assessed concerns that are NOT established blockers

### `ModelPageSession.cancellable()` double peek: improve it, but no production NPE sequence established

At `ModelPageSession.java:44–46`, a local `op` is read, then `ownsActive()` independently rereads control. This is needlessly inconsistent; use one local operation and compare `op != null && op.id.equals(ownedId)` before evaluating its flags/kind.

However, the common completion race does **not** cause an NPE: if the first read sees operation A and the worker clears it, `ownsActive()` returns false and short-circuits. To dereference null, the first read must see null and the second must see an operation matching `ownedId`. In the current production wiring, page state/adoption and every new maintenance admission run on Android's main thread; the worker can finish/clear but does not start another owned operation. Operation IDs are fresh UUIDs and finished operations are not reinstalled. Thus that null-to-owned-active transition cannot be manufactured merely by worker completion during the same main-thread call. It would require off-main page/admission use or a different future integration. Classify as single-snapshot hardening, not a proven Activity crash. Existing helper tests do not interleave these reads.

### Reentrant notifications, lock order, release and button refresh

- `TaskCoordinator.java:46–58` acquires the sole atomic owner before admission and dispatch; busy requests never queue. `RequestRunner.java:49–61` finalizes resources before coordinator release. `ModelManagementController.java:105–118` keeps terminal disk inspection inside the owner; `finish():184–185` clears model control before `TaskCoordinator.release():73–76` publishes the free-owner notification through AppState.
- Model-state notification occurs synchronously while the controller holds its short monitor. The current Activity invalidation handler (`ModelManagementActivity.java:237–251`) only reads an atomic snapshot and coordinator busy and posts to main. Crucially `run()` releases the dispatch monitor before entering `render()`/admission. There is no present controller→dispatch→controller inversion. Readiness commit inside control (`ModelOperationControl.java:47–51`) is callback-free; readiness invalidation/notification generally happens outside its state monitor. Actual listeners in Main/IME/page enqueue rather than synchronously rendering/admitting.
- Terminal notification can precede owner release, but the separate AppState notification after release triggers a fresh read and re-enables buttons. No stale captured snapshot is applied. This is stronger evidence than only checking a terminal snapshot's `pageOwnerHeld` field.
- Cancellation latches; it does not interrupt or close provider streams on main. The worker owns stream close, cleanup, inspect and release. A blocked query/read/close may keep the task busy indefinitely; that is the explicitly documented cooperative-cancel limitation, not evidence of a deadlock. R3 is about terminal presentation in a small finish window, not early release.

### Picker/lifecycle isolation

- `ModelPickerTickets.java:13–20` allows one pending ticket process-wide, with nonreused request codes 100–999 distinct from audio/export. No URI is stored in tickets. `ModelPageSession.java:21–33` rejects stale/duplicate callbacks and consumes even a busy-rejected callback exactly once. `destroy():54` releases its own ticket; another page's ticket cannot be released by an old page.
- A recreated page restores only operation ID, not the picker ticket or URI (`ModelManagementActivity.java:55,217–219`). Old picker results are rejected and the user is told to reselect. Waiting for SAF does not hold owner.
- The result callback can defer its URI while not resumed; `consumePicker():139–144` requires visible **and** interactive/unlocked eligibility. Resuming consumes before automatic inspect. Neither Home nor lock causes an unattended background import from a deferred callback.
- Active import/verify cancellation is delayed until nonconfiguration `onStop()` so rotation can transfer ownership. Screen-off broadcasts request cancellation, and deletion is deliberately noncancellable. `onPause()` detaches listeners and clears KEEP_SCREEN_ON; weak/coalesced callbacks do not hold the old Activity as a worker closure. R1 is the concrete return-state defect despite these otherwise sound policies.

### Main recording navigation and IME safe departure

- `MainActivity.java:161–170` asks explicitly before leaving an uncommitted recording. “停止并等待” remains on Main until `AsrOperation.clearRecording():101–103`; capture's inference handoff at lines 92–97 occurs only after `RecordingControl.tryCommitInference()` acknowledges hardware release. `MainActivity.refresh():120–123` then opens management. Its ensuing onPause cancellation cannot cancel a recording that has already committed/cleared. The owner remains held for inference, so the model page remains read-only until it finishes.
- Cancelling and leaving is explicitly destructive, not silent; the owner remains occupied during actual microphone cleanup. `navigateAfterRecording` is cleared onPause, so a delayed capture notification cannot later relaunch management after Home. The UI should still be manually checked around a stop/cancel dialog left open while the 30-second recording finishes; no new lock violation is inferred from that race.
- `AsrImeService.java:65–79` uses the existing safe leave path. `ImeController.java:34–45` invalidates the session/preview and requests capture cancel before hiding and launching. Committed inference is not forcibly cancelled, but its result fails `live()` and cannot revive old preview. Only the synchronous commit action obtains the current InputConnection (`AsrImeService.java:135–140`), and management launch carries no transcript/audio URI extras.
- The new IME entry is the only Service behavioral diff from the prefeature baseline. The existing `ImeController` and recorder arbitration remain unchanged. No source basis was found for a new stale submission or automatic recording restoration.

### Result isolation and platform permissions

`AppGraph.java:39–48,108` injects one coordinator/readiness into maintenance and both ASR consumers. AppState delegates verified state to that readiness; a true setter cannot manufacture a hash proof. `ModelAccess.java:13–28` acquires the verify token before SHA, fails stale commit, and notifies shared state. Maintenance uses private runner State, no-op reports, no ASR cleanup and MAINTENANCE policy (`ModelManagementController.java:20–33,187–197`; `RequestRunner.java:59,71–86`). It neither clears Main text nor writes `last-result.json`. Model deletion remains the fixed-root whitelist; unknown files and results are not expanded into the deletion scope.

The manifest declares only RECORD_AUDIO, an internal nonexported management Activity with no intent filter/separate process, and the existing BIND_INPUT_METHOD-protected service. Opening management itself neither requests mic permission nor launches SAF nor hashes the model. Cold graph creation reads the bundled manifest and runs existing asynchronous startup cleanup; model repository construction is deferred-IO. SAF explicitly requests only read access and does not persist URI permission. Ordinary permission/provider failures are sanitized; R2 is the precise exception to that conclusion.

## Baseline comparison and evidence limits

Compared current MainActivity/AppGraph/AppState/AsrOperation/AsrImeService/SafModelSource/ImeBackend against `.work/model-management-baseline` file contents, rather than relying on git diff. Main's three old model actions moved out; shared lazy readiness replaces the independent boolean; IME safe-leave behavior was preserved.

Compared actual SHA-256 against `.work/model-android/baseline.json`: ModelManagementActivity/ModelPageSession/ModelPickerTickets/ModelUiText/ModelAccess are absent from that lane baseline; the Android integration files changed. ModelManagementController/State/Control, RequestRunner, TaskCoordinator and ImeController are unchanged from that lane baseline. Consequently some findings are cross-lane integration effects or pre-existing lane behavior, not newly introduced edits in the Android lane. Untracked production files were read directly.

**No tests, builds, device operations, network access, commits, source/docs/dist/report-tree edits, or delegation were performed.** Only read/list/diff/hash commands and this authorized returned artifact were used. A parent progress notification was sent; no work was delegated. The prefeature `diff -u` command for Main returned the ordinary difference exit status 1, not a validation failure. Test files were read only; their assertions are not evidence that Android lifecycle/provider behavior was run.

### Final frozen-source integrity check

After completing source reads, a read-only Python SHA-256 comparison against `.work/model-android/review-input-sha256.json` reported:

```text
61 entries; 61 MATCH; 0 MISMATCH
manifest SHA256: 2769f2c6669c9c2f3877c2fea715d04ffd7e71ae20ef3a8ee9ed4a3681d836d9
```

`git diff --cached --name-only` returned no paths. No staged files. The sole authored file is this output artifact at the authoritative run-specific `.pi-subagents/artifacts/outputs/98e2832f/.work/model-management-android-independent.md` path.

## Residual risks and required parent/device follow-up

1. Parent's concurrent build/test results are independent and were not consumed or represented as this review's validation. Android helpers use a queued host executor, not framework Activity callbacks or real ContentResolver behavior; source-string tests cannot prove either.
2. Real SAF providers, temporary grant lifetime across rotation/Home/lock, null cursor/type flags, unavailable picker, read/close stalls and permission revocation need Android verification. No claim is made that cloud providers are offline simply because the app has no INTERNET permission.
3. Validate Activity recreation timing, screen-off delivery, multi-window/paused-but-visible behavior, system Back/task-stack paths from IME, and returning via the launcher. The source uses onStop rather than every focus loss to decide nonconfiguration departure; no device evidence resolves edge scheduling.
4. Visual accessibility/insets at target SDK 35, 200% font, small landscape screens, navigation bars, TalkBack and three IME tool buttons remain device-only uncertainty. Numeric stage text is not proof of acceptable layout or announcements.
5. No new actual device recording/inference/model import/delete was authorized. The lack of a static owner/IME safety defect is not a throughput, ANR, memory, or real-hardware release certification.
6. Recheck hashes after any fixes and bind the final review to the rebuilt APK. This review applies only to the 61-entry frozen input above.
