# hz4 actual host A/B — complete, parent review/commit pending

Task `llm-asr-hz4`; base `7e794773dd91cba05ab7b55715a60558dc4713bc`.
Implementation writer `model-hz4-ab`; parent `model-ab-coordinator` owns Beads and
subsequent fresh-context review/safe commit. This leaf did **not** stage/commit.

## Source result

Fresh official ModelScope `Qwen/Qwen3-ASR-0.6B` fixed history
`4ce9cc728b473a5aedbe7b6e1ea45646316824dc`: **10/10 downloaded files match exact
size/SHA**, all **9 actual HF common files byte-identical** to
`5eb144179a02acc5e5ba31e748d22b0cf3e303b0`. `configuration.json` is ModelScope-only.
Actual model body 1,880,618,215 B + metadata 4,697 B; total 1,880,622,912 B below
2.1 GB cap, zero retries, 110.22 s, real exit 0. Weight SHA
`79d6cbd4c98c7bbffe9db2edac07f56cd6637d0d5944b27f6c2b8353840323ea`,
1,876,091,704 B; read-only header 612 BF16 tensors. Fixed README declares
Apache-2.0. No SDK/auth/remote code/pickle executed. Complete raw equality
excludes raw-byte source difference; no redundant third conversion performed.

## Actual controlled comparison

[Protocol](../../docs/mnn-model-comparison.md) was declared before outputs.
SOURCEPREBENCH freeze `788eb90bd5a6dcd2d635b056110680960b2071fa7ca934fbc8ab7c8becc74621`;
actual maintained comparison source
`dd2ff2daac41ef92300a48c4a74b8682b69fc15aa56fb91d44fc02f8c4bff654`.
A=`QwenASR/`, B=`models/mnn-v1v-entry-fp16/`; exact seven assets each, same existing
native driver/runtime, CPU two inference threads, high precision/low memory,
greedy128, same language/prompt/EOS/preprocessing/effective config and exact WAV.
Host Intel i5-10400F x86_64/Linux6.18.57; no phone or device.

- **46/46 actual native processes started and exited 0**, strict status1 EOS,
  no truncation, all direct children reaped/observed PID-start identities terminal.
  Full maintained CLI wall400.36 s; timed loop385.24 s; input SHA verified before
  and after. No child failure erased. RSS cap9 GiB never reached.
- Correctness20cases/model: **20/20 raw output and normalized output agreement**;
  every pair has equal full effective-config hash. Corpus143.9400625 s is from
  **two independent public example recordings**, derivatives and synthetics,
  **zero independent human labels**, not20 independent golden recordings.
- **Both models emitted nonempty output on all3 synthetic silence cases.**
  Retain this silence-hallucination engineering observation; do not call agreement
  accuracy or silence success. Repetition heuristic flagged0cases, not a proof
  that no linguistic repetition/error exists. EOS completion is not accuracy.
- No independently verified human truth => **WER/CER unavailable**, quality winner
  inconclusive. Model-reference output is not treated as truth.

### Prespecified `zh-original` performance, three fresh-process AB/BA pairs

| Metric | A old median [min,max], MAD | B new median [min,max], MAD |
|---|---:|---:|
| load seconds |5.3264 [4.60682,6.30339],0.71958|5.2096 [4.37404,5.78933],0.57973|
| inference seconds |1.90999 [1.90919,1.95709],0.00080|1.89979 [1.89822,1.91036],0.00157|
| inference RTF |0.45433 [0.45414,0.46554],0.00019|0.45191 [0.45153,0.45442],0.00037|
| whole process seconds |7.53970 [6.87006,8.53517],0.66963|7.42946 [6.59982,8.00887],0.57941|
| whole process RTF |1.79348 [1.63420,2.03028],0.15929|1.76726 [1.56991,1.90509],0.13783|
| sampled owned RSS sum bytes |3406176256 [3406176256,3406245888],0|3406114816 [3406094336,3406180352],20480|

Paired B−A inference median −0.01177 s, min −0.05730/max +0.00117/MAD0.01294;
B/A median0.99384, min0.97072/max1.00061/MAD0.00678. Tiny n=3, one representative,
order effects and variable load/I/O mean **no defensible overall performance
winner**. Observed timings are close on this bounded sample, not proof all models
are universally equivalent. First observed loads A2.63585/B6.14646 s are separate,
not cold-cache benchmarks. Repeated correctness prewarmed both asset sets by count;
fresh process does **not** mean OS-page-cold; no cache/governor/frequency changes.

