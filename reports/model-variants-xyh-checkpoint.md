# Multi-quantization model management — source checkpoint

Task: `llm-asr-xyh`. Base: `ee40e8ef530d18fa2dd416f69fc7f1bcfc2d7458`.

## Scope and authorization

The user explicitly requested a local code commit after reporting that the current APK successfully runs different quantized models. This checkpoint preserves the existing compatible Qwen3-ASR MNN v2 package policy, model catalog and selection, compact model list/details, selected-model App/IME paths, native graph admission, packaging tools, tests and documentation. Fixed v1 compatibility remains supported. The two previously requested APK-version policy documents are included.

This is a **source checkpoint, not a release acceptance or a new APK delivery**. No product source or version was changed during the commit operation. No model computation, APK rebuild, device access or remote push was performed for this checkpoint.

## Verification evidence

- Existing formal command: `bash scripts/verify-gradle.sh --offline --rerun-tasks`; terminal exit 0, 184 tasks, 42 JUnit tests without failures/errors/skips. This ran against frozen dirty source before the checkpoint commit, not against a new commit object.
- Before staging, all 54 files in the formal source freeze matched their SHA256 values. All 257 recorded APK build inputs and the engineering APK hash also matched. The full command's successful terminal record and stdout hash were rechecked.
- Earlier INT4/INT8 artifact preparation verified real quantization descriptors, strict package integrity and public Chinese/English host MNN EOS runs. These are decoder/tied-embedding weight-only variants with FP16 audio encoder/scales, not all-integer execution or an accuracy certification.
- User-reported device result: the current APK runs different quantized models. Device identity, exact model matrix and detailed steps were not supplied; this is not an agent-run device acceptance report.
- Commit safety checks cover an exact path allowlist, regular UTF-8 text files, permissions/content identities, sensitive-pattern/artifact exclusion, staged diff checks and staged-versus-working content equality. Unrelated untracked documents/plans and QwenASR remain excluded. No weights, model ZIPs, audio, APKs, caches, signing keys, credentials or local agent session data belong in this commit.

Local historical evidence, retained outside Git: `.work/xyh-full-local-3/{source-freeze.json,full.json,current-binding.json}`, `.work/xyh-fp16-smoke/handoff.md`, `.work/xyh-quant-pack/handoff.md`. Their historical status is not rewritten by this checkpoint.

## Existing engineering APK identity

- Path: `android/app/build/outputs/apk/debug/app-debug.apk`
- Package: `org.llmasr.minimal`
- Version: `0.6-debug`, versionCode `6`
- APK SHA256: `096a563efa0577f5b9b38ea7caa1064eedda28c1f179c1393cf037edd0035b5d`
- Native SHA256: `03959ae52cef3241a91a916170f16dd2b45d6671d78b6636298f8fb7792bf5a0`
- Signing certificate SHA256: `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`

## Remaining release gates

1. Native/UI independent repair-closure review is absent. Existing local defensive review, targeted tests, full build and user-reported successful execution do not replace that review. Do not retry/rephrase/provider-fallback to circumvent the earlier refusal.
2. Before the next feature APK delivery, increment versionCode and update versionName, then rebuild and verify affected inputs/package/signature. This checkpoint deliberately preserves the tested source/version; it does not claim the existing code 6 APK meets the new-version delivery rule.
3. Detailed device/SAF/IME lifecycle, cancellation and destructive-operation acceptance remain unverified beyond the user's general successful-run report.

Keep `llm-asr-xyh` open. The next release action is permitted independent/human assessment of the outstanding native/UI delta, followed by versioned APK verification; this local commit does not waive those gates.
