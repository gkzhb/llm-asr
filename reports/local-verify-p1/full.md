# P1-A B3b 完整本地工程验证

2026-10-15，task `llm-asr-76d.5`；父 owner/唯一 Beads writer `p1-steward`，验证/报告唯一 writer `p1full-test`。主 checkout `main` / HEAD `7eace17a8ea636db6010f95d2229c28dc2f78467`，index空。**工程验证通过待安全提交；本leaf未stage/commit/push，不宣称APK已交付或M4关闭。**

## 唯一完整执行与真实终态

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

- bg `b619c639e` durable completed / exit0，无超时、无第二Gradle链。UTC 06:16:53.576→06:24:01.629，elapsed **428.052s**，Gradle `BUILD SUCCESSFUL in 7m 5s`。
- **111 actionable tasks: 111 executed**。日志140条唯一task行，119条无状态行包含8无action聚合，其余12 NO-SOURCE /6无action UP-TO-DATE /3 SKIPPED；不将140或119冒充action数。根verifyDebug与所有原host/ABI/JUnit/module/JNI/native/APK及新增Lint/格式/source/入口/fixture/checksum required gate实际执行。
- pipeline Gradle0/tee0/DEXscan1。Nix offline/空substituters-builders/maxjobs0/no锁写；Gradle strict/offline/no-daemon、单worker/JVM1536m、JUnit512m、Kotlin in-process、native并发2。无下载、metadata/lock/pin期望修改或降低门槛。
- 完整log SHA256 `96800bb18ba4c74b0be7262a2e00c7643eb0c3193ae9dd0621c74ef316073558`；execution SHA256 `4cb8e7f206ea142f9c9bdcd13c8d44d6abbc05e5121d1b338beac04f085fd56c`。精确argv/启动runner和driver SHA/PID及proc start identity/exit/log在 `.work/p1-full/full-{launch,execution}.json`，runnerPID754970、commandPID754983；不是聊天启动回执当完成。

## 旧证据保护与编译范围

先按权限fix批准freeze `c84a2641c291ffa037b2145bfb3fb8c91473e73e91b49443d9bebd10f71f5599` 核195build/80native exact；保留632历史文件逐SHA。166份可覆盖生成输出备份至 `.work/p1-full/baseline/`，含旧APK5216037 bytes / SHA `a0494d25f09b4352d30847af00228ee29a2589dbf98e812276018eb12072a3f3`，旧B2/blocked/typed/失败证据不重写成current。

只清12个已知本项目generated class/Kotlin/JUnit/APK目录，逐路径清单在 `cleaned-generated-paths.json`；不删共享cache、native578对象/源码、权重、音频、签名key或用户文件。`--rerun-tasks`重编实际模块Java/Kotlin及JNI；native578 MNN对象逐SHA保持，只JNI复编与最终增量链接，不冒全native clean重编/空缓存hermetic。

## 本次完整测试证据

| 门槛 | 本次结果与边界 |
|---|---|
| 原host/ABI/latch/生产变异 | Java/Kotlin ABI43、recording20、生产PartRecovery红绿15、IME177、真实capture12phase；ActivityWindow82checks及7个可编译生产mutants；model3mutants均编译成功后被特定行为诊断杀死exit1。其余原owner/隐私/文件/日志/报告checker回归保留，不以编译错误或timeout算行为红。 |
| JUnit | core34/runtime1 = **35 tests /0failure /0error /0skip**。逐testcase清单 [full-junit.json](full-junit.json)。XML SHA core `2414eee357f800d26e08a1ee593eb0b9f666e806b2fccc37c524a861fb8ad5a5`；runtime `ae26bb0fb48bb7abe7269c7aa70032f9cc7c27796bf097d2b76a22f8334e5201`。不是35条独立Android仪器测试。 |
| 实际模块编译边界 | core正例及Android/JSON/JNI/AppGraph四目标负例；runtime core/JNI/Android正例及禁止AppGraph负例，均实际javac classpath特定诊断。owner core50/runtime5/app8唯一，DEX定义和重复owner检查通过。 |
| JNI/native | 全10Kotlin+53Java/真实生成JNI头/NDKobject/实际linkedDSO符号一致；578对象、来源树/patch/source-lock、compiler、配置、单DSO/staticC++、ELF LOAD16KiB/W^X/RELRO/nonexecstack及真实ELF坏配置负例通过。最终DSO仍 `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`。 |
| 生产Lint | 两模块fresh XML均0Error/Fatal；App17Warning/runtime10Warning。13 gate+8 consumer各66artifacts/171edges（含原constraints）/actual files SHA精确通过；无baseline/fix任务执行，gate枚举任务不等该任务运行。 |
| 独立实际Lint fixture | full中的 `verifyLintEvidence` 新建 `run-grt2nmtn`：graph0、工件身份1/边身份1、共享生产validator files缺1/多1/替换1、普通marker恢复0；默认AGP green0=0→唯一API33/min29目标NewApi Error red=1→原bytes green1=0，具体file/line、XML/source/log/command/exit齐全。普通共享validator seam不伪装默认AGP可变classpath。 |
| 实际strict checksum | full中的 `verifyDependencyEvidence` 新建 `dependency-negative-oPC13s`，original0→target1→restored0。唯一 `com.android.tools.build:gradle:8.6.1 / gradle-8.6.1.jar` fresh HTML tbody expected全零、actual `9697733a215f2ed42e5b2fc4053484ec832eab29584ebced94d92c91090051c1` 与cache工件SHA一致；恢复metadata等主原bytes，非网络/缺cache失败当红。 |
| 其他护栏 | format24显式文本、source架构/隐私、入口allowlist、tee/grep/DEX故障传播、完整graph parser目标负例/恢复均执行。mock入口不冒真实图；此前接受fast35实际图历史仍保留，本次full不是重跑独立graph-fast。 |

