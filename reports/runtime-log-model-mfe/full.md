# llm-asr-mfe 模型维护日志闭环：唯一 full 工程验证

## 状态与授权

父/唯一Beads writer `p1-steward` 接受 fresh `mfe-review` 同模型上下文隔离（非跨家族）NO BLOCKING，含finish复杂度有界增长/terminal历史热点与旧E1LOW日志清单处置；审查receipt SHA256 `26f9f90f72e517f3250e98143ef5c3bb62f5d5dbaa34041a8f57ae4fd5f15ae8`。原唯一leaf `mfe-research`、`newapi/gpt-6.1-sol/high` 在 main/base `d48951dda51427f60a57dc622e26c069e892d79d` dirty freeze 下执行，六候选及旧研究/审查证据保持冻结；不重标为测试过未来commit对象。

**唯一正式 full 真实 exit0，当批工程证据及输入/产物绑定审计通过。尚未两阶段安全本地提交，不交付APK、不关闭mfe/M4/M5或设备验收。** near-MAX恢复序号耗尽及picker启动原因覆盖仍未实施。

## 唯一命令与真实终态

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

- `be7d867d4` 自动completed/exit0；2026-10-20T16:13:15.691747Z→16:22:15.834191Z，540.142451944s。
- 关闭后完整log `.work/mfe-full/full.log`：45200bytes，SHA256 `bb0a957fc315e4e08272f20db496be9901b5c0639a81861da5a2263fd10155a2`。runner只允许一次，未重跑、未clean、未下载prepare、未修改expected或门槛。
- Gradle **174 actionable/174 executed**；222唯一task行（含聚合/NO-SOURCE/SKIPPED/无action等，不冒222actions），全部required gate实际执行，无failed/from-cache/device任务。pipeline `Gradle=0 / tee=0 / scan=1`。
- `bd125e864` 后处理自动completed/exit0，仅消费真实当批输出，不再执行build/tests。完整exact argv、输入/logSHA及证据索引保存在ignored `.work/mfe-full/`。活stdout与最终seal日志在关闭前不入SHA清单，避免自采空文件；关闭后另终态receipt绑定。

## 分层实际证据

| 验证面 | 本次实际结果及边界 |
|---|---|
| JUnit | fresh XML core34/runtime1/test-host7，全部failure/error/skip0，方法名单与当前源码精确一致。新logging组通过既有Controller默认main实际在host及core JUnit执行：host输出556 checks，XML stdout亦含同main；数字是断言不是额外JUnit数。 |
| 模型日志切片 | 冻结默认main包含三kind admitted拒绝/finalizer、queued InternalError、pending/terminal observer Error及原Error身份/owner/control释放，正常success/failure/cancel/不重复/INSPECT/busy/stale/retry UUID、telemetry隔离及真实codec合成隐私断言。本full执行fixed行为；可编译exactold red→fixedgreen→oldred→fixed恢复是已接受targeted历史，本full不重造旧red。日志仍best-effort，非坏Store/磁盘/进程fatal持久保证。 |
| 原host/ABI/mutants | canonical混编12Kotlin+68Java，322ABI/枚举生产断言、enum10阶段compile0/精确AssertionError1/恢复0、13cleanup lifecycle故障组；PartRecovery、3模型可编译mutants目标拒绝、ActivityWindow82checks/7可编译mutants、录音fake平台12phase、原owner/IME/模型/日志/导出/source/checker负例均执行。不冒Android设备运行。 |
| 模块/Java/JNI | 实际core/runtime/app/test-host Java/Kotlin及标准仪器源码编译；真实module classpath core !→Android/JSON/JNI/AppGraph、runtime !→app正负例；生成JNI头、NDKarm64 bridge object、真实Java descriptor/linked DSO符号核验通过。 |
| native | 当前80输入与冻结/正式receipt精确一致；578MNN对象内容/mode/inode/mtime保持，DSO/provenance保持；JNI复编/增量最终链接，非全native clean/空缓存/hermetic。ELF64AArch64/LOAD16KiB/W^X/RELRO/nonexecstack/单DSO静态C++与DT_NEEDED门槛通过，不冒4K/16K真机。 |
| fixture行为 | 一次新`behavior-rf6u_n0g`，真实五阶段0/1/0/1/0；7host方法逐阶段编译，两个唯一目标AssertionError，源码恢复。仅JVM fixture，不是Android runner。 |
| Lint故障证据 | 新`run-uo8o7536`：真实默认AGP green0→唯一NewApi API33/min29精确file/line Error1→源码恢复0；工件/边identity及共享production validator classpath缺/多/替换分别目标拒绝，marker恢复。不是cache/编译/网络失败作red，不是默认typedAGP可变性试验。 |
| checksum | 新`dependency-negative-XRCIFw` strict offline help0→唯一AGP8.6.1 jar checksum拒绝1→metadata恢复0；freshHTML具体expected全零/actual-cache SHA `9697733a215f2ed42e5b2fc4053484ec832eab29584ebced94d92c91090051c1`，不是通用帮助文字冒目标。 |

