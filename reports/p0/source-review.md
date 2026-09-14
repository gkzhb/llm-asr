# Qwen3-ASR P0 read-only source review

## Scope and evidence

Reviewed `/home/zhb/gitrep/llm-asr/docs/research/{mnn-audio.txt,qwen-modeling.txt,mnn-export.txt,mnn-omni.txt,mnn-config.txt,qwen-asr-config.txt,qwen-asr-preprocessor.txt}` plus relevant engine/header/manifest excerpts. Locations below are **local snapshot line numbers**, not claims about current upstream HEAD. No source, configuration, test, or index edits; only this requested report artifact was written. No network, model imports, conversion, inference, or device workloads were run.

The manifest maps the MNN snapshots to commit `a03b005cf6f888ebf092e4753840f935827f9c36`:

- `mnn-audio.txt` → `transformers/llm/export/utils/audio.py`
- `mnn-export.txt` → `transformers/llm/export/llmexport.py`
- `mnn-omni.txt` → `transformers/llm/engine/src/omni.cpp`
- `mnn-config.txt` → `transformers/llm/engine/src/llmconfig.hpp`
- `qwen-modeling.txt` → Qwen `qwen_asr/core/transformers_backend/modeling_qwen3_asr.py`, downloaded from mutable `main`, **not commit-pinned**.

A read-only `python3 -B` stdlib check verified all seven primary files against manifest SHA-256, parsed the official model AST, and checked the integer boundary examples below. This is source/structural evidence, **not numerical model parity**. `git diff --cached --name-only` returned no entries at review time.

## Findings and minimal patches

### F1 — High / P0 gate: convolution chunks are not inference attention windows

**Proven mismatch:** `docs/research/mnn-audio.txt:183-192,215-224` constructs cumulative attention boundaries by summing each CNN chunk's valid output length. `docs/research/qwen-modeling.txt:681-699,719-726` instead groups encoded tokens into inference windows using `n_window_infer`. Config `docs/research/qwen-asr-config.txt:89-90` has `n_window=50`, `n_window_infer=800`.

For this configuration:

- CNN input chunk = `2*n_window = 100` mel frames.
- A full CNN chunk produces 13 tokens, from three stride-2, kernel-3, padding-1 convolutions (`qwen-modeling.txt:622-624`).
- Official length function (`qwen-modeling.txt:309-317`): `L(T) = 13*floor(T/100) + ceil((T mod 100)/8)` for nonnegative integer `T`.
- Normal inference window = `13 * (800 // 100) = 104` encoded tokens, **not 100, 13, or ceil(800/8)=100**.
- Exact official code uses `padded_mask_after_cnn.shape[-1] * 8`; when the only chunk is shorter than 100 frames, this width is smaller, but the whole utterance still forms one attention block.

| Valid mel frames T | Encoded length | Official boundaries | Current export-wrapper boundaries |
|---:|---:|---|---|
| 100 | 13 | `[0,13]` | `[0,13]` |
| 101 | 14 | `[0,14]` | `[0,13,14]` |
| 800 | 104 | `[0,104]` | `[0,13,26,39,52,65,78,91,104]` |
| 801 | 105 | `[0,104,105]` | `[0,13,...,104,105]` |
| 1600 | 208 | `[0,104,208]` | `[0,13,...,208]` |
| 3000 | 390 | `[0,104,208,312,390]` | `[0,13,...,390]` |

**Crucial qualification:** this does **not** establish that the current exported eager computation enforces 13-token/approximately 1-second attention. See F2: the boundaries are ignored by eager without an explicit mask. Changing only `cu_seqlens` can leave eager outputs unchanged.

**Minimal patch:** retain the 100-frame convolution split and per-chunk positional reset; change only attention-boundary construction to match the official inference grouping, including a final nonempty remainder and no duplicate terminal boundary. Do not set `n_window=400` or change convolution chunking to 800 frames: that changes convolution boundaries and positional embeddings. Assert the P0 supported configuration (`n_window=50`, positive supported inference ratio); the official length helper is hardcoded to 100/13, so arbitrary config generalization is not independently established.

### F2 — High / P0 oracle blocker: no explicit audio mask is passed; official eager is not a windowed oracle either

**Proven source behavior:**

