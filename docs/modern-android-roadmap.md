# 现代 Android 工程技术路线与 Agent 接手指南

## 1. 文档身份与授权边界

- 建设总任务：**`llm-asr-76d`**；本轮路线登记：`llm-asr-x1e`。
- 制定时源码基线：`2fd7511`（Kotlin 第二批检查点）。这是审计快照，**不是后续 Agent 必须使用的永久 HEAD**。
- 用户本轮授权：创建 Beads 任务、技术路线文档并纳入本地 Git。**不在本轮实施、不启动新 Agent、不访问设备、不推送远端。**
- 未来由用户明确派 Agent 领取窄任务；任务登记、`bd ready`、本路线均不自动授予全部架构变更或设备访问权限。
- 任务状态、依赖、owner、blocker 以 Beads 为准；本文保存技术边界与验收规格，不维护第二套完成状态。

## 2. 现状与迁移目标

### 2.1 已核验的现状

目前有 62 个生产类（基线时 10 Kotlin / 52 Java），已按 task、model、asr、ime、diagnostics、transcription、modelmanagement、platform 分包。构建使用 `scripts/build-minimal-apk.sh` 手工编排 kotlinc/javac、aapt2、D8、JNI 链接、zipalign、apksigner；尚无 Gradle/AGP 工程与标准 Android 仪器测试工程。

`AppGraph` 使用 application context 做进程级手工依赖注入；App/IME 共用单任务所有权。TXT、日志导出有独立有界 worker。现有核心回归、并发负例、生产行为 mutants、模型文件校验、JNI/包检查和构建输入指纹应保留。

主要工程缺口：

1. Android 工具版本、混合编译、变体、依赖、打包由脚本自行维护。
2. native 链接消费预先存在的 `.work` 对象，并解析 `build.ninja`；干净构建入口需要标准化。
3. host-safe 源码名单与 main() 测试入口手工维护；源码字符串检查不能代替实际 Android 回调。
4. 页面多为平台 Activity + 程序化 View、硬编码文案/主题、Listener/Handler 生命周期；缺标准 UiState/ViewModel 与完整进程重建策略。
5. 首页未见 systemBars/cutout insets 处理，模型页/日志页已有部分处理。target35 在 Android15+ 的 edge-to-edge 是优先核验项，**未做本轮设备复现**。
6. Manifest 固定 debug 身份；无标准 release/CI 发布体系。native 已有 NDK28 和 16KiB 链接参数，但仍需 ELF/包装/运行分层验证。
7. JNI 每请求创建、加载并释放模型，无暖驻留和可靠 native 硬取消；这属于后续产品/性能设计，不与构建迁移混做。

依据：[当前 APK 状态](../reports/apk/status.md)、[Kotlin 迁移](kotlin-migration.md)、[本机复现](local-apk-reproduction.md)、[需求审计快照](requirements-audit.md)。历史快照可能绑定旧 APK；实际验收必须重新核对当前 Git/输入/签名/产物，不继承旧包证明。

### 2.2 目标职责分工

| 层次 | 推荐职责 | 不应承担的职责 |
|---|---|---|
| Nix | 固定宿主 JDK/SDK/NDK/CMake、必要原生输入、资源预算 | 不能仅凭 devShell 宣称 Gradle 网络依赖完全 hermetic |
| Gradle Wrapper + AGP | Android 编译、资源/Manifest、变体、依赖、测试、打包 | 不以 Exec 包一遍旧 APK 脚本冒充标准迁移 |
| CMake/Ninja 或独立 native artifact 任务 | 明确 MNN/JNI target、源码/补丁/依赖输入、产物 provenance | 不依赖旧机器 `.work` 的隐式对象集合 |
| 项目验证任务 | 权限/隐私/JNI/模型资产/产物指纹及失败负例 | 不用 exit0 或 APK 文件存在代替全部验收 |
| 核心业务与状态机 | 单模型所有权、录音仲裁、模型发布、导出与IME隔离 | 不因换框架放宽安全契约 |

Gradle、AGP、Kotlin、JDK 必须按实施时官方兼容组合选择并验证，本文不预先固定未经构建验证的新版本。Wrapper distribution 校验、直接与传递依赖校验、适用配置的锁定、受控仓库与缓存都要建设；`libs.versions.toml` 不等于完整依赖锁，`--offline` 也不自动等于干净缓存可复现。

