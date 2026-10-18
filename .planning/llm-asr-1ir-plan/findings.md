# Findings

- P2 enum batch 4e73662 completed; old P1C paused-enum suggestion is historical, not a live blocker or work to repeat. jrq complexity skill already committed eeaf1ca.
- InferenceAdapter is core synchronous orchestration, not an engine registry/resident manager. It returns NativeResponse and owns readiness/library/native/parser/phase sequencing, not task/WAV/report ownership.
- NativeResponse.kt is MNN-specific strict UTF8 + three metrics (finite nonnegative times, 0..128 tokens), empty raw accepted, Qwen AsrText display. Completion evidence is upstream normal native return, not measured audio coverage.
- App start entry lambdas capture immutable language parameter; config/cache paths are resolved in worker. IME start validates language and captures it and editor revision; backend resolves paths later. No model selection snapshot exists.
- RequestRunner synchronous admitted hooks and finishOnce already exist; do not add a second mutex. TaskCoordinator rollback/RequestRunner actual cleanup inspected: abandoned before release on admission/executor RuntimeException/Error; finished after cleanup, worker coordinator finally releases. No new snapshot/pin in candidate.
- Old research gives broad future names/DTOs, not approved contracts. Source-first planning must avoid implementing full selection/registry/receipt/resident spec or assuming new engine readiness.
- Candidate selected: one Kotlin TranscriptResult.text() projection implemented by NativeResponse; IME narrows consumption, App retains NativeResponse legacy report and adapter descriptor unchanged. This is a limited preparatory result contract, not an interchangeable engine seam or full P3. One production adapter means full engine seam still hypothetical; no registry/capability/selection DTO stack.
- Exact future scope: 1 new production Kt + 2 production modifications; 2 existing Java regression entries + 1 isolated production mutant driver, host wiring, architecture source map, reviewed 63→64 module-owner inventory, baseline pins and 2 docs. No Gradle sourceSets/new dependency/App JSON fixture changes needed.
- Effective future mutants replace only NativeResponse.text(): display→raw or display→empty String, compile0 then independent fixed Java assertion failure then restored source green. Actual production parser/InferenceAdapter path, no fake parser; ImeBackend platform runtime is not covered by those host tests.
- Stored complexity excerpt: parser Kotlin cccc blind, knots parse7/8; ImeBackend transcribe C/Y1/1 and knots0/1; native unchanged partial cccc. NativeResponseTest main is cccc17/18 and knots17/4, not guessed values. Native file ends136; phase listener ends110; literal locator validation corrected first draft line offsets.
- Self-check corrected a draft engineering-layer mistake before final seal: fast really compiles runtime ImeBackend via test→module-boundary→compileDebugJavaWithJavac; it does not run ImeBackend platform behavior or compile app8. Separate full-source SDK compile is recommended, not falsely treated as first runtime compilation.
- Long-term knowledge reviewed against existing asr-quality-evidence memory; no new verified architectural decision or reusable memory authorized; no Beads memory/AGENTS writes.
- 82 pre-existing untracked files (including parent plan, jr q review/supervision and older protected research) and 1675 tracked files snapshotted before new outputs. Main index SHA bound. No product command run.

## 父审当前处置 / 2026-10-18
- 父接受fresh同sol fork:false文稿NO BLOCKING及产品DEFER/REJECT；现ImeController.Backend.transcribe已返回String，新局部投影接口收益不足，不独立实施。前述原选择/allowlist为历史提案，不再是下一批指令。
- R1：两NativeResponse.text方法mutants不观察实际ImeBackend.transcribe；parser/adapter断言、lexical调用、编译/DEX均不能据此宣consumer安全。未来重启须另scope的生产consumer可观察证据，本次不设计新缝隙或执行mutants。
- R2：终态前缺失序列/乱序/跨线程导致遥测失败；closed后晚回调直接忽略，不追加失败或撤回/复活/改写结果。35是core34/runtime1 JUnit方法数，不等于Gradle任务图节点数。
- 两source/metric报告无须改动；旧work/reviewreceipt及review三plan冻结。父supervision变化为合法supplemental，起终核按此次start区分不误报。