- `docs/research/mnn-audio.txt:176-182` stores the original tower and mutates its config and each attention config to `eager`. It does not clone the tower. `Qwen3ASRAudio.load` creates this wrapper at `:255`, so the Python `forward` path at `:257-260` also uses the mutated tower.
- Export layer calls at `mnn-audio.txt:223-224` pass only hidden states and `cu_seqlens`.
- Official attention at `docs/research/qwen-modeling.txt:477-514` passes boundaries as extra attention-interface kwargs. The local eager implementation at `:90-113` computes dense QK, optionally adds `attention_mask`, and does not use those boundary kwargs.
- Official `_prepare_attention_mask` at `:649-666` builds block-diagonal, **bidirectional/noncausal** additive masks, but has **zero call sites in this snapshot**. Official encoder layer calls at `:728-732` also omit the mask. The layer defaults it to `None` (`:536-560`).

Consequently the shown eager path is unmasked/global over retained tokens in both snapshots. Boundary mismatch and missing-mask behavior are separate findings. The intended FA2 varlen contract is visible at `:509-513`, but the FA2 backend implementation is not included, and no backend output was measured. A mask fix can intentionally diverge from the unmodified official eager snapshot for `T>800`; do not report that divergence as failed conversion without identifying the oracle.

**Minimal patch/testing decision:** first label the reference backend and preserve its provenance. For windowed semantics, explicitly construct `[1,1,S,S]` additive mask with zero iff query and key are in the same official window, and `finfo(dtype).min` otherwise; pass `attention_mask=...` to every audio encoder layer. Use dynamic tensor operations (e.g. token index block IDs and equality) rather than trace-time Python loops over boundaries. A 104-token block-ID mask is equivalent for this single-utterance P0 config, including short single-block utterances. Keep this mask internal to `audio.onnx` to preserve the one-input runtime ABI. If an external mask is chosen, change the Qwen3-ASR C++ branch too; the generic Omni branch is not a fallback for this model (F5).

Do not silently patch the official oracle and call it upstream output. Compare (a) immutable official backend, (b) explicitly identified corrected eager oracle with official boundaries/mask, (c) export wrapper, (d) ONNX, and (e) MNN. Either instantiate a separate reference tower or avoid the export wrapper's shared-config mutation. Verify backend selection before each run.

### F3 — High / P0 gate: requested feature attention mask is discarded

**Proven mismatch:** `docs/research/mnn-audio.txt:262-271` requests `return_attention_mask=True` but immediately retains only `['input_features'][0]`. `:257-260` declares all columns valid using `input_features.shape[-1]`. The official path at `docs/research/qwen-modeling.txt:1116-1128` sums the feature attention mask and slices `input_feature[:, :feature_len]` before calling the tower.

`docs/research/qwen-asr-preprocessor.txt:2-13` records 30 seconds / 480000 samples / 3000 frames, right padding, 128 bins and a 160-sample hop. The exact installed Whisper extractor implementation/default call semantics are absent. Thus **discarded validity information is proven**, but an unconditional assertion that every short input here becomes 3000 frames is not established by these files alone. If an extractor returns 3000 columns with only 1000 valid frames, current Python path produces 390 placeholders instead of the correct 130. Those are source-derived counts, not observed outputs.

**Minimal patch:** retain the feature-extractor result; obtain the returned frame-domain mask, validate its alignment with mel columns, compute `T_valid`, and slice to it before either encoder path. Pass the same valid length to the official tower. A P0 single-audio graph can remain `[128,T_valid]` without a new length input; document and enforce this caller contract. Do not merely pass a smaller length with an unsliced feature matrix: the official `.split(chunk_lengths.tolist())` at `:693` expects matching totals. Set padding/truncation policy explicitly and compare it with the pinned official processor. For longer audio, segment or explicitly support the length; do not silently lose speech at a default 30-second truncation boundary.

**Additional oracle hazard:** `qwen-modeling.txt:1116-1120` overwrites `audio_feature_lengths=None` when no feature mask is provided, then calls `feature_attention_mask.sum(-1)`. The shown helper cannot be safely used as a length-only reference despite the optional-looking API. Supply the feature mask or call the tower with already-sliced features and length.

### F4 — Medium–High / short-audio gate: padding the only CNN chunk changes convolution geometry

**Proven structural difference:** `docs/research/mnn-audio.txt:194-207` always extends the feature width to a multiple of 100, including `T<100`. Official `docs/research/qwen-modeling.txt:693-705` pads chunks only to the longest actual chunk; for a single short utterance that width is `T`, not 100. Both later remove invalid post-CNN tokens, but removal alone does not prove parity: activations computed in the extra padded region (including convolution bias/nonlinearity) can feed subsequent convolutions near the right edge.

