# P1-A 本地验证护栏执行计划

## Goal
同源快速/完整本地验证入口，AGP 原生 Lint 与限定格式检查及真实负例；原35JUnit/host/ABI/latch/mutants/JNI/native/APK/strict身份全部保留。

## Binding
- task llm-asr-76d.5；父 owner/唯一 Beads writer p1-steward。
- role project-agent；当前唯一执行计划/源码 writer p1b2-build；session 967c9c1e-58fa-4f12-82e3-8f1ced2f4344（2026-10-15父明确独占交接）。原p1-build-2已终止，其session/三strike与共享validator路线历史保留于progress；不转移父Beads owner。
- cwd /home/zhb/gitrep/llm-asr；main；base 7eace17a8ea636db6010f95d2229c28dc2f78467，无worktree。
- 本目录独占；父只写 supervision。无二级委派；不 claim/update/close/stage/commit/push。
- 不改生产业务/native数学/权限/包名/签名/录音gate/IME字段及owner世代；无设备/麦克风/private/模型权重操作，无新依赖下载/自动拉工具/远端CI。

## Current Phase
本文件为A/B2历史执行计划，现已归档。后续B3生产Lint修复/full及安全提交已完成，功能提交 `2d869e5`；当前195build/80native与APK报告绑定通过。当前状态/后续协调见 `.planning/llm-asr-76d.5-supervision/task_plan.md`，最终产品证据见 `reports/local-verify-p1/full.md` 及 `.work/p1-ship-review/commit-receipt.json`。不按下文历史待review重复分派。

## 历史：B2交接时状态
B2实现/有界回归完成，最终分域freeze及handoff供父只读review；唯一writer p1b2-build交接后停止写入。普通DefaultTask调用同production validator闭包/真实passed Configuration负例，与默认AGP真正NewApi0/1/0分域；production外围record/fc identity保持。typed模拟路线永久停止。A/B1不重开，B3/full/APK/设备/提交未做。

## Next Step
本历史执行计划不再分派；新协调者从监督计划及Beads恢复下一轮。B2旧freeze作为历史证据保留，本文件提交后说明不冒其supplemental哈希仍为current。

### Phase 0: 恢复与冲突核验
- [x] AGENTS/skills/references/mqn三文件/监督plan/Beads M4与M3已读。
- [x] main/base/worktree/Git与Herdr核对，无重叠writer。现有未跟踪材料不动。
- [x] 原入口/模块Gradle/覆盖映射/M3报告/工具/strict负例已读。
- **Status:** complete

### Phase 1: 入口实现与负例
- [x] 同源快速/完整入口及参数防绕过实现，mock shell/static接线证据通过；真实任务图待验。
- [x] 原生AGP Lint接线，不baseline/关error；限定文本checker/负例通过。Lint缓存与生产诊断待实测。
- [x] 文档及非敏感检查点报告。
- **Status:** complete

### Phase 2: 初始检查点串行验证及输入绑定（历史）

以下not run/工具缺失记录仅初次检查点，不代表当前状态；工具后由p1-test解除，A批已完成graph/35JUnit/checksum，Lint/full依然未做。
- [x] 新针对性/格式普通及-O/source旧回归exit0；保存command/exit/log。
- [ ] 真实graph-fast/35JUnit/Lint/checksum负例：not run，宿主工具闭包缺失。
- [ ] 完整offline rerun：cache-only外层拒绝exit1，Gradle/native/JNI/APK未执行。
- [x] 当次源码freeze SHA/验证日志指纹，明确无本轮APK身份，不复用M3证据。
- **Status:** pending

### Phase 3: 冻结交接
- [x] diff自审、安全路径检查、handoff .work/p1-build/handoff.md。
- [x] 报告完成/未完成/阻塞与下一步；交接后停止全部写入等待父审。
- **Status:** complete