Max native sampled RSS sum3,732,987,904 B; outer CLI+owned native sample
3,760,824,320 B. 250ms sampling can miss instantaneous peaks and sums shared pages;
not PSS, not phone memory, not guaranteed true peak. Hardware housekeeping/native
initialization may use threads besides the requested two inference threads.

## Validation and evidence

- Current tool/interface tiny tests11/11 on Python3.14 and3.12; committed unchanged
  process helper regression19/19, including TERM/INT/firstsample/repeats/finalwrite.
  Actual CLI cancellation test exits143 and leaves child terminal; nonzero,
  truncation, identity drift, duplicate protocol, reused output and stats tested.
- Native pinned offline sources/patch tree checkPASS; driver/runtime/source/21input
  and all assets/audio hashes in [results.json](results.json). All46 actual log
  SHA/terminal child identities and all20 full effective-config pairs verified.
- cccc1.8.3/knots1.18.0 analyzed explicit new two files, exit0/empty stderr;
  new validation/parser/statistics hotspots manually reviewed, no old baseline
  rewrite. Parent fresh-context review remains required, not replaced by local audit.
- Local `.work/hz4-ab/` holds exact commands/exits/started/PID-start/log SHAs,
  source-prebench freeze, raw local logs and ignored corpus; maintained report
  contains aggregate counts/measurements/source hashes, no audio/full transcripts.

No Android/runtime/modelmath/trust/threshold/versionlock changes, no APK rebuild,
no current APK new-model import claim, no device/mic/private data/push. Historical
strict-mel FAIL remains separate. Protection-after/final candidate receipt and
fresh parent review/commit are delivery gates, not implied by model exit0.

## R1/R2 repair supplement — not a new real-model run

Parent accepted fresh source-review blockers R1 (base-only checks allowed
metadata resource/unbound model-path overrides) and R2 (gold scoring exception
could retain run/batch PASS). Original actual metadata had no such overrides and
all cases had no gold, so original46 measurements/source identities remain valid
and unchanged in `results.json`; old `.work/hz4-ab/` and review seals are immutable.

Repaired source
`8b6a6e19398467abd51174a0e243504c24c9d41c43359091cdd86d64616a0263`
checks native recursive merged resource/nested-mllm/fixed asset path policy
before output mkdir/spawn; hash-bound gold UTF-8 before models; later requested
score errors explicitly fail run/batch/nonzero while retaining true EOS facts.
Eligible/scored/score-failure/completeness are explicit when gold is requested.
No new gold or real ASR runs/downloads/builds occur in this repair.

Three isolated OLD-source production CLI regression paths reproduce the target
failures: properly rehashed metadata thread4/unbound path accepted, bound invalid
UTF-8 gold accepted, post-admission gold deletion leaves run PASS. Current
15-test suite retains all11 earlier tests and adds finite protected-key variants,
same-value recursive metadata acceptance, bound non-UTF8 refusal before output,
and actual tiny-driver post-admission read failure with external EOS/run/batch
assertions. Current tests PASS15/15 on Python3.14 (6.197s) and3.12 (9.704s);
unchanged process helper regression PASS19/19 (11.894s). This is source/interface
validation, not model quality evidence.

Current code also validates the original actual spec and reparses all46 frozen
logs as DATA: unchanged full effective configurations, native metrics/output
hashes and exact summary verified PASS, zero native launches/downloads. Replay
receipt SHA `fa9f699b654f5bcee04b994f76286219eeef7a9d263b8f2b7419f146d2641dcf`.
Supplemental evidence is in
`.work/hz4-r1r2-repair/`; old actual source remains
`dd2ff2daac41ef92300a48c4a74b8682b69fc15aa56fb91d44fc02f8c4bff654`,
not relabeled as measured with repaired code. New focused fresh review and
subsequent safe local commit remain parent gates.

## Residual PLE closure supplement (supersedes prior source checkpoint)

