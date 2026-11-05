# Fixed-source MNN comparison (host only)

This is a reproducible **engineering agreement and host performance** experiment,
not Android/device acceptance, not a new APK/import catalog, and not an accuracy
winner selection. Historical strict-mel numerical FAIL remains separate.

## Predeclared hz4 protocol — before seeing any A/B outputs

- Source: fresh unauthenticated official ModelScope
  `Qwen/Qwen3-ASR-0.6B@4ce9cc728b473a5aedbe7b6e1ea45646316824dc`, ten exact
  `model-tools/model-lock.json` files, new ignored directory. Stream size/SHA;
  compare nine actual HF common files at `5eb144179a02acc5e5ba31e748d22b0cf3e303b0`.
  ModelScope-only `configuration.json` is not fabricated on HF. 2.1 GB body budget,
  no SDK/remote code/auth, initially zero retries. Equal complete raw SHA excludes
  raw-byte differences and does not warrant another identical-weight conversion.
- A: readonly `QwenASR/` seven assets. B: readonly
  `models/mnn-v1v-entry-fp16/` seven assets from repaired maintained reproduction
  entry. No redundant third new graph; bt4/v1v UUID-only relationship already known.
- Same audited `.work/bt4-prepare-convert/bin/asr_main` and linked MNN host DSO,
  pinned source/header/build/runtime identities; CPU, two threads, high precision,
  low memory, greedy, 128 generated-token ceiling. Same exact effective config,
  prompt/EOS/language and native WAV preprocessing for each paired input.
- Corpus: existing `bench/datasets/p0-smoke.json`, 20 cases, **two independent public
  official example recordings, zero independent human labels**. 143.9400625 seconds
  of unique test-case audio, including derivatives, seeded noise, crops/tiles,
  repeated phrase, tail silence and synthetic silence. Not 20 independent utterances;
  no noisy/long/dialect generalization. Restore missing case bytes only in ignored
  task-local corpus, validate against existing SHA. No additional public dataset:
  no audited fixed-version independently human-labeled EN+ZH subset has been
  established for this bounded run; labels must not be inferred from model outputs.
  Official model/example source is Apache-2.0 model card, examples only for local
  engineering testing; no audio or transcript redistribution in maintained reports.
- Exactly one correctness pass per model per case, alternating case pair order
  AB/BA; no model remains resident across requests. Then three fresh-process paired
  timing repetitions of the prespecified `zh-original` (4.2039375 s), AB, BA, AB.
  Correctness covers the longer English clip; repeated performance is intentionally
  limited to the Chinese representative. Correctness runs precede timing and warm
  both asset sets equally by count. Initial observed load is reported separately.
  Fresh process **does not mean OS-page-cold**; no cache dropping, affinity,
  governor/frequency change, profiler installation or background model processes.
- Each launch has exact argv/exit/start/PID-start/log SHA/cleanup evidence using
  committed `fp16_process.py`. Single resident child; 9 GiB sampled owned-tree RSS
  sum (not PSS, not guaranteed instantaneous peak), sampling at about 250 ms.
  Per-request timeout 120 s, total model-process elapsed budget 1800 s. No parallel
  model/build; compilation if needed at most two jobs. Unique corpus <5 min.
- Strict native protocol: unique effective-config, text markers and metrics;
  native status 1 + `truncated=false` + child exit0 required for complete EOS.
  Native status2/token-limit/exit6 is retained separately, never promoted because
  output looks plausible. Full effective configs must match expected values.
- Statistics: per model repeated load/inference/process wall/RTF/RSS median,
  min/max and unscaled median absolute deviation; paired B-minus-A and B/A values.
  With n=3 and one repeated clip, no significance claim or universal winner.
  Report all failures, EOS/truncation, normalized/raw agreement, silence nonempty
  counts and repeated-word/character heuristics separately from edit distances.
