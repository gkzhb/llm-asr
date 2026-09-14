# Independent P0 implementation review

## Verdict and scope

**The narrow native-CPU feasibility result is credible; full P0 correctness closure must remain separate and pending.** No blocker was found in the ASR boundary-position arithmetic itself. This was a read-only source/evidence review of `scripts/`, `native/p0/`, `model-tools/`, `patches/`, the four specified JSON reports, and `docs/implementation-plan.md`; local upstream implementation context was inspected to verify patch semantics. No model inference, builds, downloads, network or phone commands were run. Only this review artifact was written.

The parent is running the final 20-case regression. **This review does not attest its completion or outcome.** Historical original-run 16/20 agreement and the parent's parser-corrected subset observation must not be presented as a fresh full patched run.

## Patch correctness

- **ASR positions — sound for the tested single-request path.** `patches/mnn-asr-boundary-positions.patch:6–17` counts precisely the same optional start/end tokens subsequently inserted into `audio_ids`, and limits the change to `qwen3_asr`. Upstream `Omni::addPositionIds` appends ordinary sequential positions on all axes; decode uses `gen_seq_len + mPositionIds.back()`. Therefore including the boundaries repairs the state length rather than adding an arbitrary decode offset. The old prefill fallback explains why prefill could appear correct while decode was two behind. The supplied before report has one matching prefill and eight failing decode rows (first 42 versus 44); the after report has nine matching rows (decode 44–51 on all three axes). These are inspected historical observations, not rerun tests.
- **Periodic Hann — mathematically appropriate, with a wider-than-ASR scope.** `patches/mnn-whisper-periodic-hann.patch:35–54` opts Whisper into the existing periodic implementation, whose cosine denominator is N instead of N−1, matching the official feature extractor. Generic spectrogram defaults remain unchanged. However, **every `whisper_fbank` caller changes**, not just Qwen3-ASR. Adding `SpectrogramParams::periodic_hann` also changes the public struct ABI: rebuild all consumers with matching headers/library, as `model-tools/patch_whisper_hann.py:4–5` correctly warns. No independent numerical Hann rerun was performed here.
- `patches/mnn-p0.patch:39–44` deliberately repairs window metadata only. `model-tools/reference.py:36–41` explicitly selects eager with no window mask. Agreement with that oracle is **not proof of FA2/windowed-attention equivalence**, especially beyond eight seconds.

## Actionable findings

1. **Medium — position regression gate can falsely pass without decode.** `model-tools/check_positions.py:9–18` requires only one parsed row and equality of all rows. The original failing run's prefill-only prefix satisfies both conditions. Require a prefill plus the expected decode coverage, sequence/axis lengths, monotonic counters and a successful terminal run marker; reject malformed/dropped trace lines. The inspected after report does contain eight decode rows, so this gate defect does not invalidate its specific evidence.

2. **Medium — advertised remote path confinement is only lexical.** `scripts/run-device-p0.sh:6–8` accepts `/data/local/tmp/qwen-asr-p0/../other` and symlinks resolving outside the allowed tree. Shell quoting prevents metacharacter injection, but not traversal. Reject dot components and validate the remote canonical path under the dedicated directory, including referenced assets if arbitrary configurations are allowed. This is an operator-facing safety boundary, not evidence of an external exploit.

3. **Medium — interruption/timeout does not guarantee remote inference cleanup.** `scripts/run-device-p0.sh:13–46` has no remote EXIT/HUP/INT/TERM cleanup trap. Its monitor sends TERM once after 120 samples, then exits; the foreground wait can remain blocked, and killing the local adb client at 600 seconds is not proof the phone process stopped. Add bounded TERM→KILL cleanup for this run's child PID and monitor, with an interruption path. Concurrent invocations also share `inference-output.txt`/`inference-memory.txt`; use per-run files or a lock to avoid mixed evidence. The existing successful sample is unaffected by this failure-path finding.

4. **Medium — reference identity is not checked when scoring.** `model-tools/compare_smoke.py:39–41,53–55,73–80` verifies the current audio against the current manifest, but associates saved references only by case ID. Replacing an audio fixture while retaining its ID can compare a new waveform against an old transcript. Verify reference `audio_sha256` against each case, reference language/configuration, and attach model/runtime/patch hashes to the run summary. `export_mnn.py:22–35` similarly consumes prior gate results without checking that their recorded source hashes match the source being exported. This is a stale-evidence risk, not a finding that the current artifacts were actually mismatched.

5. **Low — status/evidence documentation needs reconciliation.** `docs/implementation-plan.md` still opens with “planning only/no weights/build/performance” and §14 says no flake or performance results. Preserve that as dated historical planning, but link a current P0 status rather than leaving contradictory present-tense claims. The specifically requested `reports/p0/mnn-smoke-summary-positions-fixed-subset.json` snapshot says **4 complete, 3 exact, `zh-en` different**, not four exact. Any parser-corrected claim needs its separately identified evidence; do not silently reinterpret this file or call it final regression evidence.

## Safety positives and residual risks