**Minimal patch:** for the single short-chunk case, preserve official convolution width, e.g. derive a dynamic effective CNN width `min(T,100)` for reshape/padding while retaining the established length formula and normal multi-chunk behavior. Avoid exporting a Python branch frozen by the 3000-frame example. Alternatively, clearly gate unsupported short inputs while developing a verified graph, not silently pad and claim exact parity. Numerical effect with actual weights remains unmeasured.

### F5 — High / integration gate: C++ Qwen3-ASR ABI bypasses generic Omni mask construction

`docs/research/mnn-omni.txt:1731-1740` selects `whisper_fbank` for Qwen3-ASR. Header `docs/research/mnn-audio-header.txt:165-166` declares defaults 16 kHz / 128 mel bins / FFT 400 / hop 160 / `chunk_len=0`, broadly consistent with the model preprocessor. The actual fbank implementation was not included, so frame count, padding, STFT edge handling, resampler and numerical parity are unverified.

`mnn-omni.txt:1757-1766` squeezes the leading batch dimension and calls `mAudioModule->forward(input_features)` **with one tensor**. This branch is taken before `audio_inputs>1` at `:1767-1785`. The latter generic Omni mask computes approximately `T/2` and uses a 100-token window; it is not appropriate for Qwen3-ASR's chunked 13/100 downsampling and 104-token inference windows. Adding a second ONNX input alone will break the Qwen3-ASR path, not activate that generic branch.

`mnn-omni.txt:1799-1823` permutes `[1,S,H]` into `[S,1,H]`, stores embeddings, and emits exactly `S` audio-pad IDs plus configured start/end IDs. `mnn-config.txt:142-143,328-334` defaults to `audio.mnn`, `is_audio=false`, `audio_type=whisper`; export must explicitly carry `is_audio=true`, `audio_type=qwen3_asr`, and token IDs. Confirm shape `[128,T_valid] → [1,L(T_valid),1024]` and placeholder count, not just successful model loading.

**Minimal patch:** prefer internal graph mask plus caller-side feature trimming for P0. Add input shape/length checks at the Qwen3-ASR branch. If caller output is padded, arrange explicit valid-frame information from fbank rather than inferring validity from tensor width. Keep runtime, exporter, model config and graph ABI versioned together.

### F6 — Medium–High / prompt parity gate: custom template is an unverified behavioral policy

**Proven behavior, not proven mismatch against an absent tokenizer template:**

- `docs/research/mnn-export.txt:65-73` uses only `messages[-1].content`, drops supplied system/history content, inserts an empty system turn, and emits no role-separating newlines. Assistant prefix is literally `<|im_start|>assistantlanguage {{ asr_language }}<asr_text>` and appears only if generation is requested and both literal audio tags are present.
- `mnn-export.txt:147-156` overrides the tokenizer-provided template and supplies `asr_language`, default Chinese; `docs/research/mnn-audio.txt:254` also hardcodes Chinese. This is forced-language prompting, not automatic language detection. Official README `docs/research/qwen-asr-readme.txt:168-169` explicitly demonstrates `language=None` for automatic detection.
- `mnn-export.txt:184-189` passes this custom template into the Python tokenizer wrapper; wrapper defaults for `add_generation_prompt` are not present in the snapshot set.
- `docs/research/mnn-engine.txt:111-133` actually propagates the top-level `asr_language` into template context, overriding the nested value; `:137-140` reruns that setup on config changes. Do not incorrectly claim C++ ignores language overrides. `mnn-config.txt:382` defines `asr_use_audio_template`, but no consumption was found in reviewed engine/Omni snapshots; toggling it is not an established prompt repair.
- Audio expansion uses config IDs in `mnn-audio.txt:244-253,283-288`; expected IDs are start `151669`, pad `151676`, end `151670` (`qwen-asr-config.txt:124-126`). C++ reads exported IDs at `mnn-omni.txt:476-479` and generates them at `:1816-1823`.

The official `tokenizer_config.json`/chat template and processor prompt construction are absent. Missing newlines look important but **cannot honestly be labeled a proven official-template mismatch from these files alone**.

**Minimal patch strategy:** establish a golden prompt and token ID vector from the pinned official tokenizer/processor before changing whitespace. Implement one shared prompt policy for Python export checks and C++: explicit forced Chinese/English vs automatic language, context support or an explicit no-context P0 restriction, exactly one audio segment, and generation-prefix behavior. Preserve the official syntax byte-for-byte for supported modes. Empty language must not simply produce `language <asr_text>` unless the official oracle does that. Test the raw prefix, full IDs, audio-placeholder count and EOS/stop handling separately from output text.

