# Focused 0.3 export-fix follow-up review

## Verdict

**F1 (startup cleanup/status) and F2 (cloud-provider disclosure) are resolved at source level.** The new export identity/epoch scheme closes the previously identified old-callback/new-snapshot confusion and revokes tickets created before successful clear invalidation. **One medium-severity source concurrency gap remains: export snapshot creation itself does not honor the task owner and can capture pre-clear text at the post-clear epoch.** This is a concrete permitted source interleaving, not an observed Android/provider reproduction. Do not attest unconditional clear/export revocation safety until that gap is addressed or the event ordering is excluded by a tested invariant.

Independently ran the authorized host script: **32 + 20 + 34 checks passed**. Actual SAF/Activity integration remains untested. This report attests review completion, **not user acceptance of 0.3**, APK packaging, or device behavior.

## Scope and evidence

Read `reports/apk/result-management-review.md`, all of current `MainActivity.java`, `ResultFiles.java`, `ExportSession.java`, `tests/ResultFilesTest.java`, and `docs/minimal-apk.md` including its final section (126–134). Read the host script and searched Java call sites. The Java source fingerprints below were identical before and after the independently invoked test script and at final verification.

No source, tests, docs, or index changes were made. Only this requested report was written, aside from the explicitly authorized host script's normal generated class outputs and temporary test fixtures. No device, provider, network, model operation, or heavy/full APK build was performed. Concurrently changing generated build reports were not used as verification evidence.

## Findings

### Resolved F1 — prior low severity: single startup preflight and terminal count

**Locations:** `android/app/src/org/llmasr/minimal/MainActivity.java:115–117,186–213`; `android/app/src/org/llmasr/minimal/ResultFiles.java:12–15`; `tests/ResultFilesTest.java:29–37`.

Startup now submits only a status-producing maintenance body. The single `cleanTemporary` call is in shared preflight at line 195, after the process-wide CAS acquisition at line 190. Its returned count is retained in `cleanedTemporary`, and startup publishes `startupStatus(cleanedTemporary)`, including zero, readiness, and model instructions. The `finally` releases `RUNNING` and refreshes. Failure publishes an error instead of a false success. Startup no longer cleans twice or ordinarily leaves the executing message behind.

The host checks cover two leftovers (UUID WAV and UUID report part), count rendering, zero-case readiness, retained results, and a nested model part. They do not execute `onCreate`, its scheduling, or final UI refresh; the Activity-specific resolution is source reasoning.

### Resolved F2 — prior low severity: explicit cloud-provider disclosure

**Locations:** `android/app/src/org/llmasr/minimal/MainActivity.java:49`; `docs/minimal-apk.md:130–134`.

The visible Activity note now explicitly warns that share recipients and cloud export providers may network/sync. The final docs section explicitly names cloud `DocumentProvider`, recommends a phone-local destination for local saving, and distinguishes this app's lack of network permission from external providers' behavior. It also disclaims retraction of external files and provider sync durability. This resolves the requested wording improvement; no real chooser/provider/network behavior was tested.

### N1 — Medium: export creation bypasses task ownership, allowing stale text to acquire a fresh epoch

**Locations:** `android/app/src/org/llmasr/minimal/MainActivity.java:95–104,108–110,120–128,166–167,190–195,312–316`; `android/app/src/org/llmasr/minimal/ExportSession.java:14–17,24–25`.

Clear and the eventual export **write** are correctly owned, but the export click reads `lastText` and calls `exports.begin(lastText)` without checking `RUNNING` or entering an equivalent exclusion boundary. `setBusy` is applied through a posted `refresh`, not synchronously when clear acquires the owner. Disabling controls is therefore not a source-level concurrency invariant.

Permitted interleaving if an export action is dispatched during that UI-disable window:

1. Clear acquires `RUNNING` and its worker completes preflight.
2. Clear executes `exports.invalidate()`, incrementing the epoch and removing the old pending ticket.
3. Before `ResultFiles.clearResults(...)` returns and `lastText=""` executes, an export click reads the still-present old text and calls `begin`. It receives the **new** epoch. Disk deletion can make this interval nontrivial.
4. Clear completes and releases the owner. The newly opened picker subsequently returns.
5. Export acquires the owner and `valid(ticket)` succeeds because the old text was stamped with the new epoch; it can write after a successful clear.

Synchronized `ExportSession` methods do not close this gap: synchronization does not include the separate `lastText` read/clear. The existing host tests start a new ticket after invalidation but do not model concurrent Activity text mutation or owner checks. Ordinary modal flow may make reproduction uncommon; this review did not demonstrate a queued-click/re-entry device sequence. Nevertheless, the caller lacks the explicit exclusion used elsewhere, so complete revocation safety is not established by source.