## 3. 不可变契约与高风险决策

第一批保持 applicationId/已发布组件、签名升级关系、minSdk29、targetSdk35、NDK/MNN/CPU数学配置、模型清单及权限策略；不自动加 debug 包名后缀，不自动卸载签名不兼容旧 App。后续 target/签名/组件更改需单独确认。

必须保持：

- 同一时间只有一个模型操作/推理 owner，不并行加载多套大模型。
- 录音 start/cancel/commit 线性化；离开前台的取消策略不被默认生命周期替代。
- JNI/provider 实际返回并完成清理前不能释放所有权。**取消协程不代表 native 或 provider IO 被终止。**
- 模型大小/SHA、受限路径、拒绝符号链接、原子发布和白名单删除，不移除最终验证。
- clear/export 的世代与写入准入；已开始的外部写入不承诺撤回。
- IME 编辑字段身份/世代、敏感字段保护、旧结果失效和用户确认提交。IME 不自动沿用 Activity ViewModel 生命周期。
- 无网络权限、日志脱敏、模型/私人文件不入 Git。SavedState/DataStore 不默认保存转写正文、IME预览、音频或私人URI。
- 不引入 Compose、Hilt、WorkManager、常驻服务或独立推理进程作为“标准工程”强制套餐。

项目开发机约 8GiB 内存；Gradle/JVM/native 构建并发与缓存需要受控，不同时跑模型参考推理与大规模原生编译。

## 4. Beads 阶段与任务图

所有下列实施/决策子任务创建时 **assignee 为空，未分派**。依赖阻塞由 Beads 判定；无依赖并不等于自动可以开工。

| 阶段 | Beads ID | 交付 | 前置 |
|---|---|---|---|
| M0 | `llm-asr-76d.1` | 稳定基线、现 Kotlin owner 交接/范围隔离、工具兼容候选 | 用户分派与runtime核验 |
| M1 | `llm-asr-76d.2` | Nix + Wrapper/AGP 标准构建、旧验证任务接入 | M0 |
| M2 | `llm-asr-76d.3` | 显式 MNN/JNI target，干净原生/APK复现 | M1 |
| M3 | `llm-asr-76d.4` | 少量模块、JUnit与原有测试覆盖映射 | M2 |
| M4 | `llm-asr-76d.5` | 分层 CI/Lint/本地verify/离线门槛 | M3 |
| M5 | `llm-asr-76d.6` | Android 仪器测试、fake provider、IME测试宿主 | M3 |
| M6 | `llm-asr-76d.7` | 优先核验首页insets/返回/窗口兼容 | M0；与M1协调writer |
| M7 | `llm-asr-76d.8` | 逐页 UiState/ViewModel/生命周期编排 | M4、M5、M6 |
| M8 | `llm-asr-76d.9` | 用户确认 Views/Compose UI ADR及保存规格 | M7 |
| M9 | `llm-asr-76d.10` | 已选方案的逐页资源化、主题、无障碍和适配 | M8 |
| M10 | `llm-asr-76d.11` | release/版本/签名/R8/16KiB/发布门槛 | M9、M2、M4、M5及旧设备/质量任务 |

```text
M0 ──→ M1 ──→ M2 ──→ M3 ──→ M4 ─┐
 │                       └──→ M5 ─┼──→ M7 ──→ M8 ──→ M9 ──→ M10
 └──→ M6 ────────────────────────┘                        ↑
                          原生/CI/测试与设备/质量发布门槛 ─┘
```

M6 可优先做窄修，不必须等完整现代化，但不得和构建writer并写入口/配置。M4/M5仅在文件范围和共享构建输出隔离后允许并行；同一worktree的产品实现默认一个writer。M10 是长周期发布 gate，不意味着早期 debug工程交付必须等待全部产品质量关闭。

## 5. 分阶段技术方案与验收

### M0：先交接，后迁移

`llm-asr-ap5` 仍是全量 Java→Kotlin 活动任务，原授权明确排除 Gradle/Compose/协程。新路线不会默默扩大它的范围、替换owner或关闭它。

未来接手者与用户/原owner确认：从哪个稳定提交开始、旧迁移继续/暂停/隔离到哪个范围、唯一writer是谁。不要求先把52Java全部迁完：AGP应能编译现有混合源码。owner/runtime未知或仍working时，不抢占；记录blocker。