**Additional bounded-scope risk:** `mnn-audio.txt:269-271,278-299` overwrites a single `self.audio_embeds` for each parsed audio tag and then assigns it to all pad positions. Multi-audio Python prompts therefore do not preserve per-segment embeddings. For P0, reject multiple audio segments or explicitly concatenate in token order; C++ uses an embedding vector and should be tested independently. Do not expand P0 support merely because the regex accepts multiple tags.

## Exact MNN conversion entry points and cautions

These are future integration entry points, **not commands executed during review**:

1. CLI: upstream `transformers/llm/export/llmexport.py`, `build_args` at `docs/research/mnn-export.txt:996-1060`, `main` at `:1078-1101`; library entry `export(path, **kwargs)` at `:1061-1076`. `--path` is required; `--export onnx` or `--export mnn` selects the path; `--dst_path` controls destination. `LlmExporter` construction immediately loads a model (`:30-33,76-82`), so it is not a read-only inspection command.
2. Loader: `LlmModel.from_pretrained` and `LlmTokenizer.from_pretrained` at `:77-82`. Their actual implementation files are absent here. Audio dispatch is visibly `Audio.get_audio()['qwen3_asr_audio_encoder'] → Qwen3ASRAudio` at `docs/research/mnn-audio.txt:24-38`.
3. Audio export: `LlmExporter.export_audio` at `mnn-export.txt:670-674` calls `Qwen3ASRAudio.export` at `mnn-audio.txt:303-316`, which traces `[128,3000]`, names input `input_features` and output `audio_embeds`, and declares input time axis 1 dynamic. It calls the specialized export model, **not** `Qwen3ASRAudio.forward`. Dynamic-axis annotation alone does not prove dynamic Pad/Reshape/Range/boolean Gather/mask behavior survives ONNX/MNN.
4. MNN orchestration: `mnn-export.txt:762-781` creates `MNNConverter(self)` for `mnn`, exports audio, language and tokenizer/config. Audio converter call is `export(audio_onnx, self.audio.quant_bit)`; inherited audio bit setting is 16 (`mnn-audio.txt:17`). Language export is `mnn-export.txt:739-760`, with its own converter call and explicit transformer fusion enabled for Qwen3-ASR.
5. Converter plumbing: import `utils.mnn_converter.MNNConverter` at `mnn-export.txt:19`; `--mnnconvert` at `:1021` defaults to `../../../build/MNNConvert`, with help advertising a pymnn fallback. Full `utils/mnn_converter.py`, `utils/torch_utils.py`, `utils/model.py`, and tokenizer implementation were not captured. Their filenames in a tree snapshot do not establish exact converter defaults, ONNX opset, tracing semantics, subprocess flags or compatibility. Review pinned versions before trusting fusion or full-float settings. In particular the audio call does not explicitly set `transformer_fuse`; its default is unknown here. Preserve noncausal block attention and test graph optimizations, rather than assuming decoder-style fusion is safe.
6. Precision: CLI default `--quant_bit=4`; `--quant_bit 16` is documented as FP16 weights, not FP32 (`mnn-export.txt:1015`), and default embedding export is BF16 (`:1051`). Set decoder/lm-head bits explicitly for a non-INT4 baseline; inspect actual serialized dtypes. PyTorch audio and ONNX wrapper use float32 (`mnn-audio.txt:239,306`), but that does not imply the converted graph retains FP32 weights. Establish a clearly labeled FP32 numerical reference first, then a measured FP16/BF16 conversion baseline; no quantized-quality conclusions yet.
7. Export-generated runtime config defaults to CPU, low decoder precision, mixed sampling/temperature 0.8 (`mnn-export.txt:368-388`), with a separate multimodal precision setting at `:402-408`. These are not deterministic greedy parity settings. Override sampler and decoder/audio precision explicitly using supported runtime values; record the merged effective config.
8. Preserve evidence: `mnn-export.txt:782-787` deletes ONNX intermediate files after MNN export. Use a separate ONNX export directory or a parent-owned retention patch so the exact ONNX graph compared against MNN remains available.
9. Runtime load: `Omni::initProcessorRuntime` at `mnn-omni.txt:539-588` configures processor runtime, shape mutability, and loads `audio_model()`; the latter defaults to `audio.mnn`. This is the deployment side of the ABI, not another exporter.