**Suggested correction (not applied):** reject export creation while `RUNNING` is true before reading text/beginning a ticket, with an explicit invariant that all task acquisitions and export-begin handlers run on the main thread; alternatively serialize snapshot capture and invalidation/text clearing under one shared boundary. Retain the existing owned epoch check before opening the provider. Add a deterministic owner/session test that pauses clear after invalidation but before text clearing and verifies that no new old-text ticket can be created; then validate the Activity event path separately.

### F3 — prior medium validation follow-up: substantially improved, still open for integration

**Locations:** `tests/ResultFilesTest.java:29–56`; `scripts/test-minimal-apk.sh:7–10`; `android/app/src/org/llmasr/minimal/MainActivity.java:303–334`.

The suite increased from 14 to 34 result checks. It now covers the UUID report part, multiple leftover counts, `edited-result.txt`, nested model-part preservation, write/close exception propagation, pending-export rejection, epoch revocation, stale callback isolation, immutable snapshot identity, and matching abandon behavior. However, the close test is a host try-with-resources test, and the recreation test directly invokes `abandon`; neither exercises Android callbacks or `ContentResolver`. The script still excludes `MainActivity`. No real owner contention, request delivery after process death, provider truncation/partial writes, or Activity destruction was tested. This remains a validation boundary, not evidence of an additional implementation defect.

## Task-owner and session audit

- **Cleanup/file ownership:** the only Activity `cleanTemporary` call is the shared owned preflight. Clear calls `clearResults` within maintenance and includes both preflight and result-deletion counts. Model status, edit application, clear, startup, and export writes all call `maintenance`, which delegates to the same CAS/worker as model import/verification, sample/WAV inference, and recording. Final request-WAV deletion precedes owner release. No new unowned destructive helper call or active-WAV cleanup overlap was found.
- **Preservation:** `ResultFiles.java:17–26` is still nonrecursive and exact-name restricted; matching symlinks/nonfiles fail rather than being followed. Normal maintenance does not replace the inference report, including cancellation now guarded by `report` at `MainActivity.java:204`. Clear failure can be partial; no success is claimed on an exception.
- **Request identity:** the process-wide session permits one live pending ticket. Codes increase from 1000 through 65534 without reuse in a process and are distinct from the fixed model/audio/microphone codes. Exhaustion fails explicitly rather than wrapping. `take`/`abandon` only affect a matching code; delayed callbacks cannot consume or abandon a newer ticket.
- **Activity-local identity:** only a callback matching `exportRequest` can take a snapshot. Cancellation/invalid data returns before stream creation. `onDestroy` abandons its matching pending ticket, without affecting another code. A recreated Activity starts with `-1` and refuses an old callback; private drafts are not bundled. A ticket already handed to an owned worker is not cancelled by later Activity destruction—this is an already-accepted write, not a pending-picker guarantee.
- **Revocation for pre-existing tickets:** clear's `invalidate` is inside its owner, and export's `valid` check is inside the write owner before `openOutputStream`. A successful clear invalidation rejects an earlier pending or taken ticket. Clear cannot interleave between the validity check and the write; if export already owns the task, clear is rejected as busy. If clear preflight fails before `invalidate`, the clear itself fails and does not promise revocation. N1 concerns newly minted tickets during clear, not failure of epoch comparison on earlier tickets.
- **Write semantics:** the callback captures a final ticket and target URI; the worker writes `ticket.text` as UTF-8 using `wt`. Success is published after try-with-resources closes. Busy rejection, null streams, invalid tickets, and exceptions cannot claim successful export. Picker-created empty files, partial provider writes, and external synchronization remain outside local rollback.
- **Docs precision:** `docs/minimal-apk.md:130` should be understood as one *live pending snapshot*, not necessarily one platform picker physically outstanding: invalidation/abandonment releases the slot before an old callback returns. Distinct IDs safely reject those late callbacks. Device/task-stack behavior still needs validation.

## Independently executed validation

Command:

```sh
PATH=/nix/store/4dqvcqjnhj6n29r6sh5wwrz58540gbfn-openjdk-17.0.20.1+1/bin:$PATH bash scripts/test-minimal-apk.sh
```

Observed stdout:

```text
PASS 32 minimal APK Java checks
PASS 20 recording lifecycle gate checks (fake backend; not device release latency)
PASS 34 result cleanup/export checks (host only)
```

`git diff --cached --name-only` was empty at initial and final inspection. Read-only source/call-site inspection and repeated SHA-256 commands succeeded. No additional tests were added; generated build reports were ignored as evidence.

### Reviewed Java fingerprints — SHA-256

