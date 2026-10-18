# cccc / knots 维护源码复杂度基线

任务 `llm-asr-jrq`，2026-10-18；源码基点 `4e73662d9210f083146b7dba533426896d3be083`。本批只新增审查文档/轻量工具/文本证据，**不改产品、不产生 APK、不证明缺陷/性能/安全或设备验收**。审查入口：[llm-asr-code-review](../../.agents/skills/llm-asr-code-review/SKILL.md)。

## 复现与原始证据

- [sources.json](sources.json)：精确209维护源码/构建源码路径、SHA256、bytes、分组与支持工具，policy `maintained-tracked-v1`。
- [execution.json](execution.json)：真实版本、PATH/解析后二进制与SHA、完整显式文件 argv、exit、stdout/stderr SHA。
- [cccc.json](cccc.json) / [knots.json](knots.json)：未经改写的 CLI 原始 JSON；[cccc stderr](cccc.stderr.txt) / [knots stderr](knots.stderr.txt) 均空，两个 CLI **exit0**。
- [normalized.json](normalized.json)：按工具分别展开的函数与 coverage；cccc children 保留 ancestry，行号和 occurrence 仅快照定位。不得把派生视图冒 raw。
- [checks.json](checks.json)：当批复测/脚本/文档与保护检查的非敏感摘要。具体复测和匹配步骤见 [比较规则](../../.agents/skills/llm-asr-code-review/references/comparison.md)。

仓库根执行 `python3 -B .agents/skills/llm-asr-code-review/scripts/baseline.py collect --out .work/review-complexity-current`，必须新输出目录。runner 只用标准库+已安装CLI，不执行被分析源码；cccc1.8.3=`--no-config --no-cache --jobs 2`，knots1.18.0=`--format json --jobs 2`。knots help无config/cache开关，本批未用阈值、include/exclude、recursive、compile-commands或缓存。完整argv在execution中，非目录递归扫描。工具未纳入依赖锁，版本/二进制变更先记 unknown，不自行安装。独立重复采集双CLI再次exit0，manifest/raw/stderr/execution/normalized七项逐字节相同；compare无自动指标变化，仅171个重名/重载人工映射项。首次收集器曾误拒同一行多个lambda而exit2，已修为保留occurrence并补回归；失败记录保留，不改写成CLI失败或旧成功。

## 范围与计数（不混组/不混指标）

| 组 | 库存 | cccc输入/函数记录 | knots输入/函数记录 |
|---|---:|---:|---:|
| production | 64 | 52 / 681 | 64 / 631 |
| test | 76 | 72 / 968 | 73 / 601 |
| tooling | 69 | 46 / 122 | 51 / 115 |
| 合计 | 209 | 170 / 1771 | 188 / 1347 |

生产=app/src的63实现文件+native/apk JNI；core/asr-runtime构建映射仍引用app物理源码，不按模块重复计数。测试含test-host（包括main宿主Activity）、host/仪器测试和fixtures；工具含scripts/model-tools/native P0 harness与构建脚本。Git tracked库存排除vendor/build/generated、历史reports/docs快照、下载缓存/.work、模型/音频；锁/资源/JSON/patch不计函数指标，仍须人工审其变更。新未跟踪源码须单列分析，不能被tracked筛选悄悄漏掉。

### 覆盖盲区

- cccc **不支持本批12 `.kt` 与6 `.kts`**：实际小样本RequestRunner.kt和core/build.gradle.kts均exit0但files[]，不是复杂度0。knots报告8个Kotlin文件的命名函数，4个仅enum/字段声明文件零记录；6个`.kts`均零记录，不能代表Gradle DSL任务/闭包没有复杂度。
- 两工具均不覆盖21库存项：14 `.sh`、Nix、2 CMake、2 Gradle/Groovy fixture、Unix/Windows wrappers。逐路径见manifest的空tools。只做人工控制流/构建接线审查，不填0或宣称全仓覆盖。
- cccc JNI `native/apk/asr_jni.cpp` 报 line72/75/80 三条syntax error，仍exit0且产出7函数；该文件为 **partial-parse**。宏/JNI声明不是完整编译AST，本批不跑编译，函数分数仅参考。
- cccc另11文件无函数记录，knots另21文件零记录（包括6 `.kts`、4 Kotlin声明文件和11相同Python文件）。11个Python是顶层命令式脚本，没有 `def`；其分支不在函数排名内。它们均非空，零记录不等于低风险。精确coverage保留unknown，后续增加/消失须人工核。
- 实际空/坏Java探针：cccc空文件无函数，坏语法仍输出函数+parse_error且exit0；knots空文件[]，坏语法仍输出broken函数、exit0且无诊断。**exit0不是语法/完整解析证明**；knots JSON无逐文件parse状态。
- cccc含声明、构造器和匿名children；knots默认不独立计匿名closures，命名/嵌套处理也不同。相同名字的 cognitive 不作等价。cccc file aggregate 与summary函数和可不同（apk_report_binding.py文件3/7，而函数cognitive和2/cyclomatic和6）；下表明确区分文件原值与单函数，不能跨粒度拼合。

