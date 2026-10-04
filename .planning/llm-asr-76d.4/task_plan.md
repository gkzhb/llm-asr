# M3 执行计划

## Goal
按真实依赖建立少量 Gradle 模块与真实核心 JUnit 回归，保持全部业务/安全/native 身份与旧 gate。中间交接，父审后才批准提交/关闭/交付。

## Binding
- task: llm-asr-76d.4；role: project-agent；唯一源码/Beads/本计划 writer: m3-build。
- cwd/worktree: /home/zhb/gitrep/llm-asr；branch main；base HEAD: 538dcb66f16d41adeba440d934d3a0d2e8c4eb6d。
- PI session: 01a107f4-71d8-778e-a743-2b336eef290c；subagent id: f27adc1c-44eb-4da9-bbbb-609b9b920bdc；Herdr pane wJ:p3（agent list实核）。
- plan: .planning/llm-asr-76d.4/；父监管 .planning/llm-asr-76d.4-supervision/ 只读。
- 原子claim及实施owner m3-build；父批准工程验收并授权后显式关闭M3成功。功能提交9ec98d3b9e9a2f28af21d86f811639e3a0302ade（父安全提交），关闭文本由父另提交。
- 保留他人两个未跟踪分析目录；不修改根历史计划。不stage/commit/push/设备/私人数据/新功能/全Kotlin迁移；本次仅按父显式授权关闭M3，不关闭其他任务。

## Current Phase
Phase 6 完成：功能已安全本地提交9ec98d3，父工程验收满足、M3已授权关闭。当前APK可供用户手动验证，设备未验收；本次关闭文本未提交，由父另提交。

### Phase 1: 依赖调查与最小方案
- [x] AGENTS/路线/任务/M2/ap5/memories/runtime/Git核验与claim。
- [x] 阅读实际生产依赖、所有host入口及脚本指纹接线，形成模块图/旧测试入口清单（findings）。
- **Status:** complete

### Phase 2: 模块与JUnit实施
- [x] 采用渐进sourceSets唯一归属：50core/5runtime/8app（63生产源码，M6新增ActivityWindow），保持生产路径/字节/包/入口。
- [x] 锁定JUnit依赖与来源SHA/传递锁；实际核心JUnit及模块编译负例。
- [x] 同步旧host/JNI/APK/指纹脚本，不漏构建输入；63实际唯一owner实核通过。
- **Status:** complete

### Phase 3: 完整验证与自审
- [x] fresh核心JUnit/边界负例、全部host/ABI/真实mutants。
- [x] 全源/JNI/native provenance/ELF/AGP APK/身份/指纹。
- [x] strict offline rerun与真实checksum负例，文档/报告/直接自审。
- **Status:** complete

### Phase 4: 中间交接
- [x] 保存所有命令/退出码/报告/身份/限制；Beads保持真实in_progress。
- [x] 不提交不关闭不交付；中间结果回父，父只读审查/复验待执行。
- **Status:** complete

### Phase 5: 父验收后文档收尾
- [x] 只读父复验/异家族审查/逐项处置报告，确认无本轮工程阻塞。
- [x] 更新独占计划和必要状态文本，保持174构建输入/生产/测试/native不变。
- [x] Beads显式append-notes验收完成待提交，保持in_progress；不stage/commit/close。
- **Status:** complete

### Phase 6: 功能提交后授权关闭
- [x] 核HEAD完整9ec98d3、174current/committedSHA、4external与APK一致。
- [x] 依bd close --help显式关闭llm-asr-76d.4，准确记录父70全gate/35JUnit/异家族处置与设备not run。
- [x] 更新最终状态文本，未改构建输入/产物，未stage/commit；不关其他task，不领下一任务。
- **Status:** complete

## Next Step
等待父提交关闭状态文本；本Agent保持idle，不领取下一任务。

## Errors
- 读取 app/build.gradle.kts ENOENT：实际:app目录android/app。
- Maven旧依赖.sha256 sidecar404：官方.sha1交叉+实算SHA256，5artifact严格固定。
- 新plugin marker未缓存/根lock旧marker导致两次编译exit1：显式useModule复用已校验实现、Gradle --write-locks生成锁后corecompile成功。
- AGP sourceSets.java.include未限制实际JavaCompile源集合（fresh后136runtimeclass）不是旧class污染：直接JavaCompile.include与空sourcepath联合约束，clean runtime测试green，实际owner待边界gate证明。