| File | SHA-256 |
|---|---|
| `android/app/src/org/llmasr/minimal/MainActivity.java` | `d3490c5ea2a40736c4d09ebd96f2093c1c0f1a645ddb35ae4721aef58aa2bf4e` |
| `android/app/src/org/llmasr/minimal/ResultFiles.java` | `7ce787785e4dc20e6e320824119c758d6792358b28151290bb8f72f47fd67e01` |
| `android/app/src/org/llmasr/minimal/ExportSession.java` | `01c32db7def0e596cdd4ac3ffabf2adb3a7a6c73a8497004ff492a6ade769111` |
| `tests/ResultFilesTest.java` | `393d7e16fb85177405c1531daadf91f7aae35c03e2532dacb1cb2e6508593e58` |

## Residual risks

- N1 prevents an unconditional source attestation of clear/export revocation; Android reachability has not been reproduced.
- SAF/Activity integration, actual recreation versus rotation, process death and request routing, Home/launcher or multi-window re-entry, and real provider failure/close behavior require framework/device validation.
- Host checks do not establish APK build/package correctness, upgrade preservation, microphone release timing, active-WAV survival on-device, or user acceptance of 0.3.
- Failed cleanup may partially delete files; deletion is not secure erasure. A failed preflight can block clear before invalidation. External documents, clipboard and recipient/provider-held copies are not revoked by app-private clear.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Located findings resolve F1 and F2, identify medium N1 at MainActivity.java:95-110 and ExportSession.java:14-25, and retain F3 integration limits; independently observed 32+20+34 host checks and recorded stable SHA-256 identities for all four requested Java sources."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/566272bd/reports/apk/export-fix-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "PATH=/nix/store/4dqvcqjnhj6n29r6sh5wwrz58540gbfn-openjdk-17.0.20.1+1/bin:$PATH bash scripts/test-minimal-apk.sh",
      "result": "passed",
      "summary": "Independently observed 32 minimal Java, 20 fake-backend recording gate, and 34 host result cleanup/export checks."
    },
    {
      "command": "sha256sum android/app/src/org/llmasr/minimal/{MainActivity,ResultFiles,ExportSession}.java tests/ResultFilesTest.java",
      "result": "passed",
      "summary": "Identical before/after host tests and at final inspection; fingerprints recorded in report."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "Empty staged diff at initial and final inspection."
    },
    {
      "command": "Read-only requested-file and Java call-site inspection",
      "result": "passed",
      "summary": "Read prior review, current four Java files, host script and final docs section; traced all cleanup, clear, export session and write callers."
    },
    {
      "command": "Device/provider/network/model operations and full APK build",
      "result": "not-run",
      "summary": "Excluded by review scope. Concurrent generated build reports were not verification evidence."
    }
  ],
  "validationOutput": [
    "PASS 32 minimal APK Java checks",
    "PASS 20 recording lifecycle gate checks (fake backend; not device release latency)",
    "PASS 34 result cleanup/export checks (host only)"
  ],
  "residualRisks": [
    "N1: export begin can stamp pre-clear text with a post-invalidation epoch if dispatched during clear; source interleaving identified, no device reproduction claimed.",
    "Actual SAF/Activity lifecycle, queued events, provider I/O and task-stack integration remain untested.",
    "Failed preflight can prevent clear invalidation; deletion can be partial and is not secure erasure; external copies are not recalled.",
    "No full build, on-device recording/WAV regression result, or user 0.3 acceptance is established."
  ],
  "noStagedFiles": true,
  "diffSummary": "Reviewer wrote only the requested report; authorized host script generated normal test outputs. No source/test/docs/index edits.",
  "reviewFindings": [
    "Resolved F1, prior low: MainActivity.java:115-117,190-213 and ResultFiles.java:12-15 now use one startup preflight cleanup and publish terminal count/readiness.",
    "Resolved F2, prior low: MainActivity.java:49 and docs/minimal-apk.md:132 explicitly disclose cloud-provider networking/sync.",
    "N1 medium: android/app/src/org/llmasr/minimal/MainActivity.java:95-110,123-128 and ExportSession.java:14-25 lack owner exclusion for snapshot creation; old text can receive the new epoch during clear. Blocks unconditional revocation-safety attestation, not a demonstrated Android exploit.",
    "F3 medium validation follow-up: tests/ResultFilesTest.java:29-56 improves helper coverage but scripts/test-minimal-apk.sh:7-10 still excludes MainActivity/SAF integration."
  ],
  "manualNotes": "Review completion only, not release/user acceptance. Source hashes remained stable. No devices, providers, network, model operations, heavy builds or generated-build-report attestation. Parent was notified of N1."
}
```