冻结当前代码、测试/产物身份、native输入/签名及回退路径，明确本轮只验证哪一项。设备验收任务旧描述可能含过时hash，分派实际设备测试前由其合法writer更新。

### M1：只换构建，不换业务

建立标准 `settings.gradle.kts`、模块构建配置、Wrapper和版本管理。初期可用sourceSets接现有目录，避免一轮同时改目录/业务/工具版本；测试与工具报告输出采用每次构建独立路径。

AGP接管编译/资源/DEX/打包。已有native库作为**显式hash校验输入**过渡接入；生成assets、模型清单/许可证、JNI与post-package检查定义inputs/outputs并连接任务图，不在configuration阶段下载或执行生成步骤。沿用本地签名时不在仓库保存密钥。

验收：声明的 `./gradlew` 命令可测试/生成 APK，host竞态/负例/mutants、Java/Kotlin/JNI、DEX runtime定义、权限/组件/签名/输入绑定均通过；D8 metadata异常不可吞。AGP可能改变APK容器和DEX布局，**不要求整包逐字相同**；首批native数学输入/产物身份应保持，差异必须解释并停止不安全交付。缓存准备与`--offline`证据分开记录。

### M2：让 native 构建成为一等输入

在明确CMake target与独立native artifact任务之间选一条可验证路径。固定源revision/归档hash/补丁/构建flag/NDK/依赖；KleidiAI下载失败不得静默降级。保持单DSO/单C++runtime，避免引入与已有布局不一致的头文件/旧对象。

验收：全新临时构建目录可从受控源码/缓存生成native/APK，不读旧`.work`对象；JNI生成头/descriptor/实际符号通过，报告包含MNN/patch/flags/工具指纹。若构建结构导致native字节改变，先解释与对照，不改期望SHA凑green。ELF/RELRO/包装模式与16KiB运行分别检查；静态对齐不等于设备通过。

### M3：少量模块与测试标准化

候选为 `:app`（页面/IME装配）、`:core`（纯JVM算法/状态机）、`:asr-runtime`（Android音频/平台/JNI）；按实际依赖调整，不机械套包名。不立刻拆十几个feature模块，也不必引入Hilt。

将main()回归逐步接JUnit，保留生产实现、latch、异常身份、mutation和清理断言；明确覆盖映射，不以测试数量相同作为等价证明。纯core不得出现android.*或org.json依赖；编译边界有真实负例。

### M4/M5：先有验证，再做UI生命周期迁移

CI至少分为快速核心/Lint/格式/架构、全APK/JNI/包身份、native复现三层；提供同源本地verify命令。选择远端CI平台、联网依赖获取/上传/触发均需明确授权，本轮/后续默认不push。缓存受输入指纹控制，不上传权重、私人内容或密钥。

Android测试构建覆盖Activity重建、权限拒绝、SAF fake provider阻塞/失败、选择回调/导出票据、IME宿主输入框/密码/换框/解绑/旧结果。fake inference降低资源需求；真实JNI/MNN与麦克风走另外的授权套件。

M5交付的是测试基础设施；缺授权/环境的实际运行写not run，不能因此宣称Android功能已验收，也不能把source检查代替实际测试。设备结果归`llm-asr-tc3`并绑定当次产物。

### M6/M7：窗口与状态/生命周期

先核首页target35 insets风险、字体/横屏/导航模式和返回录音策略。之后逐页引入不可变UiState、UDF/ViewModel、生命周期订阅、ActivityResult/权限/返回实现。

推理/模型操作由应用级执行域持有，页面仅订阅/提交；页面销毁不提前放开真实阻塞中的owner。ViewModel帮助配置重建，但不恢复进程死亡中的JNI；SavedState只保存经批准的非敏感UI状态。IME保持专门Service/session controller，不让重新显示复活旧预览。

### M8/M9：UI方案是显式决策

先评估XML/ViewBinding/Material与Compose/Material3的维护收益、依赖兼容和受限主机构建成本；用户批准再实现。三个页面与IME是否统一导航/主题单独选择，single Activity不是硬要求。

逐页资源化文案/主题/间距，适配深浅色、大字体、触控目标、无障碍及大屏；UI测试与截图只记录授权公开测试内容。保持业务协议，不同时加VAD、音频格式、历史数据库、驻留或联网下载。

### M10：发布是新gate，不是给debug改文件名