## Proposed minimal test suite / execution order

All filenames below are **proposals for the parent**, not files created by this review. Start with stdlib/pure helper tests and only later run numerical/model workloads under parent authorization.

### 1. `tests/p0/test_audio_contract.py` — no model weights

- Assert config constants, token IDs, 128 mel bins and encoder output/decoder hidden width 1024.
- Check length formula and boundaries for `T={1,7,8,9,15,16,17,95,96,97,99,100,101,799,800,801,1599,1600,1601,2999,3000,3001}`. For every case boundaries start at zero, end at `L(T)`, strictly increase, and sum to valid tokens. Exact full chunks must not create a zero-length tail. Reject `T=0` explicitly instead of calling a max/split operation on an empty sequence.
- Assert mask shape/dtype, symmetry, zero diagonal and all within-block cells, negative cross-block cells. At `T=101`, encoded indices 12/13 **must** attend each other. At `T=801`, indices 103/104 **must not** attend each other. These two checks distinguish per-CNN masking, correct inference-window masking, and global attention.
- Trace/spy on layer calls to assert mask is actually delivered; testing an unused mask helper is insufficient. Check original/reference backend config is not silently changed by export wrapper construction.

### 2. `tests/p0/test_feature_lengths.py` — mocked extractor first

- Fake extractor returns `[1,128,3000]` features and a mask with 1000 leading valid frames. Assert tower and export wrapper receive `[128,1000]`, length 1000 and exactly 130 placeholders, not 390.
- Add mask all-zero, all-valid, misaligned mask dimensions and unsupported non-prefix validity patterns; reject invalid contracts clearly.
- Later compare real official/Python/C++ preprocessing for sample counts around hop and STFT edges, short clips, just below/at/above 1 s, 8 s and 30 s, silence, and explicit tail padding. Distinguish **invalid extractor padding** (must be ignored) from **real appended silence** (is valid audio, not an invariance test). Confirm truncation and resampling policies, frame-domain mask rounding and C++ fbank width.

### 3. `tests/p0/test_prompt_contract.py` — tokenizer fixtures, no model weights

- Golden rendered bytes and token IDs for forced Chinese, forced English and automatic detection; `add_generation_prompt` true/false; optional context or explicit context rejection; audio tag expansion and exactly one start/end pair.
- Compare Python and C++ template output/tokenization, including whitespace, top-level language override versus nested context, empty/unsupported language, literal audio-tag gate, EOS and stop behavior.
- For a known 1000 valid mel frames, exactly 130 pad IDs and 130 injected embeddings; verify neighboring text embeddings remain unchanged. Reject or correctly support two audio tags; test sequential requests for stale state.

### 4. `tests/p0/test_audio_numerical_parity.py` — later, explicitly labeled oracle

- Separate reference instance, eval/no dropout, fixed seed and recorded dtype/backend. Compare official backend, corrected windowed eager oracle, wrapper, ONNX and MNN using identical already-computed valid mel tensors first.
- Dump CNN output, valid-token selection, positional embedding, layer-0 attention/hidden state, final projected embedding. Short lengths below 100 isolate F4; 101–800 detect too-small masks; 801 and above detect missing/global masks. Perturb a late-window hidden token at the attention-layer input and verify the first window is unaffected for a block mask; perturb index 13 and verify index 12 can respond in a correct 104-token block.
- Do not conflate window patch with convolution-padding patch. Test each against intermediate tensors; record max absolute error, relative error with safe denominator, RMSE/cosine and any nonfinite values. Set justified dtype-specific tolerances before the release gate, not after seeing failures.
- Export once with the standard 3000-frame example, then run that **same graph** at all boundary lengths and repeated sequences such as `801→99→3000→101`; reject frozen trace dimensions or stale shape state. Compare unoptimized/optimized ONNX and MNN CPU graphs, especially masked noncausal attention fusion.

### 5. `tests/p0/test_asr_end_to_end.py` — last

With matched preprocessing, prompt, positions, stop tokens and deterministic greedy decoding, compare audio placeholder counts, first-step decoder logits, generated IDs and parsed transcript. Use short and >8-second Chinese/English clips, mixed-language speech, silence/noise and a small labeled corpus for CER/WER. Record separately (a) conversion parity relative to the declared backend and (b) recognition accuracy relative to human labels. Repeat before/after FP16/BF16 or INT4/INT8 experiments; source mismatches alone establish neither CER/WER loss nor improvement.

