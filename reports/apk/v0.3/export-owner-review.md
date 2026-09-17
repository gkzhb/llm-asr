# N1 final task-owner review

## Verdict and findings

**N1 (previously medium) is resolved at the current source/call-site level. No new blocking or actionable correctness defect was found in this narrow fix.** Independently ran the authorized host suite: **32 + 20 + 41 checks passed**. This is not an Android/SAF integration or release acceptance attestation.

- **Resolved N1 — `android/app/src/org/llmasr/minimal/ExportSession.java:17–29`; `android/app/src/org/llmasr/minimal/MainActivity.java:95–110,186–213,312–318`.** `beginOwned` acquires the same `RUNNING` via CAS before invoking the supplier and creating the ticket. The Activity passes `() -> lastText`, with no preceding export-handler text read. Failed acquisition throws outside the `try/finally`, so it cannot release clear's owner. Successful acquisition releases on success, empty text, supplier failure, or `begin` failure. The picker starts only after release; waiting for a user/provider does not retain ownership.
- **Ordering verified in actual callers.** Clear's invalidation, file deletion and text erasure remain within `maintenance`/`launch` ownership. If clear wins, snapshot creation cannot read text in the invalidate-to-erasure interval; after successful clear it sees empty text. If snapshot creation wins, subsequent clear invalidates its epoch. Callback ticket retrieval remains outside ownership, but the validity check and provider open/write/close remain together inside ownership, so clear cannot interleave after validation and before writing. No production call directly bypasses `beginOwned` via `begin`; direct `begin` calls are in helper tests.
- **Regression evidence — `tests/ResultFilesTest.java:59–89`.** The clear thread acquires the owner, invalidates, then signals a latch and parks before text erasure. While parked, export must reject, invoke its supplier zero times, and leave the clear owner held. The test releases/joins clear and checks thread errors, empty-text rejection with owner release, a fresh snapshot with owner released for picker waiting, later invalidation of that snapshot, and supplier-exception release. This deterministically models the N1 ordering, not a reproduction through Activity callbacks or disk deletion. Three-second latch deadlines can fail on a severely stalled host; they did not fail here.

## Evidence boundaries and residual risks

- `scripts/test-minimal-apk.sh:7–10` compiles helper/test Java, **not `MainActivity`**. Current Activity wiring is source-reviewed, not framework-executed. SAF routing, recreation/process death, queued UI/re-entry and real provider write/close behavior remain untested in this review.
- `ExportSession.java:18,26,36–37` still exposes caller-supplied ownership and unowned primitives. The invariant is satisfied by current production callers, not enforced against arbitrary future callers supplying a different owner or calling `begin`/`invalidate` directly. This is a maintenance risk, not a newly observed call-site defect.
- Clear preflight can fail before invalidation; deletion can fail partially before `lastText` erasure (`MainActivity.java:109–110,195`; `ResultFiles.java:17–25`). A failed clear does not guarantee erasure. Private deletion is not secure erasure and cannot retract external copies. Provider-created empty/partial files and sync durability remain outside rollback. An already-owned export write is not revoked by a later rejected-busy clear.
- No device, provider, network, model operation or heavy/full build was run. Generated build reports were ignored as evidence. No unrelated roadmap findings were reopened.

## Reviewed SHA-256

These hashes were identical before and after the independent host run.

| File | SHA-256 |
|---|---|
| `android/app/src/org/llmasr/minimal/MainActivity.java` | `df37c98d4447fca2e909d7a37e77cb35341dc10f8277b43dd24ba79450aed95f` |
| `android/app/src/org/llmasr/minimal/ExportSession.java` | `af57a8bc968f8eae2c51acab49b808402cb747051837ed89ffc41e6203f70a9f` |
| `android/app/src/org/llmasr/minimal/ResultFiles.java` | `7ce787785e4dc20e6e320824119c758d6792358b28151290bb8f72f47fd67e01` |
| `tests/ResultFilesTest.java` | `1549175761ab2758ed51b9709049366191c03046a98fb4ada8289b3c7a63ec6a` |

Only this report was written by the reviewer, apart from normal authorized host-test outputs. Existing workspace modifications were left untouched. The staged diff was empty before and after testing.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Reviewed actual MainActivity export/clear/write callers and ExportSession owner handling; located resolution of prior medium N1 and bounded the tests/ResultFilesTest.java:59-89 latch evidence. Independently observed 32+20+41 host checks with unchanged SHA-256 fingerprints for all four requested files."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/2662ccce/reports/apk/export-owner-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "PATH=/nix/store/4dqvcqjnhj6n29r6sh5wwrz58540gbfn-openjdk-17.0.20.1+1/bin:$PATH bash scripts/test-minimal-apk.sh",
      "result": "passed",
      "summary": "Independent run: 32 minimal Java, 20 fake-backend recording lifecycle, 41 host result cleanup/export checks."
    },
    {
      "command": "sha256sum android/app/src/org/llmasr/minimal/{MainActivity,ExportSession,ResultFiles}.java tests/ResultFilesTest.java",
      "result": "passed",
      "summary": "Identical fingerprints before and after tests; full hashes recorded above."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "Empty at both inspections."
    },
    {
      "command": "Read reports/apk/export-fix-review.md, four requested Java files and scripts/test-minimal-apk.sh; search Java ownership/session/cleanup call sites with rg",
      "result": "passed",
      "summary": "Confirmed production snapshot creation uses beginOwned with the same RUNNING as clear and provider write; no prior export-handler text read."
    },
    {
      "command": "Device/provider/network/model operations and heavy/full builds",
      "result": "not-run",
      "summary": "Excluded by scope; generated build reports were not evidence."
    }
  ],
  "validationOutput": [
    "PASS 32 minimal APK Java checks",
    "PASS 20 recording lifecycle gate checks (fake backend; not device release latency)",
    "PASS 41 result cleanup/export checks (host only)"
  ],
  "residualRisks": [
    "Activity/SAF integration and real provider/lifecycle behavior remain untested; MainActivity is excluded from the host suite.",
    "ExportSession ownership remains a current-caller invariant, not enforced against future wrong-owner or direct primitive use.",
    "Failed clear may precede invalidation or leave partial deletion and unerased text; deletion is not secure erasure and does not retract external copies.",
    "Provider partial/empty files and external sync durability are outside rollback; no APK packaging or release/user acceptance is established."
  ],
  "noStagedFiles": true,
  "diffSummary": "Reviewer added only the requested report; no source/test/docs/index edits. Authorized host tests produced normal generated outputs.",
  "reviewFindings": [
    "Resolved prior medium N1: android/app/src/org/llmasr/minimal/ExportSession.java:17-29 and MainActivity.java:95-110 now serialize snapshot reading/ticket creation against clear with the same RUNNING owner.",
    "No new actionable correctness defect or blocker found within N1 scope; MainActivity.java:312-318 retains owned validity check and provider write/close.",
    "tests/ResultFilesTest.java:59-89 provides deterministic helper-level parked-clear regression coverage, not Android/provider integration evidence."
  ],
  "manualNotes": "Read-only narrow review completed on stable source fingerprints. Existing workspace changes were not reviewer changes. N1 source-level resolution does not close the previously documented integration evidence boundary."
}
```