## 生产 Lint 与 warning

app/runtime两份fresh XML均Error/Fatal0。13identity gates/8actual consumers各66工件/171边；test-host Lint未接根图，**not run**，resolve消费者名称不冒执行。

- app17warnings：UnusedAttribute3、GradleDependency1、ChromeOsAbiSupport1、DataExtractionRules1、UsableSpace1、MissingApplicationIcon1、SetTextI18n9。
- runtime10：UsableSpace1+SetTextI18n9，按message/source位置全是app同源重复，无新增类别。
- 沿原明确defer：旧系统忽略Back属性、固定依赖升级提示、arm64-only、未来备份迁移策略、空间提示、开发图标和中文拼接国际化；无baseline/抑制/关闭error/升级依赖。CMake兼容弃用、Java deprecated和固定aapt2 override实验提示另属工具提示，不称零warning。未新增资源峰值采样，不继承旧RSS数值。

## 三个 signed APK 与两个正式receipt

三包原证书SHA256 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c` / v3有效；actual SDK apksigner/aapt2/zipalign exit0，raw报告SHA、ZIP全库存与完整DEX再解析均与receipt精确一致。

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5217737 | `ef7600811e43d8a56a46fe904fd0f7c2811f70b6e4942edc7435e77ccc70a51f` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2585912 | `114d93f14df874124ad03f17357dc7fae1e226c77e23b775c248dbff355d3fd2` |
| instrumentation | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

product为org.llmasr.minimal/0.6-debug/code6/min29,target35/arm64，唯一RECORD_AUDIO及原三Activity/IME组件/debug/backup/back策略，唯一JNI库和精确公开资产许可清单保持，无模型权重/私音频/正文资产。product1327类、host1235类、test43类，无重复definition/产品fixtureJUnitmockrunner泄漏；测试包无权限/native/assets/意外组件/launcher，标准runner/target/required libraries保持。精确14个public concrete `()V`仪器方法仅编译/DEX库存，**不证明runner发现或执行**。

正式独立product：`android/app/build/verification/debug/run-84922a0d56fa4cae8284e3e50f0d1405/`；三包：`build/verification/android-test/run-69941ac0c2c246b59fe8caa4cbda0728/receipt.json`。动态 **213build/10test-module/80native** current/frozen/两个正式receipt逐路径SHA一致，实际包/metadata/签名/输入绑定；非敏感完整摘要与输入见[full-inputs.json](full-inputs.json)。

## 输出保护/六候选与工具身份

- preflight `b2b96f022` completed0：1863已知generated文件/旧三包/旧receipt等逐mode/bytes/SHA备份，未删目录/cleanMNN。保护4069明确文件全部保持；2555旧isolated文本证据保持，旧research/review/implementation六候选及summary完全冻结，不回写pending历史。
- 1863备份原字节完整，1838当前保持/25已枚举正常generated delta：两重建APK、新Controller.class两处JNI-check、stage/native验证inputSHA、JUnit/XML/binary与HTML时序/LintHTML。其余578对象/DSO/provenance/锁metadata不变；两新随机正式receipt是新增，不覆盖旧receipt。保护限显式清单，不宣称全ignored/cache/private扫描。
- tool entry+resolved分域核验，与上批已接受当前Nix2.34.9身份及JDK/SDK/NDK/Kotlin/CMake等精确SHA保持，未切PATH/改flake/锁补绿；未另做供应链CVE/闭包审计。单worker/JVM1536m、JUnit512m、Kotlininprocess/nativeparallel2与既有fixtureLintheap768m策略保持，无第二Gradle并行。

## 未运行与唯一下一步

设备/Androidrunner发现执行/安装ADB模拟器/micprivate/系统SAF-Binder-grant-InputConnection/IME及真实生命周期/JNI-device/4KiB16KiB运行、模型计算下载、远端CI/release/R8、stagecommit/pushamend均not run。主机/ELF/parsedAPK不替设备验收。

父消费`.work/mfe-full/handoff.md`、closure/候选manifest/实际证据后，另阶段安排安全候选只读审与精确本地提交；成功提交及身份绑定核实前不交APK。本leaf完成后停写，不自行第二full/提交，不关闭剩余mfe两项或关联任务。