## Residual risks / release position

- **P0 correctness not cleared.** Structural window mismatch, absent mask plumbing, validity loss and single-short-chunk padding difference are established; actual numerical/transcription effects have not been measured.
- Official eager is not a reliable oracle for claimed windowed behavior in this snapshot; FA2/backend revision and intended prompt must be explicitly pinned. Snapshot hashes bind reviewed bytes but do not pin model weights, tokenizer, processor, Transformers or installed MNN Python utilities.
- Official prompt assets, fbank/resampler implementation, MNN converter defaults/optimizations and torch-to-ONNX exporter helper remain unreviewed because they are absent from the captured set.
- No graph export/load, numerical parity, model output, CER/WER, latency, memory, GPU/APU or phone measurements were performed. Proposed tests are not passing test evidence.
- Scope is single-utterance P0, not batch/multi-audio/streaming. Zero/very-short and long/truncated audio need explicit supported-input policy.
- Parent remains sole source/test/config writer. The only review-side write is this authoritative report artifact.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "F1-F6 provide severity, exact docs/research snapshot paths/line ranges, source proofs versus unmeasured effects, minimal parent-owned patches and proposed tests; conversion entry points and residual risks are documented."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/4bf1ad87/reports/p0/source-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "python3 -B stdlib-only inline review: SHA-256 manifest verification, official AST mask-call inspection, integer feature-length/window checks",
      "result": "passed",
      "summary": "Seven primary snapshot hashes matched; official _prepare_attention_mask has zero call sites; boundary cases from T=1 through T=3001 satisfied the stated source-derived assertions. No model imports or inference."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "No staged paths at review time; no staging commands executed."
    },
    {
      "command": "ONNX/MNN conversion, tensor/model parity, prompt tokenizer execution, ASR/device tests",
      "result": "not-run",
      "summary": "Outside read-only source-review workload; tests proposed for parent execution."
    }
  ],
  "validationOutput": [
    "T=800: 104 tokens; official boundaries [0,104], wrapper [0,13,26,39,52,65,78,91,104].",
    "T=801: official boundaries [0,104,105]. T=3000: [0,104,208,312,390].",
    "Official mask helper exists but has no call sites; eager ignores cu-sequence-length kwargs in the reviewed source.",
    "Only the report artifact was written; no source/config/test modifications."
  ],
  "residualRisks": [
    "Numerical embedding, decoder-token and CER/WER effects are unmeasured; P0 correctness is not cleared.",
    "Official eager reference omits the window mask; backend/oracle policy must be pinned and labeled before parity claims.",
    "Official tokenizer/processor prompt assets, fbank implementation and MNN conversion helper defaults were absent from reviewed snapshots.",
    "Dynamic graph correctness, precision/fusion behavior, short-audio boundary effects and runtime feature validity remain to be tested."
  ],
  "noStagedFiles": true,
  "diffSummary": "Report-only artifact; no implementation or tests changed.",
  "reviewFindings": [
    "high: docs/research/mnn-audio.txt:221-224 versus docs/research/qwen-modeling.txt:719-726 — per-CNN cu_seqlens differ from 104-token official inference windows.",
    "high: docs/research/mnn-audio.txt:180-182,223-224 and docs/research/qwen-modeling.txt:90-113,649-666,728-732 — eager has no supplied mask; official helper is unused; current per-CNN boundaries do not prove enforced local attention.",
    "high: docs/research/mnn-audio.txt:257-271 versus docs/research/qwen-modeling.txt:1116-1128 — feature validity mask discarded and full feature width treated as valid.",
    "medium-high: docs/research/mnn-audio.txt:194-207 versus docs/research/qwen-modeling.txt:693-705 — T<100 convolution padding geometry differs; numerical effect unmeasured.",
    "high: docs/research/mnn-omni.txt:1757-1785 — Qwen3-ASR uses one-input ABI and bypasses incompatible generic Omni mask construction.",
    "medium-high: docs/research/mnn-export.txt:65-73,147-156 — custom forced-language last-message-only prompt lacks official tokenizer golden verification; exact official whitespace mismatch not proven."
  ],
  "manualNotes": "Parent is sole source/test/config writer. Report-only write follows the explicit output destination. The important correction to prior window-only reasoning is that eager ignores boundaries without a mask, and the captured official eager implementation omits that mask too."
}
```