- Deployment verifies local sizes/hashes, per-chunk hashes, assembled hashes, and runtime/audio hashes; explicit chunk lists exclude stale tails (`scripts/deploy-p0-model.py:29–97`). Commands avoid system tuning/root/microphone access. Small files and binaries are pushed directly rather than atomically, and there is no deploy/run lock: keep deployment and inference serialized; a failed update must not be treated as a runnable verified bundle. The device endpoint is hard-coded at line 9 rather than selected explicitly.
- `native/p0/asr_main.cpp:17–25,35–51` forces greedy/synchronous decoding, validates requested language, reports truncation and returns nonzero at the token limit. It remains a trusted-file single-shot harness, not a bounded untrusted-audio API: duration/resource checks, cancellation, native ASR text parsing, repeated requests/KV reset and Android lifecycle behavior remain unvalidated.
- `verified-device-result.json` supports one Chinese shell-process transcription: load **14.9002 s**, inference **3.78953 s**, audio **4.2039375 s**, RTF **0.9014**, sampled PSS **3.0645 GiB**. Its own limitations are appropriate: second process launch, not persistent warm inference; sampled PSS may miss peaks; stage counters overlap and must not be summed. Do not attribute this earlier measurement to a final patched binary without provenance/redeployment evidence. These measurements miss the planning cold-load ≤5 s and PSS ≤2 GiB targets and exceed the 2.5 GiB protection-review threshold; they do not defeat narrow feasibility but preclude product-readiness claims.
- The 20 fixtures derive from only two speech sources, lack human labels and music/dialect/independent number coverage, and the main reference report lacks EOS/token-limit termination evidence. Backend text agreement is not CER/WER or guaranteed complete long-audio transcription. Full P0 acceptance needs an explicit disposition of window semantics and all final golden differences; APK/IME, quantization quality, GPU, thermal endurance, persistent warm P95 and production memory protection belong to subsequent milestones, not fabricated P0 passes.

### Evidence snapshot identifiers

SHA-256 at review time (reports may subsequently update):

- `verified-device-result.json`: `484a43fd075a9e4bde8080791a174194d4f66351c6676ca57a4d24ad97063eaa`
- `position-parity-before-fix.json`: `450e6954a5db800e1d11aba9073e8d9fe7b0b8a5549bf7898fb0a332cb3279a4`
- `position-parity.json`: `bab15391f536d3dd9a5df68cebc51721d98d0e4ea289d5e4875c4bd0155d1e3c`
- `mnn-smoke-summary-positions-fixed-subset.json`: `3c6864d7e384833ee3cfec3fc15ab441332026d9de5e5b33b1bd47bd3fcce26e`

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Five severity-ranked findings above identify concrete paths/lines, failure conditions and remedies; patch reasoning and residual-risks sections distinguish narrow feasibility from full P0 closure."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/5cc7b897/reports/p0/implementation-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "Read-only file listing, nl/sed-equivalent reads, rg, wc and stat of reviewed source/evidence",
      "result": "passed",
      "summary": "Inspected scoped implementation and upstream position/Hann context; no project runtime executed."
    },
    {
      "command": "for f in scripts/*.sh; do bash -n \"$f\" || exit; done; Python ast.parse of scripts/*.py and model-tools/*.py",
      "result": "passed",
      "summary": "Static syntax checks only; no model imports, source writes or builds."
    },
    {
      "command": "Python standard-library JSON/hash inspection of the four specified reports",
      "result": "passed",
      "summary": "Recorded immutable snapshot hashes; before positions 1/9 equal, after 9/9 equal; subset 3/4 exact. Confirmed prefill-only gate counterexample."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "No staged paths at inspection time; reviewer staged nothing."
    },
    {
      "command": "Final 20-case regression, model/build/network/phone commands",
      "result": "not-run",
      "summary": "Outside read-only authorization; parent regression outcome not attested."
    }
  ],
  "validationOutput": [
    "Review findings and residual risks recorded; source files unchanged.",
    "Static syntax success is not a native/model test pass."
  ],
  "residualRisks": [
    "Final full patched regression pending parent evidence; parser-corrected subset not represented by the scoped 3/4 snapshot.",
    "Eager-oracle agreement does not establish windowed/FA2 equivalence or human-label accuracy.",
    "Prior phone result exceeds load/memory targets and is not final-binary or sustained warm evidence.",
    "Hann ABI/all-Whisper impact requires matching rebuilds; run/deploy failure isolation and untrusted-input limits remain incomplete."
  ],
  "noStagedFiles": true,
  "diffSummary": "Only the requested independent review artifact was created; no source edits or tests added.",
  "reviewFindings": [
    "medium: model-tools/check_positions.py:9-18 - prefill-only false-positive gate.",
    "medium: scripts/run-device-p0.sh:6-8 - lexical path guard permits traversal/symlink escape.",
    "medium: scripts/run-device-p0.sh:13-46 - incomplete interruption cleanup and shared run files.",
    "medium: model-tools/compare_smoke.py:39-80 and export_mnn.py:22-35 - stale evidence identity not enforced.",
    "low: docs/implementation-plan.md and scoped subset summary - historical/current status and parser evidence require reconciliation.",
    "No blocker found in the reviewed single-request ASR boundary-position arithmetic; full P0 closure not attested."
  ],
  "manualNotes": "Independent read-only assessment. Existing report observations are not rerun passes. Reports may change during the parent's ongoing final regression; snapshot hashes above define this review's evidence."
}
```