## 当前热点与审查优先级

以下是审查阅读顺序，不是已确认缺陷，也不批准大重构。`C/Y`=cccc cognitive/cyclomatic，`C/M`=knots cognitive/mccabe；两列各自真实值。

| 优先对象（production） | 定位 | cccc C/Y | knots C/M | 重点契约与已有测试入口（未在本批重跑） |
|---|---|---:|---:|---|
| 模型终态仲裁 | `android/app/src/org/llmasr/minimal/modelmanagement/ModelManagementController.java:216 terminal` | 93/33 | 37/33 | nesting5；终态/owner释放、锁外通知、错误脱敏；ModelOperationControlTest/ModelNotificationTest/ModelReviewFixTest |
| 模型导入事务 | `android/app/src/org/llmasr/minimal/model/ModelRepository.java:342 importFrom` | 53/35 | 55/41 | nesting4/SLOC80；复用SHA、取消/发布、残片/空间、安全根；ModelRepositoryCancelTest/PartRecoveryTest/ModelProviderBoundaryTest |
| 录音采集 | `android/app/src/org/llmasr/minimal/audio/ForegroundRecorder.java:13 capture` | 28/20 | 38/28 | worker实际释放、start/cancel/commit、上限/超时；RecordingRaceTest/AdmissionBoundaryTest，真实设备仍另验 |
| 页面渲染 | `android/app/src/org/llmasr/minimal/ModelManagementActivity.java:167 render` | 35/32 | 18/32 | 状态显示与真实取消边界/生命周期；源码接线审+Android验证，不把host当UI运行 |
| JNI桥 | `native/apk/asr_jni.cpp:80 Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribeWithListener` | 18/19（partial） | 24/24 | RAII/stream寿命、pending异常、正常结束判据、ABI；InferenceAdapterTest/真实JNI编译和设备分层 |

Kotlin `RequestRunner.kt:42 submit` 仅knots可见：C8/M10/nesting3；重载line40另C0/M1，不能覆盖合并。关键owner/lifecycle不因较低分免审。

cccc生产**文件原始aggregate**：ModelRepository.java=178/184，ModelManagementController.java=164/125，ModelManagementActivity.java=92/112，MainActivity.java=70/104。文件值只定位阅读量，不与单函数阈值混用；knots本批是逐函数数组，没有伪造同名文件指标。

测试热点单列：`tests/LogExportTest.java:28 main` knots C68/M40；`tests/AdmissionBoundaryTest.java:45 main` knots C61/M47；`tests/runtime_log_enum_cleanup_test.py:171 case` cccc C54/Y40；`tests/gradle_lint_verification_test.py:40 run_fixture` cccc C45/Y37、knots C62/M54。长main/fixture可能有大量断言/清理分支，优先查假阳性、回调吞断言、有界收尾；不能与生产事务混排证明产品差。

工具热点单列：`scripts/deploy-minimal-apk.py:22 main` cccc C60/Y33、knots C74/M48；`scripts/verify-android-test-apk.py:185 check_packages` cccc C57/Y38；`scripts/verify-gradle-apk.py:48 check_identity` cccc C34/Y25、knots C31/M26。部署权限/产物身份/gate完整性依旧人工审，本批仅分析文本、未执行这些命令。

建议正式后续开发：先按**改动触及的契约**选窄审查范围，将上述热点与调用者/测试对应，再按skill的新增/相对增长信号要求解释与必要最小处置；不因历史分数高立即拆函数，不靠刷新基线或移动分支降低分数。AIRD/AICP仅knots启发式（例如importFrom AIRD66/AICP76），不升级为项目缺陷概率或AI安全保证。
