# llm-asr-xyh — user-authorized source checkpoint

Task: llm-asr-xyh. Existing owner/sole Beads writer: model-variants-coordinator; current session owns this checkpoint. Project: llm-asr; branch: main; base: ee40e8ef530d18fa2dd416f69fc7f1bcfc2d7458.

## Contract
The user explicitly confirmed committing the existing code after reporting that the APK can run different quantized models. Save a normal local source checkpoint, not a release or an assertion that the remaining review/version gates passed. Do not alter product source, rebuild models/APKs, access devices/private content, retry the refused review, or push. Preserve unrelated untracked documents, QwenASR, historical planning and all ignored artifacts.

### Phase 1: Restore and inspect
**Status:** complete
Restored Git, embedded llm_asr task, existing supervision plans, full-build binding and live runtime. No same-project worker is active; index was empty. All 54 full-build frozen files match their recorded SHA256. The only tracked changes outside that freeze are the two previously requested APK-version policy documents.

### Phase 2: Candidate safety and evidence binding
**Status:** complete
Use the exact 54-file build freeze plus two version-policy documents and this checkpoint's non-sensitive planning/report files. Inspect file types, text, modes, secret/artifact exclusions and immutable evidence. Record hashes without modifying old receipts. Stop on unexplained drift or unknown index content.

### Phase 3: Stage, commit and verify
**Status:** in_progress
Stage only the approved files individually, compare staged path/mode/blob/content identities to the approved candidate, run diff checks and create a normal local commit with hooks/signing policy intact. Verify committed/current/frozen inputs and the unchanged APK. Keep llm-asr-xyh open until release gates are actually met.

## Next Step
Stage the exact 54 frozen source files, two existing version-policy documents, this checkpoint's three planning files and reports/model-variants-xyh-checkpoint.md; verify staged identities and create the explicitly authorized local source checkpoint. Post-commit receipt stays outside Git to avoid self-referential hashes. Release gates remain open.