- Normalization for hypothetical independently labeled scoring: Unicode NFKC,
  casefold, Unicode punctuation/symbols removed, whitespace collapsed; English
  whitespace words (WER), Chinese nonspace characters (CER). No number verbalization,
  script conversion or model-reference truth. Micro edit sums/reference units,
  empty truth handled explicitly. Current human WER/CER is **not available**.

## Maintained entry

`model-tools/compare_mnn_models.py --spec <frozen-local-spec.json> --output <new-dir>`
uses only Python standard library and the committed owned-process helper. The
spec binds all seven assets per model, driver, runtime libraries/source evidence,
corpus WAV and optional independently verified gold hashes. It refuses identity
mismatch, reused output directories, invalid WAV/language/config or unsupported
protocol. The supported INPUT Interface is deliberately exact-byte fixed:
`config.json` SHA `fd4127baf9ff5c45aaa2224f65db36e9110088d2aa7223e4853238d31be84e9d`
and `llm_config.json` SHA `8e548c5393334616cffb6e8a751603ad8314d01293aa29765f1f507f0b412b85`,
anchored independently in the reviewed repository fixed tuple, not caller spec
hashes or any `QwenASR/` path authority. Alternative formatting, benign metadata,
templates or values are unsupported even if semantically equivalent and correctly
rehashed by the caller. No allow-any escape exists. Graph/weight A/B identities
may differ; this benchmark input pin does not change APK graph trust. Raw
output/transcripts remain only in ignored local logs. The result
JSON contains aggregate counts, input hashes, output hashes and measurements,
not full transcripts. Run the focused tests with
`python3 -B -m unittest discover -s model-tools -p test_compare_mnn_models.py`.

The exact local spec, commands, evidence hashes and finite results are bound by
`reports/model-ab-hz4/`; source/interface tests alone never constitute real A/B.
Parent fresh-context review and a separate safe local commit remain required.

## hz4 actual outcome (protocol above was frozen before outputs)

The real maintained entry completed46/46 native processes with normal EOS/no
truncation. Both models agree on all20derived engineering cases; both also emit
nonempty output on all3synthetic silence fixtures, which is an observed failure
mode rather than evidence of accuracy. With no independent human labels, quality
WER/CER and a quality winner remain unavailable. Three alternating timing pairs
on the predefined Chinese clip have inference medians1.90999s(A)/1.89979s(B),
paired B−A median−0.01177s with range−0.05730..+0.00117s. This tiny finite sample
supports no robust universal performance winner. Exact per-run measurement,
input/hash/exit/EOS/cleanup and finite-corpus boundaries are in
[status](../reports/model-ab-hz4/status.md) and
[aggregate results](../reports/model-ab-hz4/results.json). Raw model/audio/text
remain ignored local artifacts. Source-prebench protocol/source identity is
preserved separately; only this outcome appendix and report status changed after
execution. No APK/import/device acceptance or historical strict-mel closure.

## R1/R2 admission and scoring supplement (after the actual measurement)

Fresh review found that metadata overrides could bypass base-only resource/path
checks, and gold decoding errors could leave an EOS-complete run marked PASS.
The repaired admission checks the **native recursive merged** configuration:
CPU/two threads/high precision/low memory/greedy128/synchronous, the same nested
`mllm` policy, and fixed `llm.mnn`/weight/tokenizer paths. External asset/cache
path overrides are rejected before output creation or child launch. Same-value
metadata is allowed only when the native effective policy remains exact.

Hash-bound requested gold must decode as UTF-8 before admission. A later gold
read/hash/decode/scoring failure explicitly fails the run/batch and exits
nonzero; native EOS may remain true as a separate fact. Gold aggregates expose
eligible/scored/failures/completeness, never treating missing scores as success.
The current actual corpus still has no gold and no CER/WER claim.