App Lint XML SHA `6fc8964b6e020d845d6508eae2a0fd0a1403f0b655e98dc429d2ac6b9adeb62f`；runtime `54f11ee95e5f86aa3dabba916fc4f5feb416c5ca86677a394b1485ecf9bc1c3d`。App17=SetTextI18n9/UnusedAttribute3/GradleDependency1/ChromeOsAbiSupport1/DataExtractionRules1/UsableSpace1/MissingApplicationIcon1；runtime10逐source/message是App的SetTextI18n9+UsableSpace1重复。UI资源化、legacy属性、依赖升级、额外ABI、backup/icon及空间API明确defer，不抑制或声称warning为零。既有P10 NOTICE/kxmlvariant/CVE/PGP边界未关闭。

本次完整入口启动时已保存driver/被测入口全SHA，所以不再借旧B2缺launch-time driver SHA的限制描述本次；旧历史局限原样保留。被测源码/metadata/锁/工具身份/collector/生产逻辑全冻结，没有为凑full改动。

## 当前signed APK与输入绑定（待安全提交产物）

| 属性 | 实际核验 |
|---|---|
| 路径 | `android/app/build/outputs/apk/debug/app-debug.apk` |
| 大小 / SHA256 | **5216237 bytes** / `3fe42d623e8f9b60e7d34fd63f432de2aa780520395fc8aeeb5bbbc1ca32df10` |
| 包 / 版本 | `org.llmasr.minimal` / `0.6-debug` / versionCode6 |
| SDK / ABI | min29 / target35 / compile35 / 仅arm64-v8a |
| 权限 / policy | 唯一plain RECORD_AUDIO；debuggable=true、allowBackup=false、extractNativeLibs=true；microphone required=false；未扩白名单 |
| 组件 | 恰3Activity：MainActivity（launcher）、ModelManagementActivity/LogsActivity（nonexported）；恰1AsrImeService（exported、BIND_INPUT_METHOD/action/metadata）；单进程、三个Activity现代back opt-in保持 |
| 签名 | 原PKCS12流程，**1 signer/v3=true**，v1/v2/v3.1/v4=false；证书SHA256 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`，无新key/读key内容 |
| ZIP/native/assets | 30文件、ZIP完整无重复；唯一 `lib/arm64-v8a/libqwen_asr_jni.so` 为原d5bacffc…a416；6精确assets（4NOTICE/license+model-manifest+公开sample）、全部SHA通过，无权重/中间jar/o/rsp；所有DEX owner唯一 |
| 实际checker/report | `:app:verifyDebugPackage` 真实通过，生成 `android/app/build/verification/debug/run-655b61c010574299899adaa31fe0111d`；apksigner/aapt2/zipalign、legacy实际checker、JNIactualclasses/DSO、6报告hash绑定均通过，collector再核binding |
| current输入 | 包报告195build map与批准freeze/启动前/终态后/current逐项exact；native verification80map exact；样例/两配置/DSO external hashes单列，只读配置未加载权重。[full-inputs.json](full-inputs.json)记录完整清单 |

`product_baseline=82a5563` 是既有产品基线标签，不是本次HEAD或commit；本次HEAD仍7eace17且存在本轮已审未提交源码，195map才是当前输入绑定。六份fresh真实merged/stagedManifest由共享精确权限checker核对，不只以XML预测signedAPK。签名/包校验已真实兑现，设备运行仍未做。

## 资源与采样限制

主机MemTotal16293168KiB（约15.54GiB）、SwapTotal33554428KiB。5秒有界86次command后代采样观察最多**4同时相关JVM**，同时JVM RSS和最大3125124KiB≈2.980GiB、相关进程树3155308KiB≈3.009GiB；host MemAvailable采样最低9748416KiB≈9.297GiB。父Gradle与fixture/checksum子JVM确可并驻，但各task单worker串行、没有另一并行Gradle链。RSS和重复计算共享页；未测PSS/全机峰值，短进程/未见detached可能漏采；no-daemon不等于只有一个JVM。终态无相关Gradle/Lint/native后台。

## 审查、最终文档与未做项

权限fix fresh只读review NO BLOCKING/74项，B2及三修复审查已由父接受；均用户指定同模型 `newapi/gpt-6.1-sol` fresh上下文隔离，**非跨家族独立验证**，本leaf不重跑/追溯扩展审查。新full证据由父消费后交reviewagent最后只读证据/安全清单审，随后另阶段限定本地commit；不stage -A混入旧研究/其他planning或 `.beads.gate.lock`。本leaf只更新本报告、status/docs/B3执行计划及必要非敏感JSON输入/证据/JUnit清单；rawlogs/APK/cache/密钥/私有会话不入仓库。

ignored总交接 `.work/p1-full/handoff.md`，final freeze `.work/p1-full/final-input-freeze.json` 分build/native/supplemental/evidence，旧snapshot不自动rehash/改写；[full-evidence.json](full-evidence.json)导航hash。文档在formatinventory内者收尾重查格式，文档域变化不污染APK195map。

**Not run**：设备4KiB/16KiB安装加载/JNI/录音权限撤销/麦克风、IME/InputConnection/生命周期、SAF/私人文件、模型权重加载或计算、远端CI/provider/部署、release/R8/新签名、空缓存或另一机器hermetic、全native clean重编。主机fakes/ELF静态16KiB不等设备验收；P1-B/C不在此slice。不关闭M4，不push；安全本地提交尚未完成，不能通知用户已交付可验证APK。