### Phase A: 审查R1–R3/R4 checksum集中修复
- [x] R1旧真实入口目标red→修复green/恢复，3.12普通/-O；真实grep读取错误亦拒绝。
- [x] R2 parser/mock及真实无plugin direct/indirect图红绿、主graph-fast35任务/fast23action全执行35JUnit一次。
- [x] R3 ignored新freeze生成器实读collector AST集合/hash、native输入子集及external/tool证据；不重构collector、不覆盖旧13/179。
- [x] R4 checksum唯一节点/目标HTML诊断纯负例，真实strict help原0→mutant1→恢复0。
- [x] 本批报告/冻结/batch-a-handoff，停止写入等fresh窄审；B Lint/full/APK仍not run。
- **Status:** complete

### Phase A-polish: A1–A4限定收尾
- [x] 新目标tests旧程序red：unknowncore/leading whitespace放行；unknown AST到eval/新src遗漏。
- [x] 基于真实35task精确path清单、畸形前导空白拒绝；approved源码span SHA执行前比对，不宣称sandbox。
- [x] 3.12普通/-O和PATH3.14兼容、真实unknowncore图红绿、主graph35task/fast23action35JUnit串行。
- [x] v3身份/临时目录变异/辅助工具及tests绑定、handoff停止writes。
- **Status:** complete

### Phase A-decorator: ignored helper最小收尾
- [x] 旧helper临时@print/@sorted([])/多行decorator由observer拦截compile，目标red1，无变体执行。
- [x] 所有实际compile/exec AST中的FunctionDef显式拒非空decorator_list，3.12/3.14普通/-O绿及合法native恢复。
- [x] 闭合183build inputs零漂移，旧v3/历史保留，新v4辅助工具/test身份，handoff后停写。
- **Status:** complete

### Phase B2: 实际Lint消费约束及单一真实fixture
- [x] 23新增metadata语义增量/412旧项不变，主锁及pins不变（接管read-only语义复核）。
- [x] 最新共享validator主app6/runtime7 typed consumer ONLY RESOLVE，仅2 gate actions，171边含2constraints/66artifact。
- [x] 已成功run-ukwens2p恢复核当前payload/源快照/命令/log/XML，identity目标拒绝及普通共享validator files负例/恢复；默认AGP Lint0/NewApi1/原源0另证据，无理由不重跑。
- [x] bg b6763c0ee自动completed0：固定3.12/PATH3.14各normal/-O七项矩阵、exact35图、fast23action/35JUnit零fail/error/skip、checksum help0/target1/0；193build inputs回归前/后/当前一致。
- [x] 当前文档/报告/全新分域freeze/handoff；历史A-v4/blocked/typed-stop及旧失败handoff原字节保留，交接后停写。
- **Status:** complete

## Errors Encountered
| 错误 | 处置 |
|---|---|
| 历史fixture launch driver完整SHA未落盘，mtime位于早期missing命令期间 | 当前gate/manifest/wrapper/metadata/模板+literal注入/命令/源快照/log/XML精确恢复对应；具体记录限制，不称launch attestation、不无理由重跑成功fixture |
| 旧typed模拟路线三不同方法均AGP validation失败/默认fc immutable | 原失败/交接原字节保存，路线永久停止；父批准普通共享validator/真实Config集成负例，默认AGP NewApi另证据通过，不把旧异常当目标红 |
| B2 green0及独立diagnostic在lintAnalyzeDebug doFirst source类型失败 | diagnostic bced34ba2 failed1；gate1Configuration→doFirst66FileCollectionAdapter、同66files、Gradle FinalizePropertiesTaskExecuter/固定bytecode佐证；无NewApiXML/worker，按API不行stop条件停报父待窄修批准 |
| 初次普通nix-env在Gradle之前发生cache.nixos.org substituter下载 | 父SIGTERM；exit1，PID已消失；承认越界、不删除缓存、不称测试完成 |
| cache-only缺FixNullPtrCast.patch/alsa/NDK/JDK closure，exit1 | 显式offline/空substituters+builders/max-jobs0拒绝，禁止自动补齐 |
| 真实Gradle/Lint/full无法执行 | 保留fail-closed gate，交可验证代码检查点及准备授权输入，不重复无效尝试 |

## Blocked Stop Condition
owner/runtime冲突，缺缓存/需下载或策略决定，Lint需超scope产品修复，无法维持原gate时停止相关分支上报。同阻塞三种有效不同尝试仍无解停止。不baseline/宽抑制/降级strict/改hash凑green。