**Temporal binding:** the46 actual model runs used source
`dd2ff2daac41ef92300a48c4a74b8682b69fc15aa56fb91d44fc02f8c4bff654`.
The repaired validation/parser source is
`8b6a6e19398467abd51174a0e243504c24c9d41c43359091cdd86d64616a0263`.
The repair is tested using tiny production-CLI fixtures, not a rerun of those
models. The frozen original spec and46logs are additionally admitted/reparsed
as **data** using the repaired code, verifying unchanged effective config,
metrics/output hashes and exact aggregate summary. No new model timing is
measured, and old actual numbers/source identities in `results.json` remain
unchanged. Original `.work/hz4-ab/` evidence remains immutable; supplemental
repair evidence is `.work/hz4-r1r2-repair/`. Subsequent focused review closed R2,
but found a residual PLE input-selector hole; the supplement below supersedes
that source approval checkpoint without rewriting its sealed evidence.

## Residual PLE / finite Qwen input closure supplement

Current guard source
`a8e3e7d26a49666e84e269835a2d6af7bd67d8796d925c3af0516f92f5fb1957`
rejects `ple_embed_file` **presence** in base or metadata, including empty/null
values, zero dimension and later overrides, before output or native launch.
One bounded audit of the pinned Qwen3-ASR load accessors/read sites also identified
optional default `context.json` and fallback separate embeddings. Admission now
refuses that sibling context file, requires positive structured tied-weight
offset into bound `llm.mnn.weight`, and excludes alternate vision/talker/TTS/
embedding/speculative/mmap input routes from this fixed Qwen-ASR protocol.
This is not a general schema for other upstream models or a native-code sandbox.
The finite table/source hashes are in `.work/hz4-ple-repair/loadpath-audit.md` and
`loadpath-source-bindings.json`; no native math/runtime or weights changed.

Prior15 tests/R2 remain, with new PLE base/metadata/empty/overridden and audited
selector/context CLI regressions. Original source `8b6a6e19…a0263` is separately
reproduced as a PLE target red in an isolated copy. Actual model measurements
still belong only to `dd2ff2da…ff654`; original46 inputs contain no PLE, optional
context, fallback embedding or gold. New guards/parser/stats only reparse those
old logs as DATA, not a new model run. `results.json` bytes/identities stay
unchanged; parent fresh follow-up closure/safe commit remain required.

## Final fixed-config Interface supplement (supersedes arbitrary-config admission)

A subsequent finite review retained PLE/R2 CLOSED but demonstrated that a rebound
constant `jinja.chat_template` could select an unbound WAV. Rather than expanding
field-by-field template checks, the supported Interface now requires the **exact
independently trusted two config SHA values above** before output mkdir/spawn.
The old metadata-partial positive test is intentionally changed to a named
fixed-policy refusal. Tiny fixtures use those exact public config bytes with
synthetic graph/weight payloads; known fixed config succeeds, caller-rehashed
constant templates, formatting-only edits, benign value edits and `talker_type`
changes fail specifically for fixed trusted policy, not self-hash mismatch.

Current guard source
`2e9f856cd2bc21f8747864adbfc5929bbbfedca314831e3a9cd20ddddc203c5e`
is not the old measured source. No native runtime/math/trust manifest changes,
Jinja sandbox or general upstream config support are added; optional context
closure and R2 scoring-failure truth remain. Original results JSON and46actual
measurements retain old `dd2ff2da…ff654` identity; current guards only reparse old
logs as DATA. All prior seals remain immutable in their original directories.

Audit correction: the earlier table categorically excluded the QwenTTS alternate
branch based on model_type/has_talker alone; native actually checks
`talker_type == qwen3_tts OR model_type == qwen3_tts`. No extra file read was
established for that branch with has_talker false. Exact trusted metadata now
excludes unsupported talker_type as well, without claiming a general branch or
all-upstream audit. Current supplemental evidence is `.work/hz4-template-repair/`;
parent fresh bounded closure and separate safe commit remain required.