建设debug/release/version身份配置；release `debuggable=false`。正式密钥由用户授权安全提供，缺失则blocked，不提交或自动生成生产身份。R8逐步启用，JNI/回调/入口keep规则有运行验证；mapping/native符号置于受控产物路径。

检查16KiB ELF/包装与真实运行、资产/依赖/示例音频再分发许可、隐私政策、签名升级、APK及如需AAB。质量/设备旧任务是发布前置；若用户选择带已知限制发布，必须显式风险处置并由合法writer调整gate，不能强关未完成任务。商店发布、远端推送/上传不在此任务默认授权内。

## 6. 已有任务复用，不重复登记

| 原任务 | 与路线的关系 |
|---|---|
| `llm-asr-ap5` | Kotlin迁移现owner；M0明确交接，不强制先全迁完、不改其范围 |
| `llm-asr-tc3` | 当前候选APK真实设备/生命周期验证；M10依赖，实际运行前刷新身份 |
| `llm-asr-ujz` / `llm-asr-cl4` | 人工有许可质量集、前端与oracle边界；M10依赖，不放宽容差 |
| `llm-asr-rpb` / `llm-asr-mfe` | 已知Worker恢复与日志测试；独立窄修，不归因“换Flow即可修好” |
| `llm-asr-msj` | 产品批次/API/性能边界决策，未被现代化epic取代 |
| `llm-asr-z1w` | 引擎冷暖/内存/驻留量化候选，需质量与设备基线，另授权 |
| `llm-asr-pa1` / `llm-asr-8po` | VAD/长音频格式产品任务，不混入UI与构建 |
| `llm-asr-9fe` | 推理API调用方/鉴权/服务边界，未授权联网/独立进程 |

原任务状态/依赖未因本路线自动解除。新epic与这些任务建立关联；仅发布gate增加设备/质量前置，不让所有工程任务被产品功能阻塞。

## 7. 新 Agent 接手协议

建议第一个新Agent只接 **`llm-asr-76d.1`（M0）**，不要直接领取整epic“完成全部现代化”。

1. 阅读根[AGENTS.md](../AGENTS.md)、本文、目标`bd show <id>`及依赖；核cwd/worktree/branch/current Git diff、必要Beads memories。
2. 核实际runtime与`llm-asr-ap5`等重叠owner；unknown/working/冲突时停止协调，不凭旧文档猜无人执行。
3. 由用户/Steward明确 `role: project-agent`、任务ID、Agent/owner/session、基准commit、范围/禁区、Beads writer、回报渠道及验收命令；未授权不委派worker。
4. 确认依赖满足和领取许可后，按当前CLI help使用显式ID原子claim；claim失败不reassign。建立`.planning/<task-id>/task_plan.md`、`findings.md`、`progress.md`，一计划一writer。
5. 复杂阶段分批实现/验证；遇owner冲突、工具组合不兼容、需权限/私人数据/密钥、native身份或安全契约无法保持时记录blocker和下一输入并停止，不绕过门槛。
6. 按AGENTS完成测试/必要审查/输入-产物指纹核对与暂存安全审计，本地提交后才交付；不push、不amend已有历史。关闭task需真实满足本任务验收，Agent done不等于完成。

参考查询（运行前核当前CLI help）：

```bash
bd show llm-asr-76d
bd show llm-asr-76d.1
bd show llm-asr-ap5
bd ready
bd blocked
bd dep cycles
```

`bd status`汇总与逐条记录可能有口径差异，分派需显式show/list依赖和状态，不能只看ready计数。epic父子关系不是自动串行执行，blocking依赖才是阶段gate。

## 8. 本轮证据边界与参考

本轮只登记任务/文档、检查依赖图/文本一致性并本地提交；**未编译Gradle工程、未构建新APK、未运行Android/设备/麦克风测试。**不能以本路线存在宣称现代化已实现。具体检查结果见`.planning/llm-asr-x1e/`和登记任务。

官方参考（实施时重新核查版本与要求）：

- [Gradle build overview](https://developer.android.com/build/gradle-build-overview)
- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Kotlin / D8 / R8 compatibility](https://developer.android.com/build/kotlin-support)
- [Edge-to-edge in Views](https://developer.android.com/develop/ui/views/layout/edge-to-edge)
- [16 KB page size support](https://developer.android.com/guide/practices/page-sizes)