Fresh focused review accepted R2 CLOSED but found residual R1: native key-presence
`has_ple()` could activate unbound `ple_embed_file`. Current source
`a8e3e7d26a49666e84e269835a2d6af7bd67d8796d925c3af0516f92f5fb1957`
rejects PLE key presence in base/metadata even empty/null/dim0/overridden.
A single finite pinned Qwen3-ASR actual load/read-site audit also closes optional
default `context.json` and separate embedding fallback: sibling context presence
is refused, positive structured tied embedding offset selects bound weight,
and alternate vision/talker/TTS/embedding/speculative/mmap input routes are
excluded from this fixed-seven protocol. No generic future-model schema or
runtime/math/weights change is claimed. Detailed finite accessor/read-site table
and source bindings are `.work/hz4-ple-repair/loadpath-audit.md` and
`loadpath-source-bindings.json`.

Prior15 tests and R2 remain, plus two new test functions covering9rehashedPLE
base/metadata/empty/null/override combinations,11auditedroute/fallback variants
and an extra default context file. Isolated prior-source PLE CLI target red
reproduced (testexit1 FAIL, checkerexit0). Current17/17PASS on Python3.14
(7.782s) and3.12 (11.024s); unchanged helper19/19PASS (11.878s). Prior15 test
function ASTs remain unchanged. Current validation/parser/stats replayed all46
old logs as DATA: exact config/metrics/output hashes/summary unchanged, zero
native launches. Replay SHA
`ecb944ab037a689619b3b0c6104a2c8960f2b94c3a2b5f075bf83c45e050733b`.
These interface/data-replay tests are separate from model measurements.
No model benchmark/download/build/device run is repeated. The original46logs
remain OLD-source measurements (`dd2ff2da…ff654`) with no PLE/context/fallback/gold;
new code validation/parser/statistics over them is only supplemental DATA replay.
Original `results.json` SHA/numbers/source identities are unchanged. Parent new
focused closure review and separately authorized safe commit remain pending.

## Final trusted-config restriction supplement

Parent accepted the later constant-Jinja audio-selector R1 finding; original PLE
and R2 remain CLOSED. The Interface is now intentionally narrowed instead of
adding arbitrary metadata/template sandboxing: actual `config.json` must have
SHA `fd4127baf9ff5c45aaa2224f65db36e9110088d2aa7223e4853238d31be84e9d`,
metadata `llm_config.json` SHA
`8e548c5393334616cffb6e8a751603ad8314d01293aa29765f1f507f0b412b85`.
Production constants derive from the reviewed repository fixed tuple, not caller
spec self-hashes or a QwenASR filesystem path. These two actual byte identities
are checked before output mkdir/spawn; even formatting-only or benign edits are
unsupported. No allow-any switch. Graphs/weights still may differ A/B, and no APK
trust/config manifest was altered. Optional context closure and prior resource,
PLE and gold score-failure guards remain in place.

Current source
`2e9f856cd2bc21f8747864adbfc5929bbbfedca314831e3a9cd20ddddc203c5e`
has tiny production-CLI tests/data replay only, not new actual ASR measurements.
Fixtures embed exact nonsensitive fixed config bytes with tiny synthetic model
payloads. The earlier benign-partial-metadata positive test now deliberately
asserts fixed-policy rejection; original R2/cancellation/negative assertions are
retained. New constant-template/base-format/value/talker-type cases rebind all
spec/file SHA and fail for independent trusted policy, before output.
Current suites PASS18/18 on Python3.14 (7.421s) and3.12 (10.026s), helper19/19
PASS (11.880s). Isolated a8e old-source constant-template target red is actual
CLI testFAIL/nonERROR with outer checkerexit0. Current guard/parser/stats replay
of46oldlogs as DATA PASS: exact configs/metrics/output hashes/summary unchanged,
zero native/model launches. Supplemental replay SHA
`e81ed9d99decf044f6f9351e382773fbe55e76163c9ed08d5b033f860e89c9b8`.

Earlier finite table correction: native QwenTTS shortcut tests talker_type OR
model_type, so old categorical exclusion based only on model_type/has_talker was
too broad. No additional file read for has_talker=false was demonstrated; exact
config pin now excludes changed talker_type without a generic runtime audit.
Original `results.json` bytes/numbers/old actual source dd2ff2da remain unchanged;
old46 fixed-template observations remain valid. New evidence lives solely in
`.work/hz4-template-repair/`; no model rerun/download/build/device access, no
fresh closure/commit claim until parent follow-up acceptance.
